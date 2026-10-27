package com.greenaltambe.vela.net

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileDownloadStorageTest {
    private val root = Files.createTempDirectory("vela-storage").toFile()
    private val publishDir = File(root, "Videos")
    private val storage = FileDownloadStorage(publishDir)

    @AfterTest fun cleanUp() { root.deleteRecursively() }

    private fun write(name: String, bytes: ByteArray) = storage.append(name).use { it.write(bytes, 0, bytes.size) }
    private fun com.greenaltambe.vela.downloads.DownloadSink.use(block: (com.greenaltambe.vela.downloads.DownloadSink) -> Unit) { try { block(this) } finally { close() } }

    @Test fun appendingContinuesWhereTheFileEndsAndSizeReportsIt() {
        assertNull(storage.size("a.part"))
        write("a.part", ByteArray(10) { 1 })
        assertEquals(10L, storage.size("a.part"))
        write("a.part", ByteArray(5) { 2 })
        assertEquals(15L, storage.size("a.part"))
        assertEquals(listOf<Byte>(1, 1, 2), File(storage.localPath("a.part")).readBytes().let { listOf(it[0], it[9], it[10]) })
    }

    @Test fun partialFilesStayOutOfTheDestinationFolder() {
        write("x.part", ByteArray(3))
        assertTrue(publishDir.listFiles().orEmpty().none { it.isFile }, "the viewer's folder shows nothing until a file is finished")
    }

    @Test fun renameReplacesAndDeleteRemoves() {
        write("a.part", ByteArray(3)); write("b", ByteArray(9))
        storage.rename("a.part", "b")
        assertEquals(3L, storage.size("b"))
        assertNull(storage.size("a.part"))
        storage.delete("b")
        assertNull(storage.size("b"))
        storage.delete("never existed")
    }

    @Test fun publishMovesTheFileAndNeverOverwritesAnother() {
        write("T [abcdefghijk].mp4", ByteArray(4) { 1 })
        val first = storage.publish("T [abcdefghijk].mp4")
        assertEquals(File(publishDir, "T [abcdefghijk].mp4").path, first)
        assertNull(storage.size("T [abcdefghijk].mp4"), "moved out of the working area")

        write("T [abcdefghijk].mp4", ByteArray(6) { 2 })
        val second = storage.publish("T [abcdefghijk].mp4")
        assertEquals(File(publishDir, "T [abcdefghijk] (1).mp4").path, second)
        assertEquals(4L, File(first).length())
        assertEquals(6L, File(second).length())
    }

    @Test fun deletingAPublishedFileOnlyWorksInsideTheDestination() {
        write("T [abcdefghijk].mp4", ByteArray(4))
        val uri = storage.publish("T [abcdefghijk].mp4")
        val outside = File(root, "precious.txt").apply { writeText("keep") }
        storage.deletePublished(outside.path)
        assertTrue(outside.exists(), "a path outside the folder is left alone")
        storage.deletePublished(uri)
        assertFalse(File(uri).exists())
    }

    @Test fun nameWithPathPartsAreRefused() {
        assertFailsWith<IllegalArgumentException> { storage.size("../escape") }
        assertFailsWith<IllegalArgumentException> { storage.append("a/b") }
        assertFailsWith<IllegalArgumentException> { storage.delete("..") }
    }

    @Test fun assetsAreKeptApartAndDeletableByPathOnly() {
        val path = storage.writeAsset("thumb.jpg", ByteArray(7))
        assertEquals(7L, File(path).length())
        assertFalse(path.startsWith(publishDir.path + File.separator) && File(path).parentFile == publishDir)
        storage.deleteAsset(File(root, "other").path)
        assertTrue(File(path).exists())
        storage.deleteAsset(path)
        assertFalse(File(path).exists())
    }

    @Test fun usedBytesCountsOnlyOurOwnFiles() {
        publishDir.mkdirs()
        File(publishDir, "holiday.mp4").writeBytes(ByteArray(1000))
        write("A [abcdefghijk].mp4", ByteArray(10)); storage.publish("A [abcdefghijk].mp4")
        write("B [lmnopqrstuv].m4a", ByteArray(20)); storage.publish("B [lmnopqrstuv].m4a")
        assertEquals(30L, storage.usedBytes())
    }

    @Test fun freeSpaceIsReported() {
        assertTrue((storage.freeBytes() ?: 0) > 0)
        assertEquals(publishDir.path, storage.description)
    }
}
