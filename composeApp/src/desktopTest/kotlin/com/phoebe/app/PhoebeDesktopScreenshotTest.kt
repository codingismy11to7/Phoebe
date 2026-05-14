package com.phoebe.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.phoebe.app.ui.PhoebeScreenshotApp
import com.phoebe.app.ui.PhoebeScreenshotScenario
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

class PhoebeDesktopScreenshotTest {

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun desktopCoreFlowsDark() = runDesktopComposeUiTest(width = 1365, height = 900) {
        listOf(
            PhoebeScreenshotScenario.Home,
            PhoebeScreenshotScenario.Library,
            PhoebeScreenshotScenario.Playlist,
            PhoebeScreenshotScenario.Artist,
            PhoebeScreenshotScenario.Album,
            PhoebeScreenshotScenario.Search,
            PhoebeScreenshotScenario.Player,
            PhoebeScreenshotScenario.Settings,
            PhoebeScreenshotScenario.SignIn,
        ).forEach { scenario ->
            setContent {
                Box(Modifier.size(1365.dp, 900.dp)) {
                    PhoebeScreenshotApp(scenario = scenario)
                }
            }
            waitForIdle()
            onRoot().captureRoboImage(
                filePath = "src/screenshotTest/roborazzi/desktop-${scenario.name.lowercase()}-dark.png",
            )
        }
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun desktopRepresentativeFlowsLight() = runDesktopComposeUiTest(width = 1365, height = 900) {
        listOf(
            PhoebeScreenshotScenario.Home,
            PhoebeScreenshotScenario.Library,
            PhoebeScreenshotScenario.Search,
            PhoebeScreenshotScenario.Player,
        ).forEach { scenario ->
            setContent {
                Box(Modifier.size(1365.dp, 900.dp)) {
                    PhoebeScreenshotApp(
                        scenario = scenario,
                        useLightAppearance = true,
                    )
                }
            }
            waitForIdle()
            onRoot().captureRoboImage(
                filePath = "src/screenshotTest/roborazzi/desktop-${scenario.name.lowercase()}-light.png",
            )
        }
    }
}
