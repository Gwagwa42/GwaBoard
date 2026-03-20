package dev.gwaboard.keyboard.ui

import dev.gwaboard.keyboard.engine.HybridSuggestionEngine
import dev.gwaboard.keyboard.engine.Suggestion
import dev.gwaboard.keyboard.engine.SuggestionSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionBarViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var engine: HybridSuggestionEngine
    private lateinit var viewModel: SuggestionBarViewModel

    private val testSuggestions = listOf(
        Suggestion("hello", 0.9f, SuggestionSource.NGRAM),
        Suggestion("help", 0.7f, SuggestionSource.NGRAM),
        Suggestion("here", 0.5f, SuggestionSource.NGRAM),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock the HybridSuggestionEngine directly to avoid internal timeout issues
        engine = mockk(relaxed = true)
        coEvery { engine.suggest(any(), any()) } returns testSuggestions

        viewModel = SuggestionBarViewModel(
            engine = engine,
            dispatcher = testDispatcher,
            scope = testScope,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        viewModel.close()
    }

    @Test
    fun `initial state is compact with empty suggestions`() {
        val state = viewModel.state.value
        assertEquals(SuggestionBarMode.Compact, state.mode)
        assertTrue(state.ngramSuggestions.isEmpty())
        assertTrue(state.aiSuggestions.isEmpty())
        assertFalse(state.isAiLoading)
        assertTrue(state.isAiAvailable)
    }

    @Test
    fun `onTextChanged updates ngram suggestions`() {
        viewModel.onTextChanged("hel")
        testScope.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(3, state.ngramSuggestions.size)
        assertEquals("hello", state.ngramSuggestions[0].word)
    }

    @Test
    fun `hide sets mode to Hidden`() {
        viewModel.hide()
        assertEquals(SuggestionBarMode.Hidden, viewModel.state.value.mode)
    }

    @Test
    fun `show transitions from Hidden to Compact`() {
        viewModel.hide()
        viewModel.show()
        assertEquals(SuggestionBarMode.Compact, viewModel.state.value.mode)
    }

    @Test
    fun `show does nothing when already in Compact`() {
        viewModel.show()
        assertEquals(SuggestionBarMode.Compact, viewModel.state.value.mode)
    }

    @Test
    fun `expand changes mode to Expanded`() {
        viewModel.expand("hello ")
        assertEquals(SuggestionBarMode.Expanded, viewModel.state.value.mode)
        assertTrue(viewModel.state.value.isAiLoading)
    }

    @Test
    fun `expand does nothing when hidden`() {
        viewModel.hide()
        viewModel.expand("hello ")
        assertEquals(SuggestionBarMode.Hidden, viewModel.state.value.mode)
    }

    @Test
    fun `collapse returns to Compact and clears AI suggestions`() {
        viewModel.expand("hello ")
        viewModel.collapse()

        val state = viewModel.state.value
        assertEquals(SuggestionBarMode.Compact, state.mode)
        assertTrue(state.aiSuggestions.isEmpty())
        assertFalse(state.isAiLoading)
    }

    @Test
    fun `typing during expanded mode auto-collapses`() {
        viewModel.expand("hello ")
        viewModel.onTextChanged("hello w")

        assertEquals(SuggestionBarMode.Compact, viewModel.state.value.mode)
    }

    @Test
    fun `setContactName updates state`() {
        viewModel.setContactName("Alice")
        assertEquals("Alice", viewModel.state.value.contactName)

        viewModel.setContactName(null)
        assertEquals(null, viewModel.state.value.contactName)
    }

    @Test
    fun `setAiAvailable false hides expand button`() {
        viewModel.setAiAvailable(false)
        assertFalse(viewModel.state.value.isAiAvailable)
    }

    @Test
    fun `setAiAvailable false collapses expanded mode`() {
        viewModel.expand("hello ")
        viewModel.setAiAvailable(false)
        assertEquals(SuggestionBarMode.Compact, viewModel.state.value.mode)
    }

    @Test
    fun `onSuggestionSelected returns word text`() {
        val suggestion = Suggestion("hello", 0.9f)
        assertEquals("hello", viewModel.onSuggestionSelected(suggestion))
    }

    @Test
    fun `onAiSuggestionSelected with autoSend collapses bar`() {
        viewModel.expand("hello ")
        val suggestion = Suggestion("Hello! How are you?", 0.95f, SuggestionSource.LLM)

        val text = viewModel.onAiSuggestionSelected(suggestion, shouldAutoSend = true)

        assertEquals("Hello! How are you?", text)
        assertEquals(SuggestionBarMode.Compact, viewModel.state.value.mode)
    }

    @Test
    fun `onAiSuggestionSelected without autoSend keeps expanded`() {
        viewModel.expand("hello ")
        val suggestion = Suggestion("Hello! How are you?", 0.95f, SuggestionSource.LLM)

        val text = viewModel.onAiSuggestionSelected(suggestion, shouldAutoSend = false)

        assertEquals("Hello! How are you?", text)
        assertEquals(SuggestionBarMode.Expanded, viewModel.state.value.mode)
    }
}
