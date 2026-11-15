package io.lumensync.habattery.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.domain.Level
import io.lumensync.habattery.domain.model.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** System follows the day/night mode of the context; Light and Dark ignore it (spec 0008, A1). */
@RunWith(RobolectricTestRunner::class)
class WidgetColorsTest {
  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  /** Every colour a widget draws, from one [WidgetColors], resolved for the current context. */
  private fun resolved(theme: ThemeChoice): List<Color> =
    with(WidgetColors(theme)) {
      val fixed: List<ColorProvider> =
        listOf(card, text, sub, dim, tile, track, accent, okBg, bad) +
          Level.entries.flatMap { listOf(pillBackground(it), pillText(it), ring(it)) }
      fixed.map { it.getColor(context) }
    }

  private fun palette(p: WidgetPalette): List<Color> =
    listOf(p.card, p.text, p.sub, p.dim, p.tile, p.track, p.accent, p.okBg, p.bad) +
      Level.entries.flatMap { listOf(p.pillBackground(it), p.pillText(it), p.ringColor(it)) }

  @Test
  @Config(qualifiers = "notnight")
  fun system_byDay_isLight() = assertEquals(palette(WidgetPalette.Light), resolved(ThemeChoice.SYSTEM))

  @Test
  @Config(qualifiers = "night")
  fun system_byNight_isDark() = assertEquals(palette(WidgetPalette.Dark), resolved(ThemeChoice.SYSTEM))

  @Test
  @Config(qualifiers = "night")
  fun light_byNight_isStillLight() = assertEquals(palette(WidgetPalette.Light), resolved(ThemeChoice.LIGHT))

  @Test
  @Config(qualifiers = "notnight")
  fun dark_byDay_isStillDark() = assertEquals(palette(WidgetPalette.Dark), resolved(ThemeChoice.DARK))
}
