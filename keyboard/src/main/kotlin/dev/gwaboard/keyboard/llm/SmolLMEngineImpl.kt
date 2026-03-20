package dev.gwaboard.keyboard.llm

import android.util.Log
import dev.gwaboard.keyboard.engine.SmolLMEngine
import dev.gwaboard.keyboard.engine.Suggestion
import dev.gwaboard.keyboard.engine.SuggestionSource
import dev.gwaboard.shared.models.ContactProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Concrete implementation of [SmolLMEngine] wrapping [LlamaCppBridge] with
 * full lifecycle management, thread safety, and context-aware prompt building.
 *
 * Lifecycle states:
 * ```
 * UNLOADED -> LOADING -> LOADED -> INFERRING -> LOADED -> UNLOADING -> UNLOADED
 * ```
 *
 * Thread safety: state transitions are guarded by a [Mutex]. The [cancelInference]
 * method is lock-free and safe to call from any thread (delegates to the
 * native cancel which is thread-safe in llama.cpp).
 *
 * @param bridge The JNI bridge to llama.cpp native functions.
 * @param config Configuration for the engine (model path, threads, tokens).
 */
class SmolLMEngineImpl(
    private val bridge: LlamaCppBridge,
    private val config: SmolLMConfig = SmolLMConfig(),
) : SmolLMEngine {

    /** Current lifecycle state, readable without locks for fast checks. */
    private val state = AtomicReference(EngineState.UNLOADED)

    /** Native context handle returned by [LlamaCppBridge.loadModel]. 0 = no model loaded. */
    @Volatile
    private var contextHandle: Long = 0L

    /** Guards load/unload/infer transitions to prevent concurrent native calls. */
    private val lifecycleMutex = Mutex()

    override val isLoaded: Boolean
        get() = state.get() == EngineState.LOADED || state.get() == EngineState.INFERRING

    override suspend fun load() {
        if (isLoaded) return

        lifecycleMutex.withLock {
            // Double-check after acquiring lock
            if (isLoaded) return

            val currentState = state.get()
            if (currentState != EngineState.UNLOADED) {
                Log.w(TAG, "Cannot load from state $currentState")
                return
            }

            state.set(EngineState.LOADING)
            Log.i(TAG, "Loading model: ${config.modelPath} (threads=${config.nThreads})")

            try {
                val handle = withContext(Dispatchers.Default) {
                    bridge.loadModel(config.modelPath, config.nThreads)
                }

                if (handle == 0L) {
                    state.set(EngineState.UNLOADED)
                    Log.e(TAG, "Native model load returned null handle")
                    return
                }

                contextHandle = handle
                state.set(EngineState.LOADED)
                Log.i(TAG, "Model loaded (handle=$handle)")
            } catch (e: Exception) {
                state.set(EngineState.UNLOADED)
                Log.e(TAG, "Model load failed", e)
                throw e
            }
        }
    }

    override suspend fun unload() {
        if (state.get() == EngineState.UNLOADED) return

        lifecycleMutex.withLock {
            val currentState = state.get()
            if (currentState == EngineState.UNLOADED) return

            // Cancel any running inference before unloading
            if (currentState == EngineState.INFERRING) {
                bridge.cancelInference(contextHandle)
            }

            state.set(EngineState.UNLOADING)
            Log.i(TAG, "Unloading model (handle=$contextHandle)")

            try {
                val handle = contextHandle
                contextHandle = 0L

                withContext(Dispatchers.Default) {
                    bridge.unloadModel(handle)
                }

                state.set(EngineState.UNLOADED)
                Log.i(TAG, "Model unloaded, native memory freed")
            } catch (e: Exception) {
                // Even on error, mark as unloaded — native state is indeterminate
                state.set(EngineState.UNLOADED)
                Log.e(TAG, "Error during model unload", e)
            }
        }
    }

    override fun cancelInference() {
        val handle = contextHandle
        if (handle != 0L && state.get() == EngineState.INFERRING) {
            Log.d(TAG, "Cancelling inference")
            bridge.cancelInference(handle)
        }
    }

    override suspend fun suggest(context: String, maxResults: Int): List<Suggestion> =
        suggestWithContext(context, maxResults)

    override suspend fun suggestWithContext(
        context: String,
        maxResults: Int,
        contactProfile: ContactProfile?,
        lastReceivedMessage: String?,
    ): List<Suggestion> {
        if (!isLoaded) {
            Log.d(TAG, "Suggest called but model not loaded, returning empty")
            return emptyList()
        }

        val handle = contextHandle
        if (handle == 0L) return emptyList()

        val prompt = buildPrompt(context, contactProfile, lastReceivedMessage)
        if (prompt.isBlank()) return emptyList()

        return lifecycleMutex.withLock {
            if (!isLoaded || contextHandle == 0L) return emptyList()

            state.set(EngineState.INFERRING)
            try {
                val rawOutput = withContext(Dispatchers.Default) {
                    bridge.infer(handle, prompt, config.maxTokens)
                }

                state.set(EngineState.LOADED)

                if (rawOutput.isBlank()) return emptyList()

                parseCompletions(rawOutput, maxResults)
            } catch (e: Exception) {
                // Restore to LOADED on error (model is still in memory)
                if (contextHandle != 0L) {
                    state.set(EngineState.LOADED)
                }
                Log.e(TAG, "Inference failed", e)
                emptyList()
            }
        }
    }

    override suspend fun learn(
        precedingWords: List<String>,
        completedWord: String,
        isPasswordField: Boolean,
    ) {
        // LLM does not learn from user input — it's a pre-trained model.
        // Learning is handled exclusively by Tier 1 (NGramEngine).
    }

    override fun close() {
        val handle = contextHandle
        if (handle != 0L) {
            bridge.cancelInference(handle)
            bridge.unloadModel(handle)
            contextHandle = 0L
        }
        state.set(EngineState.UNLOADED)
        Log.i(TAG, "SmolLMEngineImpl closed")
    }

    /** Returns the current engine lifecycle state (visible for testing). */
    internal fun currentState(): EngineState = state.get()

    /**
     * Builds the SmolLM2-Instruct prompt template with optional SMS and contact context.
     *
     * SmolLM2-360M-Instruct uses a chat template format:
     * ```
     * <|im_start|>system
     * {system_message}<|im_end|>
     * <|im_start|>user
     * {user_message}<|im_end|>
     * <|im_start|>assistant
     * ```
     *
     * The prompt is kept compact to stay within the 128-token context window.
     */
    internal fun buildPrompt(
        context: String,
        contactProfile: ContactProfile? = null,
        lastReceivedMessage: String? = null,
    ): String {
        val systemParts = mutableListOf<String>()
        systemParts.add("Complete the user's message naturally.")

        // Inject contact profile for personalization
        contactProfile?.let { profile ->
            systemParts.add("Language: ${profile.dominantLanguage}. Tone: ${profile.tone}.")
            if (profile.avgResponseLength > 0) {
                systemParts.add("Keep responses around ${profile.avgResponseLength} chars.")
            }
        }

        val systemMessage = systemParts.joinToString(" ")

        val userParts = mutableListOf<String>()

        // Add received SMS for conversational context (truncate to save tokens)
        lastReceivedMessage?.let { sms ->
            val truncated = if (sms.length > MAX_SMS_CONTEXT_CHARS) {
                sms.takeLast(MAX_SMS_CONTEXT_CHARS)
            } else {
                sms
            }
            userParts.add("Received: \"$truncated\"")
        }

        userParts.add("Continue: $context")

        val userMessage = userParts.joinToString("\n")

        return buildString {
            append("<|im_start|>system\n")
            append(systemMessage)
            append("<|im_end|>\n")
            append("<|im_start|>user\n")
            append(userMessage)
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
    }

    /**
     * Parses raw LLM output into ranked [Suggestion] entries.
     *
     * The LLM generates continuation text. We extract individual words,
     * assign decreasing scores based on position, and return unique results.
     */
    internal fun parseCompletions(rawOutput: String, maxResults: Int): List<Suggestion> {
        // Clean the output: remove special tokens, trim whitespace
        val cleaned = rawOutput
            .replace("<|im_end|>", "")
            .replace("<|im_start|>", "")
            .trim()

        if (cleaned.isBlank()) return emptyList()

        // Extract words from the completion
        val words = cleaned.split(WORD_SPLITTER)
            .filter { it.isNotBlank() && it.length >= MIN_WORD_LENGTH }
            .distinct()
            .take(maxResults)

        // Assign decreasing confidence scores — first word is most likely
        return words.mapIndexed { index, word ->
            Suggestion(
                word = word,
                score = BASE_LLM_SCORE - (index * SCORE_DECAY),
                source = SuggestionSource.LLM,
            )
        }
    }

    /** Lifecycle states for the engine. */
    enum class EngineState {
        UNLOADED,
        LOADING,
        LOADED,
        INFERRING,
        UNLOADING,
    }

    companion object {
        private const val TAG = "SmolLMEngine"

        /** Maximum characters from last received SMS to include in the prompt. */
        internal const val MAX_SMS_CONTEXT_CHARS = 100

        /** Minimum word length to consider as a valid suggestion. */
        internal const val MIN_WORD_LENGTH = 2

        /** Base confidence score for LLM suggestions (first word). */
        internal const val BASE_LLM_SCORE = 0.85f

        /** Score reduction per position in the completion output. */
        internal const val SCORE_DECAY = 0.1f

        private val WORD_SPLITTER = Regex("[\\s,.!?;:]+")
    }
}

/**
 * Configuration for SmolLMEngineImpl.
 *
 * @property modelPath Absolute path to the SmolLM2 GGUF model file on device.
 * @property nThreads Number of CPU threads for inference. Default 4 for Tensor G3.
 * @property maxTokens Maximum tokens to generate per inference call. Kept low for latency.
 */
data class SmolLMConfig(
    val modelPath: String = "/data/local/tmp/smollm2-360m-instruct-q4_k_m.gguf",
    val nThreads: Int = 4,
    val maxTokens: Int = 32,
)
