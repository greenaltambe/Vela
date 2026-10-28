package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.extractor.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** One alternate player client: who it claims to be and whether it is currently worth trying. Pure data, see `innertube-profiles.json`. */
data class ClientProfile(val name: String, val clientNameId: Int, val userAgent: String, val client: JsonObject, val enabled: Boolean = true) {
    companion object {
        /** The bundled profiles, or those in [override] when that file exists and parses. */
        fun load(override: File? = null): List<ClientProfile> {
            val text = override?.takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }
                ?: ClientProfile::class.java.getResource("/innertube-profiles.json")!!.readText()
            return parse(text)
        }

        fun parse(text: String): List<ClientProfile> = lenientJson.parseToJsonElement(text)["profiles"].array.mapNotNull { p ->
            ClientProfile(p["name"].string ?: return@mapNotNull null, (p["clientNameId"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null,
                p["userAgent"].string ?: return@mapNotNull null, p["client"].obj ?: return@mapNotNull null,
                (p["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true)
        }
    }
}

/** Supplies a proof-of-origin token for a video. No implementation ships yet; the default is none and URLs are used as returned. */
fun interface PoTokenProvider {
    fun token(videoId: String, clientName: String): String?
    companion object { val None = PoTokenProvider { _, _ -> null } }
}

/**
 * Tier 3: the player endpoint called as the other Google clients (iOS, Android...), which hand out directly playable
 * URLs without a signature cipher. Profiles are tried in order; the first with a playable status and streams wins.
 * Anonymous by design: the app clients reject website cookies (measured: HTTP 400).
 */
class AltClientExtractor(
    private val transport: AuthTransport = OkHttpAuthTransport(),
    private val profiles: () -> List<ClientProfile> = { ClientProfile.load() },
    private val poTokens: PoTokenProvider = PoTokenProvider.None,
    private val clock: Clock = Clock.System,
    private val base: String = "https://www.youtube.com/youtubei/v1",
    /** Called with the name of the profile that served each request, or a failure line; for the diagnostics log. */
    private val onProfile: (String) -> Unit = {},
    /** The "allow controversial" setting: whether the player call answers YouTube's content-warning checks (contentCheckOk / racyCheckOk). */
    private val contentChecks: () -> Boolean = { true },
) : Extractor {

    override suspend fun search(query: String, continuation: String?): ExtractorResult<Page<VideoSummary>> = notSupported()
    override suspend fun channel(channelId: String): ExtractorResult<ChannelPage> = notSupported()
    override suspend fun channelVideos(channelId: String, continuation: String?): ExtractorResult<Page<VideoSummary>> = notSupported()
    override suspend fun comments(videoId: String, sort: CommentSort, continuation: String?): ExtractorResult<CommentsPage> = notSupported()
    override suspend fun replies(videoId: String, commentId: String, continuation: String?): ExtractorResult<CommentsPage> = notSupported()

    override suspend fun videoInfo(videoId: String): ExtractorResult<VideoInfo> = attempt(videoId) { _, json ->
        val d = json["videoDetails"] ?: return@attempt ExtractorResult.Failure(ExtractorError.ParseFailure("no videoDetails"))
        ExtractorResult.Success(VideoInfo(
            videoId, d["title"].string ?: "", d["shortDescription"].string ?: "", d["channelId"].string ?: "", d["author"].string ?: "",
            d["lengthSeconds"].string?.toLongOrNull() ?: 0, d["viewCount"].string?.toLongOrNull(), null,
            d["thumbnail"]["thumbnails"].array.lastOrNull()["url"].string,
            isLive = (d["isLive"] as? JsonPrimitive)?.booleanOrNull == true,
        ))
    }

    override suspend fun streams(videoId: String): ExtractorResult<StreamSet> = attempt(videoId) { profile, json ->
        val set = parseStreams(json, profile, clock.now())
        if (set.videoStreams.isEmpty() && set.audioStreams.isEmpty() && set.hlsUrl == null) ExtractorResult.Failure(ExtractorError.Unplayable("${profile.name}: no playable streams"))
        else ExtractorResult.Success(set)
    }

    private suspend fun <T> attempt(videoId: String, handle: (ClientProfile, JsonElement) -> ExtractorResult<T>): ExtractorResult<T> {
        var last: ExtractorError = ExtractorError.TierUnavailable("No alternate client profile is enabled")
        for (p in profiles().filter { it.enabled }) {
            // The transport is blocking: it always runs on the IO dispatcher, whatever thread the caller is on (Android main throws otherwise).
            val response = try { withContext(Dispatchers.IO) { callPlayer(p, videoId) } } catch (e: java.io.IOException) {
                last = ExtractorError.Network("${p.name}: ${e.message}", e); onProfile("${p.name} network error"); continue
            }
            when {
                response.code == 429 -> { last = ExtractorError.RateLimited("${p.name}: HTTP 429"); onProfile("${p.name} rate limited"); continue }
                response.code !in 200..299 -> { last = ExtractorError.Blocked("${p.name}: HTTP ${response.code}"); onProfile("${p.name} HTTP ${response.code}"); continue }
            }
            val json = runCatching { lenientJson.parseToJsonElement(response.body) }.getOrNull()
            if (json == null) { last = ExtractorError.ParseFailure("${p.name}: not JSON"); continue }
            val status = json.at("playabilityStatus", "status").string
            val reason = json.at("playabilityStatus", "reason").string ?: status
            when (status) {
                "OK" -> {
                    val r = handle(p, json)
                    if (r is ExtractorResult.Success) { onProfile("${p.name} served $videoId"); return r }
                    last = (r as ExtractorResult.Failure).error
                }
                "LOGIN_REQUIRED" -> last = ExtractorError.SignInRequired("${p.name}: $reason")
                "UNPLAYABLE", "ERROR" -> last = ExtractorError.Unplayable("${p.name}: $reason")
                else -> last = if (reason?.contains("rate", true) == true) ExtractorError.RateLimited("${p.name}: $reason") else ExtractorError.Blocked("${p.name}: $status $reason")
            }
            onProfile("${p.name} ${status ?: "?"}")
        }
        return ExtractorResult.Failure(last)
    }

    private fun callPlayer(p: ClientProfile, videoId: String): RawResponse {
        val client = JsonObject(p.client + mapOf("hl" to JsonPrimitive("en"), "gl" to JsonPrimitive("US")))
        val body = buildJsonObject {
            put("context", buildJsonObject { put("client", client) })
            put("videoId", videoId); put("contentCheckOk", contentChecks()); put("racyCheckOk", contentChecks())
        }
        return transport.execute("POST", "$base/player?prettyPrint=false", mapOf(
            "Content-Type" to "application/json", "User-Agent" to p.userAgent,
            "X-YouTube-Client-Name" to p.clientNameId.toString(), "X-YouTube-Client-Version" to (p.client["clientVersion"].string ?: ""),
        ), body.toString())
    }

    private fun parseStreams(json: JsonElement, p: ClientProfile, now: Instant): StreamSet {
        val sd = json["streamingData"]
        val headers = mapOf("User-Agent" to p.userAgent)
        val pot = { videoId: String -> poTokens.token(videoId, p.name) }
        val vid = json.at("videoDetails", "videoId").string ?: ""
        fun url(f: JsonElement?): String? = f["url"].string?.let { u -> pot(vid)?.let { "$u&pot=$it" } ?: u }
        val video = ArrayList<VideoStream>(); val audio = ArrayList<AudioStream>()
        (sd["formats"].array.map { it to true } + sd["adaptiveFormats"].array.map { it to false }).forEach { (f, muxed) ->
            val u = url(f) ?: return@forEach
            val mime = f["mimeType"].string ?: return@forEach
            val codec = Regex("codecs=\"([^\"]+)\"").find(mime)?.groupValues?.get(1)?.split(",")?.first()?.trim() ?: ""
            val bitrate = (f["bitrate"] as? JsonPrimitive)?.intOrNull ?: 0
            val size = f["contentLength"].string?.toLongOrNull()
            val itag = (f["itag"] as? JsonPrimitive)?.intOrNull
            if (mime.startsWith("video")) video += VideoStream(u, mime.substringBefore(';'), codec, bitrate, (f["width"] as? JsonPrimitive)?.intOrNull ?: 0, (f["height"] as? JsonPrimitive)?.intOrNull ?: 0, (f["fps"] as? JsonPrimitive)?.intOrNull ?: 0, hasAudio = muxed, itag = itag, contentLength = size)
            else if (mime.startsWith("audio")) audio += AudioStream(u, mime.substringBefore(';'), codec, bitrate, f["audioTrack"]["id"].string?.substringBefore('.'), itag, size, isDrc = f["isDrc"].let { (it as? JsonPrimitive)?.booleanOrNull == true }, isOriginalTrack = f["audioTrack"]["audioIsDefault"].let { (it as? JsonPrimitive)?.booleanOrNull == true })
        }
        val expiresIn = sd["expiresInSeconds"].string?.toLongOrNull()
        return StreamSet(
            video.sortedWith(compareByDescending<VideoStream> { it.height }.thenByDescending { it.bitrate }),
            audio.sortedByDescending { it.bitrate },
            hlsUrl = sd["hlsManifestUrl"].string,
            expiresAt = expiresIn?.let { now + it.seconds },
            headers = headers, client = p.name.lowercase(),
        )
    }
}
