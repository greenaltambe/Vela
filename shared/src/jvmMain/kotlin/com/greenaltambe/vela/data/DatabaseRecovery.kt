package com.greenaltambe.vela.data

import java.io.File
import java.sql.DriverManager

/**
 * Recovery for a damaged `vela.db`: the damaged file is moved aside (`<name>.corrupt-<time>`, never deleted), a fresh database with the
 * current schema is created, and every table that is NOT a cache (see [CacheMaintenance]) is copied over column by column, one table at a
 * time, so damage in one table (or in a cache, which is skipped) does not lose the others. Whatever cannot be read is reported by name.
 */
object DatabaseRecovery {
    class Result(val movedTo: File, val copied: Map<String, Int>, val failed: Map<String, String>) {
        fun describe() = "The damaged file was kept as ${movedTo.name}. Recovered: " +
            (copied.entries.filter { it.value > 0 }.joinToString { "${it.key} (${it.value})" }.ifEmpty { "nothing" }) +
            (if (failed.isEmpty()) "." else ". Could not read: ${failed.keys.joinToString()}.")
    }

    fun salvage(path: String, now: Long = System.currentTimeMillis()): Result {
        val file = File(path)
        val moved = File(file.parentFile, "${file.name}.corrupt-${now / 1000}")
        check(file.renameTo(moved)) { "could not move $path aside" }
        listOf("-wal", "-shm", "-journal").forEach { File(path + it).takeIf { f -> f.exists() }?.renameTo(File(moved.path + it)) }
        JvmDatabaseDriverFactory(path).create().close()   // a new file with the current schema
        val copied = linkedMapOf<String, Int>(); val failed = linkedMapOf<String, String>()
        DriverManager.getConnection("jdbc:sqlite:$path").use { c ->
            c.createStatement().use { it.execute("ATTACH DATABASE '${moved.path.replace("'", "''")}' AS old") }
            val tables = runCatching {
                c.createStatement().use { st -> st.executeQuery("SELECT name FROM old.sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'").use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } } }
            }.getOrElse { failed["(table list)"] = it.message.orEmpty(); emptyList() }
            for (t in tables) {
                if (t in CacheMaintenance.cacheTables) continue
                try {
                    val cols = columns(c, "main", t).intersect(columns(c, "old", t).toSet()).toList()
                    if (cols.isEmpty()) continue
                    val list = cols.joinToString { "`$it`" }
                    // Settings: the cache rows (health.*, release.*) are not carried over either.
                    val where = if (t == "Setting") " WHERE " + CacheMaintenance.cacheSettingPrefixes.joinToString(" AND ") { "`key` NOT LIKE '$it%'" } else ""
                    val n = c.createStatement().use { it.executeUpdate("INSERT OR IGNORE INTO main.`$t`($list) SELECT $list FROM old.`$t`$where") }
                    copied[t] = n
                } catch (e: Exception) { failed[t] = e.message?.take(100).orEmpty() }
            }
        }
        return Result(moved, copied, failed)
    }

    private fun columns(c: java.sql.Connection, schema: String, table: String): List<String> =
        c.createStatement().use { st -> st.executeQuery("PRAGMA $schema.table_info(`$table`)").use { rs -> buildList { while (rs.next()) add(rs.getString("name")) } } }
}
