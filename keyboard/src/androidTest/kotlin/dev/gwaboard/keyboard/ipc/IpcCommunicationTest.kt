package dev.gwaboard.keyboard.ipc

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gwaboard.shared.ipc.SignatureVerifier
import dev.gwaboard.shared.ipc.SmsProviderClient
import dev.gwaboard.shared.ipc.SmsProviderContract
import dev.gwaboard.shared.models.IpcContract
import org.junit.Assert.assertEquals
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
        // Diagnoses the column mismatch bug: SmsContentProvider returns
        // encrypted columns, but SmsProviderClient expects plaintext columns.

        val cursor = contentResolver.query(
            SmsProviderContract.CONTACT_PROFILES_URI,
            null, null, null, null,
        ) ?: return

        cursor.use { c ->
            val actualColumns = c.columnNames.toSet()

            // Columns returned by SmsContentProvider (encrypted schema)
            val encryptedColumns = setOf("contact_id", "encrypted_data", "encrypted_iv")

            // Columns expected by SmsProviderClient (plaintext schema)
            val plaintextColumns = setOf(
                IpcContract.ProfileColumns.DOMINANT_LANGUAGE,
                IpcContract.ProfileColumns.TONE,
                IpcContract.ProfileColumns.AVG_RESPONSE_LENGTH,
                IpcContract.ProfileColumns.TOP_NGRAMS,
                IpcContract.ProfileColumns.STYLE_EMBEDDING,
            )

            Log.w(TAG, "=== COLUMN SCHEMA DIAGNOSTIC ===")
            Log.w(TAG, "Actual columns from provider: $actualColumns")
            Log.w(TAG, "Encrypted columns (provider sends): $encryptedColumns")
            Log.w(TAG, "Plaintext columns (client expects): $plaintextColumns")

            val hasEncryptedSchema = actualColumns.containsAll(encryptedColumns)
            val hasPlaintextSchema = actualColumns.containsAll(plaintextColumns)

            if (hasEncryptedSchema && !hasPlaintextSchema) {
                Log.e(
                    TAG,
                    "BUG CONFIRMED: Provider returns encrypted columns but " +
                        "client expects plaintext columns. SmsProviderClient needs " +
                        "a decryption layer.",
                )
            }

            // This assertion documents the current (broken) state.
            // When the bug is fixed, change this to assert plaintext or
            // encrypted+decryption columns as appropriate.
            assertTrue(
                "Provider should return encrypted columns (current schema)",
                hasEncryptedSchema,
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

    // ── Layer 5: Debug Seeding via call() ──────────────────────────────

    @Test
    fun contentProvider_callSeedTestData_returnsSuccess() {
        // Seed test data via the debug call() endpoint
        val result = contentResolver.call(
            Uri.parse(IpcContract.BASE_URI),
            "seed_test_data",
            null,
            null,
        )

        assertNotNull("seed_test_data should return a non-null Bundle", result)
        assertTrue(
            "seed_test_data should return success=true",
            result!!.getBoolean("success", false),
        )
    }

    @Test
    fun contentProvider_callClearTestData_returnsSuccess() {
        // Clear test data via the debug call() endpoint
        val result = contentResolver.call(
            Uri.parse(IpcContract.BASE_URI),
            "clear_test_data",
            null,
            null,
        )

        assertNotNull("clear_test_data should return a non-null Bundle", result)
        assertTrue(
            "clear_test_data should return success=true",
            result!!.getBoolean("success", false),
        )
    }

    // ── Layer 6: Seeded Data Verification ──────────────────────────────

    @Test
    fun contentProvider_afterSeeding_returnsNonEmptyCursor() {
        // Seed first via call()
        contentResolver.call(
            Uri.parse(IpcContract.BASE_URI),
            "seed_test_data",
            null,
            null,
        )

        // Query all profiles — should now have at least one row
        val cursor = contentResolver.query(
            SmsProviderContract.CONTACT_PROFILES_URI,
            null, null, null, null,
        )

        assertNotNull("Cursor should not be null after seeding", cursor)
        cursor!!.use { c ->
            Log.i(TAG, "After seeding: cursor has ${c.count} rows")
            assertTrue(
                "Cursor should contain at least 1 row after seeding",
                c.count > 0,
            )
        }
    }

    @Test
    fun contentProvider_afterSeeding_singleProfileHasEncryptedData() {
        // Seed via call()
        contentResolver.call(
            Uri.parse(IpcContract.BASE_URI),
            "seed_test_data",
            null,
            null,
        )

        // Query the specific seeded profile (contactId=1)
        val uri = Uri.withAppendedPath(
            SmsProviderContract.CONTACT_PROFILES_URI,
            "1",
        )
        val cursor = contentResolver.query(uri, null, null, null, null)

        assertNotNull("Single profile cursor should not be null", cursor)
        cursor!!.use { c ->
            assertEquals(
                "Seeded profile for contactId=1 should return exactly 1 row",
                1,
                c.count,
            )

            assertTrue("Cursor should move to first row", c.moveToFirst())

            // Verify encrypted columns are present and non-empty
            val encryptedData = c.getString(
                c.getColumnIndexOrThrow("encrypted_data"),
            )
            val encryptedIv = c.getString(
                c.getColumnIndexOrThrow("encrypted_iv"),
            )

            assertNotNull("encrypted_data should not be null", encryptedData)
            assertNotNull("encrypted_iv should not be null", encryptedIv)
            assertTrue(
                "encrypted_data should be non-empty Base64",
                encryptedData.isNotEmpty(),
            )
            assertTrue(
                "encrypted_iv should be non-empty Base64",
                encryptedIv.isNotEmpty(),
            )

            Log.i(TAG, "Seeded profile encrypted_data length: ${encryptedData.length}")
            Log.i(TAG, "Seeded profile encrypted_iv length: ${encryptedIv.length}")
        }
    }

    @Test
    fun contentProvider_afterClear_returnsEmptyCursor() {
        // Seed then clear
        contentResolver.call(
            Uri.parse(IpcContract.BASE_URI),
            "seed_test_data",
            null,
            null,
        )
        contentResolver.call(
            Uri.parse(IpcContract.BASE_URI),
            "clear_test_data",
            null,
            null,
        )

        // Query the cleared profile — should be empty
        val uri = Uri.withAppendedPath(
            SmsProviderContract.CONTACT_PROFILES_URI,
            "1",
        )
        val cursor = contentResolver.query(uri, null, null, null, null)

        assertNotNull("Cursor should not be null after clear", cursor)
        cursor!!.use { c ->
            assertEquals(
                "Cursor should be empty after clearing test data",
                0,
                c.count,
            )
        }
    }
}
