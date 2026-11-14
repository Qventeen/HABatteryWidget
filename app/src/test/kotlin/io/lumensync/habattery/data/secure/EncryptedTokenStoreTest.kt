package io.lumensync.habattery.data.secure

import io.lumensync.habattery.data.store.JsonSerializer
import io.lumensync.habattery.data.store.TokenRecord
import io.lumensync.habattery.data.store.jsonDataStore
import java.io.File
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private object FlipCrypto : Crypto {
  override fun encrypt(plain: ByteArray) = flip(plain)

  override fun decrypt(data: ByteArray) = flip(data)

  private fun flip(b: ByteArray) =
    b.reversedArray().map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
}

private object BrokenCrypto : Crypto {
  override fun encrypt(plain: ByteArray) = plain

  override fun decrypt(data: ByteArray): ByteArray = throw AEADBadTagException()
}

class EncryptedTokenStoreTest {
  @get:Rule val tmp = TemporaryFolder()

  private val file: File by lazy { File(tmp.root, SECURE_FILE) }

  private fun TestScope.dataStore() =
    jsonDataStore(JsonSerializer(TokenRecord(), TokenRecord.serializer()), backgroundScope, { file })

  private fun TestScope.store() = EncryptedTokenStore(dataStore(), FlipCrypto)

  @Test
  fun setThenGet() = runTest {
    val store = store()
    store.set("abc")
    assertEquals("abc", store.get())
    assertTrue(store.hasToken.first())
  }

  @Test
  fun fileHasNoPlainToken() = runTest {
    store().set("abc123")
    assertFalse(file.readText().contains("abc123"))
  }

  @Test
  fun set_trimsToken() = runTest {
    val store = store()
    store.set("  abc \n")
    assertEquals("abc", store.get())
  }

  @Test
  fun blankToken_clears() = runTest {
    val store = store()
    store.set("abc")
    store.set("  ")
    assertNull(store.get())
    assertFalse(store.hasToken.first())
  }

  @Test
  fun decryptFailure_clearsAndReturnsNull() = runTest {
    val s = EncryptedTokenStore(dataStore(), BrokenCrypto)
    s.set("abc")
    assertNull(s.get())
    assertFalse(s.hasToken.first())
  }

  @Test
  fun invalidBase64_clearsAndReturnsNull() = runTest {
    val ds = dataStore()
    ds.updateData { it.copy(encrypted = "***not base64***") }
    val s = EncryptedTokenStore(ds, FlipCrypto)
    assertNull(s.get())
    assertFalse(s.hasToken.first())
  }

  @Test
  fun clear_removesToken() = runTest {
    val store = store()
    store.set("abc")
    store.clear()
    assertNull(store.get())
  }
}
