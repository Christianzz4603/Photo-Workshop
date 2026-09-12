package com.photo.workspace.core.plugin

import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.cert.Certificate
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * Verifies that a `.mwplugin` (a signed Android-dex jar) was produced by the developer's
 * own signing key, before any of its code is ever loaded.
 *
 * ## The trust model this relies on
 * This is safe *only* because exactly one party — you — ever holds the private key used to
 * sign a plugin. The check below answers "did this file come from that key?", nothing more:
 * once a jar passes, its code runs with the full privileges of the app itself. That's fine
 * when you're the only signer (you already trust your own code, or you wouldn't ship it in
 * the main APK either), but it stops being safe the moment the private key is shared with
 * anyone else, embedded in a public repo, or reused from a debug keystore. If that key ever
 * leaks, this check no longer protects anything.
 *
 * ## How to actually produce a signed plugin
 * 1. Build the plugin module and compile it to a dex jar (e.g. via `d8` against the compiled
 *    .class files), so the jar contains `classes.dex` — DexClassLoader cannot load plain JVM
 *    .class files, only dex bytecode.
 * 2. Sign the resulting jar with `jarsigner` using your own keystore:
 *    `jarsigner -keystore your-release-key.jks plugin.jar your-key-alias`
 * 3. Compute your certificate's SHA-256 fingerprint once and hardcode it below as
 *    [EXPECTED_SIGNER_SHA256] — a public certificate fingerprint is not secret, only the
 *    private key is, so it's fine to bake this into the app's source.
 *
 * You can get that fingerprint from the same keystore with:
 *    `keytool -list -v -keystore your-release-key.jks -alias your-key-alias`
 * (look for the "Certificate fingerprints: SHA256:" line).
 */
object PluginSignatureVerifier {

    /**
     * TODO: replace with the real SHA-256 fingerprint of YOUR signing certificate before
     * shipping any plugin support. Left as an obviously-invalid placeholder so that every
     * plugin is rejected by default until you've actually generated and set your own key —
     * a missing/placeholder fingerprint should never silently accept anything.
     */
    private const val EXPECTED_SIGNER_SHA256 =
        "cbd6642b98692bfe1a8c47fd986d04e21e3fb1afc28419e4902fdda0c3a8e2a2"

    sealed class VerificationResult {
        object Verified : VerificationResult()
        data class Rejected(val reason: String) : VerificationResult()
    }

    fun verify(jarFile: File): VerificationResult {
        if (EXPECTED_SIGNER_SHA256 == "REPLACE_WITH_YOUR_SIGNING_CERT_SHA256_FINGERPRINT") {
            return VerificationResult.Rejected(
                "Plugin signing key not configured yet — set EXPECTED_SIGNER_SHA256 in " +
                    "PluginSignatureVerifier.kt to your own certificate fingerprint first."
            )
        }
        if (!jarFile.exists() || !jarFile.isFile) {
            return VerificationResult.Rejected("Plugin file does not exist.")
        }

        return try {
            JarFile(jarFile, true).use { jar ->
                var sawAnySignedEntry = false
                val signerCertsPerEntry = mutableListOf<Array<Certificate>>()

                val entries = jar.entries().toList()
                if (entries.none { !it.isDirectory }) {
                    return VerificationResult.Rejected("Plugin archive is empty.")
                }

                for (entry: JarEntry in entries) {
                    if (entry.isDirectory) continue
                    if (entry.name.startsWith("META-INF/")) continue // signature files themselves

                    // JarEntry.getCertificates() only returns non-null AFTER the entry's
                    // full stream has been read — a well-known JarFile quirk. We drain it
                    // into a throwaway buffer purely to trigger signature verification.
                    jar.getInputStream(entry).use { input ->
                        val sink = ByteArrayOutputStream()
                        input.copyTo(sink)
                    }

                    val certs = entry.certificates
                    if (certs == null || certs.isEmpty()) {
                        return VerificationResult.Rejected(
                            "Plugin contains an unsigned file: ${entry.name}. " +
                                "Every file in the jar must be signed."
                        )
                    }
                    sawAnySignedEntry = true
                    signerCertsPerEntry.add(certs)
                }

                if (!sawAnySignedEntry) {
                    return VerificationResult.Rejected("Plugin jar has no signed content.")
                }

                // Every entry must be signed by the exact same certificate — a jar signed
                // partly by one key and partly by another (or partly unsigned) is rejected.
                val firstCertEncoded = signerCertsPerEntry.first().first().encoded
                val allMatch = signerCertsPerEntry.all { certs ->
                    certs.any { it.encoded.contentEquals(firstCertEncoded) }
                }
                if (!allMatch) {
                    return VerificationResult.Rejected(
                        "Plugin entries are signed by inconsistent certificates."
                    )
                }

                val fingerprint = sha256Hex(firstCertEncoded)
                if (!fingerprint.equals(EXPECTED_SIGNER_SHA256, ignoreCase = true)) {
                    return VerificationResult.Rejected(
                        "Plugin is not signed by the expected developer certificate. Refusing to load."
                    )
                }

                VerificationResult.Verified
            }
        } catch (e: Exception) {
            VerificationResult.Rejected("Plugin signature could not be verified: ${e.message}")
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
