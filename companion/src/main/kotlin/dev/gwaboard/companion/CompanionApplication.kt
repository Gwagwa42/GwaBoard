package dev.gwaboard.companion

import android.app.Application
import android.util.Log
import dev.gwaboard.companion.provider.ProfileStore
import dev.gwaboard.shared.models.ContactProfile

/**
 * Application class for the companion SMS app.
 *
 * Initializes Koin DI and any app-wide configuration.
 * Kept minimal — this is a utility app, not a main app.
 */
class CompanionApplication : Application() {

    companion object {
        private const val TAG = "CompanionApp"

        /**
         * Shared ProfileStore instance accessible to both the Application
         * (for debug seeding) and the SmsContentProvider.
         *
         * This will be replaced by Koin DI once services are implemented.
         */
        val profileStore = ProfileStore()
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "GwaBoard Companion application created")

        if (BuildConfig.DEBUG) {
            seedDebugProfiles()
        }

        // TODO: Initialize Koin DI modules once services are implemented
    }

    /**
     * Seeds the ProfileStore with sample data for IPC integration testing.
     *
     * Only runs in debug builds. Provides known test data so that
     * instrumented tests can validate the full IPC pipeline:
     * keyboard → ContentResolver → SmsContentProvider → ProfileStore → data.
     */
    private fun seedDebugProfiles() {
        profileStore.putProfile(
            1L,
            ContactProfile(
                dominantLanguage = "fr",
                tone = "casual",
                avgResponseLength = 42,
                topNgrams = listOf("salut", "ok", "merci"),
                styleEmbedding = listOf(0.1f, 0.2f, 0.3f),
            ),
        )
        profileStore.putProfile(
            2L,
            ContactProfile(
                dominantLanguage = "en",
                tone = "formal",
                avgResponseLength = 120,
                topNgrams = listOf("hello", "regards", "please"),
                styleEmbedding = listOf(0.4f, 0.5f, 0.6f),
            ),
        )
        Log.i(TAG, "Debug profiles seeded: ${profileStore.size} entries")
    }
}
