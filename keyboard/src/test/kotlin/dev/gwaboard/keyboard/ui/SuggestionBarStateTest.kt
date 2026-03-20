package dev.gwaboard.keyboard.ui

import dev.gwaboard.keyboard.engine.Suggestion
import dev.gwaboard.keyboard.engine.SuggestionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionBarStateTest {

    @Test
    fun `default state is compact with empty lists`() {
        val state = SuggestionBarState()
        assertEquals(SuggestionBarMode.Compact, state.mode)
        assertTrue(state.ngramSuggestions.isEmpty())
        assertTrue(state.aiSuggestions.isEmpty())
        assertNull(state.contactName)
        assertFalse(state.isAiLoading)
        assertTrue(state.isAiAvailable)
    }

    @Test
    fun `copy preserves unmodified fields`() {
        val suggestions = listOf(Suggestion("test", 0.8f))
        val state = SuggestionBarState(
            ngramSuggestions = suggestions,
            contactName = "Bob",
        )

        val updated = state.copy(mode = SuggestionBarMode.Expanded)

        assertEquals(SuggestionBarMode.Expanded, updated.mode)
        assertEquals(suggestions, updated.ngramSuggestions)
        assertEquals("Bob", updated.contactName)
    }

    @Test
    fun `buildHeaderText without contact name`() {
        assertEquals("AI Suggestions", buildHeaderText(null))
    }

    @Test
    fun `buildHeaderText with contact name`() {
        assertEquals("AI Suggestions (Alice)", buildHeaderText("Alice"))
    }
}
