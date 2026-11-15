package io.lumensync.habattery.data.secure

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.GeneralSecurityException
import java.security.KeyStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreCryptoTest {
  private val crypto = KeystoreCrypto("habattery_test_key")
  private val plain = "token-123".encodeToByteArray()

  @Test
  fun roundTripEqualsInput() {
    assertArrayEquals(plain, crypto.decrypt(crypto.encrypt(plain)))
  }

  @Test
  fun twoEncryptionsDiffer() {
    assertFalse(crypto.encrypt(plain).contentEquals(crypto.encrypt(plain)))
  }

  @Test
  fun changedByteFailsToDecrypt() {
    val data = crypto.encrypt(plain)
    data[data.size - 1] = (data[data.size - 1].toInt() xor 1).toByte()
    try {
      crypto.decrypt(data)
      fail("Expected GeneralSecurityException")
    } catch (_: GeneralSecurityException) {}
  }

  @Test
  fun missingKeyFailsToDecrypt() {
    val blob = crypto.encrypt(plain)
    KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("habattery_test_key")
    try {
      crypto.decrypt(blob)
      fail("Expected GeneralSecurityException")
    } catch (_: GeneralSecurityException) {}
  }
}
