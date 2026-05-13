package com.phoebe.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import com.phoebe.app.domain.PlayerState
import com.phoebe.app.media.MacMediaSession
import com.phoebe.app.media.loadMacMediaDylib
import java.awt.EventQueue
import java.util.logging.Level
import java.util.logging.LogManager
import kotlinx.coroutines.flow.collectLatest

private val isMacOs: Boolean
    get() = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

@Composable
actual fun GlobalMediaKeysEffect(
    player: PlayerState,
    onTogglePlayPause: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
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
                println(
                    "[Phoebe] macOS media bridge dylib not found. Run a desktop build on a Mac first " +
                        "(e.g. ./gradlew :composeApp:compileMacMediaKeysNative) so libPhoebeMediaKeys.dylib exists.",
                )
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
                println("[Phoebe] macOS media session init failed: ${e.message}")
                return@LaunchedEffect
            }
            try {
                snapshotFlow { playerState.value }.collectLatest { p ->
                    val t = p.currentTrack
                    val duration = when {
                        p.durationMs > 0L -> p.durationMs
                        t != null && t.durationMs > 0L -> t.durationMs
                        else -> 0L
                    }
                    MacMediaSession.nativeUpdateNowPlaying(
                        t?.title.orEmpty(),
                        t?.artist.orEmpty(),
                        p.positionMs,
                        duration,
                        p.isPlaying,
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

                val registeredHookHere = if (!GlobalScreen.isNativeHookRegistered()) {
                    GlobalScreen.registerNativeHook()
                    true
                } else {
                    false
                }

                val listener = object : NativeKeyListener {
                    override fun nativeKeyPressed(nativeKeyEvent: NativeKeyEvent) {
                        EventQueue.invokeLater {
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
                    }

                    override fun nativeKeyReleased(nativeKeyEvent: NativeKeyEvent) = Unit

                    override fun nativeKeyTyped(nativeKeyEvent: NativeKeyEvent) = Unit
                }

                GlobalScreen.addNativeKeyListener(listener)

                onDispose {
                    GlobalScreen.removeNativeKeyListener(listener)
                    if (registeredHookHere && GlobalScreen.isNativeHookRegistered()) {
                        runCatching {
                            GlobalScreen.unregisterNativeHook()
                        }.onFailure { e ->
                            println("[Phoebe] Failed to unregister global media key hook: ${e.message}")
                        }
                    }
                }
            } catch (t: Throwable) {
                println("[Phoebe] Global media keys unavailable: ${t.message}")
                onDispose { }
            }
        }
    }
}
