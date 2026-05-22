package com.phoebe.app.data.db

import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import com.phoebe.app.platform.isDebugBuild
import kotlinx.browser.window
import org.w3c.dom.Worker

actual suspend fun createSqlDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver {
    val schemaKey = "${localDatabaseRevisionKey()}.web.async"
    val storedRevision = window.localStorage.getItem(schemaKey)?.toLongOrNull()
    val driver = WebWorkerDriver(createPersistentSqlWorker(LocalDbRevision, isDebugBuild()))
    if (storedRevision == null || storedRevision != LocalDbRevision) {
        schema.awaitCreate(driver)
        window.localStorage.setItem(schemaKey, LocalDbRevision.toString())
    }
    return driver
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    (revision, debug) => {
      const url = new URL('phoebe-sqljs.worker.js', document.baseURI || location.href);
      url.searchParams.set('revision', String(revision));
      if (debug) url.searchParams.set('debug', '1');
      return new Worker(url.toString());
    }
    """,
)
private external fun createPersistentSqlWorker(revision: Long, debug: Boolean): Worker
