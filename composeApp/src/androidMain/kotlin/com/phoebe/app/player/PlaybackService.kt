package com.phoebe.app.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaConstants
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.phoebe.app.MainActivity
import com.phoebe.app.R

class PlaybackService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null
    private val servicePlayerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                AndroidPlaybackBridge.onTrackEnded?.invoke()
            }
        }

        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
            if (AndroidPlaybackBridge.isCastActive?.invoke() != true) return
            val maxVolume = mediaLibrarySession?.player?.deviceInfo?.maxVolume ?: 0
            if (maxVolume <= 0) return
            AndroidPlaybackBridge.onCastVolume?.invoke(
                if (muted) 0f else (volume.toFloat() / maxVolume.toFloat()).coerceIn(0f, 1f),
            )
        }
    }

    private val librarySessionCallback = object : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            return MediaSession.ConnectionResult.accept(
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
            )
        }

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int,
        ): Int {
            if (AndroidPlaybackBridge.isCastActive?.invoke() == true) {
                val handled = when (playerCommand) {
                    Player.COMMAND_PLAY_PAUSE -> {
                        AndroidPlaybackBridge.onCastTogglePlayPause?.invoke()
                        true
                    }
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    -> {
                        AndroidPlaybackBridge.onCastSkipNext?.invoke()
                        true
                    }
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    -> {
                        AndroidPlaybackBridge.onCastSkipPrevious?.invoke()
                        true
                    }
                    else -> false
                }
                if (handled) return Player.COMMAND_INVALID
            }
            return when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                -> {
                    AndroidPlaybackBridge.onSkipNext?.invoke()
                    Player.COMMAND_INVALID
                }
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                -> {
                    AndroidPlaybackBridge.onSkipPrevious?.invoke()
                    Player.COMMAND_INVALID
                }
                else -> super.onPlayerCommandRequest(session, controller, playerCommand)
            }
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            AndroidPlaybackRuntime.ensureInstalled()
            val rootParams = androidAutoRootParams(params)
            val source = AndroidPlaybackRuntime.catalogBrowseSource
            return if (source == null) {
                immediateFuture(
                    LibraryResult.ofItem(
                        browseFolderItem(BrowseMediaIds.ROOT, "Phoebe"),
                        rootParams,
                    ),
                )
            } else {
                listenableFuture("onGetLibraryRoot") {
                    LibraryResult.ofItem(source.getLibraryRoot(), rootParams)
                }
            }
        }

        @OptIn(UnstableApi::class)
        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val source = AndroidPlaybackRuntime.catalogBrowseSource
                ?: return immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            return listenableFuture("onGetItem") {
                val item = source.getItem(mediaId)
                if (item == null) {
                    LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                } else {
                    LibraryResult.ofItem(item, null)
                }
            }
        }

        @OptIn(UnstableApi::class)
        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            AndroidPlaybackRuntime.ensureInstalled()
            val source = AndroidPlaybackRuntime.catalogBrowseSource
            if (source == null) {
                val children = when (parentId) {
                    BrowseMediaIds.ROOT -> listOf(
                        browseFolderItem(
                            BrowseMediaIds.SIGN_IN,
                            "Open Phoebe and sign in to Plex",
                        ),
                    )
                    else -> emptyList()
                }
                return if (children.isEmpty()) {
                    immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
                } else {
                    immediateFuture(LibraryResult.ofItemList(children, params))
                }
            }
            return listenableFuture("onGetChildren") {
                val children = source.getChildren(parentId)
                LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            if (mediaItems.isInAppPlaybackQueue()) {
                return immediateFuture(mediaItems)
            }
            val source = AndroidPlaybackRuntime.catalogBrowseSource
                ?: return immediateFuture(mediaItems)
            return listenableFuture("onAddMediaItems") {
                source.resolveTracks(mediaItems).map { playbackMediaItem(it) }
            }
        }

        @OptIn(UnstableApi::class)
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaItemsWithStartPosition> {
            val source = AndroidPlaybackRuntime.catalogBrowseSource
                ?: return immediateFuture(
                    MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs),
                )
            if (mediaItems.isInAppPlaybackQueue()) {
                return immediateFuture(MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
            }
            return listenableFuture("onSetMediaItems") {
                val expanded = expandMediaItems(source, mediaItems, startIndex)
                if (expanded != null) {
                    val tracks = source.resolveTracks(expanded.items)
                    if (tracks.isNotEmpty()) {
                        AndroidPlaybackBridge.onAdoptQueue?.invoke(
                            tracks,
                            expanded.startIndex.coerceIn(tracks.indices),
                            true,
                        )
                    }
                    MediaItemsWithStartPosition(expanded.items, expanded.startIndex, startPositionMs)
                } else {
                    val resolved = source.resolveTracks(mediaItems).map { playbackMediaItem(it) }
                    val tracks = source.resolveTracks(mediaItems)
                    if (tracks.isNotEmpty()) {
                        AndroidPlaybackBridge.onAdoptQueue?.invoke(
                            tracks,
                            startIndex.coerceIn(tracks.indices),
                            true,
                        )
                    }
                    MediaItemsWithStartPosition(resolved, startIndex, startPositionMs)
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        AndroidPlaybackRuntime.ensureInstalled()
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setNotificationId(NOTIFICATION_ID)
            .build()
            .apply { setSmallIcon(R.drawable.ic_notification) }
        setMediaNotificationProvider(notificationProvider)
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        AndroidPlaybackBridge.attachServicePlayer(player, servicePlayerListener)

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, librarySessionCallback)
            .setSessionActivity(openAppIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    @OptIn(UnstableApi::class)
    override fun onDestroy() {
        mediaLibrarySession?.player?.let { player ->
            AndroidPlaybackBridge.detachServicePlayer(servicePlayerListener)
            player.release()
        }
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        super.onDestroy()
    }

    private data class ExpandedItems(val items: List<MediaItem>, val startIndex: Int)

    private suspend fun expandMediaItems(
        source: CatalogBrowseSource,
        mediaItems: List<MediaItem>,
        startIndex: Int,
    ): ExpandedItems? {
        if (mediaItems.size != 1) return null
        val item = mediaItems.first()
        val tracks = source.expandPlayableItem(item)
        if (tracks.size <= 1) return null
        val items = tracks.map { playbackMediaItem(it) }
        val index = tracks.indexOfFirst { it.id == item.mediaId }.takeIf { it >= 0 } ?: startIndex
        return ExpandedItems(items, index)
    }

    private fun List<MediaItem>.isInAppPlaybackQueue(): Boolean =
        isNotEmpty() && all { it.requestMetadata.extras?.getBoolean(InAppPlaybackExtra, false) == true }

    private companion object {
        private const val NOTIFICATION_ID = 1001

        private fun androidAutoRootParams(
            @Suppress("UNUSED_PARAMETER") incoming: MediaLibraryService.LibraryParams?,
        ): MediaLibraryService.LibraryParams {
            val extras = Bundle().apply {
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
                )
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
                )
            }
            return MediaLibraryService.LibraryParams.Builder()
                .setExtras(extras)
                .build()
        }
    }
}
