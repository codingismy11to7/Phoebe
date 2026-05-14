package com.phoebe.app.data.db

import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import kotlinx.browser.window
import org.w3c.dom.Worker

actual suspend fun createSqlDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver {
    val driver = WebWorkerDriver(createPersistentSqlWorker())
    val schemaKey = "$LocalDbRevisionKey.web.async"
    val storedRevision = window.localStorage.getItem(schemaKey)?.toLongOrNull()
    when {
        storedRevision == null -> schema.awaitCreate(driver)
        storedRevision < LocalDbRevision -> schema.awaitMigrate(driver, oldVersion = 1, newVersion = schema.version)
    }
    if (storedRevision != LocalDbRevision) {
        window.localStorage.setItem(schemaKey, LocalDbRevision.toString())
    }
    return driver
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => new Worker('/phoebe-sqljs.worker.js')")
private external fun createPersistentSqlWorker(): Worker
