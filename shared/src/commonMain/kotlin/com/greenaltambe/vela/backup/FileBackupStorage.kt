package com.greenaltambe.vela.backup

/** Backups kept as `.json` files in a plain folder (the app's own folder, or a folder the viewer chose on desktop). Writes are atomic (temp file, then replace). */
expect fun fileBackupStorage(directory: String, label: String): BackupStorage
