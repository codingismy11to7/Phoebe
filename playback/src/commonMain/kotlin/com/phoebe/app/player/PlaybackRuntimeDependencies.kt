package com.phoebe.app.player

import com.phoebe.app.data.CatalogRepository
import com.phoebe.app.data.SessionRepository
import com.phoebe.app.db.PhoebeDatabase

interface PlaybackRuntimeDependencies {
    val database: PhoebeDatabase
    val catalogRepository: CatalogRepository
    val sessionRepository: SessionRepository
}
