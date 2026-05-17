package com.phoebe.app.data.db

import android.content.Context
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.phoebe.app.AndroidContextHolder

actual suspend fun createSqlDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver {
    val context = AndroidContextHolder.application
    wipeIfRevisionChanged(context)
    return AndroidSqliteDriver(
        schema = schema.synchronous(),
        context = context,
        name = localDatabaseFileName(),
    )
}

/**
 * Pre-release shortcut: if the persisted DB revision differs from [LocalDbRevision], drop
 * the SQLite database entirely so SQLDelight can rebuild it from the current schema.
 * Replace with real migrations once we ship.
 */
private fun wipeIfRevisionChanged(context: Context) {
    val revisionKey = localDatabaseRevisionKey()
    val prefs = context.getSharedPreferences(localDatabaseMetaPrefsName(), Context.MODE_PRIVATE)
    val onDisk = if (prefs.contains(revisionKey)) prefs.getLong(revisionKey, -1L) else null
    if (onDisk != null && onDisk < 6L) {
        context.deleteDatabase(localDatabaseFileName())
    }
    prefs.edit().putLong(revisionKey, LocalDbRevision).apply()
}
