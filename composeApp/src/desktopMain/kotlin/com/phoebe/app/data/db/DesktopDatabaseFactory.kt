package com.phoebe.app.data.db

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.sql.DriverManager
import java.util.Properties

actual suspend fun createSqlDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver {
    val root = desktopDatabaseRoot()
    val dbFileName = localDatabaseFileName()
    val dbFile = File(root, dbFileName)
    val revFile = File(root, "$dbFileName.rev")

    wipeIfRevisionChanged(dbFile, revFile)

    val properties = Properties().apply {
        setProperty("busy_timeout", "10000")
        setProperty("journal_mode", "WAL")
        setProperty("synchronous", "NORMAL")
    }
    val driver = openDriver(dbFile, properties, schema)

    // Guard against a revision marker that was written even though the wipe failed.
    if (dbFile.exists() && !appSettingsSchemaCompatible(dbFile)) {
        driver.close()
        deleteDatabaseFiles(dbFile)
        val rebuilt = openDriver(dbFile, properties, schema)
        revFile.writeText(LocalDbRevision.toString())
        rebuilt.execPragma("PRAGMA busy_timeout=30000")
        return rebuilt
    }

    revFile.writeText(LocalDbRevision.toString())
    driver.execPragma("PRAGMA busy_timeout=30000")
    return driver
}

private fun openDriver(
    dbFile: File,
    properties: Properties,
    schema: SqlSchema<QueryResult.AsyncValue<Unit>>,
): JdbcSqliteDriver =
    JdbcSqliteDriver(
        url = "jdbc:sqlite:${dbFile.absolutePath}",
        properties = properties,
        schema = schema.synchronous(),
    )

/**
 * Pre-release shortcut: if the on-disk revision marker doesn't match [LocalDbRevision],
 * delete the database file so SQLDelight can rebuild it from the current schema. Replace
 * with real migrations once we ship.
 */
private fun wipeIfRevisionChanged(dbFile: File, revFile: File) {
    if (!dbFile.exists()) return
    val onDiskRev = revFile.takeIf { it.exists() }?.runCatching { readText().trim().toLong() }?.getOrNull()
    val revisionStale = onDiskRev != null && onDiskRev != LocalDbRevision
    val schemaStale = !appSettingsSchemaCompatible(dbFile)
    if (revisionStale || schemaStale) {
        deleteDatabaseFiles(dbFile)
    }
}

internal fun deleteDatabaseFiles(dbFile: File) {
    dbFile.delete()
    // SQLite may leave auxiliary journal/WAL/SHM files alongside the main db; drop
    // them too so the rebuilt schema doesn't pick up half-written pages.
    File(dbFile.parentFile, "${dbFile.name}-journal").delete()
    File(dbFile.parentFile, "${dbFile.name}-wal").delete()
    File(dbFile.parentFile, "${dbFile.name}-shm").delete()
}

internal fun appSettingsSchemaCompatible(dbFile: File): Boolean =
    runCatching {
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA table_info(AppSettingsRow)").use { rows ->
                    generateSequence { if (rows.next()) rows.getString("name") else null }
                        .any { it == LocalDbRevision19Column }
                }
            }
        }
    }.getOrDefault(false)

internal fun desktopDatabaseRoot(): File =
    System.getProperty("phoebe.storage.root")?.let(::File)
        ?: File(System.getProperty("user.home"), desktopDataDirectoryName()).also { it.mkdirs() }
