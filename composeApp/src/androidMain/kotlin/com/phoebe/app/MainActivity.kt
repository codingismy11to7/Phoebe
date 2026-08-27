package com.phoebe.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import android.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import androidx.mediarouter.app.MediaRouteControllerDialogFragment
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.phoebe.app.platform.PhoebeAppLifecycle
import com.phoebe.app.player.AndroidPlaybackBridge

class MainActivity : FragmentActivity(), AndroidCastRoutePickerHost {
    private var castRouteButton: MediaRouteButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidContextHolder.application = application
        AndroidContextHolder.activity = this
        setContent { App() }
        installCastRouteButton()
        handlePlayFromSearchIntent(intent)
        handleAssistantSearchIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlayFromSearchIntent(intent)
        handleAssistantSearchIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        PhoebeAppLifecycle.setUiVisible(true)
    }

    override fun onStop() {
        PhoebeAppLifecycle.setUiVisible(false)
        super.onStop()
    }

    override fun onDestroy() {
        castRouteButton = null
        if (AndroidContextHolder.activity === this) {
            AndroidContextHolder.activity = null
        }
        super.onDestroy()
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val handled = when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> AndroidPlaybackBridge.adjustCastVolumeStep?.invoke(true)
                KeyEvent.KEYCODE_VOLUME_DOWN -> AndroidPlaybackBridge.adjustCastVolumeStep?.invoke(false)
                else -> null
            }
            if (handled == true) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun showCastRoutePicker(): Boolean {
        if (isCastConnected()) {
            return showCastControllerDialog()
        }
        val button = castRouteButton ?: return false
        button.post {
            button.showDialog()
        }
        return true
    }

    private fun isCastConnected(): Boolean =
        runCatching {
            CastContext.getSharedInstance(this).castState == CastState.CONNECTED
        }.getOrDefault(false)

    private fun showCastControllerDialog(): Boolean {
        val shown = runCatching {
            dismissCastDialog(CAST_CONTROLLER_DIALOG_TAG)
            MediaRouteControllerDialogFragment().show(supportFragmentManager, CAST_CONTROLLER_DIALOG_TAG)
            true
        }.getOrDefault(false)
        return shown || showFallbackConnectedCastDialog()
    }

    private fun showFallbackConnectedCastDialog(): Boolean {
        val castContext = runCatching { CastContext.getSharedInstance(this) }.getOrNull() ?: return false
        val deviceName = castContext.sessionManager.currentCastSession?.castDevice?.friendlyName ?: "Chromecast"
        AlertDialog.Builder(this)
            .setTitle("Connected to $deviceName")
            .setMessage("Playback is on your Chromecast.")
            .setPositiveButton("Disconnect") { _, _ ->
                AndroidPlaybackBridge.onCastDisconnect?.invoke()
                    ?: castContext.sessionManager.endCurrentSession(true)
            }
            .setNeutralButton("Change device") { _, _ ->
                showCastChooserDialog()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        return true
    }

    private fun showCastChooserDialog(): Boolean =
        runCatching {
            dismissCastDialog(CAST_CHOOSER_DIALOG_TAG)
            MediaRouteChooserDialogFragment().show(supportFragmentManager, CAST_CHOOSER_DIALOG_TAG)
            true
        }.getOrDefault(false)

    private fun dismissCastDialog(tag: String) {
        (supportFragmentManager.findFragmentByTag(tag) as? DialogFragment)
            ?.dismissAllowingStateLoss()
    }

    private fun installCastRouteButton() {
        val themedContext = ContextThemeWrapper(this, R.style.MediaRouteButtonTheme)
        val button = MediaRouteButton(themedContext).apply {
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        CastButtonFactory.setUpMediaRouteButton(themedContext, button)
        castRouteButton = button
        addContentView(
            button,
            FrameLayout.LayoutParams(1, 1, Gravity.TOP or Gravity.START),
        )
    }

    private fun handlePlayFromSearchIntent(intent: Intent?) {
        if (intent?.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
        startPlaybackFromSearch(
            query = intent.getStringExtra(SearchManager.QUERY).orEmpty(),
            extras = intent.extras,
        )
    }

    /** Handles the query passed by the Assistant App Actions GET_THING capability. */
    private fun handleAssistantSearchIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val query = intent.getStringExtra(AssistantSearchQueryExtra)?.trim().orEmpty()
        if (query.isBlank()) return
        startPlaybackFromSearch(query = query, extras = null)
    }

    private fun startPlaybackFromSearch(query: String, extras: Bundle?) {
        val serviceIntent = Intent(this, com.phoebe.app.player.PlaybackService::class.java)
            .setAction(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(SearchManager.QUERY, query)
            .apply { extras?.let(::putExtras) }
        // Deliberately not startForegroundService: the service only calls startForeground once
        // playback actually begins, and a search that matches nothing would otherwise trip
        // ForegroundServiceDidNotStartInTimeException. The activity is foreground here, so a
        // plain start is allowed, and media3 promotes the service when the player starts.
        startService(serviceIntent)
    }

    private companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 1001
        private const val AssistantSearchQueryExtra = "phoebe.assistant.SEARCH_QUERY"
        private const val CAST_CONTROLLER_DIALOG_TAG = "cast_controller"
        private const val CAST_CHOOSER_DIALOG_TAG = "cast_chooser"
    }
}
