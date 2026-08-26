package com.phoebe.app.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.io.File
import java.net.URI

private const val NOTIFICATIONS_BUS = "org.freedesktop.Notifications"
private const val NOTIFICATIONS_PATH = "/org/freedesktop/Notifications"

/** Keeps the newest this many cover-art files, deleting oldest-first. */
private const val MaxCoverArtFiles = 200

@DBusInterfaceName(NOTIFICATIONS_BUS)
internal interface FreedesktopNotifications : DBusInterface {
    fun Notify(
        appName: String,
        replacesId: UInt32,
        appIcon: String,
        summary: String,
        body: String,
        actions: List<String>,
        hints: Map<String, Variant<*>>,
        expireTimeout: Int,
    ): UInt32
}

/**
 * Track-change notifications via org.freedesktop.Notifications.
 *
 * Linux only. macOS already surfaces now-playing through its media session and Windows
 * toasts are a different API, so both no-op rather than pretending.
 */
actual class NowPlayingNotifier actual constructor() {

    private val isLinux: Boolean
        get() = System.getProperty("os.name").orEmpty().lowercase().contains("linux")

    private var connection: DBusConnection? = null
    private var notifications: FreedesktopNotifications? = null

    /** Sent as replaces_id so a new track replaces the previous popup rather than stacking. */
    private var lastNotificationId: UInt32 = UInt32(0L)

    actual suspend fun notifyNowPlaying(
        title: String,
        artist: String,
        album: String,
        artworkUrl: String,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isLinux || title.isBlank()) return@withContext false

        val proxy = connect() ?: return@withContext false

        val hints = buildMap<String, Variant<*>> {
            put("category", Variant("x-gnome.music"))
            cachedArtFile(artworkUrl)?.let { put("image-path", Variant(it.toURI().toString())) }
        }

        runCatching {
            lastNotificationId = proxy.Notify(
                "Phoebe",
                lastNotificationId,
                "phoebe",
                title,
                listOf(artist, album).filter { it.isNotBlank() }.joinToString("\n"),
                emptyList(),
                hints,
                -1,
            )
            true
        }.getOrElse { e ->
            PhoebeLog.d("Phoebe") { "Now playing notification failed: ${e.message}" }
            false
        }
    }

    private fun connect(): FreedesktopNotifications? {
        notifications?.let { return it }
        return runCatching {
            val conn = DBusConnectionBuilder.forSessionBus().build()
            // Only the four-argument overload takes a Class. The trailing flag is
            // autostart, letting D-Bus activate the notification daemon if it is not
            // already running.
            val proxy = conn.getRemoteObject(
                NOTIFICATIONS_BUS,
                NOTIFICATIONS_PATH,
                FreedesktopNotifications::class.java,
                true,
            )
            connection = conn
            notifications = proxy
            proxy
        }.getOrElse { e ->
            PhoebeLog.d("Phoebe") { "No notification service available: ${e.message}" }
            null
        }
    }

    /**
     * A local file for the artwork, fetched once if not already cached. Returns null on
     * any failure: a notification without art beats no notification at all.
     */
    private fun cachedArtFile(artworkUrl: String): File? {
        if (artworkUrl.isBlank()) return null
        return runCatching {
            if (artworkUrl.startsWith("file:")) return File(URI(artworkUrl))

            val target = storageRoot.resolve(coverArtCachePath(artworkUrl))
            if (target.exists() && target.length() > 0L) return target

            target.parentFile?.mkdirs()
            URI(artworkUrl).toURL().openStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            pruneCoverArtCache(target.parentFile)
            target.takeIf { it.length() > 0L }
        }.getOrNull()
    }
}

private fun pruneCoverArtCache(dir: File?) {
    val files = dir?.listFiles()?.takeIf { it.size > MaxCoverArtFiles } ?: return
    files.sortedBy { it.lastModified() }
        .take(files.size - MaxCoverArtFiles)
        .forEach { runCatching { it.delete() } }
}
