package com.phoebe.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.dispatcher.SwingDispatchService
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import com.phoebe.app.domain.PlayerState
import com.phoebe.app.media.MacMediaSession
import com.phoebe.app.media.loadMacMediaDylib
import com.phoebe.app.platform.PhoebeLog
import java.util.logging.Level
import java.util.logging.LogManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val isMacOs: Boolean
    get() = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

private data class MacNowPlayingSnapshot(
    val title: String,
    val artist: String,
    val positionBucketMs: Long,
    val durationMs: Long,
    val playing: Boolean,
)

@Composable
actual fun GlobalMediaKeysEffect(
    player: PlayerState,
    onTogglePlayPause: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val playerState = rememberUpdatedState(player)
    val toggle = rememberUpdatedState(onTogglePlayPause)
    val play = rememberUpdatedState(onPlay)
    val pause = rememberUpdatedState(onPause)
    val next = rememberUpdatedState(onNext)
    val previous = rememberUpdatedState(onPrevious)

    if (isMacOs) {
        LaunchedEffect(Unit) {
            if (!loadMacMediaDylib()) {
                PhoebeLog.d("Phoebe") {
                    "macOS media bridge dylib not found. Run a desktop build on a Mac first " +
                        "(e.g. ./gradlew :composeApp:compileMacMediaKeysNative) so libPhoebeMediaKeys.dylib exists."
                }
                return@LaunchedEffect
            }
            MacMediaSession.onToggle = Runnable { toggle.value.invoke() }
            MacMediaSession.onPlay = Runnable { play.value.invoke() }
            MacMediaSession.onPause = Runnable { pause.value.invoke() }
            MacMediaSession.onNext = Runnable { next.value.invoke() }
            MacMediaSession.onPrevious = Runnable { previous.value.invoke() }
            runCatching {
                MacMediaSession.nativeInit()
            }.onFailure { e ->
                PhoebeLog.d("Phoebe") { "macOS media session init failed: ${e.message}" }
                return@LaunchedEffect
            }
            try {
                snapshotFlow { playerState.value }
                    .map { state ->
                        val track = state.currentTrack
                        val durationMs = when {
                            state.durationMs > 0L -> state.durationMs
                            track != null && track.durationMs > 0L -> track.durationMs
                            else -> 0L
                        }
                        MacNowPlayingSnapshot(
                            title = track?.title.orEmpty(),
                            artist = track?.artist.orEmpty(),
                            positionBucketMs = state.positionMs / 1_000L,
                            durationMs = durationMs,
                            playing = state.isPlaying,
                        )
                    }
                    .distinctUntilChanged()
                    .collectLatest { snapshot ->
                        MacMediaSession.nativeUpdateNowPlaying(
                            snapshot.title,
                            snapshot.artist,
                            snapshot.positionBucketMs * 1_000L,
                            snapshot.durationMs,
                            snapshot.playing,
                        )
                    }
            } finally {
                runCatching { MacMediaSession.nativeShutdown() }
            }
        }
    } else {
        DisposableEffect(Unit) {
            try {
                runCatching {
                    LogManager.getLogManager()?.getLogger("com.github.kwhat.jnativehook")?.level = Level.WARNING
                }

                GlobalScreen.setEventDispatcher(SwingDispatchService())

                val registeredHookHere = if (!GlobalScreen.isNativeHookRegistered()) {
                    GlobalScreen.registerNativeHook()
                    true
                } else {
                    false
                }

                val listener = object : NativeKeyListener {
                    override fun nativeKeyPressed(nativeKeyEvent: NativeKeyEvent) {
                        runCatching {
                            when (nativeKeyEvent.keyCode) {
                                NativeKeyEvent.VC_MEDIA_PLAY -> toggle.value.invoke()
                                NativeKeyEvent.VC_MEDIA_NEXT -> next.value.invoke()
                                NativeKeyEvent.VC_MEDIA_PREVIOUS -> previous.value.invoke()
                                NativeKeyEvent.VC_MEDIA_STOP -> pause.value.invoke()
                                else -> Unit
                            }
                        }
                    }

                    override fun nativeKeyReleased(nativeKeyEvent: NativeKeyEvent) = Unit

                    override fun nativeKeyTyped(nativeKeyEvent: NativeKeyEvent) = Unit
                }

                GlobalScreen.addNativeKeyListener(listener)
                DesktopGlobalMediaKeyHook.isActive = true

                onDispose {
                    DesktopGlobalMediaKeyHook.isActive = false
                    GlobalScreen.removeNativeKeyListener(listener)
                    if (registeredHookHere && GlobalScreen.isNativeHookRegistered()) {
                        runCatching {
                            GlobalScreen.unregisterNativeHook()
                        }.onFailure { e ->
                            PhoebeLog.d("Phoebe") { "Failed to unregister global media key hook: ${e.message}" }
                        }
                    }
                }
            } catch (t: Throwable) {
                DesktopGlobalMediaKeyHook.isActive = false
                PhoebeLog.d("Phoebe") { "Global media keys unavailable: ${t.message}" }
                t.printStackTrace()
                onDispose { }
            }
        }
    }
}
