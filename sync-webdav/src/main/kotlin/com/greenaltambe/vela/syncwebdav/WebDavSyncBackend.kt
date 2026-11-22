package com.greenaltambe.vela.syncwebdav

import com.greenaltambe.vela.sync.SyncBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * A folder on a WebDAV server (Nextcloud, Koofr, Synology, ...). [folderUrl] is the folder itself; it is created when missing. Credentials go in a Basic
 * header, so use an app password and an https address. Failures come back as [IOException]s with a sentence the viewer can act on.
 */
class WebDavSyncBackend(
    folderUrl: String,
    private val user: String,
    private val password: String,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build(),
) : SyncBackend {
    private val base = folderUrl.trim().trimEnd('/').toHttpUrl()
    private val auth = Credentials.basic(user, password, Charsets.UTF_8)
    @Volatile private var folderReady = false

    private fun url(name: String) = base.newBuilder().addPathSegment(name).build()

    private fun request(method: String, url: okhttp3.HttpUrl, body: RequestBody? = null, headers: Map<String, String> = emptyMap()): Response {
        val b = Request.Builder().url(url).header("Authorization", auth).method(method, body)
        headers.forEach { (k, v) -> b.header(k, v) }
        return try { client.newCall(b.build()).execute() } catch (e: IOException) { throw IOException("Could not reach ${base.host}: ${e.message ?: e::class.simpleName}", e) }
    }

    private fun check(r: Response, what: String): Response {
        if (r.isSuccessful) return r
        val code = r.code
        r.close()
        throw IOException(when (code) {
            401 -> "The server rejected the username or password. Use an app password if your service has them."
            403 -> "The server does not allow this account to use that folder (403)."
            404 -> "The folder was not found on the server (404). Check the address."
            507 -> "The server has no storage space left (507)."
            else -> "The server answered $code to $what."
        })
    }

    /** Makes the folder if it is not there. Only the last path segment is created; a missing parent is reported. */
    private fun ensureFolder() {
        if (folderReady) return
        request("PROPFIND", base, "".toRequestBody(), mapOf("Depth" to "0")).use { r ->
            if (r.code == 404) request("MKCOL", base).use { m ->
                if (m.code == 409) throw IOException("The parent of that folder does not exist on the server. Create the folder there first.")
                if (!m.isSuccessful && m.code != 405) check(m, "creating the folder")
            } else if (r.code == 401) check(r, "opening the folder")
        }
        folderReady = true
    }

    override suspend fun list(): List<String> = withContext(Dispatchers.IO) { entries().map { it.name } }

    override suspend fun read(name: String): ByteArray? = withContext(Dispatchers.IO) {
        request("GET", url(name)).use { r -> if (r.code == 404) null else check(r, "downloading $name").body.bytes() }
    }

    override suspend fun write(name: String, data: ByteArray) = withContext(Dispatchers.IO) {
        ensureFolder()
        val body = data.toRequestBody("application/octet-stream".toMediaTypeOrNull())
        // Upload under a temporary name and move it into place, so a reader never sees half a file; plain PUT when the server cannot MOVE.
        val tmp = url(".$name.tmp")
        val moved = request("PUT", tmp, body).use { put ->
            check(put, "uploading $name")
            request("MOVE", tmp, headers = mapOf("Destination" to url(name).toString(), "Overwrite" to "T")).use { it.isSuccessful }
        }
        if (!moved) { request("PUT", url(name), body).use { check(it, "uploading $name") }; request("DELETE", tmp).close() }
    }

    override suspend fun delete(name: String) { withContext(Dispatchers.IO) { request("DELETE", url(name)).close() } }

    /** Names and change markers of the sync files, as one string: it differs when any device published something. */
    override suspend fun fingerprint(): String? = withContext(Dispatchers.IO) { runCatching { entries().sortedBy { it.name }.joinToString("|") { "${it.name}:${it.marker}" } }.getOrNull() }

    private class Entry(val name: String, val marker: String)

    private fun entries(): List<Entry> {
        val body = "<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:getetag/><d:getlastmodified/></d:prop></d:propfind>"
            .toRequestBody("application/xml".toMediaTypeOrNull())
        val text = request("PROPFIND", base, body, mapOf("Depth" to "1")).use { r ->
            if (r.code == 404) return emptyList()
            check(r, "listing the folder").body.string()
        }
        return RESPONSE.findAll(text).mapNotNull { m ->
            val block = m.value
            val href = HREF.find(block)?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
            val name = URLDecoder.decode(href.trimEnd('/').substringAfterLast('/').replace("+", "%2B"), "UTF-8")
            if (!name.endsWith(".vsync") || name.startsWith(".")) return@mapNotNull null
            Entry(name, ETAG.find(block)?.groupValues?.get(1) ?: MODIFIED.find(block)?.groupValues?.get(1) ?: "")
        }.toList()
    }

    private companion object {
        val RESPONSE = Regex("<[^>/]*response[^>]*>.*?</[^>]*response>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val HREF = Regex("<[^>/]*href[^>]*>([^<]+)</[^>]*href>", RegexOption.IGNORE_CASE)
        val ETAG = Regex("<[^>/]*getetag[^>]*>([^<]*)</[^>]*getetag>", RegexOption.IGNORE_CASE)
        val MODIFIED = Regex("<[^>/]*getlastmodified[^>]*>([^<]*)</[^>]*getlastmodified>", RegexOption.IGNORE_CASE)
    }
}
