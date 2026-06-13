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
import androidx.compose.runtime.DisposableEffect
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
import com.phoebe.app.feature.playback.GlobalMediaKeysEffect
import com.phoebe.app.ui.HomeScreenLayoutMode
import com.phoebe.app.ui.PhoebePaletteDark
import com.phoebe.app.ui.PhoebeTheme
import com.phoebe.app.ui.PhoebeTintOption
import com.phoebe.app.ui.PhoebeRoot
import com.phoebe.app.ui.PlatformInteractionLocals
import com.phoebe.app.feature.playback.mediaPlaybackShortcuts
import com.phoebe.app.platform.isDesktopPlatform
import com.phoebe.app.telemetry.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val AppearanceThemeFile = "appearance_theme"
private const val AppearanceTintFile = "appearance_tint"
private const val HomeScreenLayoutModeFile = "home_screen_layout_mode"

private sealed interface AppBootstrapState {
    data object Loading : AppBootstrapState
    data class Ready(val dependencies: AppDependencies) : AppBootstrapState
    data class Failed(val message: String) : AppBootstrapState
}

@Composable
fun App(
    dependencies: AppDependencies? = null,
    onAppearanceChange: ((Boolean) -> Unit)? = null,
    onAppStateReady: ((AppState?) -> Unit)? = null,
    navigationPath: String? = null,
    onNavigationPathChange: ((path: String, replace: Boolean) -> Unit)? = null,
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
            val created = if (isDesktopPlatform()) {
                withContext(Dispatchers.Default) { AppDependencies.create() }
            } else {
                AppDependencies.create()
            }
            AppBootstrapState.Ready(created)
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
    val uiScope = rememberCoroutineScope()
    val desktopAppStateScope = remember(readyDependencies) {
        if (isDesktopPlatform()) {
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
        } else {
            null
        }
    }
    val stateScope = desktopAppStateScope ?: uiScope
    val state = remember(readyDependencies, stateScope, uiScope) {
        AppState(
            dependencies = readyDependencies,
            scope = stateScope,
            playbackScope = uiScope,
        )
    }
    DisposableEffect(state, desktopAppStateScope) {
        onAppStateReady?.invoke(state)
        onDispose {
            state.dispose()
            desktopAppStateScope?.cancel()
            onAppStateReady?.invoke(null)
        }
    }
    val session by state.session.collectAsState()
    val mediaSources by state.mediaSources.collectAsState()

    // Keep navigation in sync when session / local folders appear after async restore.
    // Catalog refresh is handled by explicit user actions (manual refresh, post sign-in library
    // pick, folder add/remove, sign-out). Startup restores the cached catalog without kicking off
    // a full Plex rebuild.
    LaunchedEffect(session, mediaSources) {
        state.reconcileBrowseScreenIfNeeded()
    }

    var useLightAppearance by remember(readyDependencies) { mutableStateOf(false) }
    var appearanceTintId by remember(readyDependencies) { mutableStateOf(PhoebeTintOption.Purple.id) }
    var homeScreenLayoutMode by remember(readyDependencies) { mutableStateOf<HomeScreenLayoutMode?>(null) }

    LaunchedEffect(readyDependencies) {
        installPlatformPlayback(readyDependencies)
        val stored = readyDependencies.platformStorage.readText(AppearanceThemeFile)?.trim()?.lowercase()
        useLightAppearance = stored == "light" || stored == "true"
        appearanceTintId = readyDependencies.platformStorage.readText(AppearanceTintFile)
            ?.trim()
            ?.lowercase()
            ?.let { PhoebeTintOption.fromId(it).id }
            ?: PhoebeTintOption.Purple.id
        homeScreenLayoutMode = HomeScreenLayoutMode.fromStorage(
            readyDependencies.platformStorage.readText(HomeScreenLayoutModeFile)?.trim(),
        )
    }

    LaunchedEffect(state) {
        bindCarPlayPlayback(state)
        bindPlatformAppLifecycle(state)
    }

    LaunchedEffect(useLightAppearance) {
        onAppearanceChange?.invoke(useLightAppearance)
    }

    PhoebeTheme(useLightAppearance = useLightAppearance, tintId = appearanceTintId) {
        PlatformInteractionLocals {
        val resolvedHomeScreenLayoutMode = homeScreenLayoutMode ?: return@PlatformInteractionLocals

        GlobalMediaKeysEffect(
            playerFlow = state.player,
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
                    uiScope.launch {
                        readyDependencies.platformStorage.writeText(
                            AppearanceThemeFile,
                            if (value) "light" else "dark",
                        )
                    }
                },
                appearanceTintId = appearanceTintId,
                onAppearanceTintChange = { value ->
                    val tintId = PhoebeTintOption.fromId(value).id
                    appearanceTintId = tintId
                    uiScope.launch {
                        readyDependencies.platformStorage.writeText(
                            AppearanceTintFile,
                            tintId,
                        )
                    }
                },
                homeScreenLayoutMode = resolvedHomeScreenLayoutMode,
                onHomeScreenLayoutModeChange = { value ->
                    homeScreenLayoutMode = value
                    uiScope.launch {
                        readyDependencies.platformStorage.writeText(
                            HomeScreenLayoutModeFile,
                            value.storageValue,
                        )
                    }
                },
                navigationPath = navigationPath,
                onNavigationPathChange = onNavigationPathChange,
            )
        }
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
