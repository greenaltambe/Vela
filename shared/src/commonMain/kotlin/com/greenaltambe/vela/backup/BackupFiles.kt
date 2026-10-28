package com.greenaltambe.vela.backup

/** The platform's way to save and open the backup file. Both return null when the viewer cancels. */
interface BackupFiles {
    /** Saves [text] under a name the viewer chooses; returns where it ended up (a path or a description). */
    suspend fun save(suggestedName: String, text: String): String?
    /** Lets the viewer pick a backup and returns its text. */
    suspend fun pick(): String?
}
