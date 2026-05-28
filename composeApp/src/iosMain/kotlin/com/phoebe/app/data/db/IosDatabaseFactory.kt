package com.phoebe.app.data.db

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual suspend fun createSqlDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver {
    val driver = NativeSqliteDriver(
        schema = schema.synchronous(),
        name = localDatabaseFileName(),
    )
    driver.execPragma("PRAGMA journal_mode=WAL")
    driver.execPragma("PRAGMA synchronous=NORMAL")
    driver.execPragma("PRAGMA temp_store=MEMORY")
    driver.execPragma("PRAGMA busy_timeout=30000")
    return driver
}
