package com.greenaltambe.vela.health

import com.greenaltambe.vela.core.http.ApiClient
import com.greenaltambe.vela.core.http.ApiResult
import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.core.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Dotted numeric versions (`v0.26.5`, `2026.10.26`, `2026.10.26.232815`); a leading `v` is ignored. Missing parts count as 0. */
fun compareVersions(a: String, b: String): Int {
    fun parts(v: String) = v.trim().removePrefix("v").split('.', '-').mapNotNull { it.toIntOrNull() }
    val (x, y) = parts(a) to parts(b)
    for (i in 0 until maxOf(x.size, y.size)) {
        val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}

/** What the last look at a project's newest release found, compared with what this build bundles. */
data class ReleaseStatus(val name: String, val bundled: String, val latest: String?, val latestDate: String?, val checkedAt: Instant?, val failure: String?) {
    val newerAvailable: Boolean get() = latest != null && compareVersions(latest, bundled) > 0
    val upToDate: Boolean get() = latest != null && !newerAvailable

    /** The one line Health shows. A warning only when a newer release exists; never from age alone. */
    fun describe(): String = when {
        newerAvailable -> "$name $bundled is out of date: $latest${latestDate?.let { " ($it)" } ?: ""} is available."
        upToDate -> "$name $bundled is up to date (checked ${checkedAt.toString().substring(0, 10)})."
        failure != null -> "$name $bundled: could not check for a newer release ($failure)."
        else -> "$name $bundled: not checked yet."
    }
}

/**
 * Asks GitHub's releases API for [repo]'s newest release, at most once per [interval] (default a day). The answer is kept in
 * [store] so a restart does not ask again, and an offline or rate-limited check keeps the last answer and notes the failure.
 * Anonymous: no credentials, through the shared [ApiClient] (identifies itself, retries with backoff).
 */
class ReleaseCheck(
    private val name: String,
    private val bundled: String,
    private val repo: String,
    private val client: ApiClient,
    private val store: SettingsStore,
    private val clock: Clock = Clock.System,
    private val interval: kotlin.time.Duration = 24.hours,
) {
    private val key = "release.$repo"
    private val _status = MutableStateFlow(load())
    val status: StateFlow<ReleaseStatus> = _status

    private fun load(): ReleaseStatus {
        val raw = store.get(key, SettingLayer.Global)?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        fun s(k: String) = raw?.get(k)?.jsonPrimitive?.content
        return ReleaseStatus(name, bundled, s("latest"), s("date"), s("at")?.let { runCatching { Instant.parse(it) }.getOrNull() }, null)
    }

    /** [force] ignores the daily limit (the Health button). */
    suspend fun check(force: Boolean = false): ReleaseStatus {
        val last = _status.value
        if (!force && last.latest != null && last.checkedAt != null && clock.now() - last.checkedAt < interval) return last
        val result = client.get("https://api.github.com/repos/$repo/releases/latest", ttl = interval, headers = mapOf("Accept" to "application/vnd.github+json"))
        val next = when (result) {
            is ApiResult.Ok -> if (result.stale) last.copy(failure = "offline, showing the last answer") else parse(result.body)?.let { (tag, date) ->
                ReleaseStatus(name, bundled, tag, date, clock.now(), null).also { save(it) }
            } ?: last.copy(failure = "unexpected answer")
            is ApiResult.Failed -> last.copy(failure = result.message)
        }
        _status.value = next
        return next
    }

    private fun parse(body: String): Pair<String, String?>? {
        val o: JsonObject = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val tag = o["tag_name"]?.jsonPrimitive?.content ?: return null
        return tag to o["published_at"]?.jsonPrimitive?.content?.take(10)
    }

    private fun save(s: ReleaseStatus) = store.put(key, SettingLayer.Global, """{"latest":"${s.latest}","date":"${s.latestDate ?: ""}","at":"${s.checkedAt}"}""")
}

/** One installed copy of a tool, for the Health screen (yt-dlp can be installed more than once). */
data class ToolCopy(val path: String, val version: String?, val used: Boolean, val updateHint: String)

/** A project watched for newer releases, plus any installed copies to compare with it. */
class ReleaseWatch(val check: ReleaseCheck, val copies: List<ToolCopy> = emptyList()) {
    /** Lines for each copy: which is stale (older than the newest release) and the command to fix it. Copies are never changed by the app. */
    fun copyLines(): List<String> {
        val latest = check.status.value.latest
        return copies.map { c ->
            val state = when {
                c.version == null -> "does not run"
                latest == null -> "version ${c.version}"
                compareVersions(c.version, latest) < 0 -> "STALE (${c.version}, newest is $latest); update with: ${c.updateHint}"
                else -> "up to date (${c.version})"
            }
            "${c.path}${if (c.used) " [in use]" else ""}: $state"
        }
    }
}

/** An updater for a tool the app manages itself (the app's own yt-dlp copy). System-installed copies are never touched. */
interface ManagedUpdater {
    val status: StateFlow<String>
    /** Installs release [tag]; true when it is now the one in use. */
    suspend fun install(tag: String): Boolean
}
