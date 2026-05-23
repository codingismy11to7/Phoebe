@file:OptIn(ExperimentalWasmJsInterop::class)

package com.phoebe.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import com.phoebe.app.domain.PlayerState
import com.phoebe.app.domain.Track
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow

private data class WebMediaSessionSnapshot(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String,
    val positionBucketMs: Long,
    val durationMs: Long,
    val playing: Boolean,
)

@Composable
actual fun GlobalMediaKeysEffect(
    playerFlow: StateFlow<PlayerState>,
    @Suppress("UNUSED_PARAMETER") onTogglePlayPause: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onPlay: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val next = rememberUpdatedState(onNext)
    val previous = rememberUpdatedState(onPrevious)
    val seek = rememberUpdatedState(onSeek)

    DisposableEffect(Unit) {
        webInstallMediaSessionHandlers(
            onNext = { next.value.invoke() },
            onPrevious = { previous.value.invoke() },
            onSeekBackward = { offsetMs ->
                seekBy(playerFlow.value, seek.value, -offsetMs.toLong().coerceAtLeast(1L))
            },
            onSeekForward = { offsetMs ->
                seekBy(playerFlow.value, seek.value, offsetMs.toLong().coerceAtLeast(1L))
            },
            onSeekTo = { positionMs ->
                seek.value.invoke(positionMs.toLong().coerceAtLeast(0L))
            },
        )
        onDispose { webClearMediaSession() }
    }

    LaunchedEffect(playerFlow) {
        playerFlow
            .map { it.toWebMediaSessionSnapshot() }
            .distinctUntilChanged()
            .collectLatest { snapshot ->
                webUpdateMediaSession(
                    title = snapshot.title,
                    artist = snapshot.artist,
                    album = snapshot.album,
                    artworkUrl = snapshot.artworkUrl,
                    positionMs = (snapshot.positionBucketMs * 1_000L).toDouble(),
                    durationMs = snapshot.durationMs.toDouble(),
                    playing = snapshot.playing,
                )
            }
    }
}

private fun PlayerState.toWebMediaSessionSnapshot(): WebMediaSessionSnapshot {
    val track = currentTrack
    val durationMs = when {
        this.durationMs > 0L -> this.durationMs
        track != null && track.durationMs > 0L -> track.durationMs
        else -> 0L
    }
    return WebMediaSessionSnapshot(
        trackId = track?.id.orEmpty(),
        title = track?.title.orEmpty(),
        artist = track?.artist.orEmpty(),
        album = track?.album.orEmpty(),
        artworkUrl = track?.browserMediaSessionArtworkUrl().orEmpty(),
        positionBucketMs = positionMs / 1_000L,
        durationMs = durationMs,
        playing = isPlaying,
    )
}

private fun seekBy(player: PlayerState, onSeek: (Long) -> Unit, deltaMs: Long) {
    val trackDurationMs = player.currentTrack?.durationMs ?: 0L
    val durationMs = when {
        player.durationMs > 0L -> player.durationMs
        trackDurationMs > 0L -> trackDurationMs
        else -> 0L
    }
    val target = player.positionMs + deltaMs
    onSeek(if (durationMs > 0L) target.coerceIn(0L, durationMs) else target.coerceAtLeast(0L))
}

private fun Track.browserMediaSessionArtworkUrl(): String? =
    listOfNotNull(localArtworkUri, thumbUrl)
        .firstOrNull { it.isBrowserLoadableArtworkUrl() }

private fun String.isBrowserLoadableArtworkUrl(): Boolean =
    startsWith("https://", ignoreCase = true) ||
        startsWith("http://", ignoreCase = true) ||
        startsWith("data:", ignoreCase = true) ||
        startsWith("blob:", ignoreCase = true)

@JsFun(
    """
    (onNext, onPrevious, onSeekBackward, onSeekForward, onSeekTo) => {
        const session = globalThis.navigator?.mediaSession;
        if (!session) return;
        const set = (action, handler) => {
            try { session.setActionHandler(action, handler); } catch (_) {}
        };
        // Let Chrome keep native play/pause control over the active <audio> element.
        // Phoebe only supplies actions the browser cannot infer from the element.
        set("play", null);
        set("pause", null);
        set("stop", null);
        set("nexttrack", () => onNext());
        set("previoustrack", () => onPrevious());
        set("seekbackward", (event) => {
            const offset = Number(event?.seekOffset);
            onSeekBackward((Number.isFinite(offset) && offset > 0 ? offset : 10) * 1000);
        });
        set("seekforward", (event) => {
            const offset = Number(event?.seekOffset);
            onSeekForward((Number.isFinite(offset) && offset > 0 ? offset : 10) * 1000);
        });
        set("seekto", (event) => {
            const seekTime = Number(event?.seekTime);
            if (Number.isFinite(seekTime)) onSeekTo(Math.max(0, seekTime * 1000));
        });
    }
    """,
)
private external fun webInstallMediaSessionHandlers(
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekBackward: (Double) -> Unit,
    onSeekForward: (Double) -> Unit,
    onSeekTo: (Double) -> Unit,
)

@JsFun(
    """
    (title, artist, album, artworkUrl, positionMs, durationMs, playing) => {
        const session = globalThis.navigator?.mediaSession;
        if (!session) return;

        const clean = (value) => String(value || "").trim();
        const nextTitle = clean(title);
        const nextArtist = clean(artist);
        const nextAlbum = clean(album);
        const nextArtworkUrl = clean(artworkUrl);
        const hasMetadata = !!(nextTitle || nextArtist || nextAlbum || nextArtworkUrl);

        if (!hasMetadata) {
            try { session.metadata = null; } catch (_) {}
            try { session.playbackState = "none"; } catch (_) {}
            globalThis.__phoebeMediaSessionMetadataKey = "";
            return;
        }

        const metadataKey = JSON.stringify([nextTitle, nextArtist, nextAlbum, nextArtworkUrl]);
        if (globalThis.__phoebeMediaSessionMetadataKey !== metadataKey && typeof MediaMetadata !== "undefined") {
            const artwork = nextArtworkUrl ? [
                { src: nextArtworkUrl, sizes: "96x96" },
                { src: nextArtworkUrl, sizes: "128x128" },
                { src: nextArtworkUrl, sizes: "192x192" },
                { src: nextArtworkUrl, sizes: "256x256" },
                { src: nextArtworkUrl, sizes: "512x512" },
            ] : [];
            try {
                session.metadata = new MediaMetadata({
                    title: nextTitle || "Phoebe",
                    artist: nextArtist,
                    album: nextAlbum,
                    artwork,
                });
                globalThis.__phoebeMediaSessionMetadataKey = metadataKey;
            } catch (_) {}
        }

        try { session.playbackState = playing ? "playing" : "paused"; } catch (_) {}

        const duration = Number(durationMs) / 1000;
        const rawPosition = Number(positionMs) / 1000;
        if (Number.isFinite(duration) && duration > 0 && typeof session.setPositionState === "function") {
            const position = Number.isFinite(rawPosition) ? Math.min(Math.max(0, rawPosition), duration) : 0;
            try {
                session.setPositionState({
                    duration,
                    playbackRate: 1,
                    position,
                });
            } catch (_) {}
        }
    }
    """,
)
private external fun webUpdateMediaSession(
    title: String,
    artist: String,
    album: String,
    artworkUrl: String,
    positionMs: Double,
    durationMs: Double,
    playing: Boolean,
)

@JsFun(
    """
    () => {
        const session = globalThis.navigator?.mediaSession;
        if (!session) return;
        const clear = (action) => {
            try { session.setActionHandler(action, null); } catch (_) {}
        };
        for (const action of [
            "play",
            "pause",
            "stop",
            "nexttrack",
            "previoustrack",
            "seekbackward",
            "seekforward",
            "seekto",
        ]) {
            clear(action);
        }
        try { session.metadata = null; } catch (_) {}
        try { session.playbackState = "none"; } catch (_) {}
        globalThis.__phoebeMediaSessionMetadataKey = "";
    }
    """,
)
private external fun webClearMediaSession()
