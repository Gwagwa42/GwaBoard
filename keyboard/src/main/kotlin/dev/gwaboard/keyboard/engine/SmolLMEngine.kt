package dev.gwaboard.keyboard.engine

import dev.gwaboard.shared.models.ContactProfile

/**
 * Tier 2 suggestion engine backed by SmolLM2-360M-Instruct via llama.cpp NDK/JNI.
 *
 * This interface defines the contract for the LLM-based engine. The actual
 * implementation is delivered in issue #11 (SmolLMEngine lifecycle). The
 * [HybridSuggestionEngine] depends only on this interface, allowing Tier 2
 * to be swapped, stubbed, or disabled without affecting Tier 1 operation.
 *
 * Lifecycle:
 * - [load]: Loads the quantized model into memory (~400MB RAM)
 * - [unload]: Releases model memory (called after inactivity timeout)
 * - [isLoaded]: Checks if the model is currently in memory
 * - [cancelInference]: Cancels any in-progress inference (user resumed typing)
 */
interface SmolLMEngine : SuggestionEngine {

    /** Whether the LLM model is currently loaded in memory. */
    val isLoaded: Boolean

    /**
     * Loads the quantized SmolLM2 model into memory.
     * This is an expensive operation (~1-2s) and should be called lazily
     * on first Tier 2 trigger, not at keyboard startup.
     */
    suspend fun load()

    /**
     * Unloads the model from memory, freeing ~400MB RAM.
     * Called by [HybridSuggestionEngine] after inactivity timeout.
     */
    suspend fun unload()

    /**
     * Cancels any in-progress inference. Called when the user resumes
     * typing after a pause, making the pending LLM result stale.
     */
    fun cancelInference()

    /**
     * Generates completions using the LLM with optional context from IPC.
     *
     * @param context The text preceding the cursor.
     * @param maxResults Maximum number of completions to return.
     * @param contactProfile Optional profile from the companion app for personalization.
     * @param lastReceivedMessage Optional last received SMS for conversational context.
     * @return Ranked suggestions, or empty if model is not loaded or inference was cancelled.
     */
    suspend fun suggestWithContext(
        context: String,
        maxResults: Int = 3,
        contactProfile: ContactProfile? = null,
        lastReceivedMessage: String? = null,
    ): List<Suggestion>
}
