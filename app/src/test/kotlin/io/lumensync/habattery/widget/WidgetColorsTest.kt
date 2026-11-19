package io.lumensync.habattery.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.domain.Level
import io.lumensync.habattery.domain.model.LevelColor
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
          Level.entries.flatMap { listOf(pillBackground(it), pillText(it), ring(it)) } +
          LevelColor.entries.flatMap { listOf(pillBackground(it), pillText(it), ring(it)) }
      fixed.map { it.getColor(context) }
    }

  private fun palette(p: WidgetPalette): List<Color> =
    listOf(p.card, p.text, p.sub, p.dim, p.tile, p.track, p.accent, p.okBg, p.bad) +
      Level.entries.flatMap { listOf(p.pillBackground(it), p.pillText(it), p.ringColor(it)) } +
      LevelColor.entries.flatMap {
        val s = p.shades(it)
        listOf(s.background, s.text, s.ring)
      }

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

  @Test
  fun palette_matchesSpec() {
    assertEquals(
      Shades(Color(0xFF1565C0), Color(0xFFE3EEFB), Color(0xFF1E88E5)),
      WidgetPalette.Light.shades(LevelColor.BLUE),
    )
    assertEquals(
      Shades(Color(0xFFFF9A66), Color(0xFF3D2215), Color(0xFFFB8C4A)),
      WidgetPalette.Dark.shades(LevelColor.ORANGE),
    )
    assertEquals(Color(0xFF858C87), WidgetPalette.Light.shades(LevelColor.GREY).ring)
  }

  @Test
  fun todaysColours_unchanged() {
    val light = WidgetPalette.Light
    assertEquals(light.accent, light.shades(LevelColor.GREEN).text)
    assertEquals(light.okBg, light.shades(LevelColor.GREEN).background)
    assertEquals(light.sub, light.shades(LevelColor.GREY).text)
    assertEquals(light.tile, light.shades(LevelColor.GREY).background)
    assertEquals(
      Shades(Color(0xFFFFB869), Color(0xFF3A2A12), Color(0xFFFFB74D)),
      WidgetPalette.Dark.shades(LevelColor.AMBER),
    )
    assertEquals(Color(0xFFD32F2F), light.shades(LevelColor.RED).ring)
  }

  @Test
  fun plain_isTodaysPillAndRing() {
    assertEquals(
      Shades(Color(0xFF1B1D1C), Color(0xFFF1F4F1), Color(0xFF43A047)),
      WidgetPalette.Light.shades(LevelColor.PLAIN),
    )
    assertEquals(
      Shades(Color(0xFFF1F4F1), Color(0xFF242926), Color(0xFF66BB6A)),
      WidgetPalette.Dark.shades(LevelColor.PLAIN),
    )
  }
}
