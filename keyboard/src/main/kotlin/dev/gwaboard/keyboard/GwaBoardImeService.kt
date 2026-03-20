package dev.gwaboard.keyboard

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.gwaboard.keyboard.security.SensitiveFieldDetector
import dev.gwaboard.keyboard.ui.PlaceholderKeyboardView

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
class GwaBoardImeService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "GwaBoardIme"
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry get() = savedStateRegistryController.savedStateRegistry

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
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        Log.i(TAG, "GwaBoard IME service created")
    }

    override fun onCreateInputView(): View {
        Log.d(TAG, "Creating input view (placeholder QWERTY)")
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // Propagate lifecycle/savedstate to the IME window's decor view so
        // ComposeView can find them when walking up the view tree.
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }
        val composeView = ComposeView(this).apply {
            setContent {
                MaterialTheme {
                    PlaceholderKeyboardView(
                        onKeyPress = { key ->
                            currentInputConnection?.commitText(key, 1)
                        },
                        onBackspace = {
                            currentInputConnection?.deleteSurroundingText(1, 0)
                        },
                        onEnter = {
                            currentInputConnection?.commitText("\n", 1)
                        },
                    )
                }
            }
        }
        return composeView
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
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        Log.i(TAG, "GwaBoard IME service destroyed")
        super.onDestroy()
    }
}
