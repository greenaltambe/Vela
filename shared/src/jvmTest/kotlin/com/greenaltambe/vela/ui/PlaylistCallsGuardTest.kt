package com.greenaltambe.vela.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Task 84, R4: every screen changes playlists through `PlaylistService`, so a playlist behaves the same wherever it is touched. A screen that calls
 * the repository's writes or the account's playlist writes directly would skip the sync, the rollback and the outbox; this fails when one does.
 */
class PlaylistCallsGuardTest {
    private val uiDir = listOf("src/commonMain/kotlin/com/greenaltambe/vela/ui", "shared/src/commonMain/kotlin/com/greenaltambe/vela/ui").map(::File).first { it.isDirectory }

    @Test fun noScreenWritesPlaylistsOrTheirAccountCopyDirectly() {
        val direct = Regex("""playlists\.(add|remove|create|rename|delete|setRemote|linked)\(|actions[!?]*\.(addToPlaylist|removeFromPlaylist|addManyToPlaylist|createPlaylist|renamePlaylist|deletePlaylist|savePlaylist|unsavePlaylist)\(""")
        val offenders = uiDir.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line -> if (direct.containsMatchIn(line) && !line.trimStart().startsWith("//") && !line.trimStart().startsWith("*")) "${f.name}:${i + 1}: ${line.trim().take(100)}" else null }
        }.toList()
        assertTrue(offenders.isEmpty(), "go through services.playlistService instead:\n" + offenders.joinToString("\n"))
    }
}
