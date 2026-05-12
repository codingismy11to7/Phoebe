package com.phoebe.app.player

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
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

    override fun start(scope: CoroutineScope) {
        if (observer != null) return
        val handler = Handler(Looper.getMainLooper())
        val newObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                _volume.value = readVolume()
            }
        }
        observer = newObserver
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            newObserver,
        )
        _volume.value = readVolume()
    }

    override fun setVolume(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
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

    private fun readVolume(): Float = runCatching {
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max > 0) cur.toFloat() / max else 0.7f
    }.getOrDefault(0.7f)
}
