package com.phoebe.app.testing

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.phoebe.app.data.db.phoebeDatabaseFromDriver
import com.phoebe.app.db.PhoebeDatabase
import java.util.Properties

fun newInMemoryPhoebeDatabase(): Pair<PhoebeDatabase, SqlDriver> {
    val schema: SqlSchema<QueryResult.AsyncValue<Unit>> = PhoebeDatabase.Schema
    val driver = JdbcSqliteDriver(
        url = "jdbc:sqlite::memory:",
        properties = Properties(),
        schema = schema.synchronous(),
    )
    return phoebeDatabaseFromDriver(driver) to driver
}
