package com.nthb.kibooz.data.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [ValueCipher] dengan AES-256-GCM. Kuncinya dibuat dan disimpan di Android Keystore sehingga tidak bisa
 * dibaca keluar dari perangkat. Hasil enkripsi berupa IV 12 byte diikuti ciphertext, dalam Base64.
 */
class KeystoreValueCipher(private val alias: String = DEFAULT_ALIAS) : ValueCipher {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @Synchronized
    override fun encrypt(plain: String): String = try {
        seal(plain)
    } catch (e: InvalidKeyException) {
        // Kunci tidak bisa dipakai lagi, misalnya karena kunci layar diganti. Buat baru lalu coba sekali lagi
        keyStore.deleteEntry(alias)
        seal(plain)
    }

    @Synchronized
    override fun decrypt(encrypted: String): String = try {
        val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
        require(bytes.size > IV_BYTES) { "Nilai terlalu pendek" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    } catch (e: GeneralSecurityException) {
        throw UndecryptableException(e)
    } catch (e: IllegalArgumentException) {
        throw UndecryptableException(e)
    }

    private fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun key(): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "kibooz_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
