package io.lumensync.habattery.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import io.lumensync.habattery.domain.Level

/** [WidgetPalette] as Glance day/night colours. */
object WidgetColors {
  private fun of(pick: (WidgetPalette) -> Color): ColorProvider =
    ColorProvider(day = pick(WidgetPalette.Light), night = pick(WidgetPalette.Dark))

  val card = of { it.card }
  val text = of { it.text }
  val sub = of { it.sub }
  val dim = of { it.dim }
  val tile = of { it.tile }
  val track = of { it.track }
  val accent = of { it.accent }
  val okBg = of { it.okBg }
  val bad = of { it.bad }

  fun pillBackground(level: Level): ColorProvider = of { it.pillBackground(level) }

  fun pillText(level: Level): ColorProvider = of { it.pillText(level) }

  fun ring(level: Level): ColorProvider = of { it.ringColor(level) }
}
