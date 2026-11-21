package com.greenaltambe.vela

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/** What to run to get libmpv on this system, from /etc/os-release. */
fun libmpvInstallHint(osRelease: String? = runCatching { java.io.File("/etc/os-release").readText() }.getOrNull(), os: String = System.getProperty("os.name").orEmpty()): String {
    if (os.startsWith("Windows")) return "Put libmpv-2.dll next to Vela.exe (see docs/BUILDING.md)."
    val ids = osRelease.orEmpty().lineSequence().filter { it.startsWith("ID=") || it.startsWith("ID_LIKE=") }.joinToString(" ").lowercase()
    return when {
        "fedora" in ids || "rhel" in ids -> "sudo dnf install https://mirrors.rpmfusion.org/free/fedora/rpmfusion-free-release-\$(rpm -E %fedora).noarch.rpm && sudo dnf install mpv-libs"
        "debian" in ids || "ubuntu" in ids -> "sudo apt install libmpv2   (Ubuntu 22.04: libmpv1)"
        "arch" in ids -> "sudo pacman -S mpv"
        "suse" in ids -> "sudo zypper install libmpv2"
        else -> "Install your distribution's libmpv package (mpv-libs, libmpv2 or libmpv1)."
    }
}

/** Shown at the top of every window while libmpv could not be loaded: without it the app runs but plays nothing, which used to be only a line on a video page. */
@Composable
fun PlayerMissingBanner(reason: String?, modifier: Modifier = Modifier) {
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return
    val hint = remember { libmpvInstallHint() }
    val clipboard = LocalClipboardManager.current
    Surface(modifier.fillMaxWidth().testTag("player.missing"), color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Videos can't play: the libmpv player library was not found.", style = MaterialTheme.typography.titleSmall)
                Text("Install it, then restart Vela:  $hint", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { clipboard.setText(AnnotatedString(hint)) }) { Text("Copy command") }
            TextButton(onClick = { dismissed = true }) { Text("Dismiss") }
        }
    }
}
