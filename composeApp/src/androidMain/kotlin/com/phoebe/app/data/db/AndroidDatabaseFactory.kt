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
    val driver = AndroidSqliteDriver(
        schema = schema.synchronous(),
        context = context,
        name = localDatabaseFileName(),
        callback = object : AndroidSqliteDriver.Callback(schema.synchronous()) {
            override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Android execSQL rejects PRAGMAs that return result rows; route all through query().
                db.execPragma("PRAGMA journal_mode=WAL")
                db.execPragma("PRAGMA synchronous=NORMAL")
                db.execPragma("PRAGMA temp_store=MEMORY")
                db.execPragma("PRAGMA busy_timeout=30000")
            }
        },
    )
    return driver
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
    if (onDisk != null && onDisk != LocalDbRevision) {
        context.deleteDatabase(localDatabaseFileName())
    }
    prefs.edit().putLong(revisionKey, LocalDbRevision).apply()
}

/** Runs a PRAGMA on Android, which may return a result row even for assignment forms. */
private fun androidx.sqlite.db.SupportSQLiteDatabase.execPragma(sql: String) {
    query(sql).use { /* consume result if any */ }
}
