package io.lumensync.habattery.data.store

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import java.io.File
import kotlinx.coroutines.CoroutineScope

/** A corrupt file is replaced by the serializer's default value. */
fun <T> jsonDataStore(
  serializer: JsonSerializer<T>,
  scope: CoroutineScope,
  migrations: List<DataMigration<T>> = emptyList(),
  file: () -> File,
): DataStore<T> =
  DataStoreFactory.create(
    serializer = serializer,
    corruptionHandler = ReplaceFileCorruptionHandler { serializer.defaultValue },
    migrations = migrations,
    scope = scope,
    produceFile = file,
  )

/** The settings store, with every settings migration. */
fun settingsDataStore(scope: CoroutineScope, file: () -> File): DataStore<SettingsRecord> =
  jsonDataStore(
    JsonSerializer(SettingsRecord(), SettingsRecord.serializer()),
    scope,
    listOf(SettingsMigrationV2),
    file,
  )
