package com.greenaltambe.vela.ui.platform

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

@Composable
actual fun rememberTextFilePicker(onPicked: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val text = uri?.let {
            runCatching { context.contentResolver.openInputStream(it)?.use { s -> s.readBytes().decodeToString() } }.getOrNull()
        }
        callback.value(text)
    }
    return { launcher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/json", "text/plain", "*/*")) }
}

@Composable
actual fun rememberFolderPicker(onPicked: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // Keep access across restarts and reboots; without this the folder stops working after the app is closed.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        callback.value(uri?.toString())
    }
    return { launcher.launch(null) }
}
