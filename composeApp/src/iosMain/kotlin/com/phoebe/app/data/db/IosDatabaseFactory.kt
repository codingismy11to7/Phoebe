package com.phoebe.app.data.db

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask

actual suspend fun createSqlDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver {
    wipeIfRevisionChanged()
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

/**
 * Pre-release shortcut: if the persisted DB revision differs from [LocalDbRevision],
 * delete the SQLDelight SQLite file under `~/Documents/databases/` so SQLDelight can
 * rebuild it from the current schema. Replace with real migrations once we ship.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun wipeIfRevisionChanged() {
    val defaults = NSUserDefaults.standardUserDefaults
    val revisionKey = localDatabaseRevisionKey()
    val dbFileName = localDatabaseFileName()
    val present = defaults.objectForKey(revisionKey) != null
    val onDisk = if (present) defaults.integerForKey(revisionKey) else null
    if (onDisk != null && onDisk != LocalDbRevision) {
        val docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String
        if (docs != null) {
            // NativeSqliteDriver writes to `${documents}/databases/${name}.db` by default.
            val base = "$docs/databases/$dbFileName.db"
            NSFileManager.defaultManager.removeItemAtPath(base, error = null)
            NSFileManager.defaultManager.removeItemAtPath("$base-wal", error = null)
            NSFileManager.defaultManager.removeItemAtPath("$base-shm", error = null)
        }
    }
    defaults.setInteger(LocalDbRevision, forKey = revisionKey)
}
