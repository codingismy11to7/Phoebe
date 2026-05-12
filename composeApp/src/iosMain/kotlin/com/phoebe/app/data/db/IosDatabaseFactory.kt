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
    return NativeSqliteDriver(
        schema = schema.synchronous(),
        name = LocalDbName,
    )
}

/**
 * Pre-release shortcut: if the persisted DB revision differs from [LocalDbRevision],
 * delete the SQLDelight SQLite file under `~/Documents/databases/` so SQLDelight can
 * rebuild it from the current schema. Replace with real migrations once we ship.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun wipeIfRevisionChanged() {
    val defaults = NSUserDefaults.standardUserDefaults
    val present = defaults.objectForKey(LocalDbRevisionKey) != null
    val onDisk = if (present) defaults.integerForKey(LocalDbRevisionKey) else null
    if (onDisk != null && onDisk != LocalDbRevision) {
        val docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String
        if (docs != null) {
            // NativeSqliteDriver writes to `${documents}/databases/${name}.db` by default.
            val path = "$docs/databases/$LocalDbName.db"
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        }
    }
    defaults.setInteger(LocalDbRevision, forKey = LocalDbRevisionKey)
}
