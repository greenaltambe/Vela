package com.greenaltambe.vela.config

import com.greenaltambe.vela.core.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** The public key bundled with the app (`remote-config-pubkey.txt`, base64 of the raw 32 bytes). Blank file = no remote config is ever trusted. */
fun bundledRemoteConfigKey(): java.security.PublicKey? =
    RemoteConfigManager::class.java.getResource("/remote-config-pubkey.txt")?.readText()?.lineSequence()
        ?.map { it.trim() }?.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }?.let(RemoteConfigCodec::publicKeyFromBase64)

/** GET with a hard cap: reads at most [RemoteConfigCodec.MAX_BYTES] + 1 bytes, anonymous, no cookies. Null on any network problem or non-200. */
suspend fun fetchCapped(url: String, client: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()): ByteArray? = withContext(Dispatchers.IO) {
    try {
        client.newCall(Request.Builder().url(url).header("User-Agent", "Vela/1.0 (remote config)").build()).execute().use { r ->
            if (r.code != 200) return@use null
            val src = r.body.source()
            src.request(RemoteConfigCodec.MAX_BYTES + 1L)
            src.buffer.readByteArray(minOf(src.buffer.size, RemoteConfigCodec.MAX_BYTES + 1L))
        }
    } catch (_: java.io.IOException) { null } catch (_: IllegalArgumentException) { null }
}

fun createRemoteConfig(url: () -> String, store: SettingsStore): RemoteConfigManager =
    RemoteConfigManager(url, { fetchCapped(it) }, store, bundledRemoteConfigKey())
