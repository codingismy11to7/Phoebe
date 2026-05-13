package com.phoebe.app

import android.app.Application

class PhoebeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidContextHolder.application = this
    }
}
