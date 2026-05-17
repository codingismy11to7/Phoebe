package com.phoebe.app.data.db

import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import kotlinx.browser.window
import org.w3c.dom.Worker

actual suspend fun createSqlDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver {
    val schemaKey = "$LocalDbRevisionKey.web.async"
    val storedRevision = window.localStorage.getItem(schemaKey)?.toLongOrNull()
    val driver = WebWorkerDriver(createPersistentSqlWorker(LocalDbRevision))
    if (storedRevision == null || storedRevision != LocalDbRevision) {
        schema.awaitCreate(driver)
        window.localStorage.setItem(schemaKey, LocalDbRevision.toString())
    }
    return driver
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(revision) => new Worker('/phoebe-sqljs.worker.js?revision=' + revision)")
private external fun createPersistentSqlWorker(revision: Long): Worker
