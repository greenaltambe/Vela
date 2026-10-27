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
        },
    )
}
