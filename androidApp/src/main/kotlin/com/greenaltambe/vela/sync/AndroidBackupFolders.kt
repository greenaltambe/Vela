package com.greenaltambe.vela.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.greenaltambe.vela.backup.BackupFolders
import com.greenaltambe.vela.backup.BackupStorage
import com.greenaltambe.vela.backup.StoredBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The optional second backup folder through the system folder picker. A "Vela backups" sub-folder keeps it tidy; the permission survives restarts. */
class AndroidBackupFolders(private val context: Context, private val pick: suspend () -> Uri?) : BackupFolders {
    override suspend fun choose(): String? {
        val tree = pick() ?: return null
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val root = DocumentFile.fromTreeUri(context, tree) ?: return tree.toString()
        val sub = root.findFile("Vela backups")?.takeIf { it.isDirectory } ?: root.createDirectory("Vela backups")
        return (sub?.uri ?: tree).toString()
    }

    override fun label(descriptor: String): String = Uri.parse(descriptor).let { u -> DocumentFile.fromTreeUri(context, u)?.name ?: u.lastPathSegment ?: descriptor }
    override fun open(descriptor: String): BackupStorage = SafBackupStorage(context, Uri.parse(descriptor), label(descriptor))
}

private class SafBackupStorage(private val context: Context, private val uri: Uri, override val label: String) : BackupStorage {
    private fun dir(): DocumentFile = DocumentFile.fromTreeUri(context, uri)?.takeIf { it.isDirectory && it.canWrite() } ?: throw java.io.IOException("The backup folder is not available. Choose it again.")

    override suspend fun list(): List<StoredBackup> = withContext(Dispatchers.IO) {
        dir().listFiles().filter { it.name?.let { n -> n.startsWith("vela-") && n.endsWith(".json") } == true }.map { StoredBackup(it.name!!, it.length(), it.lastModified()) }
    }

    override suspend fun read(name: String): String? = withContext(Dispatchers.IO) {
        dir().findFile(name)?.let { f -> context.contentResolver.openInputStream(f.uri)?.use { String(it.readBytes()) } }
    }

    override suspend fun write(name: String, text: String) = withContext(Dispatchers.IO) {
        val d = dir()
        val f = d.findFile(name) ?: d.createFile("application/json", name)?.let { c -> if (c.name != name) c.renameTo(name); c } ?: throw java.io.IOException("Could not create $name")
        context.contentResolver.openOutputStream(f.uri, "wt")?.use { it.write(text.toByteArray()) } ?: throw java.io.IOException("Could not write $name")
        Unit
    }

    override suspend fun delete(name: String) { withContext(Dispatchers.IO) { dir().findFile(name)?.delete() } }
}
