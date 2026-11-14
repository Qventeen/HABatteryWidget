package io.lumensync.habattery.data.secure

/** Name of the DataStore file that holds the encrypted token. */
const val SECURE_FILE = "secure_settings.json"

interface Crypto {
  fun encrypt(plain: ByteArray): ByteArray

  fun decrypt(data: ByteArray): ByteArray
}
