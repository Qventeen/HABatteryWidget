package io.lumensync.habattery.widget

import androidx.compose.ui.graphics.Color
import io.lumensync.habattery.domain.Level

/**
 * The colours of widget design v4 (CSS tokens in `docs/design/widget-4x2.html`), one set per theme.
 * The widget (Glance) and the app's overview card (Compose) both draw with it.
 */
data class WidgetPalette(
  val card: Color,
  val text: Color,
  val sub: Color,
  val dim: Color,
  val tile: Color,
  val track: Color,
  val accent: Color,
  val okBg: Color,
  val ring: Color,
  val warn: Color,
  val warnBg: Color,
  val warnRing: Color,
  val bad: Color,
  val badBg: Color,
) {
  fun pillBackground(level: Level): Color =
    when (level) {
      Level.OK -> okBg
      Level.WARN -> warnBg
      Level.BAD -> badBg
      Level.PLAIN,
      Level.IDLE -> tile
    }

  fun pillText(level: Level): Color =
    when (level) {
      Level.OK -> accent
      Level.WARN -> warn
      Level.BAD -> bad
      Level.PLAIN -> text
      Level.IDLE -> sub
    }

  fun ringColor(level: Level): Color =
    when (level) {
      Level.WARN -> warnRing
      Level.BAD -> bad
      Level.OK,
      Level.PLAIN,
      Level.IDLE -> ring
    }

  companion object {
    val Light =
      WidgetPalette(
        card = Color(0xFFFFFFFF),
        text = Color(0xFF1B1D1C),
        sub = Color(0xFF5C625E),
        dim = Color(0xFFB9C0BA),
        tile = Color(0xFFF1F4F1),
        track = Color(0xFFE1E6E1),
        accent = Color(0xFF2E7D32),
        okBg = Color(0xFFE5F3E6),
        ring = Color(0xFF43A047),
        warn = Color(0xFFA05A00),
        warnBg = Color(0xFFFFF1E0),
        warnRing = Color(0xFFF59E0B),
        bad = Color(0xFFD32F2F),
        badBg = Color(0xFFFDE7E7),
      )

    val Dark =
      WidgetPalette(
        card = Color(0xFF1A1D1B),
        text = Color(0xFFF1F4F1),
        sub = Color(0xFFA3ABA5),
        dim = Color(0xFF4E5651),
        tile = Color(0xFF242926),
        track = Color(0xFF2E3530),
        accent = Color(0xFF7BC67E),
        okBg = Color(0xFF1E3320),
        ring = Color(0xFF66BB6A),
        warn = Color(0xFFFFB869),
        warnBg = Color(0xFF3A2A12),
        warnRing = Color(0xFFFFB74D),
        bad = Color(0xFFEF7070),
        badBg = Color(0xFF3D1E1E),
      )
  }
}
