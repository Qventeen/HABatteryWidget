package io.lumensync.habattery.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.format.Formatters
import io.lumensync.habattery.testing.configuredSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class WidgetTextsTest {
  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  private fun ring(soc: Int): String {
    val settings = configuredSettings()
    val reading = BatteryReading(chargePercent = soc.toDouble(), updatedAtMillis = 1L)
    val model = WidgetModel.build(BatteryOverview.build(CacheState(reading), settings, true, 2L), settings)
    return WidgetTexts(context, Formatters(context)).ringDescription(model as WidgetModel.Data)
  }

  @Test
  @Config(qualifiers = "en")
  fun ringDescription_en() {
    assertEquals("State of charge 1 percent", ring(1))
    assertEquals("State of charge 55 percent", ring(55))
  }

  private fun refresh(minutes: Long, refreshing: Boolean = false): String =
    WidgetTexts.refreshDescription(context, refreshing, minutes)

  @Test
  @Config(qualifiers = "en")
  fun refreshDescription_saysTheAgeInFullWords() {
    assertEquals("Refresh. Data is 3 minutes old", refresh(3))
    assertEquals("Refresh. Data is 59 minutes old", refresh(59))
    assertEquals("Refresh. Data is 1 hour old", refresh(60))
    assertEquals("Refresh. Data is 23 hours old", refresh(23 * 60L + 59))
    assertEquals("Refresh. Data is 1 day old", refresh(24 * 60L))
    assertEquals("Refresh. Data is 2 days old", refresh(2880))
  }

  @Test
  @Config(qualifiers = "en")
  fun refreshDescription_refreshing_saysTheAge() =
    assertEquals("Refreshing… Data is 2 hours old", refresh(150, refreshing = true))

  @Test
  @Config(qualifiers = "uk")
  fun refreshDescription_uk_usesThePluralForm() {
    assertEquals("Оновити. Вік даних: 3 хвилини", refresh(3))
    assertEquals("Оновити. Вік даних: 21 хвилина", refresh(21))
    assertEquals("Оновити. Вік даних: 59 хвилин", refresh(59))
    assertEquals("Оновити. Вік даних: 1 година", refresh(60))
    assertEquals("Оновити. Вік даних: 23 години", refresh(23 * 60L + 59))
    assertEquals("Оновити. Вік даних: 1 день", refresh(24 * 60L))
    assertEquals("Оновити. Вік даних: 2 дні", refresh(2880))
    assertEquals("Оновити. Вік даних: 5 днів", refresh(5 * 24 * 60L))
    assertEquals("Оновлення… Вік даних: 2 дні", refresh(2880, refreshing = true))
  }

  @Test
  @Config(qualifiers = "uk")
  fun ringDescription_uk_usesThePluralForm() {
    assertEquals("Рівень заряду 21 відсоток", ring(21))
    assertEquals("Рівень заряду 22 відсотки", ring(22))
    assertEquals("Рівень заряду 25 відсотків", ring(25))
    assertEquals("Рівень заряду 12 відсотків", ring(12))
  }
}
