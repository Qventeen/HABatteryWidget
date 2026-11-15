package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.Field.CAPACITY
import io.lumensync.habattery.domain.Field.CHARGE
import io.lumensync.habattery.domain.Field.FALLBACK_URL
import io.lumensync.habattery.domain.Field.INTERVAL
import io.lumensync.habattery.domain.Field.POWER
import io.lumensync.habattery.domain.Field.PRIMARY_URL
import io.lumensync.habattery.domain.Problem.INSECURE
import io.lumensync.habattery.domain.Problem.INVALID
import io.lumensync.habattery.domain.Problem.OUT_OF_RANGE
import io.lumensync.habattery.domain.Problem.REQUIRED
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
  @Test
  fun connection_primaryRequired() =
    assertEquals(
      listOf(FieldError(PRIMARY_URL, REQUIRED)),
      Validation.connection(ConnectionSettings()),
    )

  @Test
  fun connection_insecureFallback() =
    assertEquals(
      listOf(FieldError(FALLBACK_URL, INSECURE)),
      Validation.connection(ConnectionSettings("http://192.168.1.2:8123", "http://example.com")),
    )

  @Test
  fun connection_invalidPrimary() =
    assertEquals(
      listOf(FieldError(PRIMARY_URL, INVALID)),
      Validation.connection(ConnectionSettings("not a url")),
    )

  @Test
  fun entities_chargeRequired() =
    assertEquals(listOf(FieldError(CHARGE, REQUIRED)), Validation.entities(EntitySettings()))

  @Test
  fun entities_badIds() =
    assertEquals(
      listOf(FieldError(POWER, INVALID), FieldError(Field.TOTAL_VOLTAGE, INVALID)),
      Validation.entities(
        EntitySettings(charge = "sensor.soc", power = "bad id", totalVoltage = "sensor v")
      ),
    )

  @Test
  fun refresh_rules() {
    assertEquals(emptyList<FieldError>(), Validation.refresh(RefreshSettings()))
    assertEquals(
      listOf(FieldError(INTERVAL, REQUIRED)),
      Validation.refresh(RefreshSettings(true, null)),
    )
    assertEquals(
      listOf(FieldError(INTERVAL, OUT_OF_RANGE)),
      Validation.refresh(RefreshSettings(true, 10)),
    )
    assertEquals(emptyList<FieldError>(), Validation.refresh(RefreshSettings(true, 1440)))
  }

  @Test
  fun capacity_rules() {
    assertEquals(emptyList<FieldError>(), Validation.capacity(null))
    assertEquals(listOf(FieldError(CAPACITY, OUT_OF_RANGE)), Validation.capacity(0.0))
    assertEquals(listOf(FieldError(CAPACITY, OUT_OF_RANGE)), Validation.capacity(10_000.1))
    assertEquals(listOf(FieldError(CAPACITY, OUT_OF_RANGE)), Validation.capacity(Double.NaN))
  }

  @Test
  fun isConfigured_needsUrlChargeAndToken() {
    val s = Settings(ConnectionSettings("http://ha.local:8123"), EntitySettings(charge = "sensor.soc"))
    assertTrue(Validation.isConfigured(s, hasToken = true))
    assertFalse(Validation.isConfigured(s, hasToken = false))
    assertFalse(Validation.isConfigured(Settings(), hasToken = true))
  }
}
