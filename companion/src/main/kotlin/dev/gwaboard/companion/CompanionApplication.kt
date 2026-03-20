package dev.gwaboard.companion

import android.app.Application
import android.util.Log

/**
 * Application class for the companion SMS app.
 *
 * Initializes Koin DI and any app-wide configuration.
 * Kept minimal — this is a utility app, not a main app.
 */
class CompanionApplication : Application() {

    companion object {
        private const val TAG = "CompanionApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "GwaBoard Companion application created")
        // TODO: Initialize Koin DI modules once services are implemented
    }
}
