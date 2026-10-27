package com.greenaltambe.vela.downloads

import android.app.Application
import org.robolectric.RuntimeEnvironment
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.DownloadKind
import com.greenaltambe.vela.data.DownloadState
import com.greenaltambe.vela.net.FileDownloadStorage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidDownloadsTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val settings = SettingsResolver(InMemorySettingsStore())
    private val provider = AndroidStorageProvider(context, settings)

    @Test fun theDefaultFolderIsTheAppsOwnMoviesFolderNeedingNoPermission() {
        val storage = provider.get()
        assertIs<FileDownloadStorage>(storage)
        assertTrue(storage.publishDir.path.startsWith(context.getExternalFilesDir(null)!!.path), storage.publishDir.path)
    }

    @Test fun aPlainPathIsUsedAsTheFolder() {
        val dir = File(context.cacheDir, "chosen").apply { mkdirs() }
        settings.set(Settings.downloadsFolder, dir.path)
        assertEquals(dir, (provider.get() as FileDownloadStorage).publishDir)
    }

    @Test fun aContentUriMeansTheSafTreeAndChangingTheSettingSwitchesStorage() {
        val default = provider.get()
        settings.set(Settings.downloadsFolder, "content://com.android.externalstorage.documents/tree/1234-ABCD%3AVela")
        val saf = provider.get()
        assertIs<SafDownloadStorage>(saf)
        assertFalse(default === saf)
        settings.set(Settings.downloadsFolder, "")
        assertIs<FileDownloadStorage>(provider.get())
    }

    @Test fun workingFilesAreKeptApartFromTheViewersFolderEvenOnTheSdCard() {
        settings.set(Settings.downloadsFolder, "content://com.android.externalstorage.documents/tree/1234-ABCD%3AVela")
        val saf = provider.get()
        saf.append("x.part").let { sink -> try { sink.write(ByteArray(3), 0, 3) } finally { sink.close() } }
        assertEquals(3L, saf.size("x.part"))
        assertTrue(saf.localPath("x.part").contains("downloads-partial"), saf.localPath("x.part"))
        assertEquals(null, saf.freeBytes(), "a SAF tree does not say how much room it has")
        saf.delete("x.part")
    }

    @Test fun theMuxerMergerDeclaresMp4AndWebmOnly() {
        val merger = AndroidMediaMerger()
        assertTrue(merger.available)
        assertEquals(setOf(Container.MP4, Container.WEBM), merger.containers)
        assertEquals(Container.WEBM, merger.containerFor("vp9", "opus"))
        assertEquals(Container.MP4, merger.containerFor("av01.0.12M.08", "opus"))
        assertEquals(Container.MP4, merger.containerFor("avc1.640028", "mp4a.40.2"))
    }

    @Test fun theProgressNotificationNamesTheDownloadAndOffersPauseAndCancel() {
        val item = DownloadItem(1, "v", "c", "My video", DownloadKind.VIDEO, "720p", DownloadState.DOWNLOADING, 50, 100, null, null, speedBps = 2_000_000)
        val n = progressNotification(context, listOf(item))
        assertEquals("My video", n.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertEquals(listOf("Pause all", "Cancel all"), n.actions.map { it.title.toString() })
        assertEquals(50, n.extras.getInt(android.app.Notification.EXTRA_PROGRESS))
        assertTrue((n.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0)
    }

    @Test fun severalDownloadsAreSummarised() {
        fun item(id: Long) = DownloadItem(id, "v$id", "c", "T$id", DownloadKind.VIDEO, "720p", DownloadState.DOWNLOADING, 10, 100, null, null)
        assertEquals("3 downloads", progressNotification(context, listOf(item(1), item(2), item(3))).extras.getString(android.app.Notification.EXTRA_TITLE))
    }

    @Test fun resultNotificationsWithoutThePermissionAreSkippedSilently() {
        // Robolectric starts with POST_NOTIFICATIONS not granted (API 33+): nothing may throw, nothing is shown.
        val notifier = AndroidDownloadNotifier(context)
        notifier.completed("A video")
        notifier.failed("A video", "disk full")
    }
}
