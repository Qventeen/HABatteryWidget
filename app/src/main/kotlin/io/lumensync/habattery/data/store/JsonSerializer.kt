package io.lumensync.habattery.data.store

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

val StoreJson = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
}

class JsonSerializer<T>(
  override val defaultValue: T,
  private val serializer: KSerializer<T>,
) : Serializer<T> {
  override suspend fun readFrom(input: InputStream): T =
    try {
      StoreJson.decodeFromString(serializer, input.readBytes().decodeToString())
    } catch (e: SerializationException) {
      throw CorruptionException("Cannot read stored JSON", e)
    } catch (e: IllegalArgumentException) {
      throw CorruptionException("Cannot read stored JSON", e)
    }

  override suspend fun writeTo(t: T, output: OutputStream) {
    output.write(StoreJson.encodeToString(serializer, t).encodeToByteArray())
  }
}
