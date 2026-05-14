package com.phoebe.app

import android.app.Application

/** Robolectric application without playback/cast warm-up (avoids flaky Media3 binds in screenshot tests). */
class ScreenshotTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidContextHolder.application = this
    }
}
