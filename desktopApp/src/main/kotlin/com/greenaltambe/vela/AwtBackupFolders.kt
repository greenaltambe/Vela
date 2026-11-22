package com.greenaltambe.vela

import com.greenaltambe.vela.backup.BackupFolders
import com.greenaltambe.vela.backup.BackupStorage
import com.greenaltambe.vela.backup.fileBackupStorage
import com.greenaltambe.vela.ui.platform.NativePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser

/** The optional second backup folder, chosen with the system's folder dialog; a "Vela backups" sub-folder keeps it tidy. */
class AwtBackupFolders : BackupFolders {
    override suspend fun choose(): String? {
        val home = File(System.getProperty("user.home"))
        val picked: File? = when (val r = withContext(Dispatchers.IO) { NativePicker.folder("Choose a folder for Vela backups", home) }) {
            is NativePicker.Result.Chosen -> r.file
            NativePicker.Result.Cancelled -> null
            NativePicker.Result.Unavailable -> withContext(Dispatchers.Swing) {
                JFileChooser(home).apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY; dialogTitle = "Choose a folder for Vela backups" }
                    .let { if (it.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) it.selectedFile else null }
            }
        }
        return picked?.let { File(it, "Vela backups").also { d -> d.mkdirs() }.path }
    }

    override fun label(descriptor: String): String = descriptor.replaceFirst(System.getProperty("user.home"), "~")
    override fun open(descriptor: String): BackupStorage = fileBackupStorage(descriptor, label(descriptor))
}
