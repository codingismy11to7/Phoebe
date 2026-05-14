package com.phoebe.app.platform

import com.phoebe.app.data.PlexClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970
import platform.SafariServices.SFSafariViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

@OptIn(ExperimentalNativeApi::class)
actual fun isDebugBuild(): Boolean = Platform.isDebugBinary

actual fun createPlatformHttpClient(): HttpClient = HttpClient(Darwin) {
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 60_000
    }
    install(ContentNegotiation) {
        json(PlexClient.PlexJson)
    }
}

actual class PlatformStorage actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual suspend fun readText(name: String): String? = defaults.stringForKey(name)

    actual suspend fun writeText(name: String, value: String) {
        defaults.setObject(value, forKey = name)
    }

    actual suspend fun delete(name: String) {
        defaults.removeObjectForKey(name)
    }

    actual suspend fun writeBytes(name: String, bytes: ByteArray): String {
        defaults.setObject(bytes.size.toString(), forKey = name)
        return "phoebe://offline/$name"
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun openExternalUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    val presenter = topPresenterViewController()
    if (presenter != null) {
        presenter.presentViewController(
            viewControllerToPresent = SFSafariViewController(nsUrl),
            animated = true,
            completion = null,
        )
        return
    }
    UIApplication.sharedApplication.openURL(
        url = nsUrl,
        options = emptyMap<Any?, Any>(),
        completionHandler = null,
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun topPresenterViewController(): UIViewController? {
    val application = UIApplication.sharedApplication
    activeWindow(application)?.rootViewController?.let { return topPresentedViewController(it) }

    for (scene in application.connectedScenes) {
        val windowScene = scene as? UIWindowScene ?: continue
        windowScene.keyWindow?.rootViewController?.let { return topPresentedViewController(it) }
        for (window in windowScene.windows) {
            val uiWindow = window as? UIWindow ?: continue
            uiWindow.rootViewController?.let { return topPresentedViewController(it) }
        }
    }

    for (window in application.windows) {
        val uiWindow = window as? UIWindow ?: continue
        uiWindow.rootViewController?.let { return topPresentedViewController(it) }
    }
    return null
}

@OptIn(ExperimentalForeignApi::class)
private fun activeWindow(application: UIApplication): UIWindow? {
    application.keyWindow?.let { return it }
    for (scene in application.connectedScenes) {
        val windowScene = scene as? UIWindowScene ?: continue
        windowScene.keyWindow?.let { return it }
    }
    return application.windows.firstOrNull() as? UIWindow
}

private fun topPresentedViewController(controller: UIViewController): UIViewController {
    var current = controller
    while (true) {
        val presented = current.presentedViewController ?: return current
        current = presented
    }
}

actual fun currentTimeMs(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

actual fun prefersReducedArtworkEffects(): Boolean = false

actual fun catalogTrackPrefetchAlbumCount(): Int = 6

actual fun catalogTrackPrefetchParallelism(): Int = 2

internal actual fun platformLog(tag: String, message: String) {
    println("[$tag] $message")
}
