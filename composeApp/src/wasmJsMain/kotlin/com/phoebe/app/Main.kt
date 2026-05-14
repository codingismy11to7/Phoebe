package com.phoebe.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.phoebe.app.e2e.PhoebeWasmE2eApp
import com.phoebe.app.ui.PhoebeScreenshotApp
import com.phoebe.app.ui.PhoebeScreenshotScenario
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val queryParams = window.location.search
        .removePrefix("?")
        .split("&")
        .mapNotNull { part ->
            val key = part.substringBefore("=", missingDelimiterValue = "")
            val value = part.substringAfter("=", missingDelimiterValue = "")
            if (key.isBlank()) null else key to value
        }
        .toMap()
    val screenshotScenario = queryParams["screenshot"]
        ?.let { raw -> PhoebeScreenshotScenario.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
    val useLightAppearance = queryParams["theme"] == "light"
    val e2eMode = queryParams["e2e"]
    ComposeViewport(viewportContainerId = "composeApp") {
        when {
            e2eMode != null -> PhoebeWasmE2eApp(e2eMode = e2eMode)
            screenshotScenario != null -> {
                PhoebeScreenshotApp(
                    scenario = screenshotScenario,
                    useLightAppearance = useLightAppearance,
                )
            }
            else -> App()
        }
    }
}
