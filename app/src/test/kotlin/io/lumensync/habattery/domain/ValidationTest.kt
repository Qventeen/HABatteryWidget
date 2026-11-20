package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.Field.CAPACITY
import io.lumensync.habattery.domain.Field.CHARGE
import io.lumensync.habattery.domain.Field.INTERVAL
import io.lumensync.habattery.domain.Field.LEVEL_EDGE
import io.lumensync.habattery.domain.Field.MOSFET_TEMPERATURE
import io.lumensync.habattery.domain.Field.POWER
import io.lumensync.habattery.domain.Field.PREFIX
import io.lumensync.habattery.domain.Field.URL
import io.lumensync.habattery.domain.Field.TEMPERATURES
import io.lumensync.habattery.domain.Field.TOTAL_CAPACITY
import io.lumensync.habattery.domain.Problem.DUPLICATE
import io.lumensync.habattery.domain.Problem.INVALID
import io.lumensync.habattery.domain.Problem.OUT_OF_RANGE
import io.lumensync.habattery.domain.Problem.OVERLAP
import io.lumensync.habattery.domain.Problem.REQUIRED
import io.lumensync.habattery.domain.Problem.TOO_MANY
import io.lumensync.habattery.domain.Validation.isPrefixValid
import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.LevelSide
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
  @Test
  fun `total capacity checks the id`() =
    assertEquals(
      listOf(FieldError(TOTAL_CAPACITY, INVALID)),
      Validation.entities(EntitySettings(charge = "sensor.soc", totalCapacity = "bad id")),
    )

  @Test
  fun connection_urlRequired() =
    assertEquals(
      listOf(FieldError(URL, REQUIRED)),
      Validation.connection(ConnectionSettings()),
    )

  @Test
  fun `public http is not a field error`() =
    assertEquals(emptyList<FieldError>(), Validation.connection(ConnectionSettings("http://example.com")))

  @Test
  fun connection_invalidUrl() =
    assertEquals(
      listOf(FieldError(URL, INVALID)),
      Validation.connection(ConnectionSettings("not a url")),
    )

  @Test
  fun entities_chargeRequired() =
    assertEquals(listOf(FieldError(CHARGE, REQUIRED)), Validation.entities(EntitySettings()))

  @Test
  fun entities_badIds() =
    assertEquals(
      listOf(FieldError(Field.TOTAL_VOLTAGE, INVALID), FieldError(POWER, INVALID)),
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

  private fun entities(e: EntitySettings) = Validation.entities(e)

  @Test
  fun `prefix rules`() {
    assertTrue(isPrefixValid("bps_bms"))
    assertFalse(isPrefixValid("BPS_BMS"))
    assertFalse(isPrefixValid("bps-bms"))
    assertEquals(
      listOf(FieldError(PREFIX, INVALID)),
      entities(EntitySettings(prefix = "BPS", charge = "sensor.soc")),
    )
    assertEquals(
      emptyList<FieldError>(),
      entities(EntitySettings(prefix = "", charge = "sensor.soc")),
    )
  }

  @Test
  fun `blank temperature row is required at its index`() =
    assertEquals(
      listOf(FieldError(TEMPERATURES, REQUIRED, 0)),
      entities(EntitySettings(charge = "sensor.soc", temperatures = listOf("", "sensor.t"))),
    )

  @Test
  fun `more than 8 temperatures`() =
    assertTrue(
      FieldError(TEMPERATURES, TOO_MANY) in
        entities(EntitySettings(charge = "sensor.soc", temperatures = List(9) { "sensor.t$it" }))
    )

  @Test
  fun `entity id rules`() {
    assertTrue(Validation.isEntityId("sensor.bms_soc"))
    listOf("", "sensor", "sensor.", ".soc", "sensor.a b", "sensor.a/../b", "..").forEach {
      assertFalse(it, Validation.isEntityId(it))
    }
  }

  @Test
  fun `new single field checks the id`() =
    assertEquals(
      listOf(FieldError(MOSFET_TEMPERATURE, INVALID)),
      entities(EntitySettings(charge = "sensor.soc", mosfetTemperature = "bad id")),
    )

  private fun lv(side: LevelSide, edge: Double? = null, id: Int = 0) =
    ColorLevel(id = id, side = side, edge = edge)

  private val m = lv(LevelSide.MIDDLE)

  private fun b(edge: Double?) = lv(LevelSide.BELOW, edge)

  private fun a(edge: Double?) = lv(LevelSide.ABOVE, edge)

  @Test
  fun levels_defaults_areValid() {
    for (value in LevelValue.entries) {
      assertEquals(
        emptyList<FieldError>(),
        Validation.levels(value, DefaultLevels.of(value, LevelBasis(100.0, 16))),
      )
    }
  }

  @Test
  fun levels_tooMany() {
    val seven = listOf(m) + (1..6).map { a(it.toDouble()) }
    assertEquals(
      listOf(FieldError(LEVEL_EDGE, TOO_MANY)),
      Validation.levels(LevelValue.SOC, seven),
    )
  }

  @Test
  fun levels_middleCount_isInvalidWithoutIndex() {
    assertEquals(
      listOf(FieldError(LEVEL_EDGE, INVALID)),
      Validation.levels(LevelValue.SOC, listOf(m, m)),
    )
    assertEquals(
      listOf(FieldError(LEVEL_EDGE, INVALID)),
      Validation.levels(LevelValue.SOC, listOf(b(20.0), a(50.0))),
    )
  }

  @Test
  fun levels_middleWithEdge_isInvalidAtIt() =
    assertEquals(
      listOf(FieldError(LEVEL_EDGE, INVALID, 1)),
      Validation.levels(LevelValue.SOC, listOf(b(20.0), lv(LevelSide.MIDDLE, 30.0))),
    )

  @Test
  fun levels_sideWithoutEdge_isRequired() =
    assertEquals(
      listOf(FieldError(LEVEL_EDGE, REQUIRED, 0)),
      Validation.levels(LevelValue.SOC, listOf(b(null), m)),
    )

  @Test
  fun levels_sameEdgeOnTheSameSide_isDuplicateAtTheLaterIndex() =
    assertEquals(
      listOf(FieldError(LEVEL_EDGE, DUPLICATE, 2)),
      Validation.levels(LevelValue.SOC, listOf(m, a(50.0), a(50.0))),
    )

  @Test
  fun levels_noGap_isOverlapAtBoth() {
    assertEquals(
      setOf(FieldError(LEVEL_EDGE, OVERLAP, 0), FieldError(LEVEL_EDGE, OVERLAP, 2)),
      Validation.levels(LevelValue.SOC, listOf(b(40.0), m, a(41.0))).toSet(),
    )
    assertEquals(
      emptyList<FieldError>(),
      Validation.levels(LevelValue.SOC, listOf(b(40.0), m, a(42.0))),
    )
  }

  @Test
  fun levels_overlapUsesTheValuesStep() {
    assertEquals(
      setOf(FieldError(LEVEL_EDGE, OVERLAP, 0), FieldError(LEVEL_EDGE, OVERLAP, 2)),
      Validation.levels(LevelValue.VOLTAGE, listOf(b(51.2), m, a(51.21))).toSet(),
    )
    assertEquals(
      emptyList<FieldError>(),
      Validation.levels(LevelValue.VOLTAGE, listOf(b(51.2), m, a(51.22))),
    )
  }

  @Test
  fun levels_negativeEdges() =
    assertEquals(
      emptyList<FieldError>(),
      Validation.levels(LevelValue.BATTERY_TEMPERATURE, listOf(b(-10.0), b(0.0), m, a(45.0))),
    )
}
