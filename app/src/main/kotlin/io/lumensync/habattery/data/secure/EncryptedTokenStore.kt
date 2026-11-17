package io.lumensync.habattery.data.secure

import androidx.datastore.core.DataStore
import io.lumensync.habattery.data.store.TokenRecord
import io.lumensync.habattery.domain.port.TokenStore
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Keeps the HA token encrypted in a DataStore file. The token never appears in errors or logs. */
class EncryptedTokenStore(
  private val store: DataStore<TokenRecord>,
  private val crypto: Crypto,
) : TokenStore {
  override val hasToken: Flow<Boolean> = store.data.map { it.encrypted != null }

  override suspend fun get(): String? {
    val encoded = store.data.first().encrypted ?: return null
    // A token that cannot be read (lost key, damaged data) is removed; the user enters it again.
    return withContext(Dispatchers.IO) { decryptOrNull(encoded) } ?: run {
      clear()
      null
    }
  }

  override suspend fun set(token: String) {
    val trimmed = token.trim()
    if (trimmed.isEmpty()) return clear()
    val encoded =
      withContext(Dispatchers.IO) {
        Base64.getEncoder().encodeToString(crypto.encrypt(trimmed.encodeToByteArray()))
      }
    store.updateData { it.copy(encrypted = encoded) }
  }

  override suspend fun clear() {
    store.updateData { it.copy(encrypted = null) }
  }

  /** Keystore work: call it off the main thread. Null when the stored token cannot be read. */
  private fun decryptOrNull(encoded: String): String? =
    try {
      crypto.decrypt(Base64.getDecoder().decode(encoded)).decodeToString()
    } catch (_: GeneralSecurityException) {
      null
    } catch (_: ProviderException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }
}
