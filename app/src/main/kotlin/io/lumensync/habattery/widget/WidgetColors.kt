package io.lumensync.habattery.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import io.lumensync.habattery.domain.Level
import io.lumensync.habattery.domain.model.ThemeChoice

/**
 * The one place where the theme choice becomes widget colours (spec 0008, approach A1). System
 * gives Glance day/night colours; Light and Dark give fixed colours of that palette. The layout
 * code only receives a [WidgetColors] and never looks at the [ThemeChoice].
 */
class WidgetColors(theme: ThemeChoice) {
  private val choose: (WidgetPalette.() -> Color) -> ColorProvider =
    when (theme) {
      ThemeChoice.SYSTEM -> { pick -> ColorProvider(day = pick(WidgetPalette.Light), night = pick(WidgetPalette.Dark)) }
      ThemeChoice.LIGHT -> { pick -> ColorProvider(pick(WidgetPalette.Light)) }
      ThemeChoice.DARK -> { pick -> ColorProvider(pick(WidgetPalette.Dark)) }
    }

  private fun of(pick: WidgetPalette.() -> Color): ColorProvider = choose(pick)

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
}
