package com.greenaltambe.vela.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.greenaltambe.vela.auth.AccountReads
import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.FailureReason
import com.greenaltambe.vela.extractor.PlaylistContents
import com.greenaltambe.vela.extractor.PlaylistDetails
import com.greenaltambe.vela.extractor.VideoSummary
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Why a further page did not come: [expired] means the listing cannot be gone on with and has to be read again from the start. */
data class PageProblem(val message: String, val expired: Boolean)

/**
 * A YouTube playlist (not one of the app's own) as far as it has been read. The page is read without the account; only when that fails, which is what
 * happens to a private list, is the signed-in account asked. Every load goes through one lock, so scrolling and "read it all" never fetch a page twice.
 * The fields are Compose state: a screen reading them follows the loading.
 */
class RemotePlaylist(val id: String, private val extractor: Extractor, private val reads: AccountReads?) {
    var details by mutableStateOf<PlaylistDetails?>(null); private set
    var videos by mutableStateOf<List<VideoSummary>>(emptyList()); private set
    /** True once the first page has come. */
    var loaded by mutableStateOf(false); private set
    /** Why the first page could not be read. */
    var error by mutableStateOf<String?>(null); private set
    var moreProblem by mutableStateOf<PageProblem?>(null); private set
    var loading by mutableStateOf(false); private set
    /** Entries that were on the pages read but could not be understood. */
    var unread by mutableStateOf(0); private set
    private var next by mutableStateOf<String?>(null)
    private var fromAccount = false
    private val lock = Mutex()

    val hasMore: Boolean get() = next != null
    /** The whole list has been read and nothing in it was left out. */
    val complete: Boolean get() = loaded && next == null && unread == 0

    /** The first page, unless it is already here. */
    suspend fun load() = lock.withLock { if (!loaded) first() }

    /** Everything again from the start. What is on screen stays until the new first page has come. */
    suspend fun reload() = lock.withLock { first() }

    private suspend fun first() = busy {
        error = null
        var result = extractor.playlistPage(id)
        var account = false
        if (result is ExtractorResult.Failure) reads?.playlistPage(id)?.let { result = ExtractorResult.Success(it); account = true }
        when (val r = result) {
            is ExtractorResult.Success -> {
                fromAccount = account
                details = r.value.details
                videos = r.value.videos.distinctBy { it.id }
                unread = r.value.unreadCount
                next = r.value.continuation
                moreProblem = null
                loaded = true
            }
            // A failed refresh keeps what was read before.
            is ExtractorResult.Failure -> if (loaded) moreProblem = PageProblem("Could not refresh: ${r.error.message}", expired = true) else error = r.error.message
        }
    }

    /** The next page; false when it could not be had ([moreProblem] says why). True too when there is nothing more to get. */
    suspend fun loadMore(): Boolean = lock.withLock {
        val token = next ?: return@withLock true
        busy {
            moreProblem = null
            val page: PlaylistContents? = if (fromAccount) reads?.playlistPage(id, token) else when (val r = extractor.playlistPage(id, token)) {
                is ExtractorResult.Success -> r.value
                is ExtractorResult.Failure -> {
                    val expired = r.error.reason == FailureReason.TOKEN_EXPIRED || r.error.reason == FailureReason.TOKEN_NOT_MINE
                    moreProblem = PageProblem(if (expired) "This list changed while it was being read." else "Could not load more: ${r.error.message}", expired)
                    return@busy false
                }
            }
            if (page == null) { moreProblem = PageProblem("Could not load more.", expired = false); return@busy false }
            val seen = videos.mapTo(HashSet()) { it.id }
            val fresh = page.videos.filter { seen.add(it.id) }
            videos = videos + fresh
            unread += page.unreadCount
            // A page that brings nothing and hands back the same token would repeat for ever.
            next = page.continuation?.takeUnless { it == token && fresh.isEmpty() }
            true
        }
    }

    /** Reads to the end. True when every video of the list is here; false when a page failed or something could not be read. */
    suspend fun loadAll(): Boolean {
        load()
        if (!loaded) return false
        var pages = 0
        while (hasMore && pages++ < MAX_PAGES) if (!loadMore()) return false
        return complete
    }

    private suspend fun <T> busy(block: suspend () -> T): T {
        loading = true
        try { return block() } finally { loading = false }
    }

    companion object {
        /** A playlist holds up to 5000 videos, about 50 pages; this only stops a listing that never ends (a mix). */
        const val MAX_PAGES = 200
    }
}

/** The link that opens a playlist on YouTube. */
fun playlistLink(id: String): String = "https://www.youtube.com/playlist?list=$id"

/** A mix is endless and made for each viewer: there is no "all of it". */
fun isMix(playlistId: String): Boolean = playlistId.startsWith("RD")
