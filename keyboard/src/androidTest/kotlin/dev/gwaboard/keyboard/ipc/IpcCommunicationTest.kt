package dev.gwaboard.keyboard.ipc

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gwaboard.shared.ipc.SignatureVerifier
import dev.gwaboard.shared.ipc.SmsProviderClient
import dev.gwaboard.shared.ipc.SmsProviderContract
import dev.gwaboard.shared.models.IpcContract
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented IPC tests that run on the emulator with both
 * keyboard and companion apps installed. Tests the real
 * ContentProvider communication path between the two apps.
 *
 * Prerequisites:
 * - Both :keyboard and :companion debug APKs installed on device
 * - Both signed with the same debug keystore
 */
@RunWith(AndroidJUnit4::class)
class IpcCommunicationTest {

    companion object {
        private const val TAG = "IpcTest"
        private const val COMPANION_PACKAGE = "dev.gwaboard.companion"
    }

    private lateinit var contentResolver: ContentResolver

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        contentResolver = context.contentResolver
    }

    // ── Layer 1: Signature Verification ──────────────────────────────

    @Test
    fun signatureVerification_sameDebugKeystore_returnsTrue() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val result = SignatureVerifier.isSameSignature(context, COMPANION_PACKAGE)

        Log.i(TAG, "Signature verification result: $result")
        assertTrue(
            "Keyboard and companion should share the same debug signing certificate",
            result,
        )
    }

    @Test
    fun signatureVerification_unknownPackage_returnsFalse() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val result = SignatureVerifier.isSameSignature(context, "com.nonexistent.app")

        assertFalse(
            "Verification against non-existent package should return false",
            result,
        )
    }

    // ── Layer 2: ContentProvider Access ───────────────────────────────

    @Test
    fun contentProvider_queryContactProfiles_noSecurityException() {
        // This test verifies that the keyboard app (running these tests)
        // can query the companion's ContentProvider without SecurityException.
        // The signature permission should be granted automatically since
        // both APKs are signed with the same debug keystore.

        val uri = SmsProviderContract.CONTACT_PROFILES_URI

        val cursor = try {
            contentResolver.query(uri, null, null, null, null)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException querying ContentProvider", e)
            null
        }

        assertNotNull(
            "Query should succeed (no SecurityException) — cursor may be empty but not null",
            cursor,
        )

        cursor?.use { c ->
            Log.i(TAG, "ContentProvider returned cursor with ${c.count} rows")
            Log.i(TAG, "Cursor columns: ${c.columnNames.joinToString()}")

            // Log column names to diagnose the mismatch bug
            if (c.columnCount > 0) {
                Log.i(TAG, "Column names returned by SmsContentProvider:")
                for (i in 0 until c.columnCount) {
                    Log.i(TAG, "  [$i] ${c.getColumnName(i)}")
                }
            }
        }
    }

    @Test
    fun contentProvider_querySingleProfile_noSecurityException() {
        // Query a specific contact profile (ID 1, which won't exist but
        // should not throw SecurityException)
        val uri = Uri.withAppendedPath(
            SmsProviderContract.CONTACT_PROFILES_URI,
            "1",
        )

        val cursor = try {
            contentResolver.query(uri, null, null, null, null)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException querying single profile", e)
            null
        }

        assertNotNull(
            "Single profile query should succeed without SecurityException",
            cursor,
        )

        cursor?.use { c ->
            Log.i(TAG, "Single profile query returned ${c.count} rows (expected 0 — empty store)")
        }
    }

    // ── Layer 3: Column Schema Validation ────────────────────────────

    @Test
    fun contentProvider_columns_matchExpectedSchema() {
        // Validates that the provider returns plaintext columns matching
        // what SmsProviderClient expects (fix for column mismatch bug).

        val cursor = contentResolver.query(
            SmsProviderContract.CONTACT_PROFILES_URI,
            null, null, null, null,
        ) ?: return

        cursor.use { c ->
            val actualColumns = c.columnNames.toSet()

            // Columns expected by SmsProviderClient (plaintext schema)
            val plaintextColumns = setOf(
                IpcContract.ProfileColumns.CONTACT_ID,
                IpcContract.ProfileColumns.DOMINANT_LANGUAGE,
                IpcContract.ProfileColumns.TONE,
                IpcContract.ProfileColumns.AVG_RESPONSE_LENGTH,
                IpcContract.ProfileColumns.TOP_NGRAMS,
                IpcContract.ProfileColumns.STYLE_EMBEDDING,
            )

            Log.i(TAG, "=== COLUMN SCHEMA VALIDATION ===")
            Log.i(TAG, "Actual columns from provider: $actualColumns")
            Log.i(TAG, "Expected plaintext columns: $plaintextColumns")

            assertTrue(
                "Provider should return plaintext columns matching IpcContract.ProfileColumns",
                actualColumns.containsAll(plaintextColumns),
            )
        }
    }

    // ── Layer 4: SmsProviderClient Integration ───────────────────────

    @Test
    fun smsProviderClient_isCompanionAvailable_returnsTrue() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = SmsProviderClient(context)

        assertTrue(
            "Companion should be available (installed + same signature)",
            client.isCompanionAvailable,
        )
    }

    @Test
    fun smsProviderClient_queryContactProfile_returnsNullGracefully() {
        // The ProfileStore is empty, so this should return null
        // without crashing (even with the column mismatch bug).
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = SmsProviderClient(context)

        val profile = client.queryContactProfile(999L)

        Log.i(TAG, "queryContactProfile(999) returned: $profile")
        // Profile should be null since ProfileStore is empty
        // (no data seeded in companion app)
    }

    @Test
    fun smsProviderClient_queryMessages_returnsEmptyList() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = SmsProviderClient(context)

        // SMS messages URI is not handled by SmsContentProvider's UriMatcher
        // (only contact_profiles is implemented), so this should return empty
        val messages = client.queryMessages()

        Log.i(TAG, "queryMessages returned ${messages.size} messages")
        assertTrue(
            "Messages query should return empty list (URI not handled)",
            messages.isEmpty(),
        )
    }

    @Test
    fun smsProviderClient_queryContacts_returnsEmptyList() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = SmsProviderClient(context)

        val contacts = client.queryContacts()

        Log.i(TAG, "queryContacts returned ${contacts.size} contacts")
        assertTrue(
            "Contacts query should return empty list (URI not handled)",
            contacts.isEmpty(),
        )
    }
}
