package com.phoebe.app

import android.app.Application

object AndroidContextHolder {
    private var applicationInstance: Application? = null

    var application: Application
        get() = applicationOrNull ?: error("Android application context has not been initialized")
        set(value) {
            applicationInstance = value
        }

    val applicationOrNull: Application?
        get() = applicationInstance ?: findRuntimeApplicationOrNull().also { applicationInstance = it }

    var activity: AndroidCastRoutePickerHost? = null

    private fun findRuntimeApplicationOrNull(): Application? {
        val testApplication = runCatching {
            Class.forName("androidx.test.core.app.ApplicationProvider")
                .getMethod("getApplicationContext")
                .invoke(null) as? Application
        }.getOrNull()
        if (testApplication != null) return testApplication

        val robolectricApplication = runCatching {
            Class.forName("org.robolectric.RuntimeEnvironment")
                .getMethod("getApplication")
                .invoke(null) as? Application
        }.getOrNull()
        if (robolectricApplication != null) return robolectricApplication

        return null
    }
}

interface AndroidCastRoutePickerHost {
    fun showCastRoutePicker(): Boolean
}
