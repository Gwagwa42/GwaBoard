package dev.gwaboard.keyboard.privacy

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrivacyPreferencesTest {

    private val sharedPrefs = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val context = mockk<Context>()

    @Before
    fun setUp() {
        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs
        every { sharedPrefs.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
    }

    @Test
    fun `AI suggestions enabled by default`() {
        every { sharedPrefs.getBoolean("ai_suggestions_enabled", true) } returns true

        val prefs = PrivacyPreferences(context)
        assertTrue(prefs.isAiSuggestionsEnabled)
    }

    @Test
    fun `disabling AI suggestions persists the value`() {
        every { sharedPrefs.getBoolean("ai_suggestions_enabled", true) } returns false

        val prefs = PrivacyPreferences(context)
        prefs.isAiSuggestionsEnabled = false

        verify { editor.putBoolean("ai_suggestions_enabled", false) }
        verify { editor.apply() }
    }

    @Test
    fun `privacy policy not acknowledged by default`() {
        every { sharedPrefs.getBoolean("privacy_acknowledged", false) } returns false

        val prefs = PrivacyPreferences(context)
        assertFalse(prefs.isPrivacyPolicyAcknowledged)
    }
}
