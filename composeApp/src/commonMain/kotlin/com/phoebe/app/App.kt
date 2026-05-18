package com.phoebe.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phoebe.app.ui.DesktopKeyboardShortcutsEffect
import com.phoebe.app.ui.GlobalMediaKeysEffect
import com.phoebe.app.ui.PhoebePaletteDark
import com.phoebe.app.ui.PhoebeTheme
import com.phoebe.app.ui.PhoebeRoot
import com.phoebe.app.ui.mediaPlaybackShortcuts
import com.phoebe.app.telemetry.Telemetry
import kotlinx.coroutines.launch

private const val AppearanceThemeFile = "appearance_theme"

private sealed interface AppBootstrapState {
    data object Loading : AppBootstrapState
    data class Ready(val dependencies: AppDependencies) : AppBootstrapState
    data class Failed(val message: String) : AppBootstrapState
}

@Composable
fun App(
    dependencies: AppDependencies? = null,
    onAppearanceChange: ((Boolean) -> Unit)? = null,
) {
    LaunchedEffect(Unit) {
        Telemetry.initialize()
    }

    val bootstrap by produceState<AppBootstrapState>(
        initialValue = dependencies?.let { AppBootstrapState.Ready(it) } ?: AppBootstrapState.Loading,
        dependencies,
    ) {
        if (dependencies != null) {
            value = AppBootstrapState.Ready(dependencies)
            return@produceState
        }
        value = try {
            AppBootstrapState.Ready(AppDependencies.create())
        } catch (error: Throwable) {
            AppBootstrapState.Failed(error.message ?: error.toString())
        }
    }

    val readyDependencies = when (val bootstrapState = bootstrap) {
        AppBootstrapState.Loading -> {
            AppBootstrapScreen(message = "Loading Phoebe…")
            return
        }
        is AppBootstrapState.Failed -> {
            AppBootstrapScreen(
                message = "Phoebe could not start",
                details = bootstrapState.message,
            )
            return
        }
        is AppBootstrapState.Ready -> bootstrapState.dependencies
    }
    val scope = rememberCoroutineScope()
    val state = remember(readyDependencies, scope) { AppState(readyDependencies, scope) }
    val session by state.session.collectAsState()
    val mediaSources by state.mediaSources.collectAsState()
    val player by state.player.collectAsState()

    // Keep navigation in sync when session / local folders appear after async restore.
    // Catalog refresh is handled by explicit user actions (manual refresh, post sign-in library
    // pick, folder add/remove, sign-out). Startup restores the cached catalog without kicking off
    // a full Plex rebuild.
    LaunchedEffect(session, mediaSources) {
        state.reconcileBrowseScreenIfNeeded()
    }

    var useLightAppearance by remember(readyDependencies) { mutableStateOf(false) }

    LaunchedEffect(readyDependencies) {
        installPlatformPlayback(readyDependencies)
        val stored = readyDependencies.platformStorage.readText(AppearanceThemeFile)?.trim()?.lowercase()
        useLightAppearance = stored == "light" || stored == "true"
    }

    LaunchedEffect(state) {
        bindCarPlayPlayback(state)
    }

    LaunchedEffect(useLightAppearance) {
        onAppearanceChange?.invoke(useLightAppearance)
    }

    PhoebeTheme(useLightAppearance = useLightAppearance) {
        GlobalMediaKeysEffect(
            player = player,
            onTogglePlayPause = { state.mediaKeyTogglePlayPause() },
            onPlay = { state.mediaKeyPlay() },
            onPause = { state.mediaKeyPause() },
            onNext = { state.next() },
            onPrevious = { state.previous() },
            onSeek = state::seekTo,
        )
        DesktopKeyboardShortcutsEffect(onTogglePlayPause = { state.mediaKeyTogglePlayPause() })
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

@Composable
private fun AppBootstrapScreen(
    message: String,
    details: String? = null,
) {
    val palette = PhoebePaletteDark
    Box(
        Modifier
            .fillMaxSize()
            .background(palette.canvasBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            if (details == null) {
                CircularProgressIndicator(color = palette.accent)
            }
            Text(message, color = palette.primaryText)
            if (details != null) {
                Text(details, color = palette.secondaryText)
            }
        }
    }
}
