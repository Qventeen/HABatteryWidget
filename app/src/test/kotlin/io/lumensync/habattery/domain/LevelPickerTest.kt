package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.CustomLevels
import io.lumensync.habattery.domain.model.LevelName
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.withCustomLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LevelPickerTest {
  private val own = listOf(ColorLevel(id = 1, name = "mine"), ColorLevel(id = 2, from = 10.0))

  private fun settings(custom: CustomLevels) =
    Settings().withCustomLevels(LevelValue.SOC, custom)

  @Test
  fun `effective custom on uses own list`() {
    val result =
      LevelPicker.effective(LevelValue.SOC, settings(CustomLevels(true, own)), null)
    assertEquals(own, result)
  }

  @Test
  fun `effective custom on but empty uses defaults`() {
    val result = LevelPicker.effective(LevelValue.SOC, settings(CustomLevels(true)), null)
    assertEquals(DefaultLevels.of(LevelValue.SOC, null), result)
  }

  @Test
  fun `effective custom off uses defaults`() {
    val result =
      LevelPicker.effective(LevelValue.SOC, settings(CustomLevels(false, own)), null)
    assertEquals(DefaultLevels.of(LevelValue.SOC, null), result)
  }

  @Test
  fun `pick unsorted list`() {
    val a = ColorLevel(id = 1, name = "A")
    val b = ColorLevel(id = 3, from = 20.0, name = "B")
    val c = ColorLevel(id = 2, from = 50.0, name = "C")
    assertSame(b, LevelPicker.pick(listOf(a, c, b), 30.0))
    val first = ColorLevel(id = 1, from = 10.0, name = "first")
    assertSame(first, LevelPicker.pick(listOf(first, c, b), 5.0))
  }

  @Test
  fun `pick ignores a later level without an edge`() {
    val a = ColorLevel(id = 1)
    val broken = ColorLevel(id = 2)
    assertSame(a, LevelPicker.pick(listOf(a, broken), 99.0))
  }

  @Test
  fun `perCell divides every edge`() {
    val levels = listOf(ColorLevel(id = 1), ColorLevel(id = 2, from = 52.0))
    assertEquals(listOf(null, 3.25), LevelPicker.perCell(levels, 16).map { it.from })
  }

  @Test
  fun `worst picks the highest rank`() {
    val normal = ColorLevel(id = 1, type = LevelType.NORMAL)
    val info = ColorLevel(id = 2, type = LevelType.INFO)
    val warning = ColorLevel(id = 3, type = LevelType.WARNING)
    assertSame(warning, LevelPicker.worst(listOf(normal, info, warning)))
    assertSame(info, LevelPicker.worst(listOf(normal, info)))
    assertNull(LevelPicker.worst(emptyList()))
  }

  @Test
  fun `worst keeps the first on a tie`() {
    val one = ColorLevel(id = 1, type = LevelType.INFO, defaultName = LevelName.FULL)
    val two = ColorLevel(id = 2, type = LevelType.INFO)
    assertSame(one, LevelPicker.worst(listOf(one, two)))
  }

  @Test
  fun `roundTo matches the shown number`() {
    assertEquals(20.0, 19.95.roundTo(1), 0.0)
    assertEquals(19.8, 19.85.roundTo(1), 0.0)
    assertEquals(20.0, 20.000000000000004.roundTo(1), 0.0)
  }
}
