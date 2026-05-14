package com.phoebe.app.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.phoebe.app.AndroidContextHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

actual fun createSystemVolumeController(): SystemVolumeController = AndroidSystemVolumeController()

private class AndroidSystemVolumeController : SystemVolumeController {
    override val isSupported: Boolean = true
    private val context: Context get() = AndroidContextHolder.application
    private val audioManager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _volume = MutableStateFlow(readVolume())
    override val volume: StateFlow<Float> = _volume

    private var observer: ContentObserver? = null
    private var volumeReceiver: BroadcastReceiver? = null

    override fun start(scope: CoroutineScope) {
        if (observer != null) return
        val handler = Handler(Looper.getMainLooper())
        val newObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                publishVolume()
            }
        }
        observer = newObserver
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            newObserver,
        )
        AndroidPlaybackBridge.onCastVolumeChanged = { normalized ->
            _volume.value = normalized.coerceIn(0f, 1f)
        }
        volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != VOLUME_CHANGED_ACTION) return
                if (intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1) != AudioManager.STREAM_MUSIC) return
                publishVolume(forwardToCast = AndroidPlaybackBridge.isCastActive?.invoke() == true)
            }
        }
        val filter = IntentFilter(VOLUME_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(volumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(volumeReceiver, filter)
        }
        publishVolume()
    }

    override fun setVolume(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        if (AndroidPlaybackBridge.isCastActive?.invoke() == true) {
            AndroidPlaybackBridge.applyCastVolume?.invoke(clamped)
            _volume.value = clamped
            return
        }
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (clamped * max).roundToInt().coerceIn(0, max)
        runCatching {
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                target,
                0,
            )
        }
        _volume.value = if (max > 0) target.toFloat() / max else clamped
    }

    private fun publishVolume(forwardToCast: Boolean = AndroidPlaybackBridge.isCastActive?.invoke() == true) {
        val normalized = readStreamVolumeNormalized()
        if (forwardToCast) {
            AndroidPlaybackBridge.applyCastVolume?.invoke(normalized)
        }
        _volume.value = if (forwardToCast) {
            AndroidPlaybackBridge.readCastVolume?.invoke() ?: normalized
        } else {
            normalized
        }
    }

    private fun readVolume(): Float {
        if (AndroidPlaybackBridge.isCastActive?.invoke() == true) {
            return AndroidPlaybackBridge.readCastVolume?.invoke() ?: readStreamVolumeNormalized()
        }
        return readStreamVolumeNormalized()
    }

    private fun readStreamVolumeNormalized(): Float = runCatching {
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max > 0) cur.toFloat() / max else 0.7f
    }.getOrDefault(0.7f)

    private companion object {
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }
}
