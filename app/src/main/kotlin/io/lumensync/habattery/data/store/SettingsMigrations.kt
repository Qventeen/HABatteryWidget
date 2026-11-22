package io.lumensync.habattery.data.store

import androidx.datastore.core.DataMigration

/**
 * v1 to v2: the main switch `notifyOnFull` became `notificationsOn`. The value is already read
 * through the old name, so only the version changes; the next write stores the new key.
 */
object SettingsMigrationV2 : DataMigration<SettingsRecord> {
  override suspend fun shouldMigrate(currentData: SettingsRecord): Boolean =
    currentData.schemaVersion < 2

  override suspend fun migrate(currentData: SettingsRecord): SettingsRecord =
    currentData.copy(schemaVersion = 2)

  override suspend fun cleanUp() = Unit
}
