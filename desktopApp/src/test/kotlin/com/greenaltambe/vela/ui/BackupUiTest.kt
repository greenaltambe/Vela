@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.backup.BackupFiles
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupUiTest {
    private class MemoryFiles(var stored: String? = null) : BackupFiles {
        override suspend fun save(suggestedName: String, text: String): String { stored = text; return "memory:$suggestedName" }
        override suspend fun pick(): String? = stored
    }

    @Test fun createThenRestoreIntoAnotherInstallShowsAPreviewFirstAndOnlyMerges() = runDesktopComposeUiTest(800, 1000) {
        val files = MemoryFiles()
        val a = buildFixture(withDownloads = false, backupFiles = files)       // has subscriptions, playlists and history from the harness
        a.navigator.push(Route.Backup)
        setContent { AppUnderTest(a) }; waitForIdle()
        onNodeWithTag("backup.create").performClick(); waitForIdle()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithTag("backup.message").assertIsDisplayed() }.isSuccess }
        assertTrue(files.stored!!.contains("vela-backup"))
        val before = a.services.subscriptions.all().size

        // A second, empty install restores it.
        val b = buildFixture(withDownloads = false, backupFiles = files)
        b.services.subscriptions.all().forEach { b.services.subscriptions.unsubscribe(it.channelId) }
        b.navigator.push(Route.Backup)
        setContent { AppUnderTest(b) }; waitForIdle()
        onNodeWithTag("backup.pick").performClick(); waitForIdle()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithTag("backup.preview").assertIsDisplayed() }.isSuccess }
        assertEquals(0, b.services.subscriptions.all().size, "the preview changed nothing")
        onNodeWithTag("backup.confirm").performClick(); waitForIdle()
        waitUntil(timeoutMillis = 3_000) { b.services.subscriptions.all().size == before }
        assertEquals(before, b.services.subscriptions.all().size)
    }

    @Test fun aFileThatIsNotABackupIsExplainedAndNothingIsChanged() = runDesktopComposeUiTest(800, 1000) {
        val f = buildFixture(withDownloads = false, backupFiles = MemoryFiles("hello"))
        f.navigator.push(Route.Backup)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("backup.pick").performClick(); waitForIdle()
        waitUntil(timeoutMillis = 3_000) { runCatching { onNodeWithTag("backup.message").assertIsDisplayed() }.isSuccess }
    }
}
