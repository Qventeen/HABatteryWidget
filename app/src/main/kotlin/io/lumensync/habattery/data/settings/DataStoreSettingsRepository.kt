package io.lumensync.habattery.data.settings

import androidx.datastore.core.DataStore
import io.lumensync.habattery.data.store.SettingsRecord
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.clamped
import io.lumensync.habattery.domain.port.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(private val store: DataStore<SettingsRecord>) :
  SettingsRepository {
  /** Font percents and the home-screen scale out of range (an edited file) are clamped here, so no reader sees them. */
  override val settings: Flow<Settings> =
    store.data.map { it.settings.clamped() }

  override suspend fun update(transform: (Settings) -> Settings) {
    store.updateData { it.copy(settings = transform(it.settings)) }
  }
}
