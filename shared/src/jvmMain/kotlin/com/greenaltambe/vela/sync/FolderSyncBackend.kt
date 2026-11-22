package com.greenaltambe.vela.sync

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** A plain folder (which Syncthing, Dropbox, Nextcloud, Google Drive and friends keep in step). Writes go to a temp file first, then replace, so a half-written file is never seen. */
class FolderSyncBackend(private val dir: File) : SyncBackend {
    override suspend fun list(): List<String> = dir.listFiles { f -> f.isFile && f.name.endsWith(".vsync") }?.map { it.name }.orEmpty()
    override suspend fun read(name: String): ByteArray? = File(dir, name).takeIf { it.isFile }?.readBytes()
    override suspend fun write(name: String, data: ByteArray) {
        dir.mkdirs()
        val tmp = File(dir, ".$name.tmp")
        tmp.writeBytes(data)
        Files.move(tmp.toPath(), File(dir, name).toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    override suspend fun delete(name: String) { File(dir, name).delete() }
}
