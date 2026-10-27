package com.greenaltambe.vela.net

import com.greenaltambe.vela.downloads.DownloadSink
import com.greenaltambe.vela.downloads.DownloadStorage
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * [DownloadStorage] over ordinary files. Working files live in [workDir] (by default a hidden folder inside the
 * destination, so publishing is an atomic rename on the same file system), finished files go to [publishDir], and
 * small app-owned files (thumbnails) to [assetDir].
 */
class FileDownloadStorage(
    val publishDir: File,
    private val workDir: File = File(publishDir, ".vela-partial"),
    private val assetDir: File = File(workDir, "assets"),
) : DownloadStorage {
    override val description: String get() = publishDir.path

    private fun work(name: String): File {
        require('/' !in name && '\\' !in name && name != ".." && name != ".") { "not a file name: $name" }
        return File(workDir, name)
    }

    override fun size(name: String): Long? = work(name).takeIf { it.isFile }?.length()

    override fun append(name: String): DownloadSink {
        workDir.mkdirs()
        val out = FileOutputStream(work(name), /* append = */ true)
        return object : DownloadSink {
            override fun write(buffer: ByteArray, offset: Int, length: Int) = out.write(buffer, offset, length)
            override fun close() {
                out.flush()
                out.fd.sync() // a chunk that was reported done survives a power cut
                out.close()
            }
        }
    }

    override fun delete(name: String) { work(name).delete() }

    override fun rename(from: String, to: String) {
        Files.move(work(from).toPath(), work(to).toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    override fun localPath(name: String): String = work(name).path

    override fun publish(name: String): String {
        publishDir.mkdirs()
        val target = uniqueTarget(name)
        val source = work(name).toPath()
        try {
            Files.move(source, target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(source, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return target.path
    }

    /** A second download with the same title (another video, same name) must not overwrite the first. */
    private fun uniqueTarget(name: String): File {
        var target = File(publishDir, name)
        var n = 1
        while (target.exists()) { target = File(publishDir, name.substringBeforeLast('.') + " ($n)." + name.substringAfterLast('.')); n++ }
        return target
    }

    override fun deletePublished(uri: String) { File(uri).takeIf { it.parentFile?.canonicalFile == publishDir.canonicalFile }?.delete() }

    override fun writeAsset(name: String, bytes: ByteArray): String {
        assetDir.mkdirs()
        val file = File(assetDir, name)
        file.writeBytes(bytes)
        return file.path
    }

    override fun deleteAsset(path: String) { File(path).takeIf { it.parentFile?.canonicalFile == assetDir.canonicalFile }?.delete() }

    override fun freeBytes(): Long? = (if (publishDir.exists()) publishDir else publishDir.parentFile ?: publishDir).usableSpace.takeIf { it > 0 }

    /** Only files named like our downloads count (`... [videoId].ext`): the folder may hold the viewer's own videos. */
    override fun usedBytes(): Long = publishDir.listFiles { f -> f.isFile && ours.containsMatchIn(f.name) }.orEmpty().sumOf { it.length() }

    private val ours = Regex("""\[[A-Za-z0-9_-]{11}\](?: \((?:\d+|audio)\))*\.\w+$""")
}
