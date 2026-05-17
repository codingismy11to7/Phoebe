package com.phoebe.app.data.db

import com.phoebe.app.platform.isDebugBuild

/** SQLite file name for the on-device catalog database. */
internal fun localDatabaseFileName(): String =
    if (isDebugBuild()) "phoebe-debug.db" else "phoebe.db"

/** UserDefaults / SharedPreferences key for the schema wipe revision marker. */
internal fun localDatabaseRevisionKey(): String =
    if (isDebugBuild()) "phoebe-debug.db.revision" else "phoebe.db.revision"

/** Android SharedPreferences file backing the DB revision marker. */
internal fun localDatabaseMetaPrefsName(): String =
    if (isDebugBuild()) "phoebe-db-meta-debug" else "phoebe-db-meta"

/** Relative directory under the platform storage root (filesDir, Documents, ~/.phoebe). */
internal fun localStorageDirectoryName(): String =
    if (isDebugBuild()) "phoebe-debug" else "phoebe"

/** Desktop data directory under the user home folder when no override is set. */
internal fun desktopDataDirectoryName(): String =
    if (isDebugBuild()) ".phoebe-debug" else ".phoebe"
