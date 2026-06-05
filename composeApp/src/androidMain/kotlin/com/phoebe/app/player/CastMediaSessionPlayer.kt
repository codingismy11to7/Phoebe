package com.phoebe.app.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
internal class CastMediaSessionPlayer(
    player: Player,
) : ForwardingSimpleBasePlayer(player) {
    private var castState: CastMediaSessionState? = null

    fun updateCastState(state: CastMediaSessionState?) {
        castState = state
        invalidateState()
    }

    override fun getState(): SimpleBasePlayer.State {
        val delegateState = safeDelegateState()
        val cast = castState ?: return delegateState.withPhoebeQueueNavigationCommands(
            hasNext = AndroidPlaybackBridge.hasNextTrack?.invoke() == true,
            hasPrevious = AndroidPlaybackBridge.hasPreviousTrack?.invoke() == true,
        )
        val mediaItem = playbackMediaItem(cast.track, inAppPlayback = true)
        val itemData = SimpleBasePlayer.MediaItemData.Builder(cast.track.id)
            .setMediaItem(mediaItem)
            .setMediaMetadata(mediaItem.mediaMetadata)
            .setDurationUs(cast.durationMs.takeIf { it > 0L }?.times(1_000L) ?: C.TIME_UNSET)
            .setIsSeekable(cast.durationMs > 0L)
            .build()
        return delegateState
            .buildUpon()
            .setPlaylist(listOf(itemData))
            .setCurrentMediaItemIndex(0)
            .setCurrentAd(C.INDEX_UNSET, C.INDEX_UNSET)
            .setPlayerError(null)
            .setPlaybackState(
                when {
                    cast.isBuffering -> Player.STATE_BUFFERING
                    else -> Player.STATE_READY
                },
            )
            .setPlayWhenReady(
                cast.isPlaying,
                Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE,
            )
            .setContentPositionMs(cast.positionMs.coerceAtLeast(0L))
            .setContentBufferedPositionMs(
                SimpleBasePlayer.PositionSupplier.getConstant(
                    cast.durationMs.takeIf { it > 0L } ?: cast.positionMs.coerceAtLeast(0L),
                ),
            )
            .setTotalBufferedDurationMs(SimpleBasePlayer.PositionSupplier.ZERO)
            .build()
            .withPhoebeQueueNavigationCommands(
                hasNext = AndroidPlaybackBridge.hasNextTrack?.invoke() == true,
                hasPrevious = AndroidPlaybackBridge.hasPreviousTrack?.invoke() == true,
            )
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (castState != null) {
            if (playWhenReady) {
                AndroidPlaybackBridge.onCastPlay?.invoke()
            } else {
                AndroidPlaybackBridge.onCastPause?.invoke()
            }
            return Futures.immediateVoidFuture()
        }
        return super.handleSetPlayWhenReady(playWhenReady)
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        if (castState != null) {
            when (seekCommand) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                -> AndroidPlaybackBridge.onCastSkipNext?.invoke()
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                -> AndroidPlaybackBridge.onCastSkipPrevious?.invoke()
                else -> AndroidPlaybackBridge.onCastSeekTo?.invoke(positionMs.coerceAtLeast(0L))
            }
            return Futures.immediateVoidFuture()
        }
        return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
    }

    private fun safeDelegateState(): SimpleBasePlayer.State {
        val player = getPlayer()
        if (player.hasInvalidEmptyPlaylistSnapshot()) {
            return player.emptyIdleStateSnapshot()
        }
        return try {
            super.getState()
        } catch (error: IllegalArgumentException) {
            if (error.message != EmptyPlaylistStateError) throw error
            player.emptyIdleStateSnapshot()
        }
    }

    private fun Player.hasInvalidEmptyPlaylistSnapshot(): Boolean {
        val state = playbackState
        if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) return false
        return !isCommandAvailable(Player.COMMAND_GET_TIMELINE) || currentTimeline.isEmpty
    }

    private fun Player.emptyIdleStateSnapshot(): SimpleBasePlayer.State =
        SimpleBasePlayer.State.Builder()
            .setAvailableCommands(availableCommands)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackSuppressionReason(playbackSuppressionReason)
            .setPlaybackState(Player.STATE_IDLE)
            .setIsLoading(false)
            .setPlayerError(playerError)
            .setRepeatMode(repeatMode)
            .setShuffleModeEnabled(shuffleModeEnabled)
            .setPlaybackParameters(playbackParameters)
            .setTrackSelectionParameters(trackSelectionParameters)
            .setSeekBackIncrementMs(seekBackIncrement)
            .setSeekForwardIncrementMs(seekForwardIncrement)
            .setMaxSeekToPreviousPositionMs(maxSeekToPreviousPosition)
            .setVolume(volume)
            .setTotalBufferedDurationMs(SimpleBasePlayer.PositionSupplier.ZERO)
            .build()

    private companion object {
        private const val EmptyPlaylistStateError = "Empty playlist only allowed in STATE_IDLE or STATE_ENDED"
    }
}

@OptIn(UnstableApi::class)
internal fun SimpleBasePlayer.State.withPhoebeQueueNavigationCommands(
    hasNext: Boolean,
    hasPrevious: Boolean,
): SimpleBasePlayer.State {
    if (!hasNext && !hasPrevious) return this
    val commands = availableCommands.buildUpon().apply {
        if (hasNext) {
            add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }
        if (hasPrevious) {
            add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        }
    }.build()
    return buildUpon()
        .setAvailableCommands(commands)
        .build()
}
