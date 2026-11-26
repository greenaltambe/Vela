package com.greenaltambe.vela.auth

import com.greenaltambe.vela.core.settings.AccountHistorySync
import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.OpKind
import com.greenaltambe.vela.data.PlaylistOpRepository
import com.greenaltambe.vela.data.PlaylistRepository
import com.greenaltambe.vela.data.PlaylistSummary
import com.greenaltambe.vela.data.PlaylistSync
import com.greenaltambe.vela.data.PlaylistVideo
import com.greenaltambe.vela.data.RowState
import com.greenaltambe.vela.data.SqlDelightSettingsStore
import com.greenaltambe.vela.data.YOUTUBE_PLAYLIST_LIMIT
import com.greenaltambe.vela.data.SavedSource
import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.extractor.PlaylistDetails
import com.greenaltambe.vela.library.RemotePlaylist
import com.greenaltambe.vela.library.isMix
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.minutes
import com.greenaltambe.vela.data.db.VelaDatabase
import com.greenaltambe.vela.extractor.VideoSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** What a playlist change came to. [pending] means it is kept here and will reach YouTube later ([message] says why); [ok] false means nothing changed and [message] says why. */
data class PlaylistResult(val ok: Boolean, val message: String? = null, val pending: Boolean = false, val id: Long? = null, val overflow: Int = 0) {
    companion object {
        fun ok(message: String? = null, id: Long? = null, overflow: Int = 0) = PlaylistResult(true, message, false, id, overflow)
        fun failed(message: String) = PlaylistResult(false, message)
        fun pending(message: String, id: Long? = null) = PlaylistResult(true, message, true, id)
    }
}

private fun PlaylistDetails.toSource(id: String) = SavedSource(id, title, ownerName, ownerChannelId, coverUrl)

/** What started a playlist sync: the viewer, the app starting, or the periodic timer / worker. */
enum class LibrarySyncTrigger { MANUAL, START, PERIODIC }

/**
 * The one place playlists are changed. Every screen goes through it, so a playlist behaves the same wherever it is touched:
 *
 * - A playlist is LOCAL (never touches the network) or SYNCED with one YouTube playlist (`remoteId`). The "Sync with my YouTube account" box is the only control.
 * - A SAVED playlist (`sourceId`) is somebody else's: a read-only copy that follows the original. The same box says whether it is also in the account's
 *   YouTube library; its videos are never written.
 * - A change to a SYNCED playlist is applied here first, then written to YouTube through [AccountActions]. A refused write is rolled back and says why; one the hourly
 *   limit or a cool-down refused is kept in the outbox (a table, so it survives a restart) and sent later.
 * - Reads bring YouTube's changes in ([syncAll]); YouTube's order is the truth for synced playlists.
 * - YouTube holds at most [YOUTUBE_PLAYLIST_LIMIT] videos in a playlist. What does not fit stays here as "not on YouTube" and can be moved to another synced playlist.
 */
class PlaylistService(
    private val db: VelaDatabase,
    private val accounts: AccountManager,
    private val actions: AccountActions?,
    private val reads: AccountReads?,
    private val clock: Clock = Clock.System,
    private val settings: SettingsResolver? = null,
    private val limit: Int = YOUTUBE_PLAYLIST_LIMIT,
    /** Reads the originals of saved playlists; without it their copies are not refreshed. */
    private val extractor: Extractor? = null,
) {
    /** Raised after every change, so a screen showing playlists re-reads. */
    val revision = MutableStateFlow(0)
    private val lock = Mutex()
    private var startHandled = false
    /** Lives as long as the app: a change started from a sheet that closes at once must still finish. */
    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    fun launch(block: suspend () -> Unit) { appScope.launch { block() } }
    private var pulledTotal = 0
    private val store get() = SqlDelightSettingsStore(db)

    private val accountId get() = accounts.active.value.id
    private val repo get() = PlaylistRepository(db, accountId)
    private val ops get() = PlaylistOpRepository(db, accountId)
    private fun now() = clock.now()
    private fun changed() { revision.value++ }

    /** Whether the active account can be written to now (a Google account that is signed in). */
    val syncAvailable: Boolean get() = actions != null && reads != null && accounts.active.value.isSignedIn
    /** Why the box is off, for the viewer; null when it is available. */
    fun unavailableReason(): String? = when {
        actions == null || reads == null -> "Sign in to a Google account to sync"
        accounts.active.value.kind != AccountKind.GOOGLE -> "Sign in to a Google account to sync"
        accounts.active.value.authState != AuthState.OK -> "Sign in again to sync"
        else -> null
    }

    /** Changes waiting to be sent to YouTube. */
    fun waitingChanges(): Int = ops.count()

    // ---- create / link / unlink ----

    /** A new playlist; with [synced] it is also created on the account (private) and then synced. When the account refuses, nothing is created. */
    suspend fun create(name: String, synced: Boolean): PlaylistResult {
        val id = repo.create(name, now()); changed()
        if (!synced) return PlaylistResult.ok(id = id)
        val r = link(id)
        if (r.ok) return r.copy(id = id)
        // Refused on the account: nothing is created here either (the viewer asked for a synced playlist), unless it already exists there.
        if (repo.get(id)?.isSynced != true) { repo.delete(id); changed() }
        return r
    }

    suspend fun setSynced(id: Long, on: Boolean): PlaylistResult {
        if (repo.get(id)?.isSaved != true) return if (on) link(id) else unlink(id)
        return lock.withLock {
            val p = repo.get(id) ?: return PlaylistResult.failed("That playlist is gone.")
            if (p.onAccount == on) PlaylistResult.ok()
            else if (on) unavailableReason()?.let { PlaylistResult.failed(it) } ?: putOnAccount(p)
            else {
                // As for the viewer's own playlists, unticking never takes anything away on YouTube.
                ops.clearPlaylist(id); repo.setOnAccount(id, false); ignore(p.sourceId!!, true); changed()
                PlaylistResult.ok("Kept here. Your YouTube library was not changed.")
            }
        }
    }

    /** Adds a saved playlist to the account's library (lock held): shown here at once, taken back when YouTube refuses. */
    private suspend fun putOnAccount(p: PlaylistSummary): PlaylistResult {
        val source = p.sourceId ?: return PlaylistResult.failed("That playlist is gone.")
        ignore(source, false)
        repo.setOnAccount(p.id, true); ops.enqueueSave(p.id, source, now()); changed()
        val out = drainOne(p.id)
        return when {
            out.failure != null -> { ops.clearPlaylist(p.id); repo.setOnAccount(p.id, false); changed(); PlaylistResult.failed("Could not save it to your YouTube account: ${out.failure}") }
            out.queued != null -> PlaylistResult.pending(out.queued, p.id)
            else -> PlaylistResult.ok(id = p.id)
        }
    }

    /** Syncs [id] with the account: a new private YouTube playlist (or "WL" for Watch later), then the existing videos are pushed in chunks and YouTube's are pulled in. */
    suspend fun link(id: Long): PlaylistResult = lock.withLock {
        val p = repo.get(id) ?: return PlaylistResult.failed("That playlist is gone.")
        if (p.isSaved) return PlaylistResult.failed(NOT_YOURS)
        if (p.isSynced) return PlaylistResult.ok()
        unavailableReason()?.let { return PlaylistResult.failed(it) }
        val remote: String
        val created: Boolean
        if (p.isWatchLater) { remote = "WL"; created = false }
        else {
            val (r, remoteId) = actions!!.createPlaylist(p.name)
            when {
                r is ActionResult.Failed -> return PlaylistResult.failed("Could not create it on your YouTube account: ${r.message}")
                r is ActionResult.Queued -> return PlaylistResult.failed(r.message)
                remoteId == null -> return PlaylistResult.failed("Could not create it on your YouTube account.")
            }
            remote = remoteId!!; created = true
        }
        ignore(remote, false)
        repo.setRemote(id, remote, PlaylistSync.LINKING)
        repo.changeRowStates(id, RowState.SYNCED, RowState.PENDING_ADD)
        changed()
        syncOne(id, pull = !created)
    }

    /** Stops syncing: the videos stay here as a LOCAL playlist and the YouTube playlist is left exactly as it is (it is never deleted by this). */
    suspend fun unlink(id: Long): PlaylistResult = lock.withLock {
        val p = repo.get(id) ?: return PlaylistResult.failed("That playlist is gone.")
        if (p.isReadOnly) return PlaylistResult.failed(if (p.isSaved) NOT_YOURS else "Liked videos follows your likes; it cannot be unlinked.")
        if (!p.isSynced) return PlaylistResult.ok()
        p.remoteId?.let { ignore(it, true) }
        ops.clearPlaylist(id)
        repo.dropTombstones(id)
        repo.changeRowStates(id, RowState.PENDING_ADD, RowState.SYNCED)
        repo.changeRowStates(id, RowState.OVERFLOW, RowState.SYNCED)
        repo.setRemote(id, null, PlaylistSync.LOCAL)
        changed()
        PlaylistResult.ok("Kept here. The YouTube playlist was not changed.")
    }

    // ---- videos ----

    /** Adds [videos] to [id]. */
    suspend fun add(id: Long, videos: List<VideoSummary>): PlaylistResult = lock.withLock {
        val p = repo.get(id) ?: return PlaylistResult.failed("That playlist is gone.")
        if (p.isReadOnly) return PlaylistResult.failed(if (p.isSaved) NOT_YOURS else "Liked videos follows your likes; add by liking a video.")
        if (!p.isSynced) { repo.add(id, videos, now()); changed(); return PlaylistResult.ok() }
        val fresh = videos.filter { !repo.contains(id, it.id) }.distinctBy { it.id }
        if (fresh.isEmpty()) return PlaylistResult.ok()
        val rows = repo.rows(id)
        val occupied = rows.count { it.rowState != RowState.OVERFLOW }
        val room = (limit - occupied).coerceAtLeast(0)
        val fits = fresh.take(room); val over = fresh.drop(room)
        repo.add(id, fits, now(), RowState.PENDING_ADD)
        for (v in fits) ops.enqueueAdd(id, v.id, now())
        if (over.isNotEmpty()) repo.add(id, over, now(), RowState.OVERFLOW)
        changed()
        if (fits.isEmpty()) return PlaylistResult.ok("Not on YouTube: this playlist is full (${limit}).", overflow = over.size)
        val out = drainOne(id)
        when {
            out.failure != null -> {
                // R4: a refused write is rolled back. (Overflow rows were never sent; they go too, because the whole change failed.)
                for (v in fresh) { ops.cancelAdd(id, v.id); repo.remove(id, v.id) }
                changed(); PlaylistResult.failed(out.failure)
            }
            out.queued != null -> PlaylistResult.pending(out.queued).copy(overflow = over.size)
            else -> PlaylistResult.ok(if (over.isEmpty()) null else "${over.size} did not fit on YouTube.", overflow = over.size)
        }
    }

    /** Takes [videoIds] out of [id]. */
    suspend fun remove(id: Long, videoIds: List<String>): PlaylistResult = lock.withLock {
        val p = repo.get(id) ?: return PlaylistResult.failed("That playlist is gone.")
        if (p.isReadOnly) return PlaylistResult.failed(if (p.isSaved) NOT_YOURS else "Liked videos follows your likes; remove by un-liking a video.")
        if (!p.isSynced) { videoIds.forEach { repo.remove(id, it) }; changed(); return PlaylistResult.ok() }
        val tombstoned = ArrayList<String>()
        for (v in videoIds) when (repo.rowState(id, v)) {
            RowState.SYNCED -> { repo.setRowState(id, v, RowState.PENDING_REMOVE); ops.enqueueRemove(id, v, now()); tombstoned += v }
            RowState.PENDING_ADD, RowState.OVERFLOW -> { ops.cancelAdd(id, v); repo.remove(id, v) }   // it never reached YouTube
            else -> {}
        }
        changed()
        if (tombstoned.isEmpty()) return PlaylistResult.ok()
        val out = drainOne(id)
        when {
            out.failure != null -> {
                for (v in tombstoned) { repo.setRowState(id, v, RowState.SYNCED); ops.cancelRemove(id, v) }
                changed(); PlaylistResult.failed(out.failure)
            }
            out.queued != null -> PlaylistResult.pending(out.queued)
            else -> PlaylistResult.ok()
        }
    }

    // ---- rename / delete ----

    suspend fun rename(id: Long, name: String): PlaylistResult = lock.withLock {
        val p = repo.get(id) ?: return PlaylistResult.failed("That playlist is gone.")
        if (p.isSaved) return PlaylistResult.failed(NOT_YOURS)
        if (p.isWatchLater || p.isReadOnly) return PlaylistResult.failed("This playlist keeps its name.")
        val clean = name.trim(); if (clean.isEmpty()) return PlaylistResult.failed("A playlist needs a name.")
        val old = p.name
        repo.rename(id, clean, now())
        if (!p.isSynced) { changed(); return PlaylistResult.ok() }
        ops.enqueueRename(id, clean, now()); changed()
        val out = drainOne(id)
        when {
            out.failure != null -> { repo.rename(id, old, now()); ops.clearRename(id); changed(); PlaylistResult.failed(out.failure) }
            out.queued != null -> PlaylistResult.pending(out.queued)
            else -> PlaylistResult.ok()
        }
    }

    /**
     * Deletes it here; a synced playlist is deleted on YouTube too (unlink it first to keep the YouTube copy). A saved one only leaves the library:
     * with [fromAccount] the account's YouTube library as well, and when YouTube refuses that, it stays here too.
     */
    suspend fun delete(id: Long, fromAccount: Boolean = false): PlaylistResult = lock.withLock {
        val p = repo.get(id) ?: return PlaylistResult.ok()
        if (p.isSaved) return removeSaved(p, fromAccount && p.onAccount)
        if (p.isWatchLater) return PlaylistResult.failed("Watch later cannot be deleted.")
        if (p.isReadOnly) return PlaylistResult.failed("Liked videos cannot be deleted.")
        if (!p.isSynced) { repo.delete(id); changed(); return PlaylistResult.ok() }
        val a = actions ?: return PlaylistResult.failed("Sign in to delete it on YouTube, or stop syncing it first.")
        when (val r = a.deletePlaylist(p.remoteId!!)) {
            is ActionResult.Failed -> PlaylistResult.failed("Could not delete it on your YouTube account: ${r.message}")
            is ActionResult.Queued -> { ops.enqueueDelete(id, p.remoteId, now()); repo.delete(id); changed(); PlaylistResult.pending(r.message) }
            else -> { ops.clearPlaylist(id); repo.delete(id); changed(); PlaylistResult.ok() }
        }
    }

    // ---- saved playlists (somebody else's) ----

    /** Lock held. */
    private suspend fun removeSaved(p: PlaylistSummary, fromAccount: Boolean): PlaylistResult {
        val source = p.sourceId!!
        if (!fromAccount) {
            ops.clearPlaylist(p.id)
            // Still in the account's library: the next sync must not bring it straight back.
            if (p.onAccount) ignore(source, true)
            repo.delete(p.id); changed(); return PlaylistResult.ok()
        }
        unavailableReason()?.let { return PlaylistResult.failed(it) }
        return when (val r = actions!!.unsavePlaylist(source)) {
            is ActionResult.Failed -> PlaylistResult.failed("Could not remove it from your YouTube account: ${r.message}")
            is ActionResult.LocalOnly -> PlaylistResult.failed("Sign in to remove it from your YouTube account.")
            is ActionResult.Queued -> { ops.enqueueUnsave(p.id, source, now()); ignore(source, true); repo.delete(p.id); changed(); PlaylistResult.pending(r.message) }
            is ActionResult.Ok -> { ops.clearPlaylist(p.id); ignore(source, false); repo.delete(p.id); changed(); PlaylistResult.ok() }
        }
    }

    /**
     * Saves [playlist] to the library: it is there at once with the videos read so far, and the rest is read afterwards. With [onAccount] it also
     * goes into the account's YouTube library; refused there, nothing is saved.
     */
    suspend fun save(playlist: RemotePlaylist, onAccount: Boolean = false): PlaylistResult {
        if (onAccount) unavailableReason()?.let { return PlaylistResult.failed(it) }
        var created = false
        var result = lock.withLock {
            val details = playlist.details ?: return PlaylistResult.failed("The playlist has not loaded yet.")
            if (isMix(playlist.id)) return PlaylistResult.failed("A mix is made for you as you watch; it cannot be saved.")
            repo.bySource(playlist.id)?.let { return PlaylistResult.ok(id = it.id) }
            if (repo.byRemote(playlist.id) != null) return PlaylistResult.failed("That playlist is already in your library.")
            val id = repo.createSaved(details.toSource(playlist.id), now())
            repo.replaceItems(id, playlist.videos, now())
            created = true
            changed()
            PlaylistResult.ok(id = id)
        }
        val saved = result.id
        if (onAccount && saved != null) {
            result = lock.withLock {
                val p = repo.get(saved) ?: return PlaylistResult.failed("That playlist is gone.")
                if (p.onAccount) PlaylistResult.ok(id = saved)
                else putOnAccount(p).also { if (!it.ok && created) { repo.delete(saved); changed() } }
            }
            if (!result.ok) return result
        }
        result.id?.let { id -> launch { refreshSaved(id, playlist) } }
        return result
    }

    private val reading = MutableStateFlow(emptySet<Long>())
    /** The saved playlists whose originals are being read now. */
    val refreshing: StateFlow<Set<Long>> get() = reading

    /** Reads a saved playlist's original when its copy is older than [SAVED_FRESH] (or was never read whole). */
    suspend fun refreshSavedIfStale(id: Long): PlaylistResult? {
        val p = repo.get(id)?.takeIf { it.isSaved } ?: return null
        val at = p.lastSyncedAt
        if (at != null && now() - Instant.fromEpochMilliseconds(at) < SAVED_FRESH) return null
        return refreshSaved(id)
    }

    /**
     * Brings a saved playlist's copy up to its original ([read] is a reading of it already under way). Read whole, the copy becomes exactly the
     * original; read in part, new videos come in and none is taken out; not readable at all, the copy stays and is marked when the original is gone.
     */
    suspend fun refreshSaved(id: Long, read: RemotePlaylist? = null): PlaylistResult {
        val account = accountId
        val lists = PlaylistRepository(db, account)
        val before = lists.get(id) ?: return PlaylistResult.failed("That playlist is gone.")
        val source = before.sourceId ?: return PlaylistResult.failed("That playlist is gone.")
        // A page left open can hold an older reading than the copy: it must not undo a later one.
        val readAt = read?.readAt
        if (readAt != null && before.lastSyncedAt?.let { readAt.toEpochMilliseconds() < it } == true) return PlaylistResult.ok(id = id)
        val playlist = read?.takeIf { it.id == source } ?: RemotePlaylist(source, extractor ?: return PlaylistResult.failed("It cannot be read here."), reads)
        // One read of a playlist at a time.
        if (id in reading.getAndUpdate { it + id }) return PlaylistResult.ok()
        try {
            val whole = playlist.loadAll()
            return lock.withLock {
                // It may have been removed while the pages were on their way.
                val cur = lists.get(id) ?: return PlaylistResult.ok()
                if (!playlist.loaded) {
                    if (playlist.gone) lists.markUnavailable(id, now())
                    changed()
                    return PlaylistResult.failed(if (playlist.gone) "\"${cur.name}\" is no longer available on YouTube. Your copy was kept." else "Could not read \"${cur.name}\": ${playlist.error}")
                }
                playlist.details?.let { lists.updateSaved(id, it.toSource(source), now()) }
                val videos = playlist.videos
                val kept = lists.items(id).map { it.toSummary() }
                // Nothing read, though the page does not say it is empty: a failed read, not an emptied playlist.
                val suspicious = videos.isEmpty() && kept.isNotEmpty() && playlist.details?.videoCount != 0L
                when {
                    whole && !suspicious -> { lists.replaceItems(id, videos, now()); lists.markRefreshed(id, now()) }
                    else -> {
                        val have = videos.mapTo(HashSet()) { it.id }
                        lists.replaceItems(id, videos + kept.filter { it.id !in have }, now()); lists.markAvailable(id)
                    }
                }
                changed()
                if (whole && !suspicious) PlaylistResult.ok(id = id) else PlaylistResult.failed("Only part of \"${cur.name}\" could be read; nothing was taken out of your copy.")
            }
        } finally {
            reading.update { it - id }
        }
    }

    // ---- the like button ----

    /**
     * The like button wrote to the account; this keeps the local Liked videos list in step at once (a like goes to the top, a dislike or un-like takes the video out)
     * instead of waiting for the next sync, which only confirms it. Local only: nothing is sent. A no-op until Liked videos has been brought in.
     */
    fun noteRating(video: VideoSummary, rating: VideoRating) {
        val liked = repo.byRemote("LL") ?: return
        if (rating == VideoRating.LIKE) {
            if (repo.add(liked.id, listOf(video), now(), RowState.SYNCED) > 0) { val n = repo.items(liked.id).size; if (n > 1) repo.move(liked.id, n - 1, 0) }
        } else if (repo.contains(liked.id, video.id)) repo.remove(liked.id, video.id)
        changed()
    }

    // ---- overflow (YouTube's playlist is full) ----

    /** Synced playlists with room for more, to move the videos that did not fit into (the full one and Liked videos excluded), with how many more each takes. */
    fun overflowTargets(fromId: Long): List<Pair<PlaylistSummary, Int>> = repo.all()
        .filter { it.isSynced && !it.isReadOnly && it.id != fromId }
        .map { it to (limit - repo.rows(it.id).count { r -> r.rowState != RowState.OVERFLOW }).coerceAtLeast(0) }
        .filter { it.second > 0 }

    /**
     * Moves the videos of [fromId] that did not fit into [targetId], or, with null, into new synced playlists ("<name> 2", "<name> 3"...) created as needed.
     * Only videos the target accepted leave the source; anything that fails stays where it was.
     */
    suspend fun moveOverflow(fromId: Long, targetId: Long?): PlaylistResult {
        val from = repo.get(fromId) ?: return PlaylistResult.failed("That playlist is gone.")
        var left = repo.rows(fromId).filter { it.rowState == RowState.OVERFLOW }
        if (left.isEmpty()) return PlaylistResult.ok("Nothing is waiting.")
        var target = targetId
        var moved = 0
        var lastName = ""
        var guard = 0
        while (left.isNotEmpty() && guard++ < 20) {
            if (target == null) {
                val name = nextName(from.name)
                val created = create(name, synced = true)
                val newId = created.id
                if (newId == null || repo.get(newId)?.isSynced != true) {
                    if (newId != null) repo.delete(newId)
                    changed(); return PlaylistResult.failed("Could not create \"$name\" on your YouTube account${created.message?.let { ": $it" } ?: ""}. $moved moved.")
                }
                target = newId; lastName = name
            }
            val t = repo.get(target) ?: return PlaylistResult.failed("The playlist you chose is gone.")
            lastName = t.name
            val room = (limit - repo.rows(target).count { it.rowState != RowState.OVERFLOW }).coerceAtLeast(0)
            val batch = left.take(room)
            if (batch.isEmpty()) { if (targetId != null) return PlaylistResult.failed("\"${t.name}\" is full too. $moved moved."); target = null; continue }
            val r = add(target, batch.map { it.toSummary() })
            if (!r.ok) { changed(); return PlaylistResult.failed("${r.message ?: "Could not add them"} $moved moved.") }
            for (v in batch) lock.withLock { repo.remove(fromId, v.videoId) }
            moved += batch.size
            left = left.drop(batch.size)
            if (left.isNotEmpty()) target = null.takeIf { targetId == null }
            if (left.isNotEmpty() && targetId != null) return PlaylistResult.failed("\"$lastName\" has room for only $moved. ${left.size} still not on YouTube.")
        }
        changed()
        return PlaylistResult.ok("Moved $moved to \"$lastName\".")
    }

    private fun nextName(base: String): String {
        val names = repo.all().map { it.name.lowercase() }.toSet()
        val stem = base.trimEnd().replace(Regex("\\s+\\d+$"), "")
        var n = 2
        while ("$stem $n".lowercase() in names) n++
        return "$stem $n"
    }

    // ---- the outbox ----

    private class Drain(val failure: String? = null, val queued: String? = null, val sent: Int = 0)

    /** Sends what waits in the outbox (every playlist's). Safe to call any time; with nothing waiting it does nothing. */
    suspend fun drain(): PlaylistResult = lock.withLock {
        val out = drainAll()
        changed()
        when { out.failure != null -> PlaylistResult.failed(out.failure); out.queued != null -> PlaylistResult.pending(out.queued); else -> PlaylistResult.ok() }
    }

    private suspend fun drainOne(id: Long): Drain = drainPlaylists(setOf(id))
    private suspend fun drainAll(): Drain = drainPlaylists(null)

    private fun isFull(message: String): Boolean { val m = message.lowercase(); return "full" in m || "maximum" in m || "too many" in m || "$limit" in m || "limit" in m && "hourly" !in m }

    private suspend fun drainPlaylists(only: Set<Long>?): Drain {
        val a = actions ?: return Drain()
        if (!accounts.active.value.isSignedIn) return Drain()
        val all = ops.all().filter { only == null || it.playlistId in only }
        if (all.isEmpty()) return Drain()
        var failure: String? = null
        var sent = 0
        for ((pid, list) in all.groupBy { it.playlistId }) {
            // A playlist deleted here: the DELETE op carries its remote id.
            list.firstOrNull { it.kind == OpKind.DELETE }?.let { d ->
                if (d.attempts >= MAX_ATTEMPTS) return@let
                when (val r = a.deletePlaylist(d.payload.orEmpty())) {
                    is ActionResult.Queued -> return Drain(queued = r.message, sent = sent)
                    is ActionResult.Failed -> { ops.failed(d.id, r.message); failure = r.message }
                    else -> { ops.done(d.id); sent++ }
                }
                return@let
            }
            if (list.any { it.kind == OpKind.DELETE }) continue
            // A saved playlist leaving, or joining, the account's library.
            val leave = list.firstOrNull { it.kind == OpKind.UNSAVE }
            if (leave != null) {
                val source = leave.payload.orEmpty()
                when (val r = a.unsavePlaylist(source)) {
                    is ActionResult.Queued -> return Drain(queued = r.message, sent = sent)
                    is ActionResult.LocalOnly -> return Drain(queued = "Sign in to send it to YouTube.", sent = sent)
                    is ActionResult.Failed -> { ops.failed(leave.id, r.message); if (leave.attempts + 1 >= MAX_ATTEMPTS) ops.done(leave.id); failure = r.message }
                    is ActionResult.Ok -> { ops.done(leave.id); ignore(source, false); sent++ }
                }
                continue
            }
            val join = list.firstOrNull { it.kind == OpKind.SAVE }
            if (join != null) {
                when (val r = a.savePlaylist(join.payload.orEmpty())) {
                    is ActionResult.Queued -> return Drain(queued = r.message, sent = sent)
                    is ActionResult.LocalOnly -> return Drain(queued = "Sign in to send it to YouTube.", sent = sent)
                    // Refused: it is not on the account, so it is not shown as if it were.
                    is ActionResult.Failed -> { ops.done(join.id); repo.setOnAccount(pid, false); failure = r.message }
                    is ActionResult.Ok -> { ops.done(join.id); sent++ }
                }
                continue
            }
            val p = repo.get(pid)
            val remote = p?.remoteId
            if (p == null || remote == null) { ops.clearPlaylist(pid); continue }   // unlinked or gone: nothing to say to YouTube
            if (list.any { it.attempts >= MAX_ATTEMPTS }) continue                    // gave up; "Retry" resets it
            var i = 0
            var stop = false
            while (i < list.size && !stop) {
                val op = list[i]
                when (op.kind) {
                    OpKind.ADD -> {
                        val chunk = ArrayList<com.greenaltambe.vela.data.PlaylistOpRow>()
                        while (i < list.size && list[i].kind == OpKind.ADD && chunk.size < ADD_CHUNK) chunk += list[i++]
                        val ids = chunk.mapNotNull { it.videoId }
                        var r = a.addManyToPlaylist(remote, ids)
                        // One request for many is unverified against YouTube: when it is refused (and it is not the limit or a full playlist), the same videos go one at a time.
                        if (r is ActionResult.Failed && ids.size > 1 && !isFull(r.message)) {
                            var res: ActionResult = ActionResult.Ok
                            for (v in ids) { res = a.addToPlaylist(remote, v); if (res !is ActionResult.Ok && res !is ActionResult.LocalOnly) break }
                            r = res
                        }
                        when (r) {
                            is ActionResult.Queued -> return Drain(queued = r.message, sent = sent)
                            is ActionResult.Failed -> {
                                if (isFull(r.message)) { for (o in chunk) { repo.setRowState(pid, o.videoId.orEmpty(), RowState.OVERFLOW); ops.done(o.id) }; stop = false }
                                else { for (o in chunk) ops.failed(o.id, r.message); failure = r.message; stop = true }
                            }
                            is ActionResult.LocalOnly -> return Drain(queued = "Sign in to send it to YouTube.", sent = sent)
                            else -> { for (o in chunk) { repo.setRowState(pid, o.videoId.orEmpty(), RowState.SYNCED); ops.done(o.id) }; sent += chunk.size }
                        }
                    }
                    OpKind.REMOVE -> {
                        i++
                        when (val r = a.removeFromPlaylist(remote, op.videoId.orEmpty())) {
                            is ActionResult.Queued -> return Drain(queued = r.message, sent = sent)
                            is ActionResult.Failed -> { ops.failed(op.id, r.message); failure = r.message; stop = true }
                            is ActionResult.LocalOnly -> return Drain(queued = "Sign in to send it to YouTube.", sent = sent)
                            else -> { repo.remove(pid, op.videoId.orEmpty()); ops.done(op.id); sent++ }
                        }
                    }
                    OpKind.RENAME -> {
                        i++
                        when (val r = a.renamePlaylist(remote, op.payload.orEmpty())) {
                            is ActionResult.Queued -> return Drain(queued = r.message, sent = sent)
                            is ActionResult.Failed -> { ops.failed(op.id, r.message); failure = r.message; stop = true }
                            is ActionResult.LocalOnly -> return Drain(queued = "Sign in to send it to YouTube.", sent = sent)
                            else -> { ops.done(op.id); sent++ }
                        }
                    }
                    OpKind.DELETE, OpKind.SAVE, OpKind.UNSAVE -> i++
                }
            }
            // Everything of a linking playlist has been sent: it is synced now.
            if (ops.forPlaylist(pid).isEmpty()) repo.get(pid)?.let { cur -> if (cur.syncState == PlaylistSync.LINKING && cur.pendingCount == 0) repo.markSynced(pid, now()) }
            // Three failures in a row: stop trying and say so on the playlist (Retry starts again). Never an endless loop.
            if (failure != null && ops.forPlaylist(pid).any { it.attempts >= MAX_ATTEMPTS }) repo.setState(pid, PlaylistSync.ERROR, failure)
        }
        return Drain(failure = failure, sent = sent)
    }

    // ---- reads ----

    /**
     * Brings the account's playlists in (the viewer's own become SYNCED, other people's SAVED), syncs every synced playlist and refreshes the saved ones.
     */
    suspend fun syncAll(): SyncResult {
        val started = now().toEpochMilliseconds()
        val result = syncAccount()
        // Outside the lock: reading other people's lists must not hold up the viewer's own changes. Those just brought in whole are not read twice.
        if (result.failure == null) repo.all().filter { it.isSaved && (it.lastSyncedAt?.let { at -> at <= started } != false) }.forEach { refreshSaved(it.id) }
        return result
    }

    /** Whether the page says this list belongs to somebody else. When it cannot tell, it is not claimed. */
    private fun isSomeoneElses(listing: PlaylistListing): Boolean {
        val d = listing.details ?: return false
        val me = accounts.active.value.channelId
        return d.canBeSaved == true && !(me != null && d.ownerChannelId == me)
    }

    /** A playlist in the account's library that is somebody else's and new to this device (lock held). */
    private fun importSaved(remoteId: String, listing: PlaylistListing) {
        val id = repo.createSaved(listing.details!!.toSource(remoteId), now())
        repo.setOnAccount(id, true)
        repo.replaceItems(id, listing.videos, now())
        if (listing.complete) repo.markRefreshed(id, now())
        pulledTotal += listing.videos.size
    }

    /**
     * A list held as the viewer's own synced one is somebody else's (an earlier version imported saved playlists so): it becomes a saved one and what
     * waited to be written to it is dropped. Videos the viewer had added and YouTube never took are kept in a playlist of their own. Lock held.
     */
    private fun becomeSaved(p: PlaylistSummary, listing: PlaylistListing): PlaylistResult {
        val source = p.remoteId!!
        val details = listing.details!!
        val theirs = listing.videos.mapTo(HashSet()) { it.id }
        val mine = repo.rows(p.id).filter { (it.rowState == RowState.PENDING_ADD || it.rowState == RowState.OVERFLOW) && it.videoId !in theirs }
        ops.clearPlaylist(p.id)
        if (mine.isNotEmpty()) repo.add(repo.create("${p.name} (your additions)", now()), mine.map { it.toSummary() }, now())
        mine.forEach { repo.remove(p.id, it.videoId) }
        repo.changeRowStates(p.id, RowState.PENDING_REMOVE, RowState.SYNCED)
        repo.changeRowStates(p.id, RowState.PENDING_ADD, RowState.SYNCED)
        repo.changeRowStates(p.id, RowState.OVERFLOW, RowState.SYNCED)
        val onAccount = details.savedOnAccount != false
        val copy = repo.bySource(source)
        val id = if (copy != null) { repo.delete(p.id); copy.id } else { repo.makeSaved(p.id, details.toSource(source), now()); p.id }
        repo.setOnAccount(id, onAccount || copy?.onAccount == true)
        if (!onAccount) ignore(source, true)
        if (listing.complete) { repo.replaceItems(id, listing.videos, now()); repo.markRefreshed(id, now()) }
        changed()
        return PlaylistResult.ok("\"${p.name}\" is somebody else's playlist: it is now a saved one.", id = id)
    }

    private suspend fun syncAccount(): SyncResult = lock.withLock {
        val source = reads ?: return SyncResult(0, 0, "Sign in to sync playlists.")
        if (!accounts.active.value.isSignedIn) return SyncResult(0, 0, "Sign in to sync playlists.")
        drainAll()
        val remote = source.playlists() ?: return SyncResult(0, 0, "Could not read the account's playlists; local data was kept.")
        pulledTotal = 0
        val ignored = ignoredRemotes()
        for (rp in remote) {
            // Watch later joins only when the viewer ticked the box on it; a playlist the viewer stopped syncing is not brought back as a second copy.
            if (rp.id == "WL" && repo.get(repo.watchLaterId())?.remoteId == null) continue
            // Saved here already: it is somebody else's, not one to sync as the viewer's own. One kept on this device only stays so, and once it has been
            // seen in the account's library, removing it here does not bring it back.
            val copy = repo.bySource(rp.id)
            // (Held as synced as well, it is looked at below and the two become one.)
            if (copy != null && repo.byRemote(rp.id) == null) { if (!copy.onAccount) ignore(rp.id, true); continue }
            if (rp.id in ignored && copy == null) continue
            if (rp.id == "WL" || rp.id == "LL" || repo.byRemote(rp.id) != null) { syncOne(repo.linked(rp.id, rp.title, now()), pull = true, fromSyncAll = true); continue }
            // New to this device: its page says whose it is. One that cannot be read now waits for the next sync.
            val listing = source.playlistListing(rp.id) ?: continue
            if (!isSomeoneElses(listing)) syncOne(repo.linked(rp.id, rp.title, now()), pull = true, fromSyncAll = true, listed = listing)
            else if (listing.details?.savedOnAccount != false) importSaved(rp.id, listing)
        }
        // Taken out of the library on youtube.com: only the playlist's own page can say so. Not being listed, or a read that fails, removes nothing.
        val listed = remote.mapTo(HashSet()) { it.id }
        for (p in repo.all().filter { it.isSaved && it.onAccount && it.sourceId !in listed }) {
            if (ops.forPlaylist(p.id).isNotEmpty()) continue
            if (source.playlistPage(p.sourceId!!)?.details?.savedOnAccount == false) repo.delete(p.id)
        }
        // Playlists already synced here that the account listing did not include (a long list may be cut off) are read on their own.
        for (p in repo.all().filter { it.isSynced && remote.none { r -> r.id == it.remoteId } }) syncOne(p.id, pull = true, fromSyncAll = true)
        changed()
        val total = repo.all().filter { it.isSynced }.sumOf { it.itemCount }
        SyncResult(pulledTotal, (total - pulledTotal).coerceAtLeast(0))
    }

    /** Syncs one playlist (and sends what waits for it). */
    suspend fun syncPlaylist(id: Long): PlaylistResult = lock.withLock { syncOne(id, pull = true) }

    /** Lets a playlist that gave up try again, then syncs it. */
    suspend fun retry(id: Long): PlaylistResult = lock.withLock {
        ops.retry(id); repo.setState(id, PlaylistSync.SYNCED, null); syncOne(id, pull = true)
    }

    /**
     * The sync of one playlist (lock held):
     * 1. send what waits; 2. add what YouTube has and this copy lacks; 3. drop what YouTube no longer has (never a video still waiting to be added, and never
     * when the read failed, was cut off, or answered empty for a playlist that is not); 4. queue what is here and not there; 5. take YouTube's order.
     */
    private suspend fun syncOne(id: Long, pull: Boolean, fromSyncAll: Boolean = false, listed: PlaylistListing? = null): PlaylistResult {
        val p = repo.get(id) ?: return PlaylistResult.failed("That playlist is gone.")
        val remote = p.remoteId ?: return PlaylistResult.ok()
        val source = reads
        var note: String? = null
        /** Queues what is here and not on YouTube: as many as fit, the rest become "not on YouTube". */
        fun queuePending(remoteSet: Set<String>, onYouTube: Int) {
            if (p.isReadOnly) return
            val rows = repo.rows(id)
            val order = rows.filter { it.rowState == RowState.PENDING_ADD && it.videoId !in remoteSet } + rows.filter { it.rowState == RowState.OVERFLOW && it.videoId !in remoteSet }
            val room = (limit - onYouTube).coerceAtLeast(0)
            order.forEachIndexed { i, row ->
                if (i < room) {
                    if (row.rowState == RowState.OVERFLOW) repo.setRowState(id, row.videoId, RowState.PENDING_ADD)
                    if (!ops.hasAdd(id, row.videoId)) ops.enqueueAdd(id, row.videoId, now())
                } else if (row.rowState == RowState.PENDING_ADD) { ops.cancelAdd(id, row.videoId); repo.setRowState(id, row.videoId, RowState.OVERFLOW) }
            }
        }
        if (pull && source != null) {
            drainPlaylists(setOf(id))
            val listing = listed ?: source.playlistListing(remote)
            if (listing != null && remote != "WL" && remote != "LL" && isSomeoneElses(listing)) return becomeSaved(p, listing)
            if (listing == null) {
                if (!fromSyncAll) repo.setState(id, PlaylistSync.ERROR, "Could not read it on YouTube.")
                changed(); return PlaylistResult.failed("Could not read \"${p.name}\" on YouTube; nothing here was changed.")
            }
            val rows = repo.rows(id)
            val remoteIds = listing.videos.map { it.id }
            val remoteSet = remoteIds.toHashSet()
            // An empty answer for a playlist that holds synced videos here is a failed read, not a playlist someone emptied.
            val suspiciousEmpty = remoteIds.isEmpty() && rows.any { it.rowState == RowState.SYNCED }
            if (suspiciousEmpty) note = "YouTube answered an empty list; kept what is here."
            else {
                val have = rows.associateBy { it.videoId }
                for (v in listing.videos) {
                    val row = have[v.id] ?: continue
                    if (row.rowState == RowState.PENDING_ADD || row.rowState == RowState.OVERFLOW) { repo.setRowState(id, v.id, RowState.SYNCED); ops.cancelAdd(id, v.id) }
                }
                // One write for all that is new: Liked videos can bring thousands at once.
                pulledTotal += repo.add(id, listing.videos.filter { it.id !in have }, now(), RowState.SYNCED)
                if (listing.complete) for (row in rows) when {
                    row.rowState == RowState.SYNCED && row.videoId !in remoteSet -> repo.remove(id, row.videoId)
                    row.rowState == RowState.PENDING_REMOVE && row.videoId !in remoteSet -> { repo.remove(id, row.videoId); ops.cancelRemove(id, row.videoId) }
                }
                queuePending(remoteSet, if (listing.complete) remoteIds.size else repo.rows(id).count { it.rowState == RowState.SYNCED })
                repo.applyRemoteOrder(id, remoteIds)
            }
        } else if (!pull) queuePending(emptySet(), 0)   // a playlist just made on YouTube is empty: no read needed
        val out = drainPlaylists(setOf(id))
        val cur = repo.get(id)
        val stillWaiting = ops.forPlaylist(id).isNotEmpty() || (cur?.pendingCount ?: 0) > 0
        if (cur?.syncState != PlaylistSync.ERROR || out.failure == null) {
            if (!stillWaiting && out.failure == null) repo.markSynced(id, now())
            else if (cur?.syncState != PlaylistSync.LINKING && cur?.syncState != PlaylistSync.ERROR) repo.setState(id, PlaylistSync.SYNCED, null)
        }
        changed()
        val over = repo.get(id)?.overflowCount ?: 0
        return when {
            out.failure != null -> PlaylistResult.failed(out.failure)
            out.queued != null -> PlaylistResult.pending(out.queued, id).copy(overflow = over)
            else -> PlaylistResult(true, note ?: if (over > 0) "$over did not fit on YouTube." else null, false, id, over)
        }
    }

    // ---- remembered "stopped syncing" ids ----

    private fun ignoreKey() = "library.unlinkedRemotes.$accountId"
    private fun ignoredRemotes(): Set<String> = store.get(ignoreKey(), SettingLayer.Global)?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()
    /** Remembers (or forgets) that the viewer stopped syncing a YouTube playlist, so the next sync does not bring it back as a second copy. */
    private fun ignore(remoteId: String, on: Boolean) {
        val cur = ignoredRemotes()
        val next = if (on) cur + remoteId else cur - remoteId
        if (next != cur) store.put(ignoreKey(), SettingLayer.Global, next.joinToString(","))
    }

    // ---- when it runs ----

    fun mode(): AccountHistorySync = settings?.resolve(Settings.libraryAccountSync) ?: AccountHistorySync.MANUAL

    private fun isDue(trigger: LibrarySyncTrigger, account: String): Boolean = when (mode()) {
        AccountHistorySync.OFF -> trigger == LibrarySyncTrigger.MANUAL
        AccountHistorySync.MANUAL -> trigger == LibrarySyncTrigger.MANUAL
        AccountHistorySync.ON_START -> trigger == LibrarySyncTrigger.START || trigger == LibrarySyncTrigger.MANUAL
        AccountHistorySync.EVERY_6H -> trigger == LibrarySyncTrigger.MANUAL || (lastRun(account)?.let { now() - it >= SIX_HOURS } ?: true)
    }

    /**
     * What the app's timers and workers call: the outbox is sent whenever something waits (those are the viewer's own changes, whatever the sync setting says),
     * and the read happens when the setting says it is due. Returns the read's result when one ran.
     */
    suspend fun runScheduled(trigger: LibrarySyncTrigger): SyncResult? {
        if (!syncAvailable) return null
        if (trigger == LibrarySyncTrigger.START && startHandled) return null
        if (trigger == LibrarySyncTrigger.START) startHandled = true
        if (ops.count() > 0) drain()
        val account = accounts.active.value.id
        if (mode() != AccountHistorySync.OFF && isDue(trigger, account) && trigger != LibrarySyncTrigger.MANUAL) {
            return syncAll().also { if (it.failure == null) store.put(lastKey(account), SettingLayer.Global, now().toEpochMilliseconds().toString()) }
        }
        return null
    }

    /** The viewer's "Sync now". */
    suspend fun syncNow(): SyncResult = syncAll().also { if (it.failure == null) store.put(lastKey(accountId), SettingLayer.Global, now().toEpochMilliseconds().toString()) }

    private fun lastKey(accountId: String) = "library.accountSync.lastAt.$accountId"
    private fun lastRun(accountId: String): Instant? = store.get(lastKey(accountId), SettingLayer.Global)?.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) }

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val NOT_YOURS = "This playlist belongs to its owner. Use \"Add all to a playlist\" for a copy you can change."
        private val SAVED_FRESH = 10.minutes
        private val SIX_HOURS = 6.hours
    }
}
