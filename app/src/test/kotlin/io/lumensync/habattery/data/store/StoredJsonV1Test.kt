package io.lumensync.habattery.data.store

import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
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
            "primaryUrl": "http://ha.local:8123",
            "fallbackUrl": "https://ha.example.com"
          },
          "entities": {
            "charge": "sensor.soc",
            "power": "sensor.power",
            "fallbackPower": "sensor.power_alt",
            "current": "sensor.current",
            "totalVoltage": "sensor.voltage",
            "cells": ["sensor.cell_1", "sensor.cell_2"]
          },
          "refresh": { "auto": true, "intervalMinutes": 30 },
          "notifyOnFull": true,
          "capacityAh": 314.5
        }
      }
      """

    val expected =
      SettingsRecord(
        schemaVersion = 1,
        settings =
          Settings(
            connection =
              ConnectionSettings(
                primaryUrl = "http://ha.local:8123",
                fallbackUrl = "https://ha.example.com",
              ),
            entities =
              EntitySettings(
                charge = "sensor.soc",
                power = "sensor.power",
                fallbackPower = "sensor.power_alt",
                current = "sensor.current",
                totalVoltage = "sensor.voltage",
                cells = listOf("sensor.cell_1", "sensor.cell_2"),
              ),
            refresh = RefreshSettings(auto = true, intervalMinutes = 30),
            notifyOnFull = true,
            capacityAh = 314.5,
          ),
      )
    assertV1(json, expected, SettingsRecord.serializer())
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
            "cellVoltagesV": [3.301, null, 3.305],
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
                cellVoltagesV = listOf(3.301, null, 3.305),
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
        """{ "type": "insecure" }""" to SyncError.InsecureConnection,
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
