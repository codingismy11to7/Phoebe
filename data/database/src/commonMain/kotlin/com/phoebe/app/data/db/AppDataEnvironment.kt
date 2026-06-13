package com.phoebe.app.data.db

@Deprecated("Use com.phoebe.app.platform.localDatabaseFileName")
internal fun localDatabaseFileName(): String = com.phoebe.app.platform.localDatabaseFileName()

@Deprecated("Use com.phoebe.app.platform.localDatabaseRevisionKey")
internal fun localDatabaseRevisionKey(): String = com.phoebe.app.platform.localDatabaseRevisionKey()

@Deprecated("Use com.phoebe.app.platform.localStorageDirectoryName")
internal fun localStorageDirectoryName(): String = com.phoebe.app.platform.localStorageDirectoryName()

@Deprecated("Use com.phoebe.app.platform.desktopDataDirectoryName")
internal fun desktopDataDirectoryName(): String = com.phoebe.app.platform.desktopDataDirectoryName()
