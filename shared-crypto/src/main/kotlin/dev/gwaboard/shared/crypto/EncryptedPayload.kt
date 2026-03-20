package dev.gwaboard.shared.crypto

/**
 * Container for AES-256-GCM encrypted data.
 *
 * Holds the ciphertext along with the initialization vector (IV)
 * that was generated for this specific encryption operation.
 * Both fields are required for decryption.
 *
 * @property ciphertext The encrypted data including the GCM authentication tag.
 * @property iv The 12-byte initialization vector, unique per encryption operation.
 */
data class EncryptedPayload(
    val ciphertext: ByteArray,
    val iv: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedPayload) return false
        return ciphertext.contentEquals(other.ciphertext) && iv.contentEquals(other.iv)
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}
