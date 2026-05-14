package com.phoebe.app.player

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSNumber
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoMediaTypeAudio
import platform.MediaPlayer.MPNowPlayingInfoPropertyDefaultPlaybackRate
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyMediaType
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPNowPlayingPlaybackStatePaused
import platform.MediaPlayer.MPNowPlayingPlaybackStatePlaying
import platform.MediaPlayer.MPNowPlayingPlaybackStateStopped
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIImage

@OptIn(ExperimentalForeignApi::class)
object IosNowPlayingCenter {
    var onToggle: (() -> Unit)? = null
    var onPlay: (() -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null
    var onSeek: ((Long) -> Unit)? = null

    private var installed = false
    private val artworkScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var artworkJob: Job? = null

    private var lastTitle = ""
    private var lastArtist = ""
    private var lastAlbum = ""
    private var lastPositionMs = 0L
    private var lastDurationMs = 0L
    private var lastIsPlaying = false
    private var lastArtworkUrl: String? = null
    private var cachedArtwork: MPMediaItemArtwork? = null

    fun install() {
        if (installed) return
        installed = true
        val center = MPRemoteCommandCenter.sharedCommandCenter()
        disableUnusedCommands(center)

        center.togglePlayPauseCommand.setEnabled(true)
        center.togglePlayPauseCommand.addTargetWithHandler { _ ->
            onToggle?.invoke()
            MPRemoteCommandHandlerStatusSuccess
        }
        center.playCommand.setEnabled(true)
        center.playCommand.addTargetWithHandler { _ ->
            onPlay?.invoke()
            MPRemoteCommandHandlerStatusSuccess
        }
        center.pauseCommand.setEnabled(true)
        center.pauseCommand.addTargetWithHandler { _ ->
            onPause?.invoke()
            MPRemoteCommandHandlerStatusSuccess
        }
        center.nextTrackCommand.setEnabled(true)
        center.nextTrackCommand.addTargetWithHandler { _ ->
            onNext?.invoke()
            MPRemoteCommandHandlerStatusSuccess
        }
        center.previousTrackCommand.setEnabled(true)
        center.previousTrackCommand.addTargetWithHandler { _ ->
            onPrevious?.invoke()
            MPRemoteCommandHandlerStatusSuccess
        }
        center.changePlaybackPositionCommand.setEnabled(true)
        center.changePlaybackPositionCommand.addTargetWithHandler { event ->
            val positionSeconds = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime ?: 0.0
            onSeek?.invoke((positionSeconds * 1000.0).toLong().coerceAtLeast(0L))
            MPRemoteCommandHandlerStatusSuccess
        }
    }

    fun shutdown() {
        if (!installed) return
        val center = MPRemoteCommandCenter.sharedCommandCenter()
        center.togglePlayPauseCommand.removeTarget(null)
        center.playCommand.removeTarget(null)
        center.pauseCommand.removeTarget(null)
        center.nextTrackCommand.removeTarget(null)
        center.previousTrackCommand.removeTarget(null)
        center.changePlaybackPositionCommand.removeTarget(null)
        disableUnusedCommands(center)
        artworkJob?.cancel()
        artworkJob = null
        lastArtworkUrl = null
        cachedArtwork = null
        IosArtworkLoader.clear()
        clearNowPlaying()
        installed = false
    }

    fun update(
        title: String,
        artist: String,
        album: String,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        artworkUrl: String? = null,
    ) {
        if (title.isBlank()) {
            lastArtworkUrl = null
            cachedArtwork = null
            artworkJob?.cancel()
            artworkJob = null
            clearNowPlaying()
            return
        }

        lastTitle = title
        lastArtist = artist
        lastAlbum = album
        lastPositionMs = positionMs
        lastDurationMs = durationMs
        lastIsPlaying = isPlaying

        val url = artworkUrl?.takeIf { it.isNotBlank() }
        if (url != lastArtworkUrl) {
            lastArtworkUrl = url
            cachedArtwork = null
            artworkJob?.cancel()
            artworkJob = null
        }

        publishNowPlaying()

        if (url == null || cachedArtwork != null) return
        artworkJob = artworkScope.launch {
            val image = IosArtworkLoader.load(url) ?: return@launch
            if (url != lastArtworkUrl) return@launch
            cachedArtwork = createArtwork(image)
            publishNowPlaying()
        }
    }

    private fun publishNowPlaying() {
        val durationSeconds = (lastDurationMs.coerceAtLeast(0L)) / 1000.0
        val positionSeconds = (lastPositionMs.coerceAtLeast(0L)) / 1000.0
        val info = mutableMapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to lastTitle,
            MPMediaItemPropertyArtist to lastArtist,
            MPMediaItemPropertyAlbumTitle to lastAlbum,
            MPMediaItemPropertyPlaybackDuration to NSNumber(durationSeconds),
            MPNowPlayingInfoPropertyElapsedPlaybackTime to NSNumber(positionSeconds),
            MPNowPlayingInfoPropertyPlaybackRate to NSNumber(if (lastIsPlaying) 1.0 else 0.0),
            MPNowPlayingInfoPropertyDefaultPlaybackRate to NSNumber(1.0),
            MPNowPlayingInfoPropertyMediaType to NSNumber(long = MPNowPlayingInfoMediaTypeAudio.toLong()),
        )
        cachedArtwork?.let { info[MPMediaItemPropertyArtwork] = it }
        val nowPlaying = MPNowPlayingInfoCenter.defaultCenter()
        nowPlaying.setNowPlayingInfo(info)
        nowPlaying.playbackState = if (lastIsPlaying) {
            MPNowPlayingPlaybackStatePlaying
        } else {
            MPNowPlayingPlaybackStatePaused
        }
    }

    private fun createArtwork(image: UIImage): MPMediaItemArtwork =
        MPMediaItemArtwork(
            boundsSize = CGSizeMake(600.0, 600.0),
            requestHandler = { _ -> image },
        )

    private fun clearNowPlaying() {
        val nowPlaying = MPNowPlayingInfoCenter.defaultCenter()
        nowPlaying.setNowPlayingInfo(null)
        nowPlaying.playbackState = MPNowPlayingPlaybackStateStopped
    }

    private fun disableUnusedCommands(center: MPRemoteCommandCenter) {
        center.stopCommand.setEnabled(false)
        center.seekForwardCommand.setEnabled(false)
        center.seekBackwardCommand.setEnabled(false)
        center.skipForwardCommand.setEnabled(false)
        center.skipBackwardCommand.setEnabled(false)
        center.ratingCommand.setEnabled(false)
        center.likeCommand.setEnabled(false)
        center.dislikeCommand.setEnabled(false)
        center.bookmarkCommand.setEnabled(false)
    }
}
