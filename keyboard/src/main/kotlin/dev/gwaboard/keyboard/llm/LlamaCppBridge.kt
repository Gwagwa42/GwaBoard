package dev.gwaboard.keyboard.llm

import android.util.Log

/**
 * JNI bridge to llama.cpp native inference library.
 *
 * Provides Kotlin-side access to the native C++ llama.cpp functions
 * compiled via NDK. This class handles the raw native calls; lifecycle
 * management and coroutine integration are handled by SmolLMEngine (issue #11).
 *
 * Thread safety: native functions are not thread-safe. Callers must ensure
 * that only one thread calls [loadModel]/[infer]/[unloadModel] at a time.
 * [cancelInference] is safe to call from any thread.
 */
class LlamaCppBridge {

    companion object {
        private const val TAG = "LlamaCppBridge"
        private const val NATIVE_LIB = "gwaboard_llm"

        /**
         * Tracks whether the native library has been loaded.
         * Loading is idempotent but we avoid redundant System.loadLibrary calls.
         */
        @Volatile
        private var isLibraryLoaded = false

        /**
         * Load the native library. Safe to call multiple times.
         *
         * @return true if the library is available, false if loading failed.
         */
        fun ensureLoaded(): Boolean {
            if (isLibraryLoaded) return true
            return try {
                System.loadLibrary(NATIVE_LIB)
                isLibraryLoaded = true
                Log.i(TAG, "Native library loaded: $NATIVE_LIB")
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library: $NATIVE_LIB", e)
                false
            }
        }
    }

    /**
     * Load a GGUF model from the filesystem.
     *
     * @param modelPath Absolute path to the .gguf model file.
     * @param nThreads Number of CPU threads for inference (recommended: 4 for Tensor G3).
     * @return A non-zero context handle on success, or 0 on failure.
     */
    fun loadModel(modelPath: String, nThreads: Int): Long {
        require(modelPath.isNotBlank()) { "Model path must not be blank" }
        require(nThreads > 0) { "Thread count must be positive" }
        return nativeLoadModel(modelPath, nThreads)
    }

    /**
     * Unload a previously loaded model, freeing all native resources.
     *
     * @param contextHandle Handle returned by [loadModel]. No-op if 0.
     */
    fun unloadModel(contextHandle: Long) {
        if (contextHandle == 0L) {
            Log.w(TAG, "unloadModel called with null handle, ignoring")
            return
        }
        nativeUnloadModel(contextHandle)
    }

    /**
     * Run text completion inference on the loaded model.
     *
     * @param contextHandle Handle returned by [loadModel].
     * @param prompt Input text to complete.
     * @param maxTokens Maximum number of tokens to generate.
     * @return Generated text, or empty string on error/cancellation.
     * @throws IllegalArgumentException if contextHandle is 0.
     */
    fun infer(contextHandle: Long, prompt: String, maxTokens: Int): String {
        require(contextHandle != 0L) { "Cannot infer with null context handle" }
        require(maxTokens > 0) { "maxTokens must be positive" }
        return nativeInfer(contextHandle, prompt, maxTokens)
    }

    /**
     * Cancel an in-progress inference. The corresponding [infer] call
     * will return partial results at the next token boundary.
     *
     * Thread-safe: can be called from any thread while [infer] is running.
     *
     * @param contextHandle Handle returned by [loadModel]. No-op if 0.
     */
    fun cancelInference(contextHandle: Long) {
        if (contextHandle == 0L) return
        nativeCancelInference(contextHandle)
    }

    // -- Native method declarations (implemented in llama_jni_bridge.cpp) --

    private external fun nativeLoadModel(modelPath: String, nThreads: Int): Long
    private external fun nativeUnloadModel(contextHandle: Long)
    private external fun nativeInfer(contextHandle: Long, prompt: String, maxTokens: Int): String
    private external fun nativeCancelInference(contextHandle: Long)
}
