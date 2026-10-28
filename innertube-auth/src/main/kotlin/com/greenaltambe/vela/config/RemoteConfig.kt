package com.greenaltambe.vela.config

import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.core.settings.SettingsStore
import com.greenaltambe.vela.innertube.ClientProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** What a signed remote configuration may change: client profiles and their kill switches, and the health canary videos. Nothing else. */
class RemoteConfig(
    val version: Long,
    val issuedAt: Instant,
    val expiresAt: Instant,
    /** Replaces the bundled profile list when present. */
    val profiles: List<ClientProfile>?,
    /** Profile names switched off, whether they come from here or from the bundled list. */
    val disabledProfiles: Set<String>,
    val canaryVideoIds: List<String>,
)

sealed interface ConfigVerdict {
    class Accepted(val config: RemoteConfig) : ConfigVerdict
    class Rejected(val reason: String) : ConfigVerdict
}

/**
 * Envelope: `{"payload":"<base64 of the JSON config>","signature":"<base64 Ed25519 signature over those payload bytes>"}`.
 * A config is accepted only if: the file is at most [MAX_BYTES]; the signature verifies under the bundled public key; its
 * `version` is greater than the last accepted one (no rollback); it has not expired; it was not issued in the future.
 */
object RemoteConfigCodec {
    const val MAX_BYTES = 64 * 1024
    private val spkiPrefix = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

    /** A 32-byte raw Ed25519 public key, base64 encoded (what `scripts/sign-remote-config --gen-key` prints). */
    fun publicKeyFromBase64(text: String): java.security.PublicKey? = runCatching {
        val raw = Base64.getDecoder().decode(text.trim())
        require(raw.size == 32)
        KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(spkiPrefix + raw))
    }.getOrNull()

    fun verify(file: ByteArray, key: java.security.PublicKey?, lastVersion: Long, now: Instant): ConfigVerdict {
        if (key == null) return ConfigVerdict.Rejected("no public key is bundled, so no remote configuration can be trusted")
        if (file.size > MAX_BYTES) return ConfigVerdict.Rejected("file is larger than ${MAX_BYTES / 1024} KiB")
        val env = runCatching { Json.parseToJsonElement(file.decodeToString()).jsonObject }.getOrNull() ?: return ConfigVerdict.Rejected("not a JSON envelope")
        val payload = runCatching { Base64.getDecoder().decode(env["payload"]!!.jsonPrimitive.content) }.getOrNull() ?: return ConfigVerdict.Rejected("payload is not base64")
        val sig = runCatching { Base64.getDecoder().decode(env["signature"]!!.jsonPrimitive.content) }.getOrNull() ?: return ConfigVerdict.Rejected("signature is not base64")
        val valid = runCatching { Signature.getInstance("Ed25519").run { initVerify(key); update(payload); verify(sig) } }.getOrDefault(false)
        if (!valid) return ConfigVerdict.Rejected("signature does not match")
        val o = runCatching { Json.parseToJsonElement(payload.decodeToString()).jsonObject }.getOrNull() ?: return ConfigVerdict.Rejected("payload is not JSON")
        val version = o["version"]?.jsonPrimitive?.longOrNull ?: return ConfigVerdict.Rejected("no version")
        val issued = o["issuedAt"]?.jsonPrimitive?.content?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return ConfigVerdict.Rejected("no issuedAt")
        val expires = o["expiresAt"]?.jsonPrimitive?.content?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return ConfigVerdict.Rejected("no expiresAt")
        if (version <= lastVersion) return ConfigVerdict.Rejected("version $version is not newer than $lastVersion (rollback refused)")
        if (expires <= now) return ConfigVerdict.Rejected("expired at $expires")
        if (issued > now + 1.days) return ConfigVerdict.Rejected("issued in the future ($issued)")
        val profiles = o["profiles"]?.let { p -> ClientProfile.parse("""{"profiles":$p}""") }
        return ConfigVerdict.Accepted(RemoteConfig(
            version, issued, expires, profiles,
            o["disabledProfiles"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty(),
            o["canaryVideoIds"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
        ))
    }
}

/**
 * Fetches, verifies and remembers the remote configuration. With an empty URL nothing is fetched and the bundled data is used.
 * Any failure (offline, bad signature, rollback, expired, too large, plain http) keeps what was accepted before; an accepted config
 * that has since expired is ignored, so the bundled data applies again.
 */
class RemoteConfigManager(
    private val url: () -> String,
    private val fetch: suspend (url: String) -> ByteArray?,
    private val store: SettingsStore,
    private val key: java.security.PublicKey?,
    private val clock: Clock = Clock.System,
) {
    private val layer = SettingLayer.Global
    private val _status = MutableStateFlow("Remote configuration: bundled only")
    /** One line for Diagnostics: what was last accepted or why the last fetch was refused. */
    val status: StateFlow<String> = _status

    private var current: RemoteConfig? = store.get(FILE_KEY, layer)?.let { f ->
        (RemoteConfigCodec.verify(f.encodeToByteArray(), key, lastVersion = -1, now = clock.now()) as? ConfigVerdict.Accepted)?.config
    }

    /** The config in force now, or null (none accepted, or the accepted one expired). */
    fun config(): RemoteConfig? = current?.takeIf { it.expiresAt > clock.now() }

    private fun lastVersion() = store.get(VERSION_KEY, layer)?.toLongOrNull() ?: -1

    /** Bundled profiles, replaced by the remote list when there is one, minus every disabled name. */
    fun profiles(bundled: List<ClientProfile>): List<ClientProfile> {
        val c = config() ?: return bundled
        return (c.profiles ?: bundled).filter { it.name !in c.disabledProfiles }
    }

    fun canaryVideoIds(): List<String> = config()?.canaryVideoIds.orEmpty()

    suspend fun refresh() {
        val u = url().trim()
        if (u.isEmpty()) { _status.value = "Remote configuration: bundled only"; return }
        if (!u.startsWith("https://")) { _status.value = "Remote configuration refused: the URL must be https"; return }
        val bytes = try { fetch(u) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
        if (bytes == null) { _status.value = "Remote configuration: could not fetch (offline?); using ${describe()}"; return }
        when (val v = RemoteConfigCodec.verify(bytes, key, lastVersion(), clock.now())) {
            is ConfigVerdict.Accepted -> {
                store.put(FILE_KEY, layer, bytes.decodeToString()); store.put(VERSION_KEY, layer, v.config.version.toString())
                current = v.config
                _status.value = "Remote configuration: accepted version ${v.config.version}, valid until ${v.config.expiresAt}"
            }
            is ConfigVerdict.Rejected -> _status.value = "Remote configuration refused: ${v.reason}; using ${describe()}"
        }
    }

    private fun describe() = config()?.let { "version ${it.version}" } ?: "the bundled data"

    private companion object { const val FILE_KEY = "remoteConfig.file"; const val VERSION_KEY = "remoteConfig.version" }
}
