package com.phoebe.app.data.db

import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.async.coroutines.awaitQuery
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import com.phoebe.app.platform.isDebugBuild
import kotlinx.browser.window
import org.w3c.dom.Worker

actual suspend fun createSqlDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver {
    val legacyRevisionKey = "${localDatabaseRevisionKey()}.web.async"
    val schemaVersionKey = "${localDatabaseRevisionKey()}.web.schema"
    val storedSchemaVersion = window.localStorage.getItem(schemaVersionKey)?.toLongOrNull()
    val legacyRevision = window.localStorage.getItem(legacyRevisionKey)?.toLongOrNull()
    val driver = WebWorkerDriver(createPersistentSqlWorker(legacyRevision?.toString(), isDebugBuild()))
    val existingSchemaVersion = storedSchemaVersion ?: legacyRevision?.plus(1L)
    val hasSchema = driver.hasUserSchema()

    if (!hasSchema) {
        schema.awaitCreate(driver)
    } else if (existingSchemaVersion != null && existingSchemaVersion < schema.version) {
        schema.awaitMigrate(driver, existingSchemaVersion, schema.version)
    }
    if (existingSchemaVersion == null || existingSchemaVersion <= schema.version) {
        window.localStorage.setItem(schemaVersionKey, schema.version.toString())
        window.localStorage.removeItem(legacyRevisionKey)
    }
    return driver
}

private suspend fun SqlDriver.hasUserSchema(): Boolean =
    awaitQuery(
        identifier = null,
        sql = "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' LIMIT 1",
        mapper = { cursor -> cursor.next().await() },
        parameters = 0,
    )

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    (legacyRevision, debug) => {
      const url = new URL('phoebe-sqljs.worker.js', document.baseURI || location.href);
      if (legacyRevision != null) url.searchParams.set('legacyRevision', String(legacyRevision));
      if (debug) url.searchParams.set('debug', '1');
      return new Worker(url.toString());
    }
    """,
)
private external fun createPersistentSqlWorker(legacyRevision: String?, debug: Boolean): Worker
