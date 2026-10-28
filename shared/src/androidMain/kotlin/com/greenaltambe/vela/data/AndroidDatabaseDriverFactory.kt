package com.greenaltambe.vela.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.greenaltambe.vela.data.db.VelaDatabase

class AndroidDatabaseDriverFactory(private val context: Context) : DatabaseDriverFactory {
    override fun create(): SqlDriver = AndroidSqliteDriver(
        schema = VelaDatabase.Schema,
        context = context,
        name = "vela.db",
        callback = object : AndroidSqliteDriver.Callback(VelaDatabase.Schema) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(true)
            }

            /** A copy of the file as it was (`vela.db.bak-v<old>` next to it, kept) before the schema is changed; a failure to copy stops the upgrade. */
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                val path = db.path
                if (path != null && oldVersion > 0) {
                    runCatching { db.query("PRAGMA wal_checkpoint(FULL)").close() }
                    val backup = java.io.File("$path.bak-v$oldVersion")
                    if (!backup.exists()) runCatching { java.io.File(path).copyTo(backup) }.onFailure { throw IllegalStateException("Could not back up the database before updating it (${it.message}); nothing was changed.", it) }
                }
                try { super.onUpgrade(db, oldVersion, newVersion) } catch (e: Exception) {
                    throw IllegalStateException("Updating the database from version $oldVersion to $newVersion failed (${e.message?.take(160)}). A backup copy is at $path.bak-v$oldVersion.", e)
                }
            }
        },
    )
}
