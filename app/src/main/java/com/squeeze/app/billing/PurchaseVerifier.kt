package com.squeeze.app.billing

import android.util.Base64
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Verifies that a purchase really was signed by Google Play, on-device.
 *
 * Google's recommended flow verifies purchases server-side through the Play Developer API.
 * This app has no server, so verification happens here against the public key from the
 * Play Console. Every purchase response carries an RSA signature over its JSON payload,
 * and checking it locally catches replayed or fabricated purchase data.
 *
 * What it does not defend against is a modified APK: an attacker who can rewrite the
 * embedded key can bypass this entirely. That is an accepted limitation, not an oversight.
 * The only real fix is server-side verification, which would mean running a server — the
 * one thing this app's architecture exists to avoid. Since nothing behind the paywall is
 * sensitive, the exposure is lost revenue from users who were never going to pay, and
 * R8 obfuscation in release builds raises the effort enough for that population.
 */
object PurchaseVerifier {

    private const val ALGORITHM = "RSA"
    private const val SIGNATURE_ALGORITHM = "SHA1withRSA"

    /**
     * @param base64PublicKey the licensing key from the Play Console. An empty value means
     *   the build was not configured for release; see [isConfigured].
     * @param signedData the raw `originalJson` from the purchase
     * @param signature the purchase's `signature` field
     * @return true only when the signature verifies. Any malformed input returns false
     *   rather than throwing, so a corrupt purchase record cannot crash the app on launch.
     */
    fun verify(base64PublicKey: String, signedData: String, signature: String): Boolean {
        if (!isConfigured(base64PublicKey) || signedData.isEmpty() || signature.isEmpty()) {
            return false
        }

        return try {
            val key = parsePublicKey(base64PublicKey)
            Signature.getInstance(SIGNATURE_ALGORITHM).run {
                initVerify(key)
                update(signedData.toByteArray(Charsets.UTF_8))
                verify(Base64.decode(signature, Base64.DEFAULT))
            }
        } catch (e: Exception) {
            // Covers malformed keys, bad Base64 and signature provider failures alike.
            // A verification failure and an unparseable input mean the same thing here:
            // this purchase is not trustworthy.
            false
        }
    }

    fun isConfigured(base64PublicKey: String): Boolean = base64PublicKey.isNotBlank()

    private fun parsePublicKey(base64PublicKey: String): PublicKey {
        val decoded = Base64.decode(base64PublicKey, Base64.DEFAULT)
        return KeyFactory.getInstance(ALGORITHM).generatePublic(X509EncodedKeySpec(decoded))
    }
}
