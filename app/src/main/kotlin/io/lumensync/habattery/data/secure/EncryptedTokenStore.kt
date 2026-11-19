package io.lumensync.habattery.data.secure

import androidx.datastore.core.DataStore
import io.lumensync.habattery.data.store.TokenRecord
import io.lumensync.habattery.domain.model.TokenType
import io.lumensync.habattery.domain.port.TokenStore
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Keeps the HA tokens encrypted in a DataStore file, one slot per token type, both under the same
 * key. The tokens never appear in errors or logs.
 */
class EncryptedTokenStore(
  private val store: DataStore<TokenRecord>,
  private val crypto: Crypto,
) : TokenStore {
  override val savedTypes: Flow<Set<TokenType>> =
    store.data.map { record ->
      TokenType.entries.filterTo(mutableSetOf()) { record.slot(it) != null }
    }

  override suspend fun get(type: TokenType): String? {
    val encoded = store.data.first().slot(type) ?: return null
    // A token that cannot be read (lost key, damaged data) is removed; the user enters it again.
    return withContext(Dispatchers.IO) { decryptOrNull(encoded) } ?: run {
      clear(type)
      null
    }
  }

  override suspend fun set(type: TokenType, token: String) {
    val trimmed = token.trim()
    if (trimmed.isEmpty()) return clear(type)
    val encoded =
      withContext(Dispatchers.IO) {
        Base64.getEncoder().encodeToString(crypto.encrypt(trimmed.encodeToByteArray()))
      }
    store.updateData { it.withSlot(type, encoded) }
  }

  override suspend fun clear(type: TokenType) {
    store.updateData { it.withSlot(type, null) }
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

  private fun TokenRecord.slot(type: TokenType): String? =
    when (type) {
      TokenType.ADMIN -> encrypted
      TokenType.USER -> userEncrypted
    }

  private fun TokenRecord.withSlot(type: TokenType, encoded: String?): TokenRecord =
    when (type) {
      TokenType.ADMIN -> copy(encrypted = encoded)
      TokenType.USER -> copy(userEncrypted = encoded)
    }
}
