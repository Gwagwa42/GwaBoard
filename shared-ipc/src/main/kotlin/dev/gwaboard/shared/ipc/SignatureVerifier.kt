package dev.gwaboard.shared.ipc

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Verifies that the companion app's ContentProvider is signed
 * with the same certificate as the calling keyboard app.
 *
 * This prevents a malicious app from registering a ContentProvider
 * at the same authority and intercepting IPC data.
 */
object SignatureVerifier {

    /**
     * Checks whether the package hosting the ContentProvider shares
     * the same signing certificate as this app.
     *
     * @param context Application or service context
     * @param providerPackage Package name of the ContentProvider host
     *   (e.g., "dev.gwaboard.companion")
     * @return `true` if both APKs are signed with the same certificate,
     *   `false` if the provider package is not installed or signatures differ
     */
    fun isSameSignature(context: Context, providerPackage: String): Boolean {
        return try {
            val ownFingerprint = getSigningFingerprint(context, context.packageName)
            val providerFingerprint = getSigningFingerprint(context, providerPackage)

            if (ownFingerprint == null || providerFingerprint == null) return false

            MessageDigest.isEqual(ownFingerprint, providerFingerprint)
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Extracts the SHA-256 fingerprint of the first signing certificate
     * for the given package.
     *
     * Uses [PackageManager.GET_SIGNING_CERTIFICATES] on API 28+ and
     * falls back to the deprecated [PackageManager.GET_SIGNATURES] on
     * older devices.
     *
     * @return SHA-256 digest bytes, or `null` if no signatures are found
     */
    @Suppress("DEPRECATION")
    private fun getSigningFingerprint(context: Context, packageName: String): ByteArray? {
        val pm = context.packageManager

        val certBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pm.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            ).signingInfo ?: return null

            // Use the current signer for apps with a single signing lineage
            val signers = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }

            signers?.firstOrNull()?.toByteArray()
        } else {
            val packageInfo = pm.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNATURES,
            )
            packageInfo.signatures?.firstOrNull()?.toByteArray()
        }

        return certBytes?.let {
            MessageDigest.getInstance("SHA-256").digest(it)
        }
    }
}
