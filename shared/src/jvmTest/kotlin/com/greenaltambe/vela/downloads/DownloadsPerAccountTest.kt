package com.greenaltambe.vela.downloads

import com.greenaltambe.vela.data.DownloadKind
import com.greenaltambe.vela.data.DownloadState
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.VideoSummary
import kotlin.test.*

/** Leak tests: one database, two accounts, one repository that follows the active account. */
class DownloadsPerAccountTest {
    private var active = "acc-a"
    private val repo = DownloadRepository(createDatabase(JvmDatabaseDriverFactory()), { active })
    private fun video(id: String) = VideoSummary(id, "T $id", "UC1", "Chan")

    @Test fun eachAccountSeesOnlyItsOwnDownloads() {
        val a = repo.insert(DownloadRequest(video("v1")), StoredPlan(), "720p"); repo.setState(a, DownloadState.COMPLETED, null)
        active = "acc-b"
        assertEquals(emptyList(), repo.all(), "account B sees nothing of A's downloads")
        assertNull(repo.completedFor("v1")); assertNull(repo.find("v1", DownloadKind.VIDEO)); assertEquals(0L, repo.completedBytes())
        val b = repo.insert(DownloadRequest(video("v1")), StoredPlan(), "360p")   // the same video, B's own row (unique per account)
        assertNotEquals(a, b)
        assertEquals(listOf(b), repo.all().map { it.id })
        active = "acc-a"
        assertEquals(listOf(a), repo.all().map { it.id }); assertNotNull(repo.completedFor("v1"))
        assertEquals(emptyList(), repo.notCompleted(), "A's finished download is not B's pending work either")
    }

    @Test fun switchingBackFindsTheRowsUnchanged() {
        val id = repo.insert(DownloadRequest(video("v9")), StoredPlan(), "1080p")
        active = "acc-b"; active = "acc-a"
        assertEquals("1080p", repo.all().single { it.id == id }.qualityLabel)
    }
}
