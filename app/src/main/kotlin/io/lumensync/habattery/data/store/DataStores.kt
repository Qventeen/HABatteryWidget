package io.lumensync.habattery.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import java.io.File
import kotlinx.coroutines.CoroutineScope

/** A corrupt file is replaced by the serializer's default value. */
fun <T> jsonDataStore(
  serializer: JsonSerializer<T>,
  scope: CoroutineScope,
  file: () -> File,
): DataStore<T> =
  DataStoreFactory.create(
    serializer = serializer,
    corruptionHandler = ReplaceFileCorruptionHandler { serializer.defaultValue },
    migrations = emptyList(),
    scope = scope,
    produceFile = file,
  )
