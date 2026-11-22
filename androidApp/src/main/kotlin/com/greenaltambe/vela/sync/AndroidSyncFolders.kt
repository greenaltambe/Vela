package com.greenaltambe.vela.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sync's folder through the system folder picker (Storage Access Framework). The permission is kept across restarts, so no file access
 * permission is needed. [pick] shows the picker and returns the chosen tree, or null when cancelled.
 */
class AndroidSyncFolders(private val context: Context, private val pick: suspend () -> Uri?) : SyncFolders {
    override suspend fun choose(): String? {
        val tree = pick() ?: return null
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        // A "Vela sync" folder inside the chosen one keeps the shared folder tidy.
        val root = DocumentFile.fromTreeUri(context, tree) ?: return tree.toString()
        val sub = root.findFile("Vela sync")?.takeIf { it.isDirectory } ?: root.createDirectory("Vela sync")
        return (sub?.uri ?: tree).toString()
    }

    override fun label(descriptor: String): String = Uri.parse(descriptor).let { u -> (DocumentFile.fromTreeUri(context, u)?.name ?: u.lastPathSegment ?: descriptor) }
    override fun open(descriptor: String): SyncBackend = SafSyncBackend(context, Uri.parse(descriptor))
}

private class SafSyncBackend(private val context: Context, private val uri: Uri) : SyncBackend {
    private fun dir(): DocumentFile = DocumentFile.fromTreeUri(context, uri)?.takeIf { it.isDirectory && it.canWrite() } ?: throw java.io.IOException("The sync folder is not available. Choose it again.")

    override suspend fun list(): List<String> = withContext(Dispatchers.IO) { dir().listFiles().mapNotNull { it.name }.filter { it.endsWith(".vsync") } }

    override suspend fun read(name: String): ByteArray? = withContext(Dispatchers.IO) {
        val f = dir().findFile(name) ?: return@withContext null
        context.contentResolver.openInputStream(f.uri)?.use { it.readBytes() }
    }

    /** SAF has no atomic replace; a reader that catches a half-written file fails its integrity check and simply tries again next sync. */
    override suspend fun write(name: String, data: ByteArray) = withContext(Dispatchers.IO) {
        val d = dir()
        val f = d.findFile(name) ?: d.createFile("application/octet-stream", name)?.let { created ->
            if (created.name != name) created.renameTo(name)
            created
        } ?: throw java.io.IOException("Could not create $name in the sync folder")
        context.contentResolver.openOutputStream(f.uri, "wt")?.use { it.write(data) } ?: throw java.io.IOException("Could not write $name")
        Unit
    }

    override suspend fun delete(name: String) { withContext(Dispatchers.IO) { dir().findFile(name)?.delete() } }
}
