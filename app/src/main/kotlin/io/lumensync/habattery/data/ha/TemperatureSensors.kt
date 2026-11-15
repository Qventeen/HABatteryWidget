package io.lumensync.habattery.data.ha

import io.lumensync.habattery.data.store.StoreJson
import io.lumensync.habattery.domain.model.EntitySettings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/** The sensor-list answer: a JSON list of entity ids, sorted by sensor number. */
internal object TemperatureSensors {
  /** The pattern HA matches against each `sensor.*` id: one dot class, digits, end of id. */
  fun pattern(prefix: String): String = "sensor[.]${prefix}_temperature_sensor_[0-9]+$"

  /** Null when the text is not a JSON list of strings. */
  fun parse(text: String, prefix: String): List<String>? {
    val ids =
      try {
        StoreJson.decodeFromString(ListSerializer(String.serializer()), text)
      } catch (_: SerializationException) {
        return null
      } catch (_: IllegalArgumentException) {
        return null
      }
    val match = Regex("sensor\\.${Regex.escape(prefix)}_temperature_sensor_([0-9]+)")
    return ids
      .mapNotNull { id -> match.matchEntire(id)?.groupValues?.get(1)?.toLongOrNull()?.let { it to id } }
      .distinctBy { it.second }
      .sortedBy { it.first }
      .map { it.second }
      .take(EntitySettings.MAX_TEMPERATURES)
  }
}
