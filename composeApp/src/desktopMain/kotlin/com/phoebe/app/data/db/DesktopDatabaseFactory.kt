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
    migrateLegacyDesktopStorageIfNeeded(root)
    val dbFileName = localDatabaseFileName()
    val dbFile = File(root, dbFileName)
    checkpointDatabaseIfPresent(dbFile)

    val properties = Properties().apply {
        setProperty("busy_timeout", "10000")
        setProperty("journal_mode", "WAL")
        setProperty("synchronous", "NORMAL")
    }

    val driver = openDriver(dbFile, properties, schema)
    driver.execPragma("PRAGMA busy_timeout=30000")
    registerDesktopDatabaseShutdownHook(dbFile)
    return driver
}

private fun registerDesktopDatabaseShutdownHook(dbFile: File) {
    Runtime.getRuntime().addShutdownHook(
        Thread {
            runCatching {
                DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute("PRAGMA wal_checkpoint(TRUNCATE)")
                    }
                }
            }
        },
    )
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

private fun checkpointDatabaseIfPresent(dbFile: File) {
    if (!dbFile.isFile) return
    runCatching {
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE)")
            }
        }
    }
}

/**
 * Flatpak sandboxes store data under `~/.var/app/...`, but native/deb/dev builds use
 * `~/.phoebe` on the host. Copy the legacy tree once when the sandbox store is still empty.
 */
private fun migrateLegacyDesktopStorageIfNeeded(targetRoot: File) {
    if (!File("/.flatpak-info").exists()) return
    if (targetRoot.resolve(localDatabaseFileName()).isFile) return
    val userName = System.getProperty("user.name")?.takeIf { it.isNotBlank() } ?: return
    val hostLegacy = File("/home/$userName", desktopDataDirectoryName())
    if (!hostLegacy.isDirectory) return
    targetRoot.mkdirs()
    hostLegacy.listFiles()?.forEach { source ->
        val destination = targetRoot.resolve(source.name)
        if (!destination.exists()) {
            runCatching {
                if (source.isDirectory) {
                    source.copyRecursively(destination, overwrite = false)
                } else {
                    source.copyTo(destination, overwrite = false)
                }
            }
        }
    }
}
