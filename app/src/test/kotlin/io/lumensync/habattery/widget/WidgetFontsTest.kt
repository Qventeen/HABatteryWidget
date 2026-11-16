package io.lumensync.habattery.widget

import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.Level
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.testing.configuredSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 0005, 5.5 with a fake measurer (every character half the size wide). */
class WidgetFontsTest {
  private val model =
    WidgetModel.build(
      BatteryOverview.build(
        CacheState(BatteryReading(chargePercent = 55.0, currentA = -10.0, totalVoltageV = 26.5, updatedAtMillis = 1L), null, null),
        configuredSettings(),
        hasToken = true,
        nowMillis = 2L,
      ),
      configuredSettings(),
    ) as WidgetModel.Data

  private fun texts(main: String = "10.0 A", showError: Boolean = false) =
    DataTexts(
      timeLeft = "17 h",
      updatedAt = "11:29",
      showError = showError,
      errorDescription = if (showError) "Sync error: Token rejected" else null,
      socLabel = "SOC",
      soc = "55",
      underSoc = "4.58 kWh",
      voltage = "26.50 V",
      main = main,
      cells = listOf(CellPill("3.312 V", Level.PLAIN), CellPill("Δ 12 mV", Level.PLAIN), CellPill("27 °C", Level.PLAIN, icon = 1)),
    )

  private fun fonts(spec: LayoutSpec, texts: DataTexts = texts(), percents: FontPercents = FontPercents(), fontScale: Float = 1f) =
    WidgetFonts.data(model, texts, spec, percents, fontScale, FakeMeasurer)

  @Test
  fun fiftyPercent_shortValues_showTheDesignSizes() {
    val f = fonts(LayoutSpec.L4x3)
    assertEquals(22f, f.voltage, 0f)
    assertEquals(22f, f.main, 0f)
    assertEquals(listOf(13.5f, 13.5f, 13.5f), f.cells)
    assertEquals(10.5f, f.small, 0f)
  }

  @Test
  fun hundredPercent_longValue_shrinksBelowTheRequest() {
    val f = fonts(LayoutSpec.L5x3, texts(main = "125.5 A"), FontPercents(main = 100))
    assertTrue("${f.main}", f.main < 44f)
    assertTrue("${f.voltage}", f.voltage > f.main)
  }

  @Test
  fun systemFontScale_raisesTheRequest() =
    assertEquals(22f * 1.3f, fonts(LayoutSpec.L4x3, fontScale = 1.3f).voltage.coerceAtMost(28.6f), 0.3f)

  /** All ring groups raised: they shrink together toward their 50 % sizes. */
  @Test
  fun ringLines_raisedTogether_shrinkTogether() {
    val f = fonts(LayoutSpec.L4x2, percents = FontPercents(soc = 100, underSoc = 100, small = 100))
    val factor = f.soc / 56f
    assertTrue("$factor", factor < 1f && factor >= 0.5f)
    assertEquals(factor, f.underSoc / 20f, 0.02f)
    assertEquals(factor, f.ringLabel / 17f, 0.02f)
  }

  /** Review finding 1: the error icon takes the update time's place, the text size stays. */
  @Test
  fun syncError_keepsTheTopRowSize() {
    LayoutSpec.all.forEach { spec ->
      val normal = fonts(spec, percents = FontPercents(small = 100))
      val error = fonts(spec, texts(showError = true), FontPercents(small = 100))
      assertEquals(spec.name, normal.small, error.small, 0f)
    }
  }

  /** 4×2 at its bucket: 106 dp − icon 22 − gaps 15 − button 34 − the title's view leave the reason. */
  @Test
  fun emptyState_reasonFitsTheCardHeight() {
    val f = WidgetFonts.empty("No data", "Home Assistant is unreachable", "Open app", LayoutSpec.L4x2, FontPercents(small = 100), 1.3f, FakeMeasurer)
    val viewHeight = 1.06f + 0.27f
    val left = 106f - 22f - 15f - 34f - f.title * viewHeight
    assertTrue("${f.reason}", f.reason * viewHeight <= left + 0.01f)
    assertTrue("${f.reason}", f.reason < 10.5f * 2 * 1.3f)
  }
}
