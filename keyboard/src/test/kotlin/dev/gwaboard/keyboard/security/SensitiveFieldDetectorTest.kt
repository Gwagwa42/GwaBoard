package dev.gwaboard.keyboard.security

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SensitiveFieldDetector].
 * Verifies each detection rule from issue #18:
 * - TYPE_TEXT_VARIATION_PASSWORD
 * - TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
 * - TYPE_TEXT_VARIATION_WEB_PASSWORD
 * - TYPE_NUMBER_VARIATION_PASSWORD
 * - autofill hints containing "password"
 * - autofill hints containing "creditCardNumber"
 * - hint text containing sensitive keywords
 *
 * Note: Autofill hints via EditorInfo.extras Bundle cannot be tested in pure
 * unit tests (Android Bundle is stubbed). The internal [hasSensitiveAutofillHint]
 * method is tested directly with string arrays instead.
 */
class SensitiveFieldDetectorTest {

    // --- InputType-based detection ---

    @Test
    fun `text password field is detected as sensitive`() {
        val editorInfo = createEditorInfo(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        assertTrue(SensitiveFieldDetector.isSensitiveField(editorInfo))
    }

    @Test
    fun `visible password field is detected as sensitive`() {
        val editorInfo = createEditorInfo(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        )
        assertTrue(SensitiveFieldDetector.isSensitiveField(editorInfo))
    }

    @Test
    fun `web password field is detected as sensitive`() {
        val editorInfo = createEditorInfo(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        assertTrue(SensitiveFieldDetector.isSensitiveField(editorInfo))
    }

    @Test
    fun `numeric password field is detected as sensitive`() {
        val editorInfo = createEditorInfo(
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        )
        assertTrue(SensitiveFieldDetector.isSensitiveField(editorInfo))
    }

    @Test
    fun `normal text field is not sensitive`() {
        val editorInfo = createEditorInfo(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        )
        assertFalse(SensitiveFieldDetector.isSensitiveField(editorInfo))
    }

    @Test
    fun `email field is not sensitive`() {
        val editorInfo = createEditorInfo(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        assertFalse(SensitiveFieldDetector.isSensitiveField(editorInfo))
    }

    @Test
    fun `phone number field is not sensitive`() {
        val editorInfo = createEditorInfo(
            inputType = InputType.TYPE_CLASS_PHONE
        )
        assertFalse(SensitiveFieldDetector.isSensitiveField(editorInfo))
    }

    @Test
    fun `normal number field is not sensitive`() {
        val editorInfo = createEditorInfo(
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL
        )
        assertFalse(SensitiveFieldDetector.isSensitiveField(editorInfo))
    }

    // --- Autofill hint detection (direct method tests) ---
    // Note: EditorInfo.extras Bundle is stubbed in unit tests, so we test
    // the hasSensitiveAutofillHint method directly with string arrays.

    @Test
    fun `autofill hint password is detected as sensitive`() {
        assertTrue(SensitiveFieldDetector.hasSensitiveAutofillHint(arrayOf("password")))
    }

    @Test
    fun `autofill hint creditCardNumber is detected as sensitive`() {
        assertTrue(SensitiveFieldDetector.hasSensitiveAutofillHint(arrayOf("creditCardNumber")))
    }

    @Test
    fun `autofill hint password is case-insensitive`() {
        assertTrue(SensitiveFieldDetector.hasSensitiveAutofillHint(arrayOf("PASSWORD")))
        assertTrue(SensitiveFieldDetector.hasSensitiveAutofillHint(arrayOf("Password")))
    }

    @Test
    fun `autofill hint creditCardNumber is case-insensitive`() {
        assertTrue(SensitiveFieldDetector.hasSensitiveAutofillHint(arrayOf("CREDITCARDNUMBER")))
    }

    @Test
    fun `non-sensitive autofill hint is not flagged`() {
        assertFalse(SensitiveFieldDetector.hasSensitiveAutofillHint(arrayOf("emailAddress", "username")))
    }

    @Test
    fun `sensitive hint among multiple autofill hints is detected`() {
        assertTrue(SensitiveFieldDetector.hasSensitiveAutofillHint(arrayOf("username", "password")))
    }

    @Test
    fun `null autofill hints are handled safely`() {
        assertFalse(SensitiveFieldDetector.hasSensitiveAutofillHint(null))
    }

    @Test
    fun `empty autofill hints array is not sensitive`() {
        assertFalse(SensitiveFieldDetector.hasSensitiveAutofillHint(emptyArray()))
    }

    // --- Hint text-based detection ---

    @Test
    fun `hint text containing password is sensitive`() {
        val editorInfo = createEditorInfo(
            inputType = InputType.TYPE_CLASS_TEXT,
            hintText = "Enter your password"
        )
        assertTrue(SensitiveFieldDetector.isSensitiveField(editorInfo))
    }

    @Test
    fun `hint text containing credit card is sensitive`() {
        val editorInfo = createEditorInfo(
            inputType = InputType.TYPE_CLASS_TEXT,
            hintText = "Credit card number"
        )
        assertTrue(SensitiveFieldDetector.isSensitiveField(editorInfo))
    }

    @Test
    fun `hint text containing PIN is sensitive`() {
        val editorInfo = createEditorInfo(
            inputType = InputType.TYPE_CLASS_TEXT,
            hintText = "Enter PIN"
        )
        assertTrue(SensitiveFieldDetector.isSensitiveField(editorInfo))
    }

    @Test
    fun `hint text containing CVV is sensitive`() {
        val editorInfo = createEditorInfo(
            inputType = InputType.TYPE_CLASS_TEXT,
            hintText = "CVV"
        )
        assertTrue(SensitiveFieldDetector.isSensitiveField(editorInfo))
    }

    @Test
    fun `normal hint text is not sensitive`() {
        val editorInfo = createEditorInfo(
            inputType = InputType.TYPE_CLASS_TEXT,
            hintText = "Enter your name"
        )
        assertFalse(SensitiveFieldDetector.isSensitiveField(editorInfo))
    }

    @Test
    fun `null hint text is handled safely`() {
        assertFalse(SensitiveFieldDetector.hasSensitiveHintText(null))
    }

    @Test
    fun `blank hint text is not sensitive`() {
        assertFalse(SensitiveFieldDetector.hasSensitiveHintText("   "))
    }

    // --- Edge cases ---

    @Test
    fun `null EditorInfo returns false`() {
        assertFalse(SensitiveFieldDetector.isSensitiveField(null))
    }

    @Test
    fun `password inputType takes priority`() {
        // Even with no hint text or autofill hints, inputType alone triggers detection
        val editorInfo = createEditorInfo(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
        )
        assertTrue(SensitiveFieldDetector.isSensitiveField(editorInfo))
    }

    // --- Internal method: hasSensitiveInputType ---

    @Test
    fun `hasSensitiveInputType detects password variations`() {
        assertTrue(
            SensitiveFieldDetector.hasSensitiveInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            )
        )
        assertTrue(
            SensitiveFieldDetector.hasSensitiveInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            )
        )
        assertTrue(
            SensitiveFieldDetector.hasSensitiveInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            )
        )
        assertTrue(
            SensitiveFieldDetector.hasSensitiveInputType(
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            )
        )
    }

    @Test
    fun `hasSensitiveInputType rejects non-password types`() {
        assertFalse(
            SensitiveFieldDetector.hasSensitiveInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
            )
        )
        assertFalse(
            SensitiveFieldDetector.hasSensitiveInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            )
        )
        assertFalse(
            SensitiveFieldDetector.hasSensitiveInputType(InputType.TYPE_CLASS_PHONE)
        )
    }

    // --- Internal method: hasSensitiveHintText ---

    @Test
    fun `hasSensitiveHintText detects sensitive keywords`() {
        assertTrue(SensitiveFieldDetector.hasSensitiveHintText("Enter your password"))
        assertTrue(SensitiveFieldDetector.hasSensitiveHintText("Credit card number"))
        assertTrue(SensitiveFieldDetector.hasSensitiveHintText("CVV"))
        assertTrue(SensitiveFieldDetector.hasSensitiveHintText("CVC code"))
        assertTrue(SensitiveFieldDetector.hasSensitiveHintText("Enter PIN code"))
        assertFalse(SensitiveFieldDetector.hasSensitiveHintText("Email address"))
        assertFalse(SensitiveFieldDetector.hasSensitiveHintText(null))
    }

    // --- Helper ---

    private fun createEditorInfo(
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        hintText: CharSequence? = null,
    ): EditorInfo = EditorInfo().apply {
        this.inputType = inputType
        this.hintText = hintText
    }
}
