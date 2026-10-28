package com.greenaltambe.vela.data

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.greenaltambe.vela.data.db.VelaDatabase
import java.util.Properties

/** Shown to the viewer as it is: what failed, that nothing was changed, and where the backup is. */
class DatabaseStartupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * [path] null gives an in-memory database. Creates or migrates the schema like the Android driver does.
 * Before a migration the file is copied to `<name>.bak-v<old>` (kept; never deleted by the app), a migration that fails
 * leaves the file untouched (one transaction) and is reported with the backup's location, and a file that fails
 * `PRAGMA quick_check` is reported, not repaired or replaced: accounts and downloads live in it.
 */
class JvmDatabaseDriverFactory(private val path: String? = null) : DatabaseDriverFactory {
    override fun create(): SqlDriver {
        // sqlite-jdbc applies this to every connection it opens, unlike a one-off PRAGMA.
        val properties = Properties().apply { setProperty("foreign_keys", "true") }
        val driver = JdbcSqliteDriver(if (path == null) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:$path", properties)
        val schema = VelaDatabase.Schema
        val stored = try {
            driver.executeQuery(null, "PRAGMA user_version", { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
            }, 0).value
        } catch (e: Exception) {
            driver.close()
            throw DatabaseStartupException("The database file could not be read (${e.message?.take(120)}). Nothing was changed or deleted; copy $path somewhere safe before doing anything else.", e)
        }
        if (stored > schema.version) { driver.close(); throw DatabaseStartupException("The database is version $stored but this build only knows ${schema.version}. It was written by a newer Vela; nothing was changed.") }
        if (path != null && stored > 0) {
            val check = runCatching { driver.executeQuery(null, "PRAGMA quick_check", { c -> QueryResult.Value(if (c.next().value) c.getString(0) else null) }, 0).value }.getOrNull()
            if (check != null && check != "ok") { driver.close(); throw DatabaseStartupException("The database file looks damaged (quick_check said: ${check.take(120)}). Nothing was changed or deleted; copy $path somewhere safe before doing anything else.") }
        }
        if (stored != schema.version) {
            val backup = if (path != null && stored > 0) java.io.File("$path.bak-v$stored").takeIf { !it.exists() }?.also { b ->
                runCatching { java.io.File(path).copyTo(b) }.onFailure { driver.close(); throw DatabaseStartupException("Could not back up the database before updating it (${it.message}); nothing was changed.", it) }
            } ?: java.io.File("$path.bak-v$stored") else null
            // One transaction (user_version is transactional too), so a failed migration leaves the file untouched.
            try {
                object : TransacterImpl(driver) {}.transaction {
                    if (stored == 0L) schema.create(driver).value else schema.migrate(driver, stored, schema.version).value
                    driver.execute(null, "PRAGMA user_version = ${schema.version}", 0)
                }
            } catch (e: Exception) {
                driver.close()
                throw DatabaseStartupException("Updating the database from version $stored to ${schema.version} failed (${e.message?.take(160)}). Your data was not changed${backup?.let { "; a backup copy is at ${it.path}" } ?: ""}.", e)
            }
        }
        return driver
    }
}
