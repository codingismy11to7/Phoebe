package com.phoebe.app.testing

import android.content.Context
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.phoebe.app.data.db.phoebeDatabaseFromDriver
import com.phoebe.app.db.PhoebeDatabase
import java.util.UUID

data class AndroidTestDb(
    val database: PhoebeDatabase,
    val driver: AndroidSqliteDriver,
    val sqliteName: String,
)

fun newAndroidTestPhoebeDatabase(context: Context): AndroidTestDb {
    val name = "phoebe-test-${UUID.randomUUID()}.db"
    val driver = AndroidSqliteDriver(
        schema = PhoebeDatabase.Schema.synchronous(),
        context = context,
        name = name,
    )
    return AndroidTestDb(phoebeDatabaseFromDriver(driver), driver, name)
}
