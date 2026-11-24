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
            // AWT dialogs must be created and shown on the AWT event thread (off it, the native GTK dialog can take the whole process down).
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    when (val r = NativePicker.file("Choose a file")) {
                        is NativePicker.Result.Chosen -> return@runCatching r.file.readText()
                        NativePicker.Result.Cancelled -> return@runCatching null
                        NativePicker.Result.Unavailable -> Unit
                    }
                    val name = onAwtThread {
                        val dialog = FileDialog(null as Frame?, "Choose a file", FileDialog.LOAD).apply { isVisible = true }
                        dialog.file?.let { File(dialog.directory, it) }
                    }
                    name?.readText()
                }.getOrNull()
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
                runCatching {
                    when (val r = NativePicker.folder("Choose the download folder")) {
                        is NativePicker.Result.Chosen -> return@runCatching r.file.path
                        NativePicker.Result.Cancelled -> return@runCatching null
                        NativePicker.Result.Unavailable -> Unit
                    }
                    onAwtThread {
                        val chooser = javax.swing.JFileChooser().apply {
                            fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                            dialogTitle = "Choose the download folder"
                        }
                        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile.path else null
                    }
                }.getOrNull()
            }
            callback.value(chosen)
        }
    }
}

/** Runs [block] on the AWT event thread and waits for it; a modal dialog inside it keeps its own nested loop. */
private fun <T> onAwtThread(block: () -> T): T {
    var result: Result<T>? = null
    java.awt.EventQueue.invokeAndWait { result = runCatching(block) }
    return result!!.getOrThrow()
}

actual fun backdropBlurSupported(): Boolean = true
