package com.phoebe.app

import android.annotation.SuppressLint
import android.Manifest
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlayFromSearchIntent(intent)
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
        val button = castRouteButton ?: return false
        button.post {
            button.showDialog()
        }
        return true
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
        val serviceIntent = Intent(this, com.phoebe.app.player.PlaybackService::class.java)
            .setAction(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtras(intent)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 1001
    }
}
