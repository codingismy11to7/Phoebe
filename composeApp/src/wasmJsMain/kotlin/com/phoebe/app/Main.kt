package com.phoebe.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
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
    ComposeViewport(viewportContainerId = "composeApp") {
        if (screenshotScenario != null) {
            PhoebeScreenshotApp(
                scenario = screenshotScenario,
                useLightAppearance = useLightAppearance,
            )
        } else {
            App()
        }
    }
}
