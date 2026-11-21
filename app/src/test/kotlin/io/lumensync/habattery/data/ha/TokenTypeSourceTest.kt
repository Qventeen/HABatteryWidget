package io.lumensync.habattery.data.ha

import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.FetchRequest
import io.lumensync.habattery.domain.model.InverterEntitySettings
import io.lumensync.habattery.domain.model.TokenType
import io.lumensync.habattery.testing.FakeBatterySource
import io.lumensync.habattery.testing.batteryOk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenTypeSourceTest {
  private val ok = batteryOk(BatteryReading(chargePercent = 50.0, updatedAtMillis = 1L))
  private val admin = FakeBatterySource(ok)
  private val user = FakeBatterySource(ok)
  private val source = TokenTypeSource(admin, user)
  private val request =
    FetchRequest(EntitySettings(charge = "sensor.soc"), InverterEntitySettings(mode = "sensor.mode"))

  @Test
  fun routesByType() = runTest {
    for (type in TokenType.entries) {
      val connection = ConnectionSettings("http://ha", type)
      source.fetch(connection, request, "t")
      source.listTemperatureSensors(connection, "bms", "t")
    }
    assertEquals(1, admin.calls)
    assertEquals(request, admin.lastRequest)
    assertEquals(request, user.lastRequest)
    assertEquals(listOf("bms"), admin.sensorCalls)
    assertEquals(1, user.calls)
    assertEquals(listOf("bms"), user.sensorCalls)
  }

  @Test
  fun adminOnly_neverReachesUser() = runTest {
    val connection = ConnectionSettings("http://ha", TokenType.ADMIN)
    source.fetch(connection, request, "t")
    source.listTemperatureSensors(connection, "bms", "t")
    assertEquals(0, user.calls)
    assertEquals(emptyList<String>(), user.sensorCalls)
  }

  @Test
  fun userOnly_neverReachesAdmin() = runTest {
    val connection = ConnectionSettings("http://ha", TokenType.USER)
    source.fetch(connection, request, "t")
    source.listTemperatureSensors(connection, "bms", "t")
    assertEquals(0, admin.calls)
    assertEquals(emptyList<String>(), admin.sensorCalls)
  }
}
