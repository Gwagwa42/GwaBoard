package dev.gwaboard.keyboard

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo

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
 */
class GwaBoardImeService : InputMethodService() {

    companion object {
        private const val TAG = "GwaBoardIme"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "GwaBoard IME service created")
    }

    override fun onCreateInputView(): View? {
        // TODO: Replace with FlorisBoard-based keyboard view once fork integration is complete
        Log.d(TAG, "Creating input view (placeholder)")
        return null
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d(TAG, "Input view started — editor: ${info?.packageName}, restarting: $restarting")
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
