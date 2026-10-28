package com.greenaltambe.vela.auth

import com.greenaltambe.vela.data.PlaylistRepository
import kotlin.time.Clock

/**
 * Copies the account's playlists (Watch Later, Liked videos and the others) into local mirror playlists.
 * Conflict rules: it only ADDS. A video removed from the account stays in the local mirror; a video the viewer removed from the mirror comes
 * back on the next sync; mirrors are never deleted; nothing local is pushed to the account by this (Save > "Watch later (YouTube)" and the other
 * account actions write directly). A failed or signed-out read changes nothing.
 */
class AccountLibrarySync(private val reads: AccountReads, private val playlists: () -> PlaylistRepository, private val clock: Clock = Clock.System) {
    suspend fun sync(): SyncResult {
        val remote = reads.playlists() ?: return SyncResult(0, 0, "Could not read the account's playlists; local data was kept.")
        val repo = playlists()
        var added = 0; var had = 0
        for (p in remote) {
            val videos = reads.playlistVideos(p.id) ?: continue
            val local = repo.mirror(p.id, if (p.id == "WL" || p.id == "LL") p.title + " (YouTube)" else p.title, clock.now())
            val n = repo.add(local, videos, clock.now())
            added += n; had += videos.size - n
        }
        return SyncResult(added, had)
    }
}
