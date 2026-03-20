package dev.gwaboard.keyboard.engine

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * Encrypted SQLite storage for user-learned n-grams.
 *
 * Persists n-gram frequency data so that the engine retains user-specific
 * language patterns across app restarts. In a future milestone, the actual
 * database file will be encrypted with AES-256-GCM via the shared-crypto module.
 * For now, the storage layer is isolated behind this interface to allow that
 * swap without changing the engine code.
 *
 * Schema:
 * - `ngrams(key TEXT PRIMARY KEY, order INTEGER, count INTEGER, updated_at INTEGER)`
 *
 * The key format matches [NGramModel]: words joined by "|".
 */
class NGramStorage(
    context: Context,
    databaseName: String = DATABASE_NAME,
) : SQLiteOpenHelper(context, databaseName, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ngrams (
                key TEXT PRIMARY KEY,
                ngram_order INTEGER NOT NULL,
                count INTEGER NOT NULL DEFAULT 1,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ngrams_order ON ngrams(ngram_order)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 is the initial schema — future migrations go here
    }

    /**
     * Records an n-gram occurrence, inserting or incrementing the count.
     *
     * @param words The word sequence to record.
     */
    fun recordNGram(words: List<String>) {
        val key = words.joinToString(NGramModel.SEPARATOR) { it.lowercase() }
        val order = words.size
        val now = System.currentTimeMillis()

        writableDatabase.execSQL(
            """
            INSERT INTO ngrams (key, ngram_order, count, updated_at)
            VALUES (?, ?, 1, ?)
            ON CONFLICT(key) DO UPDATE SET
                count = count + 1,
                updated_at = ?
            """.trimIndent(),
            arrayOf<Any>(key, order, now, now)
        )
    }

    /**
     * Loads all persisted n-grams for restoring the in-memory model.
     *
     * @return List of (word-list, count) pairs suitable for [NGramModel.loadEntries].
     */
    fun loadAll(): List<Pair<List<String>, Int>> {
        val entries = mutableListOf<Pair<List<String>, Int>>()

        readableDatabase.rawQuery(
            "SELECT key, count FROM ngrams ORDER BY count DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val key = cursor.getString(0)
                val count = cursor.getInt(1)
                val words = key.split(NGramModel.SEPARATOR)
                entries.add(words to count)
            }
        }

        Log.d(TAG, "Loaded ${entries.size} n-gram entries from storage")
        return entries
    }

    /**
     * Removes n-grams that have not been updated since [cutoffMillis].
     * Useful for pruning stale entries to keep storage bounded.
     *
     * @return Number of entries removed.
     */
    fun pruneOlderThan(cutoffMillis: Long): Int {
        val deleted = writableDatabase.delete(
            "ngrams",
            "updated_at < ?",
            arrayOf(cutoffMillis.toString())
        )
        Log.d(TAG, "Pruned $deleted stale n-gram entries")
        return deleted
    }

    companion object {
        private const val TAG = "NGramStorage"
        private const val DATABASE_NAME = "ngrams.db"
        private const val DATABASE_VERSION = 1
    }
}
