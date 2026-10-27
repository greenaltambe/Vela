package com.greenaltambe.vela.data

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.greenaltambe.vela.data.db.VelaDatabase
import java.util.Properties

/** [path] null gives an in-memory database. Creates or migrates the schema like the Android driver does. */
class JvmDatabaseDriverFactory(private val path: String? = null) : DatabaseDriverFactory {
    override fun create(): SqlDriver {
        // sqlite-jdbc applies this to every connection it opens, unlike a one-off PRAGMA.
        val properties = Properties().apply { setProperty("foreign_keys", "true") }
        val driver = JdbcSqliteDriver(if (path == null) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:$path", properties)
        val schema = VelaDatabase.Schema
        val stored = driver.executeQuery(null, "PRAGMA user_version", { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
        }, 0).value
        check(stored <= schema.version) { "Database is version $stored but this build only knows ${schema.version}" }
        if (stored != schema.version) {
            // One transaction (user_version is transactional too), so a failed migration leaves the file untouched.
            object : TransacterImpl(driver) {}.transaction {
                if (stored == 0L) schema.create(driver).value else schema.migrate(driver, stored, schema.version).value
                driver.execute(null, "PRAGMA user_version = ${schema.version}", 0)
            }
        }
        return driver
    }
}
