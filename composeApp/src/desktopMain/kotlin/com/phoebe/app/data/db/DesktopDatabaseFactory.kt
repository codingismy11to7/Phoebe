package com.phoebe.app.data.db

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

actual suspend fun createSqlDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver {
    val root = desktopDatabaseRoot()
    val dbFileName = localDatabaseFileName()
    val dbFile = File(root, dbFileName)

    val properties = Properties().apply {
        setProperty("busy_timeout", "10000")
        setProperty("journal_mode", "WAL")
        setProperty("synchronous", "NORMAL")
    }

    val driver = openDriver(dbFile, properties, schema)
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

internal fun desktopDatabaseRoot(): File =
    System.getProperty("phoebe.storage.root")?.let(::File)
        ?: File(System.getProperty("user.home"), desktopDataDirectoryName()).also { it.mkdirs() }
