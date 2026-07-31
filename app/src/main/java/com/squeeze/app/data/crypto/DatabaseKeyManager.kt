package com.squeeze.app.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the SQLCipher passphrase, protected by hardware-backed key material.
 *
 * The scheme is envelope encryption:
 *
 *  1. A random 32-byte passphrase is generated once, on first launch. This is what
 *     actually encrypts the database.
 *  2. That passphrase is wrapped with an AES-GCM key held in the Android Keystore, which
 *     never leaves secure hardware where the device provides it.
 *  3. Only the wrapped blob is written to preferences.
 *
 * The alternative — deriving the database key directly from a Keystore key — is not
 * possible, because Keystore keys cannot be exported and SQLCipher needs raw bytes. The
 * envelope gets the same protection: an attacker with the preferences file still cannot
 * unwrap the passphrase without the device.
 *
 * androidx.security:security-crypto would have done this in fewer lines, but it is
 * deprecated and unmaintained, so it is not used here.
 */
@Singleton
class DatabaseKeyManager @Inject constructor(
    private val context: Context,
) {

    /**
     * Returns the database passphrase, generating and wrapping one on first call.
     *
     * @return raw passphrase bytes. Callers must zero the array once SQLCipher has taken
     *   it; see [SqueezeDatabaseFactory].
     */
    fun getOrCreatePassphrase(): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedBlob = prefs.getString(KEY_WRAPPED_PASSPHRASE, null)
        val storedIv = prefs.getString(KEY_WRAP_IV, null)

        if (storedBlob != null && storedIv != null) {
            return unwrap(
                wrapped = Base64.decode(storedBlob, Base64.NO_WRAP),
                iv = Base64.decode(storedIv, Base64.NO_WRAP),
            )
        }

        val passphrase = ByteArray(PASSPHRASE_BYTES).also { java.security.SecureRandom().nextBytes(it) }
        val (wrapped, iv) = wrap(passphrase)

        prefs.edit()
            .putString(KEY_WRAPPED_PASSPHRASE, Base64.encodeToString(wrapped, Base64.NO_WRAP))
            .putString(KEY_WRAP_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()

        return passphrase
    }

    /**
     * Destroys the wrapping key and the wrapped passphrase.
     *
     * This makes the database file permanently unreadable, which is the point: it is how
     * "delete all my data" is honoured even if a copy of the file survives somewhere. It
     * is irreversible and there is no recovery path, so callers must confirm first.
     */
    fun destroyKey() {
        keyStore().deleteEntry(KEYSTORE_ALIAS)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_WRAPPED_PASSPHRASE)
            .remove(KEY_WRAP_IV)
            .apply()
    }

    private fun wrap(passphrase: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
        return cipher.doFinal(passphrase) to cipher.iv
    }

    private fun unwrap(wrapped: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateWrappingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(wrapped)
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        val keyStore = keyStore()
        (keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // Deliberately not requiring user authentication to use this key. The app
                // gates access with BiometricPrompt at the UI layer instead, so that losing
                // a fingerprint enrolment cannot destroy years of measurements — which is
                // what setUserAuthenticationRequired would do by invalidating the key.
                .build(),
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEYSTORE_ALIAS = "squeeze_db_wrapping_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val PASSPHRASE_BYTES = 32
        const val PREFS_NAME = "squeeze_secure_prefs"
        const val KEY_WRAPPED_PASSPHRASE = "wrapped_passphrase"
        const val KEY_WRAP_IV = "wrap_iv"
    }
}
