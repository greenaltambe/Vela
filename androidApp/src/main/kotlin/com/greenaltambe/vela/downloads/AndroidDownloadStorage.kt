package com.greenaltambe.vela.downloads

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.documentfile.provider.DocumentFile
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.net.FileDownloadStorage
import java.io.File

/**
 * Downloads into a folder the viewer picked through the Storage Access Framework (an SD card, a USB drive, any
 * provider). Working files stay in the app's own folder (SAF is slow and cannot append reliably); a finished file is
 * **copied** into the tree and the working copy deleted. The URI it returns is persistable, so it still plays after a
 * restart as long as the permission taken by the folder picker is kept.
 */
class SafDownloadStorage(
    private val context: Context,
    private val tree: Uri,
    private val local: FileDownloadStorage,
) : DownloadStorage by local {
    private val root: DocumentFile? get() = DocumentFile.fromTreeUri(context, tree)

    override val description: String get() = root?.name?.let { "$it (chosen folder)" } ?: tree.toString()

    override fun publish(name: String): String {
        val dir = root?.takeIf { it.canWrite() } ?: throw DownloadFailure("The download folder is not writable any more; choose it again in Downloads")
        val mime = when (name.substringAfterLast('.').lowercase()) {
            "mp4" -> "video/mp4"; "webm" -> "video/webm"; "mkv" -> "video/x-matroska"
            "m4a" -> "audio/mp4"; "opus", "ogg" -> "audio/ogg"; else -> "application/octet-stream"
        }
        val target = dir.createFile(mime, name.substringBeforeLast('.')) ?: throw DownloadFailure("Could not create a file in the download folder")
        val source = File(local.localPath(name))
        context.contentResolver.openOutputStream(target.uri, "w")?.use { out -> source.inputStream().use { it.copyTo(out, 256 * 1024) } }
            ?: throw DownloadFailure("Could not write to the download folder")
        local.delete(name)
        return target.uri.toString()
    }

    override fun deletePublished(uri: String) {
        runCatching { DocumentFile.fromSingleUri(context, Uri.parse(uri))?.delete() }
    }

    override fun freeBytes(): Long? = null // a SAF tree does not say how much room its volume has

    override fun usedBytes(): Long = root?.listFiles().orEmpty()
        .filter { it.isFile && Regex("""\[[A-Za-z0-9_-]{11}\]""").containsMatchIn(it.name.orEmpty()) }.sumOf { it.length() }
}

/** The folder in Settings > Downloads: empty = the app's own movies folder, `content://` = a SAF tree, anything else a plain path. */
class AndroidStorageProvider(private val context: Context, private val settings: SettingsResolver) {
    private var folder: String? = null
    private var current: DownloadStorage? = null

    @Synchronized
    fun get(): DownloadStorage {
        val configured = settings.resolve(Settings.downloadsFolder)
        if (current == null || folder != configured) {
            folder = configured
            val base = context.getExternalFilesDir(null) ?: context.filesDir
            val work = File(base, "downloads-partial")
            val assets = File(context.filesDir, "download-assets") // thumbnails: app-private, never in the viewer's folder
            current = when {
                configured.startsWith("content://") -> SafDownloadStorage(context, Uri.parse(configured), FileDownloadStorage(work, work, assets))
                configured.isNotBlank() -> FileDownloadStorage(File(configured), work, assets)
                else -> FileDownloadStorage(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(context.filesDir, "downloads"), work, assets)
            }
        }
        return current!!
    }

    @Suppress("unused")
    fun freeInternal(): Long = StatFs(context.filesDir.path).availableBytes
}
