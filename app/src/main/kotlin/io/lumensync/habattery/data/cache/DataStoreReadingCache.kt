package io.lumensync.habattery.data.cache

import androidx.datastore.core.DataStore
import io.lumensync.habattery.data.store.CacheRecord
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.port.ReadingCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreReadingCache(private val store: DataStore<CacheRecord>) : ReadingCache {
  override val state: Flow<CacheState> = store.data.map { it.state }

  override suspend fun update(transform: (CacheState) -> CacheState) {
    store.updateData { it.copy(state = transform(it.state)) }
  }
}
