package com.greenaltambe.vela.config

import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.innertube.ClientProfile
import kotlinx.coroutines.test.runTest
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class RemoteConfigTest {
    private val now = Instant.parse("2026-10-28T00:00:00Z")
    private val clock = object : Clock { var t = now; override fun now() = t }
    private val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val otherPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)

    private fun payload(version: Long = 1, issued: Instant = now, expires: Instant = now + 30.days, extra: String = "") =
        """{"version":$version,"issuedAt":"$issued","expiresAt":"$expires","disabledProfiles":["IOS"],"canaryVideoIds":["abc12345678"]$extra}"""

    private fun sign(p: String, pairUsed: java.security.KeyPair = pair): ByteArray {
        val bytes = p.encodeToByteArray()
        val sig = Signature.getInstance("Ed25519").run { initSign(pairUsed.private); update(bytes); sign() }
        return """{"payload":"${b64(bytes)}","signature":"${b64(sig)}"}""".encodeToByteArray()
    }

    private val ios = """{"name":"IOS","clientNameId":5,"enabled":true,"userAgent":"ua","client":{"clientName":"IOS","clientVersion":"1"}}"""
    private val android = """{"name":"ANDROID","clientNameId":3,"enabled":true,"userAgent":"ua","client":{"clientName":"ANDROID","clientVersion":"2"}}"""
    private val bundled = ClientProfile.parse("""{"profiles":[$ios,$android]}""")

    private fun verdict(file: ByteArray, key: java.security.PublicKey? = pair.public, last: Long = 0) = RemoteConfigCodec.verify(file, key, last, now)
    private fun rejected(v: ConfigVerdict) = assertIs<ConfigVerdict.Rejected>(v).reason

    @Test fun validConfigIsAccepted() {
        val c = assertIs<ConfigVerdict.Accepted>(verdict(sign(payload()))).config
        assertEquals(setOf("IOS"), c.disabledProfiles); assertEquals(listOf("abc12345678"), c.canaryVideoIds)
    }

    @Test fun tamperedPayloadIsRejected() {
        val good = sign(payload()).decodeToString()
        val env = kotlinx.serialization.json.Json.parseToJsonElement(good) as kotlinx.serialization.json.JsonObject
        val changed = Base64.getEncoder().encodeToString(payload(extra = ",\"x\":1").encodeToByteArray())
        val tampered = good.replace((env["payload"] as kotlinx.serialization.json.JsonPrimitive).content, changed).encodeToByteArray()
        assertTrue("signature" in rejected(verdict(tampered)))
    }

    @Test fun wrongKeyIsRejected() = assertTrue("signature" in rejected(verdict(sign(payload()), key = otherPair.public)))
    @Test fun noBundledKeyMeansNothingIsTrusted() = assertTrue("public key" in rejected(verdict(sign(payload()), key = null)))
    @Test fun rollbackAndReplayAreRejected() {
        assertTrue("rollback" in rejected(verdict(sign(payload(version = 3)), last = 3)))
        assertTrue("rollback" in rejected(verdict(sign(payload(version = 2)), last = 3)))
    }
    @Test fun expiredIsRejected() = assertTrue("expired" in rejected(verdict(sign(payload(expires = now - 1.days)))))
    @Test fun issuedInTheFutureIsRejected() = assertTrue("future" in rejected(verdict(sign(payload(issued = now + 5.days)))))
    @Test fun oversizedIsRejectedBeforeParsing() = assertTrue("larger" in rejected(verdict(ByteArray(RemoteConfigCodec.MAX_BYTES + 1))))
    @Test fun garbageIsRejected() = assertTrue(rejected(verdict("nope".encodeToByteArray())).isNotEmpty())

    private fun manager(url: String, store: InMemorySettingsStore = InMemorySettingsStore(), key: java.security.PublicKey? = pair.public, fetch: suspend (String) -> ByteArray?) =
        RemoteConfigManager({ url }, fetch, store, key, clock)

    @Test fun emptyUrlMeansBundledOnlyAndNeverFetches() = runTest {
        var fetched = 0
        val m = manager("") { fetched++; null }
        m.refresh()
        assertEquals(0, fetched); assertEquals(bundled, m.profiles(bundled)); assertTrue("bundled only" in m.status.value)
    }

    @Test fun httpIsRefusedWithoutFetching() = runTest {
        var fetched = 0
        val m = manager("http://example.com/c.json") { fetched++; null }
        m.refresh()
        assertEquals(0, fetched); assertTrue("https" in m.status.value)
    }

    @Test fun offlineKeepsTheLastGoodConfig() = runTest {
        val store = InMemorySettingsStore()
        var answer: ByteArray? = sign(payload(version = 1))
        val m = manager("https://x/c.json", store) { answer }
        m.refresh(); assertEquals(listOf("ANDROID"), m.profiles(bundled).map { it.name })   // IOS switched off by the kill switch
        answer = null; m.refresh()
        assertEquals(listOf("ANDROID"), m.profiles(bundled).map { it.name }); assertTrue("could not fetch" in m.status.value)
        // a restart reads it back, verified again
        assertEquals(listOf("ANDROID"), manager("https://x/c.json", store) { null }.profiles(bundled).map { it.name })
    }

    @Test fun rollbackOfAnAcceptedConfigIsRefusedByTheManager() = runTest {
        var answer = sign(payload(version = 5))
        val m = manager("https://x/c.json") { answer }
        m.refresh(); answer = sign(payload(version = 4, extra = ""))
        m.refresh()
        assertTrue("rollback" in m.status.value); assertEquals(5, m.config()!!.version)
    }

    @Test fun expiredAcceptedConfigFallsBackToBundled() = runTest {
        val m = manager("https://x/c.json") { sign(payload(expires = now + 2.days)) }
        m.refresh(); assertNotNull(m.config())
        clock.t = now + 3.days
        assertNull(m.config()); assertEquals(bundled, m.profiles(bundled))
    }

    @Test fun remoteProfilesReplaceBundledAndKillSwitchAppliesToBoth() = runTest {
        val vr = """{"name":"ANDROID_VR","clientNameId":28,"enabled":true,"userAgent":"ua","client":{"clientName":"ANDROID_VR","clientVersion":"1.60.19"}}"""
        val m = manager("https://x/c.json") { sign(payload(extra = ",\"profiles\":[$ios,$vr]")) }
        m.refresh()
        assertEquals(listOf("ANDROID_VR"), m.profiles(bundled).map { it.name })
    }

    @Test fun badSignatureNeverReplacesAGoodConfig() = runTest {
        var answer = sign(payload(version = 1))
        val m = manager("https://x/c.json") { answer }
        m.refresh(); answer = sign(payload(version = 2), otherPair); m.refresh()
        assertEquals(1, m.config()!!.version); assertTrue("signature" in m.status.value)
    }

    @Test fun publicKeyParsing() {
        val raw = pair.public.encoded.takeLast(32).toByteArray()
        assertNotNull(RemoteConfigCodec.publicKeyFromBase64(b64(raw)))
        assertNull(RemoteConfigCodec.publicKeyFromBase64("AAAA")); assertNull(RemoteConfigCodec.publicKeyFromBase64(""))
    }

    /** The file written by scripts/sign-remote-config (openssl) verifies in the app's own code. */
    @Test fun fileSignedByTheScriptIsAccepted() {
        val res = { n: String -> RemoteConfigTest::class.java.getResource("/fixtures/$n")!!.readBytes() }
        val key = RemoteConfigCodec.publicKeyFromBase64(res("remote-config-test-pubkey.txt").decodeToString())
        val c = assertIs<ConfigVerdict.Accepted>(RemoteConfigCodec.verify(res("remote-config-signed-by-script.json"), key, 0, now)).config
        assertEquals(setOf("IOS"), c.disabledProfiles)
    }
}
