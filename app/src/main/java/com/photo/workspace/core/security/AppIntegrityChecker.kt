package com.photo.workspace.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Detects whether the currently-running app was signed by someone other than you — i.e.
 * whether this is a repackaged/modified/re-signed copy rather than the APK you actually built.
 *
 * ## What this does and doesn't protect against
 * This reads the certificate Android itself verified when installing the APK (via
 * PackageManager) and compares its fingerprint to the one you hardcode below. It's a real,
 * standard technique (the same idea used by most "app tampering detection" libraries) — but
 * it is a speed bump, not a wall: anyone willing to decompile the APK can find and delete this
 * check too, then re-sign with their own key and it'll pass again. What it *does* stop is the
 * common case of someone quietly patching a copy of your app (e.g. to strip ads, or reroute
 * the plugin signing check to their own key) and passing it around without your permission
 * being obvious. Treat it as one layer, not the whole defense.
 *
 * ## Setting the expected fingerprint
 * The certificate that matters is whatever actually signs the APK your users install:
 * - If you build and distribute the APK yourself (sideloading, GitHub releases, etc.), this
 *   is simply your own release keystore's certificate — get it the same way as the plugin
 *   signing key:
 *   `keytool -list -v -keystore your-release-key.jks -alias your-key-alias`
 * - If you ever publish through Google Play with "Play App Signing" enabled, Play *re-signs*
 *   your APK with its own key before distributing it — the fingerprint users' devices see is
 *   Play's signing certificate, not your upload key's. In that case get the right value from
 *   Play Console under Setup > App integrity > App signing key certificate, not from keytool.
 */
object AppIntegrityChecker {

    /**
     * TODO: replace with the real SHA-256 fingerprint of the certificate that actually signs
     * your distributed APK (see class doc above for which one that is). Left as an
     * obviously-invalid placeholder so the check fails loudly instead of silently passing
     * everything until you've set it.
     */
    private const val EXPECTED_APP_SIGNATURE_SHA256 =
        "cbd6642b98692bfe1a8c47fd986d04e21e3fb1afc28419e4902fdda0c3a8e2a2"

    sealed class Result {
        object Verified : Result()
        object NotConfigured : Result()
        data class Mismatch(val actualFingerprints: List<String>) : Result()
        data class Error(val message: String) : Result()
    }

    fun verify(context: Context): Result {
        if (EXPECTED_APP_SIGNATURE_SHA256 == "REPLACE_WITH_YOUR_APP_SIGNING_CERT_SHA256_FINGERPRINT") {
            return Result.NotConfigured
        }

        return try {
            val fingerprints = currentSignatureFingerprints(context)
            if (fingerprints.isEmpty()) {
                return Result.Error("No signing certificate found for this installation.")
            }
            if (fingerprints.any { it.equals(EXPECTED_APP_SIGNATURE_SHA256, ignoreCase = true) }) {
                Result.Verified
            } else {
                Result.Mismatch(fingerprints)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error verifying app signature.")
        }
    }

    private fun currentSignatureFingerprints(context: Context): List<String> {
        val pm = context.packageManager
        val packageName = context.packageName

        val signatures: Array<ByteArray> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo
            when {
                signingInfo == null -> emptyArray()
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners.map { it.toByteArray() }.toTypedArray()
                else -> (signingInfo.signingCertificateHistory ?: signingInfo.apkContentsSigners)
                    .map { it.toByteArray() }.toTypedArray()
            }
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            (info.signatures ?: emptyArray()).map { it.toByteArray() }.toTypedArray()
        }

        return signatures.map { sha256Hex(it) }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
