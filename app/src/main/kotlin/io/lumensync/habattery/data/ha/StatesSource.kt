package io.lumensync.habattery.data.ha

import io.lumensync.habattery.data.store.StoreJson
import io.lumensync.habattery.domain.Validation
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.SensorList
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.SyncResult
import io.lumensync.habattery.domain.port.BatterySource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient

/**
 * Reads the battery with a non-admin token (spec 0009): HA refuses `POST /api/template` to it, so
 * each configured entity is one `GET /api/states/<id>`. The reading follows the template path's
 * rules: a missing entity or a state that is not a number gives no value.
 */
class StatesSource(
  client: OkHttpClient,
  private val clock: () -> Long = System::currentTimeMillis,
) : BatterySource {
  private val http = HaHttp(client)

  override suspend fun fetch(
    connection: ConnectionSettings,
    entities: EntitySettings,
    token: String,
  ): SyncResult {
    val states =
      try {
        readStates(connection, entities.ids(), token)
      } catch (e: StateFailure) {
        return SyncResult.Failure(e.error)
      }
    fun number(id: String) = states[id]?.trim()?.toDoubleOrNull()
    val reading =
      TemplateResponse(
          soc = number(entities.charge),
          power = number(entities.power),
          current = number(entities.current),
          totalV = number(entities.totalVoltage),
          ahLeft = number(entities.capacityRemaining),
          capacity = number(entities.totalCapacity),
          avgCell = number(entities.averageCellVoltage),
          delta = number(entities.deltaCellVoltage),
          mosfet = number(entities.mosfetTemperature),
          temps = entities.temperatures.map(::number),
          balancing = states[entities.balancing],
        )
        .toReading(clock())
    return if (reading == null) SyncResult.Failure(SyncError.BadResponse)
    else SyncResult.Success(reading)
  }

  override suspend fun listTemperatureSensors(
    connection: ConnectionSettings,
    prefix: String,
    token: String,
  ): SensorList {
    val p = prefix.trim().trimEnd('_')
    if (!TemplateBuilder.isObjectIdPrefix(p)) return SensorList.Found(emptyList())
    // HA answers with every entity it has, so this one call gets more time than a state read.
    val search = http.withCallTimeout(SEARCH_TIMEOUT_SECONDS)
    return when (val reply = search.get(connection, listOf("api", "states"), token)) {
      is HaReply.Failure -> SensorList.Failed(reply.error)
      is HaReply.Body -> {
        val ids =
          try {
            StoreJson.decodeFromString<List<StateId>>(reply.text).mapNotNull { it.entityId }
          } catch (_: SerializationException) {
            return SensorList.Failed(SyncError.BadResponse)
          } catch (_: IllegalArgumentException) {
            return SensorList.Failed(SyncError.BadResponse)
          }
        SensorList.Found(TemperatureSensors.select(ids, p))
      }
    }
  }

  /**
   * Blank and malformed ids get no request and so no value, as on the template path. The check also
   * keeps `.` and `..` out of the URL path.
   */
  private fun EntitySettings.ids(): List<String> =
    (listOf(
        charge,
        totalVoltage,
        current,
        power,
        capacityRemaining,
        totalCapacity,
        averageCellVoltage,
        deltaCellVoltage,
        mosfetTemperature,
        balancing,
      ) + temperatures)
      .filter(Validation::isEntityId)
      .distinct()

  /**
   * The state text of each id; null when HA has no such entity (404) or the entity has no state.
   *
   * @throws StateFailure on any other failure. It cancels the other requests, running or waiting,
   *   so the first failure ends the sync.
   */
  private suspend fun readStates(
    connection: ConnectionSettings,
    ids: List<String>,
    token: String,
  ): Map<String, String?> {
    // Holds one sync to 4 open requests (connections and TLS handshakes). OkHttp's per-host limit
    // does not do this: it is client-wide and 5 by default.
    val gate = Semaphore(MAX_PARALLEL)
    return coroutineScope {
        ids
          .map { id -> async { id to gate.withPermit { state(connection, id, token) } } }
          .awaitAll()
      }
      .toMap()
  }

  private suspend fun state(connection: ConnectionSettings, id: String, token: String): String? =
    when (val reply = http.get(connection, listOf("api", "states", id), token)) {
      is HaReply.Body -> decode(reply.text).state
      is HaReply.Failure ->
        if (reply.error == SyncError.HttpError(404)) null else throw StateFailure(reply.error)
    }

  private fun decode(text: String): StateReply =
    try {
      StoreJson.decodeFromString<StateReply>(text)
    } catch (_: SerializationException) {
      throw StateFailure(SyncError.BadResponse)
    } catch (_: IllegalArgumentException) {
      throw StateFailure(SyncError.BadResponse)
    }

  private class StateFailure(val error: SyncError) : Exception()

  private companion object {
    const val MAX_PARALLEL = 4
    const val SEARCH_TIMEOUT_SECONDS = 30L
  }
}

/** The part of HA's state object the reading needs; the rest is ignored. */
@Serializable internal data class StateReply(val state: String? = null)

/** The part of one item of HA's state list the sensor search needs. */
@Serializable internal data class StateId(@SerialName("entity_id") val entityId: String? = null)
