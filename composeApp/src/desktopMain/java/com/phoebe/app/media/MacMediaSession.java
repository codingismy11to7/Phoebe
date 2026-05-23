package com.phoebe.app.media;

import java.awt.EventQueue;
import java.util.function.LongConsumer;

/**
 * JNI bridge to macOS {@code MediaPlayer.framework} (Now Playing + remote media commands).
 * Hardware play/pause is routed by the OS to the active Now Playing session, not to Java key events.
 */
public final class MacMediaSession {
    private MacMediaSession() {
    }

    public static volatile Runnable onToggle = () -> {};
    public static volatile Runnable onPlay = () -> {};
    public static volatile Runnable onPause = () -> {};
    public static volatile Runnable onNext = () -> {};
    public static volatile Runnable onPrevious = () -> {};
    public static volatile LongConsumer onSeek = ignored -> {};

    public static void dispatchToggleFromNative() {
        EventQueue.invokeLater(() -> onToggle.run());
    }

    public static void dispatchPlayFromNative() {
        EventQueue.invokeLater(() -> onPlay.run());
    }

    public static void dispatchPauseFromNative() {
        EventQueue.invokeLater(() -> onPause.run());
    }

    public static void dispatchNextFromNative() {
        EventQueue.invokeLater(() -> onNext.run());
    }

    public static void dispatchPreviousFromNative() {
        EventQueue.invokeLater(() -> onPrevious.run());
    }

    public static void dispatchSeekFromNative(long positionMs) {
        EventQueue.invokeLater(() -> onSeek.accept(positionMs));
    }

    public static native void nativeInit();

    public static native void nativeShutdown();

    public static native void nativeUpdateNowPlaying(
            String title,
            String artist,
            String album,
            String artworkUrl,
            long positionMs,
            long durationMs,
            boolean playing
    );
}
