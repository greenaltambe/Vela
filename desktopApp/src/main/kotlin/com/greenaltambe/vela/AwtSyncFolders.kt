package com.greenaltambe.vela

import com.greenaltambe.vela.sync.FolderSyncBackend
import com.greenaltambe.vela.sync.SyncBackend
import com.greenaltambe.vela.sync.SyncFolders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import com.greenaltambe.vela.ui.platform.NativePicker
import java.io.File
import javax.swing.JFileChooser

/** Sync's folder is picked with a folder dialog and kept as a plain path. A "Vela sync" sub-folder is used so the shared folder stays tidy. */
class AwtSyncFolders : SyncFolders {
    override suspend fun choose(): String? {
        val picked = withContext(Dispatchers.IO) { NativePicker.folder("Choose the shared folder for Vela sync", File(System.getProperty("user.home"))) }
        return when (picked) {
            is NativePicker.Result.Chosen -> File(picked.file, "Vela sync").also { it.mkdirs() }.path
            NativePicker.Result.Cancelled -> null
            NativePicker.Result.Unavailable -> chooseWithSwing()
        }
    }

    private suspend fun chooseWithSwing(): String? = withContext(Dispatchers.Swing) {
        val chooser = JFileChooser(System.getProperty("user.home")).apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY; dialogTitle = "Choose the shared folder for Vela sync" }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@withContext null
        File(chooser.selectedFile, "Vela sync").also { it.mkdirs() }.path
    }

    override fun label(descriptor: String): String = descriptor.replaceFirst(System.getProperty("user.home"), "~")
    override fun open(descriptor: String): SyncBackend = FolderSyncBackend(File(descriptor))
}
