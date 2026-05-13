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

  @Volatile
    var servicePlayer: Player? = null
        private set

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
