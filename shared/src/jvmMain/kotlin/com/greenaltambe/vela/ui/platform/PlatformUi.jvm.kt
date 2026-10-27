package com.greenaltambe.vela.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}

@Composable
actual fun rememberTextFilePicker(onPicked: (String?) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            // FileDialog blocks until closed, so it must not run on the UI thread's event loop.
            val text = withContext(Dispatchers.IO) {
                val dialog = FileDialog(null as Frame?, "Choose a subscriptions file", FileDialog.LOAD).apply { isVisible = true }
                val name = dialog.file ?: return@withContext null
                runCatching { File(dialog.directory, name).readText() }.getOrNull()
            }
            callback.value(text)
        }
    }
}

@Composable
actual fun rememberFolderPicker(onPicked: (String?) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val chosen = withContext(Dispatchers.IO) {
                val chooser = javax.swing.JFileChooser().apply {
                    fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                    dialogTitle = "Choose the download folder"
                }
                if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile.path else null
            }
            callback.value(chosen)
        }
    }
}
