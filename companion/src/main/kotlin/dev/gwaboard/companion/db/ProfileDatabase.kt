package dev.gwaboard.companion.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import dev.gwaboard.shared.models.ContactProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLite database for per-contact profiles.
 *
 * Stores derived statistical profiles (never raw SMS content).
 * The database file itself is stored in the app's private storage,
 * accessible only to the companion app process.
 *
 * Profile data is serialized to JSON for the complex fields
 * (top_ngrams, style_embedding) to keep the schema simple.
 */
class ProfileDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {

    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_SQL)
        Log.i(TAG, "Profile database created")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // For v1, drop and recreate — profiles can be regenerated from SMS
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
        Log.i(TAG, "Profile database upgraded from v$oldVersion to v$newVersion")
    }

    /**
     * Insert or update a contact's profile.
     *
     * @param contactAddress Phone number or address identifying the contact.
     * @param profile The computed [ContactProfile] to store.
     */
    suspend fun upsertProfile(
        contactAddress: String,
        profile: ContactProfile,
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COL_ADDRESS, contactAddress)
            put(COL_DOMINANT_LANGUAGE, profile.dominantLanguage)
            put(COL_TONE, profile.tone)
            put(COL_AVG_RESPONSE_LENGTH, profile.avgResponseLength)
            put(COL_TOP_NGRAMS, json.encodeToString(profile.topNgrams))
            put(COL_STYLE_EMBEDDING, json.encodeToString(profile.styleEmbedding))
            put(COL_UPDATED_AT, System.currentTimeMillis())
        }

        writableDatabase.insertWithOnConflict(
            TABLE_NAME,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )

        Log.d(TAG, "Upserted profile for $contactAddress")
    }

    /**
     * Retrieve a stored profile for a contact.
     *
     * @param contactAddress Phone number or address identifying the contact.
     * @return The stored [ContactProfile], or null if not found.
     */
    suspend fun getProfile(contactAddress: String): ContactProfile? =
        withContext(Dispatchers.IO) {
            val cursor = readableDatabase.query(
                TABLE_NAME,
                null,
                "$COL_ADDRESS = ?",
                arrayOf(contactAddress),
                null,
                null,
                null,
            )

            cursor?.use { c ->
                if (!c.moveToFirst()) return@withContext null

                ContactProfile(
                    dominantLanguage = c.getString(c.getColumnIndexOrThrow(COL_DOMINANT_LANGUAGE)),
                    tone = c.getString(c.getColumnIndexOrThrow(COL_TONE)),
                    avgResponseLength = c.getInt(c.getColumnIndexOrThrow(COL_AVG_RESPONSE_LENGTH)),
                    topNgrams = json.decodeFromString(
                        c.getString(c.getColumnIndexOrThrow(COL_TOP_NGRAMS)),
                    ),
                    styleEmbedding = json.decodeFromString(
                        c.getString(c.getColumnIndexOrThrow(COL_STYLE_EMBEDDING)),
                    ),
                )
            }
        }

    /**
     * Retrieve all stored profiles.
     *
     * @return Map of contact address to [ContactProfile].
     */
    suspend fun getAllProfiles(): Map<String, ContactProfile> =
        withContext(Dispatchers.IO) {
            val profiles = mutableMapOf<String, ContactProfile>()

            val cursor = readableDatabase.query(
                TABLE_NAME,
                null,
                null,
                null,
                null,
                null,
                "$COL_UPDATED_AT DESC",
            )

            cursor?.use { c ->
                while (c.moveToNext()) {
                    val address = c.getString(c.getColumnIndexOrThrow(COL_ADDRESS))
                    val profile = ContactProfile(
                        dominantLanguage = c.getString(c.getColumnIndexOrThrow(COL_DOMINANT_LANGUAGE)),
                        tone = c.getString(c.getColumnIndexOrThrow(COL_TONE)),
                        avgResponseLength = c.getInt(c.getColumnIndexOrThrow(COL_AVG_RESPONSE_LENGTH)),
                        topNgrams = json.decodeFromString(
                            c.getString(c.getColumnIndexOrThrow(COL_TOP_NGRAMS)),
                        ),
                        styleEmbedding = json.decodeFromString(
                            c.getString(c.getColumnIndexOrThrow(COL_STYLE_EMBEDDING)),
                        ),
                    )
                    profiles[address] = profile
                }
            }

            profiles
        }

    /**
     * Delete a contact's profile.
     *
     * @param contactAddress Phone number or address identifying the contact.
     * @return True if a profile was deleted.
     */
    suspend fun deleteProfile(contactAddress: String): Boolean =
        withContext(Dispatchers.IO) {
            val deleted = writableDatabase.delete(
                TABLE_NAME,
                "$COL_ADDRESS = ?",
                arrayOf(contactAddress),
            )
            deleted > 0
        }

    /**
     * Delete all stored profiles. Useful for privacy reset.
     */
    suspend fun deleteAllProfiles() = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_NAME, null, null)
        Log.i(TAG, "All profiles deleted")
    }

    companion object {
        private const val TAG = "ProfileDatabase"
        private const val DATABASE_NAME = "gwaboard_profiles.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_NAME = "contact_profiles"
        private const val COL_ADDRESS = "address"
        private const val COL_DOMINANT_LANGUAGE = "dominant_language"
        private const val COL_TONE = "tone"
        private const val COL_AVG_RESPONSE_LENGTH = "avg_response_length"
        private const val COL_TOP_NGRAMS = "top_ngrams"
        private const val COL_STYLE_EMBEDDING = "style_embedding"
        private const val COL_UPDATED_AT = "updated_at"

        private const val CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                $COL_ADDRESS TEXT PRIMARY KEY,
                $COL_DOMINANT_LANGUAGE TEXT NOT NULL,
                $COL_TONE TEXT NOT NULL,
                $COL_AVG_RESPONSE_LENGTH INTEGER NOT NULL,
                $COL_TOP_NGRAMS TEXT NOT NULL,
                $COL_STYLE_EMBEDDING TEXT NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL
            )
        """
    }
}
