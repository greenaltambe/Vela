package com.greenaltambe.vela.data

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import com.greenaltambe.vela.data.db.Download
import com.greenaltambe.vela.data.db.VelaDatabase

/** Platform-specific: JDBC SQLite on desktop, Android SQLite on Android. Returns a ready (schema-created) driver. */
interface DatabaseDriverFactory {
    fun create(): SqlDriver
}

fun createDatabase(factory: DatabaseDriverFactory): VelaDatabase = VelaDatabase(
    driver = factory.create(),
    DownloadAdapter = Download.Adapter(
        stateAdapter = EnumColumnAdapter(),
        kindAdapter = EnumColumnAdapter(),
    ),
)
