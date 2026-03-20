package dev.gwaboard.shared.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Manages AES-256 key lifecycle in Android Keystore.
 *
 * Keys are hardware-backed on supported devices (Tensor G3 on Pixel 8A)
 * and tied to the calling app's UID — no other app can access them.
 */
object KeystoreHelper {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

    /** Default alias for the IPC encryption key shared between keyboard and companion */
    const val IPC_KEY_ALIAS = "gwaboard_ipc_aes256"

    /**
     * Retrieve an existing AES-256 key from the Keystore, or generate a new one
     * if no key exists under the given [alias].
     *
     * The generated key is configured for:
     * - AES-256 (256-bit key size)
     * - GCM block mode (authenticated encryption)
     * - No padding (GCM handles its own padding)
     *
     * @param alias Keystore alias. Defaults to [IPC_KEY_ALIAS].
     * @return The AES-256 [SecretKey] from Android Keystore.
     */
    fun getOrCreateKey(alias: String = IPC_KEY_ALIAS): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        // Return existing key if present
        keyStore.getEntry(alias, null)?.let { entry ->
            return (entry as KeyStore.SecretKeyEntry).secretKey
        }

        // Generate a new AES-256-GCM key bound to this app's UID
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER,
        )

        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            // Require unlocked device for key access (security hardening)
            .setUserAuthenticationRequired(false)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Check whether a key with the given [alias] exists in the Keystore.
     */
    fun keyExists(alias: String = IPC_KEY_ALIAS): Boolean {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        return keyStore.containsAlias(alias)
    }

    /**
     * Delete the key with the given [alias] from the Keystore.
     * Useful for key rotation or testing cleanup.
     */
    fun deleteKey(alias: String = IPC_KEY_ALIAS) {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }
}
