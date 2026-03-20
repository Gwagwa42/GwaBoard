package dev.gwaboard.keyboard.privacy

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Tests for [PrivacyPreferences] SharedPreferences wrapper.
 *
 * Verifies default values and persistence of privacy settings.
 */
class PrivacyPreferencesTest {

    private val sharedPrefs = mock(SharedPreferences::class.java)
    private val editor = mock(SharedPreferences.Editor::class.java)
    private val context = mock(Context::class.java)

    @Before
    fun setUp() {
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPrefs)
        `when`(sharedPrefs.edit()).thenReturn(editor)
        `when`(editor.putBoolean(anyString(), any())).thenReturn(editor)
    }

    @Test
    fun `AI suggestions enabled by default`() {
        `when`(sharedPrefs.getBoolean("ai_suggestions_enabled", true)).thenReturn(true)

        val prefs = PrivacyPreferences(context)
        assertTrue(prefs.isAiSuggestionsEnabled)
    }

    @Test
    fun `disabling AI suggestions persists the value`() {
        `when`(sharedPrefs.getBoolean("ai_suggestions_enabled", true)).thenReturn(false)

        val prefs = PrivacyPreferences(context)
        prefs.isAiSuggestionsEnabled = false

        verify(editor).putBoolean("ai_suggestions_enabled", false)
        verify(editor).apply()
    }

    @Test
    fun `privacy policy not acknowledged by default`() {
        `when`(sharedPrefs.getBoolean("privacy_acknowledged", false)).thenReturn(false)

        val prefs = PrivacyPreferences(context)
        assertFalse(prefs.isPrivacyPolicyAcknowledged)
    }
}
