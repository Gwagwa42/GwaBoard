package dev.gwaboard.keyboard

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import dev.gwaboard.keyboard.security.SensitiveFieldDetector

/**
 * GwaBoard IME service — the core input method entry point.
 *
 * Current implementation provides a minimal working keyboard service.
 * Future milestones will integrate FlorisBoard's rendering pipeline
 * (once FlorisImeService is refactored into a reusable library module)
 * and layer the dual-tier AI suggestion engine on top.
 *
 * Architecture:
 * - This service handles the Android IME lifecycle
 * - HybridSuggestionEngine (future) will orchestrate N-gram + SmolLM2
 * - IPC with companion app happens via ContentProvider (shared-ipc module)
 * - All AI inference remains 100% on-device
 * - Sensitive fields (password, credit card) disable all AI features
 */
class GwaBoardImeService : InputMethodService() {

    companion object {
        private const val TAG = "GwaBoardIme"
    }

    /**
     * Flag indicating the current input field is sensitive (password, credit card, etc.).
     * When true, all AI features are disabled: no suggestions, no n-gram learning,
     * no LLM inference. Reset on each [onStartInput] call.
     */
    @Volatile
    var isSensitiveField: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "GwaBoard IME service created")
    }

    override fun onCreateInputView(): View? {
        // TODO: Replace with FlorisBoard-based keyboard view once fork integration is complete
        Log.d(TAG, "Creating input view (placeholder)")
        return null
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)

        // Evaluate sensitive field status on every input field transition
        isSensitiveField = SensitiveFieldDetector.isSensitiveField(attribute)

        if (isSensitiveField) {
            Log.i(TAG, "Sensitive field detected — AI features disabled")
        }

        Log.d(TAG, "Input started — editor: ${attribute?.packageName}, " +
            "sensitive: $isSensitiveField, restarting: $restarting")
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d(TAG, "Input view started — editor: ${info?.packageName}, restarting: $restarting")
    }

    override fun onFinishInput() {
        super.onFinishInput()
        // Reset sensitive field flag when leaving an input field
        isSensitiveField = false
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        Log.d(TAG, "Input view finished")
    }

    override fun onDestroy() {
        Log.i(TAG, "GwaBoard IME service destroyed")
        super.onDestroy()
    }
}
