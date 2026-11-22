package com.greenaltambe.vela.backup

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

actual fun fileBackupStorage(directory: String, label: String): BackupStorage = FileBackupStorage(File(directory), label)

private class FileBackupStorage(private val dir: File, override val label: String) : BackupStorage {
    override suspend fun list(): List<StoredBackup> = dir.listFiles { f -> f.isFile && f.name.startsWith("vela-") && f.name.endsWith(".json") }
        ?.map { StoredBackup(it.name, it.length(), it.lastModified()) }.orEmpty()
    override suspend fun read(name: String): String? = File(dir, name).takeIf { it.isFile }?.readText()
    override suspend fun write(name: String, text: String) {
        dir.mkdirs()
        val tmp = File(dir, ".$name.tmp").also { it.writeText(text) }
        Files.move(tmp.toPath(), File(dir, name).toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    override suspend fun delete(name: String) { File(dir, name).delete() }
}
