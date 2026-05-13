package com.phoebe.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.phoebe.app.ui.GlobalMediaKeysEffect
import com.phoebe.app.ui.PlatformBackHandler
import com.phoebe.app.ui.PhoebeTheme
import com.phoebe.app.ui.PhoebeRoot
import com.phoebe.app.ui.mediaPlaybackShortcuts
import kotlinx.coroutines.launch

private const val AppearanceThemeFile = "appearance_theme"

@Composable
fun App(dependencies: AppDependencies? = null) {
    val resolvedDependencies by produceState<AppDependencies?>(initialValue = dependencies, dependencies) {
        if (dependencies == null) {
            value = AppDependencies.create()
        }
    }

    val readyDependencies = resolvedDependencies ?: return
    val scope = rememberCoroutineScope()
    val state = remember(readyDependencies, scope) { AppState(readyDependencies, scope) }
    val session by state.session.collectAsState()
    val mediaSources by state.mediaSources.collectAsState()
    val player by state.player.collectAsState()
    val screen by state.screen.collectAsState()

    // Keep navigation in sync when session / local folders appear after async restore.
    // Catalog refresh is handled by [AppState]'s startup coroutine and explicit user actions
    // (library pick, folder add/remove, sign-out) — refreshing here too duplicated full Plex
    // rebuilds and long SQLite persists, which blocked the UI thread on wasm/js.
    LaunchedEffect(session, mediaSources) {
        state.reconcileBrowseScreenIfNeeded()
    }

    var useLightAppearance by remember(readyDependencies) { mutableStateOf(false) }

    LaunchedEffect(readyDependencies) {
        installPlatformPlayback(readyDependencies)
        val stored = readyDependencies.platformStorage.readText(AppearanceThemeFile)?.trim()?.lowercase()
        useLightAppearance = stored == "light" || stored == "true"
    }

    PhoebeTheme(useLightAppearance = useLightAppearance) {
        PlatformBackHandler(
            enabled = state.canHandleBack(screen),
            onBack = state::handleBack,
        )
        GlobalMediaKeysEffect(
            player = player,
            onTogglePlayPause = { state.mediaKeyTogglePlayPause() },
            onPlay = { state.mediaKeyPlay() },
            onPause = { state.mediaKeyPause() },
            onNext = { state.next() },
            onPrevious = { state.previous() },
        )
        Box(
            Modifier
                .fillMaxSize()
                .mediaPlaybackShortcuts(
                    onTogglePlayPause = { state.mediaKeyTogglePlayPause() },
                    onPlay = { state.mediaKeyPlay() },
                    onPause = { state.mediaKeyPause() },
                    onNext = { state.next() },
                    onPrevious = { state.previous() },
                ),
        ) {
            PhoebeRoot(
                state = state,
                useLightAppearance = useLightAppearance,
                onUseLightAppearanceChange = { value ->
                    useLightAppearance = value
                    scope.launch {
                        readyDependencies.platformStorage.writeText(
                            AppearanceThemeFile,
                            if (value) "light" else "dark",
                        )
                    }
                },
            )
        }
    }
}
