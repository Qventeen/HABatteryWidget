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

  @Test
  @Config(qualifiers = "uk")
  fun ringDescription_uk_usesThePluralForm() {
    assertEquals("Рівень заряду 21 відсоток", ring(21))
    assertEquals("Рівень заряду 22 відсотки", ring(22))
    assertEquals("Рівень заряду 25 відсотків", ring(25))
    assertEquals("Рівень заряду 12 відсотків", ring(12))
  }
}
