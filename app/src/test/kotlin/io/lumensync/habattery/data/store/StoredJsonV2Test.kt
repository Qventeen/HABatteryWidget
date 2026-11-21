package io.lumensync.habattery.data.store

import io.lumensync.habattery.domain.model.AppearanceSettings
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.DisplaySettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.MainValue
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.ThemeChoice
import io.lumensync.habattery.domain.model.TokenType
import io.lumensync.habattery.domain.model.UnderSoc
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** A fixed sample of the v2 stored settings JSON: the rename of the main switch must not slip back. */
class StoredJsonV2Test {
  /** The sample reads as [expected], and [expected] writes exactly the sample's keys and values. */
  private fun <T> assertV2(json: String, expected: T, serializer: KSerializer<T>) {
    assertEquals(expected, StoreJson.decodeFromString(serializer, json))
    assertEquals(
      Json.parseToJsonElement(json),
      Json.parseToJsonElement(StoreJson.encodeToString(serializer, expected)),
    )
  }

  @Test
  fun settingsRecord_v2() {
    val json =
      """
      {
        "schemaVersion": 2,
        "settings": {
          "connection": {
            "url": "http://ha.local:8123",
            "tokenType": "USER"
          },
          "entities": {
            "prefix": "bps_bms",
            "charge": "sensor.soc",
            "totalVoltage": "sensor.voltage",
            "current": "sensor.current",
            "power": "sensor.power",
            "capacityRemaining": "sensor.ah",
            "totalCapacity": "sensor.cap",
            "averageCellVoltage": "sensor.avg",
            "deltaCellVoltage": "sensor.delta",
            "mosfetTemperature": "sensor.mosfet",
            "temperatures": ["sensor.t1", "sensor.t2"],
            "balancing": "sensor.bal"
          },
          "refresh": { "auto": true, "intervalMinutes": 30 },
          "notificationsOn": true,
          "capacityAh": 314.5,
          "display": { "underSoc": "AH", "mainValue": "POWER" },
          "fontPercents": { "soc": 45, "underSoc": 30, "main": 75, "cell": 60, "small": 100 },
          "homeScalePercent": 128,
          "appearance": { "theme": "DARK" },
          "colorLevels": {}
        }
      }
      """

    val expected =
      SettingsRecord(
        schemaVersion = 2,
        settings =
          Settings(
            connection =
              ConnectionSettings(url = "http://ha.local:8123", tokenType = TokenType.USER),
            entities =
              EntitySettings(
                prefix = "bps_bms",
                charge = "sensor.soc",
                totalVoltage = "sensor.voltage",
                current = "sensor.current",
                power = "sensor.power",
                capacityRemaining = "sensor.ah",
                totalCapacity = "sensor.cap",
                averageCellVoltage = "sensor.avg",
                deltaCellVoltage = "sensor.delta",
                mosfetTemperature = "sensor.mosfet",
                temperatures = listOf("sensor.t1", "sensor.t2"),
                balancing = "sensor.bal",
              ),
            refresh = RefreshSettings(auto = true, intervalMinutes = 30),
            notificationsOn = true,
            capacityAh = 314.5,
            display = DisplaySettings(underSoc = UnderSoc.AH, mainValue = MainValue.POWER),
            fontPercents = FontPercents(soc = 45, underSoc = 30, main = 75, cell = 60, small = 100),
            homeScalePercent = 128,
            appearance = AppearanceSettings(ThemeChoice.DARK),
          ),
      )
    assertV2(json, expected, SettingsRecord.serializer())
  }
}
