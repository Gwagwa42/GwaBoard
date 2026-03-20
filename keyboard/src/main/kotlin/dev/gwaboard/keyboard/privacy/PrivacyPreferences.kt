package dev.gwaboard.keyboard.privacy

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences wrapper for keyboard privacy settings.
 *
 * Stores user preferences related to GDPR controls:
 * - AI suggestions enabled/disabled toggle
 * - Whether the user has acknowledged the privacy policy
 *
 * All preferences are stored in a private file accessible only
 * to the keyboard process.
 */
class PrivacyPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Whether AI (Tier 2 / LLM) suggestions are enabled.
     * When false, only standard n-gram suggestions are shown.
     * Default: true (AI enabled).
     */
    var isAiSuggestionsEnabled: Boolean
        get() = prefs.getBoolean(KEY_AI_SUGGESTIONS_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_AI_SUGGESTIONS_ENABLED, value).apply() }

    /**
     * Whether the user has acknowledged the privacy policy.
     */
    var isPrivacyPolicyAcknowledged: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_ACKNOWLEDGED, false)
        set(value) { prefs.edit().putBoolean(KEY_PRIVACY_ACKNOWLEDGED, value).apply() }

    companion object {
        private const val PREFS_NAME = "gwaboard_privacy_prefs"
        private const val KEY_AI_SUGGESTIONS_ENABLED = "ai_suggestions_enabled"
        private const val KEY_PRIVACY_ACKNOWLEDGED = "privacy_acknowledged"
    }
}
