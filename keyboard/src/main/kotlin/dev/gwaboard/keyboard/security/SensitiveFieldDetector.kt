package dev.gwaboard.keyboard.security

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.annotation.VisibleForTesting

/**
 * Detects password and other sensitive input fields where AI features
 * and n-gram learning must be disabled for user privacy.
 *
 * Detection rules (from issue #18):
 * - `TYPE_TEXT_VARIATION_PASSWORD` in inputType → disable ALL
 * - `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD` in inputType → disable ALL
 * - `TYPE_TEXT_VARIATION_WEB_PASSWORD` in inputType → disable ALL
 * - `TYPE_NUMBER_VARIATION_PASSWORD` in inputType → disable ALL
 * - `EditorInfo.extras` contains sensitive autofill hints → disable ALL
 * - `EditorInfo.hintText` contains sensitive keywords → disable ALL
 *
 * When a field is flagged as sensitive:
 * - No suggestion bar is shown
 * - No n-gram learning occurs
 * - No LLM inference is triggered
 */
object SensitiveFieldDetector {

    /**
     * Key used by Android's autofill framework to pass autofill hints
     * through [EditorInfo.extras] Bundle to IME services.
     */
    private const val AUTOFILL_HINTS_KEY = "autofillHints"

    /**
     * Autofill hint values that indicate a sensitive field.
     * Checked case-insensitively against autofill hints in EditorInfo extras.
     */
    private val SENSITIVE_AUTOFILL_HINTS = setOf(
        "password",
        "creditcardnumber",
    )

    /**
     * Keywords in hint text that suggest a sensitive field.
     * Checked case-insensitively against [EditorInfo.hintText].
     */
    private val SENSITIVE_HINT_KEYWORDS = setOf(
        "password",
        "credit card",
        "cvv",
        "cvc",
        "pin",
    )

    /**
     * Returns `true` if the given [EditorInfo] describes a sensitive field
     * where all AI features should be disabled.
     *
     * @param editorInfo The editor info from [android.inputmethodservice.InputMethodService.onStartInput].
     *                   If null, returns `false` (assume non-sensitive).
     */
    fun isSensitiveField(editorInfo: EditorInfo?): Boolean {
        if (editorInfo == null) return false

        return hasSensitiveInputType(editorInfo.inputType) ||
            hasSensitiveAutofillHint(extractAutofillHints(editorInfo)) ||
            hasSensitiveHintText(editorInfo.hintText)
    }

    /**
     * Checks whether the input type contains a password variation.
     * Masks the input type to extract the class and variation bits before comparison.
     */
    @VisibleForTesting
    internal fun hasSensitiveInputType(inputType: Int): Boolean {
        val typeClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION

        return when (typeClass) {
            InputType.TYPE_CLASS_TEXT -> variation in setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            )
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    /**
     * Checks whether any autofill hint matches a known sensitive hint.
     * Comparison is case-insensitive to handle platform variations.
     */
    @VisibleForTesting
    internal fun hasSensitiveAutofillHint(autofillHints: Array<String>?): Boolean {
        if (autofillHints.isNullOrEmpty()) return false

        return autofillHints.any { hint ->
            hint.lowercase() in SENSITIVE_AUTOFILL_HINTS
        }
    }

    /**
     * Checks whether the hint text contains a sensitive keyword.
     * Used as a fallback when inputType doesn't indicate a password field
     * but the hint text suggests one (e.g., "Enter your password").
     */
    @VisibleForTesting
    internal fun hasSensitiveHintText(hintText: CharSequence?): Boolean {
        if (hintText.isNullOrBlank()) return false

        val lower = hintText.toString().lowercase()
        return SENSITIVE_HINT_KEYWORDS.any { keyword -> keyword in lower }
    }

    /**
     * Extracts autofill hints from EditorInfo extras Bundle.
     * Android's autofill framework may pass hints via the extras Bundle
     * with the key "autofillHints" as a String array.
     */
    private fun extractAutofillHints(editorInfo: EditorInfo): Array<String>? {
        return try {
            editorInfo.extras?.getStringArray(AUTOFILL_HINTS_KEY)
        } catch (_: Exception) {
            null
        }
    }
}
