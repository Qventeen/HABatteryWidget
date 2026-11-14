package io.lumensync.habattery.data.secure

import androidx.datastore.core.DataStore
import io.lumensync.habattery.data.store.TokenRecord
import io.lumensync.habattery.domain.port.TokenStore
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Keeps the HA token encrypted in a DataStore file. The token never appears in errors or logs. */
class EncryptedTokenStore(
  private val store: DataStore<TokenRecord>,
  private val crypto: Crypto,
) : TokenStore {
  override val hasToken: Flow<Boolean> = store.data.map { it.encrypted != null }

  override suspend fun get(): String? {
    val encoded = store.data.first().encrypted ?: return null
    return try {
      crypto.decrypt(Base64.getDecoder().decode(encoded)).decodeToString()
    } catch (_: GeneralSecurityException) {
      clear()
      null
    } catch (_: ProviderException) {
      clear()
      null
    } catch (_: IllegalArgumentException) {
      clear()
      null
    }
  }

  override suspend fun set(token: String) {
    val trimmed = token.trim()
    if (trimmed.isEmpty()) return clear()
    val encoded = Base64.getEncoder().encodeToString(crypto.encrypt(trimmed.encodeToByteArray()))
    store.updateData { it.copy(encrypted = encoded) }
  }

  override suspend fun clear() {
    store.updateData { it.copy(encrypted = null) }
  }
}
