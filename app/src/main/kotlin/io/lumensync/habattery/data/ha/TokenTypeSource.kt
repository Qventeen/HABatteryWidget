package io.lumensync.habattery.data.ha

import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.SensorList
import io.lumensync.habattery.domain.model.SyncResult
import io.lumensync.habattery.domain.model.TokenType
import io.lumensync.habattery.domain.port.BatterySource

/** Sends each call to the source that matches the connection's token type. */
class TokenTypeSource(private val admin: BatterySource, private val user: BatterySource) :
  BatterySource {

  override suspend fun fetch(
    connection: ConnectionSettings,
    entities: EntitySettings,
    token: String,
  ): SyncResult = of(connection).fetch(connection, entities, token)

  override suspend fun listTemperatureSensors(
    connection: ConnectionSettings,
    prefix: String,
    token: String,
  ): SensorList = of(connection).listTemperatureSensors(connection, prefix, token)

  private fun of(connection: ConnectionSettings): BatterySource =
    when (connection.tokenType) {
      TokenType.ADMIN -> admin
      TokenType.USER -> user
    }
}
