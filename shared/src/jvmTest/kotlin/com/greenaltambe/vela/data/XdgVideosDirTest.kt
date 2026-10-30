package com.greenaltambe.vela.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class XdgVideosDirTest {
    private val home = Files.createTempDirectory("vela-home").toFile()
    @AfterTest fun cleanUp() { home.deleteRecursively() }

    private fun withDirs(text: String?): File {
        File(home, ".config").mkdirs()
        if (text != null) File(home, ".config/user-dirs.dirs").writeText(text)
        return XdgPaths.videosDir({ null }, home.path)
    }

    @Test fun readsTheConfiguredFolderExpandingHome() =
        assertEquals(File(home, "Filme"), withDirs("# comment\nXDG_DOWNLOAD_DIR=\"\$HOME/Downloads\"\nXDG_VIDEOS_DIR=\"\$HOME/Filme\"\n"))

    // "/mnt/..." has no drive letter, so it is not an absolute path on Windows.
    @Test fun anAbsolutePathIsUsedAsIs() = if (com.greenaltambe.vela.data.isWindows) Unit else assertEquals(File("/mnt/media/Videos"), withDirs("XDG_VIDEOS_DIR=\"/mnt/media/Videos\"\n"))

    @Test fun fallsBackToVideosWhenNothingIsConfigured() {
        assertEquals(File(home, "Videos"), withDirs(null))
        assertEquals(File(home, "Videos"), withDirs("XDG_DOCUMENTS_DIR=\"\$HOME/Docs\"\n"))
    }

    @Test fun aRelativeValueIsIgnored() = assertEquals(File(home, "Videos"), withDirs("XDG_VIDEOS_DIR=\"relative/path\"\n"))
}
