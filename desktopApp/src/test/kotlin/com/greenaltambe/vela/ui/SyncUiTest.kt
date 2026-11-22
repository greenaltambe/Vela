@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.*
import com.greenaltambe.vela.sync.FolderSyncBackend
import com.greenaltambe.vela.sync.SyncBackend
import com.greenaltambe.vela.sync.SyncFolders
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncUiTest {
    private class TempFolders(val dir: File) : SyncFolders {
        override suspend fun choose() = dir.path
        override fun label(descriptor: String) = "Shared/Vela sync"
        override fun open(descriptor: String): SyncBackend = FolderSyncBackend(File(descriptor))
    }

    private class FakeLan(val dir: File) : com.greenaltambe.vela.sync.LanTransport {
        override val peers = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.greenaltambe.vela.sync.LanPeer>())
        var started = false
        override fun start(secret: String, deviceId: String, deviceName: String, onPeerData: () -> Unit) { started = true }
        override fun stop() { started = false }
        override fun backend(): SyncBackend = FolderSyncBackend(dir)
        override fun reset() {}
    }

    @Test fun webDavNeedsAWorkingTestBeforeItCanStart() = runDesktopComposeUiTest(800, 3600) {
        val dir = Files.createTempDirectory("vsync-dav").toFile()
        val factory = com.greenaltambe.vela.sync.WebDavFactory { _, _, pass -> check(pass == "good") { "The server rejected the username or password." }; FolderSyncBackend(dir) }
        val f = buildFixture(withDownloads = false, syncFolders = TempFolders(dir), webDavFactory = factory)
        f.navigator.push(Route.Sync)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("sync.mode.webdav").performClick(); waitForIdle()
        onNodeWithTag("sync.newCode").performClick(); waitForIdle()
        onNodeWithTag("sync.start").assertIsNotEnabled()
        onNodeWithTag("sync.dav.user").performTextInput("me@example.org")
        onNodeWithTag("sync.dav.preview").assertTextContains("https://app.koofr.net/dav/Koofr/Vela%20sync", substring = true)
        onNodeWithTag("sync.dav.pass").performTextInput("bad")
        onNodeWithTag("sync.dav.test").performClick()
        waitUntil(timeoutMillis = 20_000) { runCatching { onNodeWithTag("sync.dav.error").assertExists() }.isSuccess }
        onNodeWithTag("sync.start").assertIsNotEnabled()
        onNodeWithTag("sync.dav.pass").performTextClearance(); onNodeWithTag("sync.dav.pass").performTextInput("good")
        onNodeWithTag("sync.dav.test").performClick()
        waitUntil(timeoutMillis = 20_000) { runCatching { onNodeWithTag("sync.dav.ok").assertExists() }.isSuccess }
        onNodeWithTag("sync.start").assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 20_000) { runCatching { onNodeWithTag("sync.statusTitle").assertTextEquals("Up to date") }.isSuccess }
        assertEquals(com.greenaltambe.vela.sync.SyncMode.WEBDAV, f.services.syncService.config.value.mode)
    }

    @Test fun wifiIsTheDefaultAndNeedsNoFolder() = runDesktopComposeUiTest(800, 3200) {
        val lan = FakeLan(Files.createTempDirectory("vsync-lan").toFile())
        val f = buildFixture(withDownloads = false, syncFolders = TempFolders(lan.dir), lanTransport = lan)
        f.navigator.push(Route.Sync)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("sync.chooseFolder").assertDoesNotExist()            // Wi-Fi is preselected
        onNodeWithTag("sync.newCode").performClick(); waitForIdle()
        onNodeWithTag("sync.start").assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 20_000) { runCatching { onNodeWithTag("sync.statusTitle").assertTextEquals("Up to date") }.isSuccess }
        assertTrue(lan.started)
        assertEquals(com.greenaltambe.vela.sync.SyncMode.LAN, f.services.syncService.config.value.mode)
    }

    @Test fun firstDeviceSetupIsThreeStepsAndEndsUpToDate() = runDesktopComposeUiTest(800, 3200) {
        val dir = Files.createTempDirectory("vsync-ui").toFile()
        val f = buildFixture(withDownloads = false, syncFolders = TempFolders(dir))
        f.navigator.push(Route.Sync)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("sync.start").assertIsNotEnabled()               // nothing chosen yet
        onNodeWithTag("sync.chooseFolder").performClick(); waitForIdle()
        onNodeWithTag("sync.folderLabel").assertExists()
        onNodeWithTag("sync.start").assertIsNotEnabled()               // still needs a code
        onNodeWithTag("sync.newCode").performClick(); waitForIdle()
        onNodeWithTag("sync.codeText").assertExists()
        onNodeWithTag("sync.start").assertIsEnabled()
        onNodeWithTag("sync.start").performClick()
        waitUntil(timeoutMillis = 20_000) { runCatching { onNodeWithTag("sync.statusTitle").assertTextEquals("Up to date") }.isSuccess }
        assertTrue(f.services.syncService.config.value.isSetUp)
        assertEquals(1, dir.listFiles()!!.count { it.name.endsWith(".vsync") })
    }

    @Test fun anotherDeviceAcceptsTheCodeTypedByHand() = runDesktopComposeUiTest(800, 3200) {
        val dir = Files.createTempDirectory("vsync-ui2").toFile()
        val first = buildFixture(withDownloads = false, syncFolders = TempFolders(dir))
        first.services.syncService.setFolder(dir.path); first.services.syncService.setCode("AAAA-BBBB-CCCC-DDDD")
        kotlinx.coroutines.runBlocking { first.services.syncService.syncNow() }

        val second = buildFixture(withDownloads = false, syncFolders = TempFolders(dir))
        second.navigator.push(Route.Sync)
        setContent { AppUnderTest(second) }; waitForIdle()
        onNodeWithTag("sync.chooseFolder").performClick(); waitForIdle()
        onNodeWithTag("sync.haveCode").performClick(); waitForIdle()
        onNodeWithTag("sync.codeField").performTextInput("aaaa bbbb cccc dddd")
        onNodeWithTag("sync.start").assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 20_000) { runCatching { onNodeWithTag("sync.statusTitle").assertTextEquals("Up to date") }.isSuccess }
        assertEquals(2, dir.listFiles()!!.count { it.name.endsWith(".vsync") })
    }
}
