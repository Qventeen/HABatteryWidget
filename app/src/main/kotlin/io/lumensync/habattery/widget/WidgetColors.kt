package io.lumensync.habattery.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider

/** Day and night colours of the widget, kept from the old widget's look. */
object WidgetColors {
  val cardBackground = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1C1C1E))
  val border = ColorProvider(day = Color(0xFFE0E0E0), night = Color(0xFF2E2E32))
  val primaryText = ColorProvider(day = Color(0xFF1C1C1F), night = Color(0xFFF2F2F7))
  val secondaryText = ColorProvider(day = Color(0xFF5F6368), night = Color(0xFFA0A4AB))
  val divider = ColorProvider(day = Color(0xFFEAECEF), night = Color(0xFF2C2C30))
  val chipBackground = ColorProvider(day = Color(0xFFEEF1F4), night = Color(0xFF2C2E33))
  val cellTileBackground = ColorProvider(day = Color(0xFFF5F7F9), night = Color(0xFF25262B))
  val cellBadgeBackground = ColorProvider(day = Color(0xFF0E5A6D), night = Color(0xFF14748A))
  val cellBadgeText = ColorProvider(day = Color.White, night = Color.White)
  val progressTrack = ColorProvider(day = Color(0xFFE5E7EB), night = Color(0xFF32343A))
  val minCell = ColorProvider(day = Color(0xFFD32F2F), night = Color(0xFFFF6B6B))
  val maxCell = ColorProvider(day = Color(0xFF1976D2), night = Color(0xFF64B5F6))

  private val green = ColorProvider(day = Color(0xFF4CAF50), night = Color(0xFF4CAF50))
  private val amber = ColorProvider(day = Color(0xFFFB8C00), night = Color(0xFFFFB74D))
  private val red = ColorProvider(day = Color(0xFFE53935), night = Color(0xFFEF5350))

  val accent: ColorProvider = green
  val errorText: ColorProvider = red

  fun level(level: Level): ColorProvider =
    when (level) {
      Level.OK -> green
      Level.WARN -> amber
      Level.BAD -> red
    }
}
