package io.lumensync.habattery.data.secure

import io.lumensync.habattery.data.store.JsonSerializer
import io.lumensync.habattery.data.store.TokenRecord
import io.lumensync.habattery.data.store.jsonDataStore
import io.lumensync.habattery.domain.model.TokenType
import io.lumensync.habattery.domain.model.TokenType.ADMIN
import io.lumensync.habattery.domain.model.TokenType.USER
import java.io.File
import java.security.ProviderException
import java.util.Base64
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

private object ProviderFailingCrypto : Crypto {
  override fun encrypt(plain: ByteArray) = plain

  override fun decrypt(data: ByteArray): ByteArray = throw ProviderException()
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
    store.set(ADMIN, "abc")
    assertEquals("abc", store.get(ADMIN))
    assertEquals(setOf(ADMIN), store.savedTypes.first())
  }

  @Test
  fun fileHasNoPlainToken() = runTest {
    store().set(ADMIN, "abc123")
    assertFalse(file.readText().contains("abc123"))
  }

  @Test
  fun set_trimsToken() = runTest {
    val store = store()
    store.set(ADMIN, "  abc \n")
    assertEquals("abc", store.get(ADMIN))
  }

  @Test
  fun blankToken_clears() = runTest {
    val store = store()
    store.set(ADMIN, "abc")
    store.set(ADMIN, "  ")
    assertNull(store.get(ADMIN))
    assertEquals(emptySet<TokenType>(), store.savedTypes.first())
  }

  @Test
  fun decryptFailure_clearsAndReturnsNull() = runTest {
    val s = EncryptedTokenStore(dataStore(), BrokenCrypto)
    s.set(ADMIN, "abc")
    assertNull(s.get(ADMIN))
    assertEquals(emptySet<TokenType>(), s.savedTypes.first())
  }

  @Test
  fun providerFailure_clearsAndReturnsNull() = runTest {
    val s = EncryptedTokenStore(dataStore(), ProviderFailingCrypto)
    s.set(ADMIN, "abc")
    assertNull(s.get(ADMIN))
    assertEquals(emptySet<TokenType>(), s.savedTypes.first())
  }

  @Test
  fun invalidBase64_clearsAndReturnsNull() = runTest {
    val ds = dataStore()
    ds.updateData { it.copy(encrypted = "***not base64***") }
    val s = EncryptedTokenStore(ds, FlipCrypto)
    assertNull(s.get(ADMIN))
    assertEquals(emptySet<TokenType>(), s.savedTypes.first())
  }

  @Test
  fun clear_removesToken() = runTest {
    val store = store()
    store.set(ADMIN, "abc")
    store.clear(ADMIN)
    assertNull(store.get(ADMIN))
  }

  @Test
  fun oldRecord_isAdminSlot() = runTest {
    val encoded = Base64.getEncoder().encodeToString(FlipCrypto.encrypt("abc".encodeToByteArray()))
    file.writeText("""{"schemaVersion":1,"encrypted":"$encoded"}""")
    val store = store()
    assertEquals("abc", store.get(ADMIN))
    assertNull(store.get(USER))
    assertEquals(setOf(ADMIN), store.savedTypes.first())
  }

  @Test
  fun slots_areIndependent() = runTest {
    val store = store()
    store.set(ADMIN, "admin-t")
    store.set(USER, "user-t")
    assertEquals("admin-t", store.get(ADMIN))
    assertEquals("user-t", store.get(USER))
    assertEquals(setOf(ADMIN, USER), store.savedTypes.first())
    store.clear(USER)
    assertEquals("admin-t", store.get(ADMIN))
    assertNull(store.get(USER))
    assertEquals(setOf(ADMIN), store.savedTypes.first())
  }

  @Test
  fun unreadableSlot_clearsOnlyThatSlot() = runTest {
    val ds = dataStore()
    val store = EncryptedTokenStore(ds, FlipCrypto)
    store.set(ADMIN, "admin-t")
    ds.updateData { it.copy(userEncrypted = "***not base64***") }
    assertNull(store.get(USER))
    assertEquals(setOf(ADMIN), store.savedTypes.first())
    assertEquals("admin-t", store.get(ADMIN))
  }
}
