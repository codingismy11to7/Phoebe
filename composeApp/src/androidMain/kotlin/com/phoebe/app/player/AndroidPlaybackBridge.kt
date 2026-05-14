package com.phoebe.app.player

import androidx.media3.common.Player
import com.phoebe.app.domain.Track

/**
 * Process-wide glue between [PlaybackService] (ExoPlayer + MediaLibrarySession) and
 * [AndroidAudioPlayer] (shared queue logic in [SimpleAudioPlayer]).
 */
object AndroidPlaybackBridge {
    var onSkipNext: (() -> Unit)? = null
    var onSkipPrevious: (() -> Unit)? = null
    var onTrackEnded: (() -> Unit)? = null
    var onPlayQueue: ((List<Track>, Int) -> Unit)? = null
    var onAdoptQueue: ((List<Track>, Int, Boolean) -> Unit)? = null
    var isCastActive: (() -> Boolean)? = null
    var onCastTogglePlayPause: (() -> Unit)? = null
    var onCastPlay: (() -> Unit)? = null
    var onCastPause: (() -> Unit)? = null
    var onCastSkipNext: (() -> Unit)? = null
    var onCastSkipPrevious: (() -> Unit)? = null
    var onCastVolume: ((Float) -> Unit)? = null
    var onEnsureLocalPlaybackPaused: (() -> Unit)? = null
    var readCastVolume: (() -> Float)? = null
    var applyCastVolume: ((Float) -> Unit)? = null
    var adjustCastVolumeStep: ((up: Boolean) -> Boolean)? = null
    var onCastVolumeChanged: ((Float) -> Unit)? = null

    @Volatile
    private var suspendingLocalPlayback = false

  @Volatile
    var servicePlayer: Player? = null
        private set

    /** Stop audible output from the foreground [PlaybackService] player immediately. */
    fun pauseLocalPlaybackImmediately() {
        if (suspendingLocalPlayback) return
        val player = servicePlayer ?: run {
            onEnsureLocalPlaybackPaused?.invoke()
            return
        }
        if (!player.isPlaying && !player.playWhenReady) {
            onEnsureLocalPlaybackPaused?.invoke()
            return
        }
        suspendingLocalPlayback = true
        try {
            player.playWhenReady = false
            if (player.isPlaying) {
                player.pause()
            }
            onEnsureLocalPlaybackPaused?.invoke()
        } finally {
            suspendingLocalPlayback = false
        }
    }

    fun attachServicePlayer(player: Player, listener: Player.Listener) {
        servicePlayer?.removeListener(listener)
        servicePlayer = player
        player.addListener(listener)
    }

    fun detachServicePlayer(listener: Player.Listener) {
        servicePlayer?.removeListener(listener)
        servicePlayer = null
    }
}
