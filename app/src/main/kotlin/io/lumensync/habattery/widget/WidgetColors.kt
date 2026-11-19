package io.lumensync.habattery.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import io.lumensync.habattery.domain.Level
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.ThemeChoice

/**
 * The one place where the theme choice becomes widget colours (spec 0008, approach A1). System
 * gives Glance day/night colours; Light and Dark give fixed colours of that palette. The layout
 * code only receives a [WidgetColors] and never looks at the [ThemeChoice].
 */
class WidgetColors(private val theme: ThemeChoice) {
  private fun of(pick: WidgetPalette.() -> Color): ColorProvider =
    when (theme) {
      ThemeChoice.SYSTEM ->
        ColorProvider(day = WidgetPalette.Light.pick(), night = WidgetPalette.Dark.pick())
      ThemeChoice.LIGHT -> ColorProvider(WidgetPalette.Light.pick())
      ThemeChoice.DARK -> ColorProvider(WidgetPalette.Dark.pick())
    }

  val card = of { card }
  val text = of { text }
  val sub = of { sub }
  val dim = of { dim }
  val tile = of { tile }
  val track = of { track }
  val accent = of { accent }
  val okBg = of { okBg }
  val bad = of { bad }

  fun pillBackground(level: Level): ColorProvider = of { pillBackground(level) }

  fun pillText(level: Level): ColorProvider = of { pillText(level) }

  fun ring(level: Level): ColorProvider = of { ringColor(level) }

  fun pillBackground(color: LevelColor): ColorProvider = of { shades(color).background }

  fun pillText(color: LevelColor): ColorProvider = of { shades(color).text }

  fun ring(color: LevelColor): ColorProvider = of { shades(color).ring }
}
