package com.phoebe.app.platform

/** SQLite file name for the on-device catalog database. */
fun localDatabaseFileName(): String =
    if (isDebugBuild()) "phoebe-debug.db" else "phoebe.db"

/** Stable key prefix for web schema-version metadata and legacy web database lookup. */
fun localDatabaseRevisionKey(): String =
    if (isDebugBuild()) "phoebe-debug.db.revision" else "phoebe.db.revision"

/** Relative directory under the platform storage root (filesDir, Documents, ~/.phoebe). */
fun localStorageDirectoryName(): String =
    if (isDebugBuild()) "phoebe-debug" else "phoebe"

/** Desktop data directory under the user home folder when no override is set. */
fun desktopDataDirectoryName(): String =
    if (isDebugBuild()) ".phoebe-debug" else ".phoebe"
