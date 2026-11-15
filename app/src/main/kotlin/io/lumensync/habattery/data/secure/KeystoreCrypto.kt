package io.lumensync.habattery.data.secure

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM with a non-exportable key in the Android Keystore. Output is `iv + ciphertext`. */
class KeystoreCrypto(private val alias: String = "habattery_token_key") : Crypto {
  override fun encrypt(plain: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    return cipher.iv + cipher.doFinal(plain)
  }

  override fun decrypt(data: ByteArray): ByteArray {
    require(data.size > IV_SIZE) { "Encrypted data is too short" }
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, existingKey(), GCMParameterSpec(TAG_BITS, data, 0, IV_SIZE))
    return cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE)
  }

  /** Decrypt never creates a key: a new key cannot open old data. */
  private fun existingKey(): SecretKey =
    lookupKey() ?: throw KeyStoreException("Key not found")

  private fun lookupKey(): SecretKey? =
    (KeyStore.getInstance(PROVIDER).apply { load(null) }.getKey(alias, null) as? SecretKey)

  @Synchronized
  private fun getOrCreateKey(): SecretKey {
    lookupKey()?.let { return it }
    val spec =
      KeyGenParameterSpec.Builder(
          alias,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
        .setKeySize(256)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setUserAuthenticationRequired(false)
        .build()
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
      .apply { init(spec) }
      .generateKey()
  }

  private companion object {
    const val PROVIDER = "AndroidKeyStore"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val IV_SIZE = 12
    const val TAG_BITS = 128
  }
}
