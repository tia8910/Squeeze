package com.squeeze.app.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores scan photographs, encrypted, in the app's private storage.
 *
 * The photographs are the most sensitive thing this app touches — more so than the numbers
 * derived from them — so they get the same protection the measurement database gets rather
 * than being dropped in a folder as plain JPEGs. A key in the Android Keystore encrypts each
 * file with AES-GCM; the key material never leaves the secure hardware, so the files are
 * unreadable if the storage is recovered from a lost device.
 *
 * They live in `filesDir`, which is private to the app and excluded from the media store, so
 * they never appear in the gallery and no other app can read them. `allowBackup="false"` in
 * the manifest keeps them out of cloud backups too.
 *
 * Photos are deleted with the measurement they belong to. There is no orphan sweep because
 * there is no path that creates one: the file is written before the row and removed with it.
 */
@Singleton
class ScanPhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    /**
     * Encrypts and writes [bitmap], returning the id to store on the measurement.
     *
     * Returns null on failure rather than throwing. A photograph that cannot be saved must
     * not cost the user the measurement it came from — the numbers are the point, the image
     * is a record of how they were obtained.
     */
    fun save(bitmap: Bitmap): String? = runCatching {
        val id = UUID.randomUUID().toString()

        // Re-encoded rather than stored raw. A modern phone camera frame is several
        // megabytes and nothing here needs that: the measurement was already taken, and what
        // remains is a visual record the user looks back at on a phone screen.
        val jpeg = java.io.ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            stream.toByteArray()
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        File(directory, id).outputStream().use { out ->
            // The IV is generated per file by the cipher and prefixed to the payload. It is
            // not secret, and reusing one across files with the same key would be the
            // classic GCM failure.
            out.write(cipher.iv.size)
            out.write(cipher.iv)
            out.write(cipher.doFinal(jpeg))
        }

        id
    }.getOrNull()

    /** Decrypts a stored photograph, or null when it is missing or unreadable. */
    fun load(id: String): Bitmap? = runCatching {
        val file = File(directory, id)
        if (!file.exists()) return null

        file.inputStream().use { input ->
            val ivLength = input.read()
            if (ivLength <= 0) return null

            val iv = ByteArray(ivLength)
            if (input.read(iv) != ivLength) return null

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))

            val plain = cipher.doFinal(input.readBytes())
            BitmapFactory.decodeByteArray(plain, 0, plain.size)
        }
    }.getOrNull()

    /** Removes a stored photograph. Safe to call for an id that no longer exists. */
    fun delete(id: String) {
        runCatching { File(directory, id).delete() }
    }

    /**
     * The wrapping key, created on first use.
     *
     * Deliberately not requiring user authentication for each use. The app already gates
     * itself behind a biometric prompt at launch, and a per-operation requirement would make
     * the history screen prompt for a fingerprint on every scroll.
     */
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.secretKey
            ?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val DIRECTORY = "scan_photos"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEYSTORE_ALIAS = "squeeze_photo_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val JPEG_QUALITY = 85
    }
}
