package com.phoebe.app.data.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.phoebe.app.db.PhoebeDatabase

/**
 * Platform-specific driver creation for the SQLDelight-backed Phoebe database.
 *
 * Each platform creates a [SqlDriver] backed by a SQLite file located alongside the rest of
 * the app's user data so the database survives app restarts.
 */
expect suspend fun createSqlDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver

suspend fun createPhoebeDatabase(): PhoebeDatabase =
    PhoebeDatabase(createSqlDriver(PhoebeDatabase.Schema))

/** Opens [PhoebeDatabase] with an existing driver (in-memory JDBC, Android test context, etc.). */
fun phoebeDatabaseFromDriver(driver: SqlDriver): PhoebeDatabase = PhoebeDatabase(driver)

/**
 * Pre-release schema revision. Bump this whenever a `.sq` file changes in a way that
 * requires existing tables/columns to be recreated. On launch, every platform driver
 * compares this constant against a persisted marker and **wipes the SQLite file** when
 * they differ.
 *
 * This is a deliberate shortcut: we haven't shipped 1.0 yet, so trashing the local cache
 * is cheap. Replace this with proper SQLDelight migration files (and a `verifyMigrations`
 * task) before any public release.
 */
internal const val LocalDbRevision: Long = 17L
