package com.greenaltambe.vela

import com.greenaltambe.vela.backup.BackupFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import com.greenaltambe.vela.ui.platform.NativePicker
import java.io.File

/** The backup file chosen with the system's own file dialog (AWT, so it is the GTK/native one where available). */
class AwtBackupFiles : BackupFiles {
    override suspend fun save(suggestedName: String, text: String): String? = withContext(Dispatchers.Swing) {
        val d = FileDialog(null as Frame?, "Save backup", FileDialog.SAVE).apply { file = suggestedName; isVisible = true }
        val name = d.file ?: return@withContext null
        File(d.directory, name).also { it.writeText(text) }.path
    }

    override suspend fun pick(): String? {
        when (val r = withContext(Dispatchers.IO) { NativePicker.file("Choose a Vela backup") }) {
            is NativePicker.Result.Chosen -> return r.file.readText()
            NativePicker.Result.Cancelled -> return null
            NativePicker.Result.Unavailable -> Unit
        }
        return pickWithAwt()
    }

    private suspend fun pickWithAwt(): String? = withContext(Dispatchers.Swing) {
        val d = FileDialog(null as Frame?, "Choose a Vela backup", FileDialog.LOAD).apply { isVisible = true }
        val name = d.file ?: return@withContext null
        File(d.directory, name).readText()
    }
}
