package com.phoebe.app.player

import com.phoebe.app.platform.PhoebeLog
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.duration
import platform.AVFoundation.isPlaybackLikelyToKeepUp
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.darwin.NSEC_PER_SEC

actual fun createAudioPlayer(): AudioPlayer = IosAudioPlayer()

@OptIn(ExperimentalForeignApi::class)
private class IosAudioPlayer : SimpleAudioPlayer() {
    private var player: AVPlayer? = null
    private var timeObserver: Any? = null
    private var endObserver: Any? = null
    private var observedGeneration = -1

    override val useProgressTicker: Boolean = false

    init {
        configureAudioSession()
    }

    override fun stopCurrentPlaybackImmediately() {
        player?.pause()
    }

    override fun playUri(uri: String) {
        if (uri.isBlank()) {
            markPlaybackFailed()
            return
        }
        val generation = activePlayGeneration
        val url = NSURL.URLWithString(uri)
        if (url == null) {
            markPlaybackFailed(generation)
            return
        }
        clearObservers()
        val item = AVPlayerItem(uRL = url)
        val avPlayer = player ?: AVPlayer().also { player = it }
        avPlayer.replaceCurrentItemWithPlayerItem(item)
        observePlayback(avPlayer, item, generation)
        if (playWhenReady) {
            avPlayer.play()
        }
    }

    override fun pause() {
        player?.pause()
    }

    override fun resume() {
        player?.play()
    }

    override fun seek(positionMs: Long) {
        val avPlayer = player ?: return
        val seconds = positionMs.coerceAtLeast(0L) / 1000.0
        val time = CMTimeMakeWithSeconds(seconds, NSEC_PER_SEC.toInt())
        avPlayer.seekToTime(time)
    }

    override fun setOutputVolume(volume: Float) = Unit

    private fun configureAudioSession() {
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, error = null)
            session.setActive(active = true, error = null)
        }.onFailure { error ->
            PhoebeLog.d("IosAudioPlayer") { "audio session setup failed: ${error.message}" }
        }
    }

    private fun observePlayback(avPlayer: AVPlayer, item: AVPlayerItem, generation: Int) {
        observedGeneration = generation
        val interval = CMTimeMakeWithSeconds(0.25, NSEC_PER_SEC.toInt())
        timeObserver = avPlayer.addPeriodicTimeObserverForInterval(interval, queue = null) { time ->
            if (!isPlayRequestCurrent(generation)) return@addPeriodicTimeObserverForInterval
            val positionMs = cmTimeToMs(time)
            val durationMs = avPlayer.currentItem?.let { currentItem ->
                cmTimeToMs(CMTimeGetSeconds(currentItem.duration))
            } ?: 0L
            val waiting = avPlayer.timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
            val playing = avPlayer.timeControlStatus == AVPlayerTimeControlStatusPlaying
            val likelyReady = avPlayer.currentItem?.isPlaybackLikelyToKeepUp() == true
            val isBuffering = playWhenReady && waiting && !likelyReady
            applyPlatformPlayback(
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = playing && playWhenReady,
                isBuffering = isBuffering,
                generation = generation,
            )
        }
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            if (!isPlayRequestCurrent(generation)) return@addObserverForName
            next()
        }
    }

    private fun clearObservers() {
        timeObserver?.let { token -> player?.removeTimeObserver(token) }
        timeObserver = null
        endObserver?.let { token -> NSNotificationCenter.defaultCenter.removeObserver(token) }
        endObserver = null
        observedGeneration = -1
    }

    private fun cmTimeToMs(seconds: Double): Long {
        if (seconds.isNaN() || seconds.isInfinite()) return 0L
        return (seconds * 1000.0).toLong().coerceAtLeast(0L)
    }

    private fun cmTimeToMs(time: CValue<CMTime>): Long = cmTimeToMs(CMTimeGetSeconds(time))
}
