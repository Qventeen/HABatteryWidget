package io.lumensync.habattery.data.store

import io.lumensync.habattery.domain.model.AppearanceSettings
import io.lumensync.habattery.domain.model.BatteryType
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.DisplaySettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.InverterEntitySettings
import io.lumensync.habattery.domain.model.InverterFontPercents
import io.lumensync.habattery.domain.model.InverterSettings
import io.lumensync.habattery.domain.model.MainValue
import io.lumensync.habattery.domain.model.ModeStyle
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.IconSlot
import io.lumensync.habattery.domain.model.InverterIcon
import io.lumensync.habattery.domain.model.icon
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.TextValue
import io.lumensync.habattery.domain.model.customTextLevels
import io.lumensync.habattery.domain.model.ThemeChoice
import io.lumensync.habattery.domain.model.TokenType
import io.lumensync.habattery.domain.model.UnderSoc
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
          "refresh": { "auto": true, "intervalMinutes": 30, "onUnlock": true },
          "notificationsOn": true,
          "capacityAh": 314.5,
          "batteryType": "LTO",
          "cellCount": 22,
          "display": { "underSoc": "AH", "mainValue": "POWER" },
          "fontPercents": { "soc": 45, "underSoc": 30, "main": 75, "cell": 60, "small": 100 },
          "homeScalePercent": 128,
          "appearance": { "theme": "DARK" },
          "colorLevels": {},
          "textLevels": {},
          "inverter": {
            "entities": {
              "prefix": "bps_inverter",
              "mode": "sensor.mode",
              "gridVoltage": "sensor.grid",
              "outputVoltage": "sensor.out"
            },
            "modeStyle": "ICON_WORD",
            "icons": { "GRID_VOLTAGE": "HOME" },
            "fontPercents": { "word": 60, "value": 40 }
          }
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
            batteryType = BatteryType.LTO,
            cellCount = 22,
            display = DisplaySettings(underSoc = UnderSoc.AH, mainValue = MainValue.POWER),
            fontPercents = FontPercents(soc = 45, underSoc = 30, main = 75, cell = 60, small = 100),
            homeScalePercent = 128,
            appearance = AppearanceSettings(ThemeChoice.DARK),
            inverter =
              InverterSettings(
                entities =
                  InverterEntitySettings(
                    prefix = "bps_inverter",
                    mode = "sensor.mode",
                    gridVoltage = "sensor.grid",
                    outputVoltage = "sensor.out",
                  ),
                modeStyle = ModeStyle.ICON_WORD,
                icons = mapOf("GRID_VOLTAGE" to "HOME"),
                fontPercents = InverterFontPercents(word = 60, value = 40),
              ),
          ),
      )
    assertV2(json, expected, SettingsRecord.serializer())
  }

  @Test
  fun refreshWithoutOnUnlock_readsOn() {
    val json =
      """{"schemaVersion":2,"settings":{"refresh":{"auto":true,"intervalMinutes":30}}}"""
    val settings = StoreJson.decodeFromString(SettingsRecord.serializer(), json).settings
    assertEquals(RefreshSettings(true, 30, onUnlock = true), settings.refresh)
  }

  @Test
  fun settingsWithoutInverter_decodeToDefaults() {
    val json = """{"schemaVersion":2,"settings":{"notificationsOn":true}}"""
    val record = StoreJson.decodeFromString(SettingsRecord.serializer(), json)
    assertEquals(InverterSettings(), record.settings.inverter)
  }

  @Test
  fun settingsWithoutPack_decodeToNull() {
    val json = """{"schemaVersion":2,"settings":{"notificationsOn":true}}"""
    val settings = StoreJson.decodeFromString(SettingsRecord.serializer(), json).settings
    assertNull(settings.batteryType)
    assertNull(settings.cellCount)
  }

  @Test
  fun settingsWithUnknownBatteryType_readsNull() {
    val json =
      """{"schemaVersion":2,"settings":{"notificationsOn":true,"batteryType":"SODIUM","cellCount":16}}"""
    val settings = StoreJson.decodeFromString(SettingsRecord.serializer(), json).settings
    assertNull(settings.batteryType)
    assertEquals(16, settings.cellCount)
    assertTrue(settings.notificationsOn)
  }

  @Test
  fun settingsWithoutTextLevels_decodeToEmpty() {
    val json = """{"schemaVersion":2,"settings":{"notificationsOn":true}}"""
    val settings = StoreJson.decodeFromString(SettingsRecord.serializer(), json).settings
    assertTrue(settings.textLevels.isEmpty())
  }

  @Test
  fun textLevelUnknownKeys_readSafely() {
    val json =
      """{"schemaVersion":2,"settings":{"textLevels":{"MODE":{"on":true,"levels":[""" +
        """{"id":1,"texts":["Eco"],"defaultName":"SOLAR","icon":"NEW_ICON","type":"URGENT"}]}}}}"""
    val level =
      StoreJson.decodeFromString(SettingsRecord.serializer(), json)
        .settings
        .customTextLevels(TextValue.MODE)
        .levels
        .single()
    assertNull(level.defaultName)
    assertEquals(InverterIcon.HELP, level.shownIcon)
    assertEquals(LevelType.NORMAL, level.type)
  }

  @Test
  fun settingsWithOldModeIconKeys_load() {
    val json =
      """{"schemaVersion":2,"settings":{"inverter":""" +
        """{"icons":{"MODE_MAINS":"FACTORY","GRID_VOLTAGE":"HOME"}}}}"""
    val inverter = StoreJson.decodeFromString(SettingsRecord.serializer(), json).settings.inverter
    assertEquals(InverterIcon.HOME, inverter.icon(IconSlot.GRID_VOLTAGE))
  }
}
