package io.lumensync.habattery.widget

import androidx.compose.ui.graphics.Color
import io.lumensync.habattery.domain.model.LevelColor

/** The three colours one [LevelColor] gives a value pill and the SOC ring, in one theme. */
data class Shades(val text: Color, val background: Color, val ring: Color)

/**
 * The colours of widget design v4 (CSS tokens in `docs/design/widget-4x2.html`) and the level
 * colours of spec 0010, one set per theme. The widget draws with it through [WidgetColors].
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
  val bad: Color,
  /** Holds every [LevelColor]; [shades] throws for a missing one. */
  private val levelShades: Map<LevelColor, Shades>,
) {
  fun shades(color: LevelColor): Shades = levelShades.getValue(color)

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
        bad = Color(0xFFD32F2F),
        levelShades =
          mapOf(
            LevelColor.PLAIN to shadesOf(0xFF1B1D1C, 0xFFF1F4F1, 0xFF43A047),
            LevelColor.RED to shadesOf(0xFFD32F2F, 0xFFFDE7E7, 0xFFD32F2F),
            LevelColor.ORANGE to shadesOf(0xFFC2410C, 0xFFFFE9DD, 0xFFE8590C),
            LevelColor.AMBER to shadesOf(0xFFA05A00, 0xFFFFF1E0, 0xFFF59E0B),
            LevelColor.GREEN to shadesOf(0xFF2E7D32, 0xFFE5F3E6, 0xFF43A047),
            LevelColor.TEAL to shadesOf(0xFF00796B, 0xFFE0F2F1, 0xFF26A69A),
            LevelColor.BLUE to shadesOf(0xFF1565C0, 0xFFE3EEFB, 0xFF1E88E5),
            LevelColor.VIOLET to shadesOf(0xFF6B3FB8, 0xFFEFE7FB, 0xFF8E5CD9),
            LevelColor.GREY to shadesOf(0xFF5C625E, 0xFFF1F4F1, 0xFF858C87),
          ),
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
        bad = Color(0xFFEF7070),
        levelShades =
          mapOf(
            LevelColor.PLAIN to shadesOf(0xFFF1F4F1, 0xFF242926, 0xFF66BB6A),
            LevelColor.RED to shadesOf(0xFFEF7070, 0xFF3D1E1E, 0xFFEF7070),
            LevelColor.ORANGE to shadesOf(0xFFFF9A66, 0xFF3D2215, 0xFFFB8C4A),
            LevelColor.AMBER to shadesOf(0xFFFFB869, 0xFF3A2A12, 0xFFFFB74D),
            LevelColor.GREEN to shadesOf(0xFF7BC67E, 0xFF1E3320, 0xFF66BB6A),
            LevelColor.TEAL to shadesOf(0xFF5FC4B8, 0xFF15302D, 0xFF4DB6AC),
            LevelColor.BLUE to shadesOf(0xFF8AB4F8, 0xFF1B2A3E, 0xFF64B5F6),
            LevelColor.VIOLET to shadesOf(0xFFB99CF2, 0xFF2B2240, 0xFFA788EC),
            LevelColor.GREY to shadesOf(0xFFA3ABA5, 0xFF242926, 0xFF6B736D),
          ),
      )
  }
}

private fun shadesOf(text: Long, background: Long, ring: Long) =
  Shades(Color(text), Color(background), Color(ring))
