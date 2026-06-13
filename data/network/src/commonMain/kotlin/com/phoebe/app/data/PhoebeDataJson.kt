package com.phoebe.app.data

import kotlinx.serialization.json.Json

val PhoebeDataJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
