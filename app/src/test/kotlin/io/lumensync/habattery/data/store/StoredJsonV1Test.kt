package io.lumensync.habattery.data.store

import io.lumensync.habattery.domain.model.AppearanceSettings
import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.DisplaySettings
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.MainValue
import io.lumensync.habattery.domain.model.UnderSoc
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.ThemeChoice
import io.lumensync.habattery.domain.model.TokenType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fixed samples of the v1 stored JSON. Unknown keys are ignored on read, so a renamed property
 * would silently fall back to its default: these tests make such a rename fail instead.
 */
class StoredJsonV1Test {
  /** The sample reads as [expected], and [expected] writes exactly the sample's keys and values. */
  private fun <T> assertV1(json: String, expected: T, serializer: KSerializer<T>) {
    assertEquals(expected, StoreJson.decodeFromString(serializer, json))
    assertEquals(
      Json.parseToJsonElement(json),
      Json.parseToJsonElement(StoreJson.encodeToString(serializer, expected)),
    )
  }

  @Test
  fun settingsRecord_v1() {
    val json =
      """
      {
        "schemaVersion": 1,
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
          "notifyOnFull": true,
          "capacityAh": 314.5,
          "display": { "underSoc": "AH", "mainValue": "POWER" },
          "fontPercents": { "soc": 45, "underSoc": 30, "main": 75, "cell": 60, "small": 100 },
          "homeScalePercent": 128,
          "appearance": { "theme": "DARK" }
        }
      }
      """

    val expected =
      SettingsRecord(
        schemaVersion = 1,
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
            notifyOnFull = true,
            capacityAh = 314.5,
            display = DisplaySettings(underSoc = UnderSoc.AH, mainValue = MainValue.POWER),
            fontPercents = FontPercents(soc = 45, underSoc = 30, main = 75, cell = 60, small = 100),
            homeScalePercent = 128,
            appearance = AppearanceSettings(ThemeChoice.DARK),
          ),
      )
    assertV1(json, expected, SettingsRecord.serializer())
  }

  @Test
  fun settingsWithoutTokenType_loadAsAdmin() {
    val record =
      StoreJson.decodeFromString(
        SettingsRecord.serializer(),
        """{"schemaVersion":1,"settings":{"connection":{"url":"http://ha.local:8123"}}}""",
      )
    assertEquals(TokenType.ADMIN, record.settings.connection.tokenType)
  }

  @Test
  fun removedKeys_areIgnoredOnRead() {
    val settings =
      StoreJson.decodeFromString(
        SettingsRecord.serializer(),
        """{"schemaVersion":1,"settings":{"entities":{"charge":"sensor.soc","fallbackPower":"sensor.alt","cells":["sensor.c1"]}}}""",
      )
    assertEquals(EntitySettings(charge = "sensor.soc"), settings.settings.entities)
    val cache =
      StoreJson.decodeFromString(
        CacheRecord.serializer(),
        """{"schemaVersion":1,"state":{"reading":{"chargePercent":50.0,"cellVoltagesV":[3.3],"updatedAtMillis":1}}}""",
      )
    assertEquals(BatteryReading(chargePercent = 50.0, updatedAtMillis = 1), cache.state.reading)
  }

  /** Spec 0005: the sp sizes of 0003 are dropped, so everyone starts at the design size (50 %). */
  @Test
  fun oldFontSizes_areIgnored() {
    val record =
      StoreJson.decodeFromString(
        SettingsRecord.serializer(),
        """{"schemaVersion":1,"settings":{"fontSizes":{"soc":41.0,"main":20.0,"small":11.5}}}""",
      )
    assertEquals(FontPercents(), record.settings.fontPercents)
  }

  @Test
  fun oldSettingsWithoutScale_loadWith100() {
    val record =
      StoreJson.decodeFromString(
        SettingsRecord.serializer(),
        """{"schemaVersion":1,"settings":{"fontPercents":{"soc":40}}}""",
      )
    assertEquals(100, record.settings.homeScalePercent)
  }

  @Test
  fun `settings saved by 0_1_0 still load`() {
    val record =
      StoreJson.decodeFromString(
        SettingsRecord.serializer(),
        """
        {
          "schemaVersion": 1,
          "settings": {
            "connection": { "primaryUrl": "http://ha.local:8123", "fallbackUrl": "" },
            "entities": {
              "charge": "sensor.soc",
              "power": "sensor.p",
              "fallbackPower": "sensor.fp",
              "current": "sensor.i",
              "totalVoltage": "sensor.v",
              "cells": ["sensor.c1"]
            }
          }
        }
        """,
      )
    val settings = record.settings
    assertEquals("", settings.connection.url)
    assertEquals("sensor.soc", settings.entities.charge)
    assertEquals("sensor.p", settings.entities.power)
    assertEquals("sensor.i", settings.entities.current)
    assertEquals("sensor.v", settings.entities.totalVoltage)
  }

  @Test
  fun cacheRecord_v1() {
    val json =
      """
      {
        "schemaVersion": 1,
        "state": {
          "reading": {
            "chargePercent": 54.6,
            "powerW": -207.0,
            "currentA": -7.8,
            "totalVoltageV": 26.5,
            "capacityRemainingAh": 170.5,
            "totalCapacityAh": 314.0,
            "averageCellVoltageV": 3.303,
            "deltaCellVoltageV": 0.004,
            "mosfetTemperatureC": 31.5,
            "temperaturesC": [24.0, null],
            "balancing": true,
            "updatedAtMillis": 1800000000000
          },
          "error": { "type": "http", "code": 503 },
          "refreshingSinceMillis": 1800000001000,
          "fullChargeNotified": true
        }
      }
      """

    val expected =
      CacheRecord(
        schemaVersion = 1,
        state =
          CacheState(
            reading =
              BatteryReading(
                chargePercent = 54.6,
                powerW = -207.0,
                currentA = -7.8,
                totalVoltageV = 26.5,
                capacityRemainingAh = 170.5,
                totalCapacityAh = 314.0,
                averageCellVoltageV = 3.303,
                deltaCellVoltageV = 0.004,
                mosfetTemperatureC = 31.5,
                temperaturesC = listOf(24.0, null),
                balancing = true,
                updatedAtMillis = 1_800_000_000_000L,
              ),
            error = SyncError.HttpError(503),
            refreshingSinceMillis = 1_800_000_001_000L,
            fullChargeNotified = true,
          ),
      )
    assertV1(json, expected, CacheRecord.serializer())
  }

  @Test
  fun cacheRecord_v1_errorTypeNames() {
    val errors =
      mapOf(
        """{ "type": "not_configured" }""" to SyncError.NotConfigured,
        """{ "type": "unauthorized" }""" to SyncError.Unauthorized,
        """{ "type": "unreachable" }""" to SyncError.Unreachable,
        """{ "type": "http", "code": 401 }""" to SyncError.HttpError(401),
        """{ "type": "bad_response" }""" to SyncError.BadResponse,
      )
    errors.forEach { (errorJson, expected) ->
      val json = """{ "schemaVersion": 1, "state": { "error": $errorJson } }"""
      val record = StoreJson.decodeFromString(CacheRecord.serializer(), json)
      assertEquals(errorJson, CacheRecord(1, CacheState(error = expected)), record)
    }
  }

  @Test
  fun tokenRecord_v1() {
    val json = """{ "schemaVersion": 1, "encrypted": "AAECAwQF" }"""

    assertV1(json, TokenRecord(schemaVersion = 1, encrypted = "AAECAwQF"), TokenRecord.serializer())
  }
}
