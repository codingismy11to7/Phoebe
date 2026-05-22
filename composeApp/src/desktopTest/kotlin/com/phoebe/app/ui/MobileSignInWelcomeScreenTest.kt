package com.phoebe.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

class MobileSignInWelcomeScreenTest {
    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun expandingProvidersScrollsProviderChoicesIntoView() = runDesktopComposeUiTest(width = 430, height = 700) {
        setContent {
            PhoebeTheme {
                Box(Modifier.size(430.dp, 700.dp)) {
                    MobileSignInWelcomeScreen(
                        message = "Sign in to your provider, or add a local music folder to get started.",
                        pinCode = null,
                        jellyfinServers = emptyList(),
                        jellyfinDiscoveryLoading = false,
                        jellyfinQuickConnect = null,
                        onStartSignIn = {},
                        onFinishSignIn = {},
                        onSignInJellyfin = { _, _, _ -> },
                        onSignInProvider = { _, _, _, _, _ -> },
                        onDiscoverJellyfinServers = {},
                        onStartJellyfinQuickConnect = {},
                        onFinishJellyfinQuickConnect = {},
                        onAddLocalFolder = {},
                        modifier = Modifier.size(430.dp, 700.dp),
                    )
                }
            }
        }

        waitForIdle()
        assertTrue(onAllNodesWithText("Add local files").fetchSemanticsNodes().isEmpty())

        onNodeWithText("Add media provider").performClick()
        mainClock.advanceTimeBy(520)
        waitForIdle()

        onNodeWithText("Sign in with Plex").assertIsDisplayed()
        onNodeWithText("Sign in with Jellyfin").assertIsDisplayed()
    }
}
