package com.phoebe.app.data.db

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

actual suspend fun createSqlDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver {
    val root = File(System.getProperty("user.home"), ".phoebe").also { it.mkdirs() }
    val dbFile = File(root, LocalDbName)
    val revFile = File(root, "$LocalDbName.rev")

    wipeIfRevisionChanged(dbFile, revFile)

    // Passing the schema parameter lets the JDBC driver invoke Schema.create / Schema.migrate
    // automatically based on PRAGMA user_version, so future schema changes "just work".
    return JdbcSqliteDriver(
        url = "jdbc:sqlite:${dbFile.absolutePath}",
        properties = Properties(),
        schema = schema.synchronous(),
    )
}

/**
 * Pre-release shortcut: if the on-disk revision marker doesn't match [LocalDbRevision],
 * delete the database file so SQLDelight can rebuild it from the current schema. Replace
 * with real migrations once we ship.
 */
private fun wipeIfRevisionChanged(dbFile: File, revFile: File) {
    val onDiskRev = revFile.takeIf { it.exists() }?.runCatching { readText().trim().toLong() }?.getOrNull()
    if (dbFile.exists() && onDiskRev != LocalDbRevision) {
        dbFile.delete()
        // SQLite may leave auxiliary journal/WAL/SHM files alongside the main db; drop
        // them too so the rebuilt schema doesn't pick up half-written pages.
        File(dbFile.parentFile, "${dbFile.name}-journal").delete()
        File(dbFile.parentFile, "${dbFile.name}-wal").delete()
        File(dbFile.parentFile, "${dbFile.name}-shm").delete()
    }
    revFile.writeText(LocalDbRevision.toString())
}
