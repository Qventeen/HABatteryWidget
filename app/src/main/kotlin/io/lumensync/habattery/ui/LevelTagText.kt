package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.LevelSpan
import io.lumensync.habattery.domain.model.LevelSide
import io.lumensync.habattery.domain.roundTo
import io.lumensync.habattery.format.formatDecimal
import java.util.Locale

/**
 * The mark a level's place starts with. The middle level has its own, so ↓ and ↑ always mean too
 * low or too high.
 */
fun LevelSide.mark(): String =
  when (this) {
    LevelSide.BELOW -> "↓"
    LevelSide.MIDDLE -> "↕"
    LevelSide.ABOVE -> "↑"
  }

/**
 * The numbers of the span, rounded to [decimals], without the mark: the edge ("20"), a range
 * ("41–99", or "41" when both ends show the same), or "≤64" / "≥0" for a middle level open on one
 * side. `null` for [LevelSpan.Anything].
 */
fun LevelSpan.numberText(decimals: Int, locale: Locale, side: LevelSide): String? {
  fun shown(number: Double) = formatDecimal(number.roundTo(decimals), locale)
  // A ↓ or ↑ mark already says which way the level goes; the middle level's ↕ does not.
  val open = side == LevelSide.MIDDLE
  return when (this) {
    is LevelSpan.AtOrBelow -> if (open) "≤" + shown(edge) else shown(edge)
    is LevelSpan.AtOrAbove -> if (open) "≥" + shown(edge) else shown(edge)
    is LevelSpan.Between -> {
      val from = shown(low)
      val to = shown(high)
      if (from == to) from else "$from–$to"
    }
    LevelSpan.Anything -> null
  }
}

/** The [side]'s mark, then [numberText]: "↓20", "↕41–99", "↕≤64"; `null` for `Anything`. */
fun LevelSpan.placeText(decimals: Int, locale: Locale, side: LevelSide): String? =
  numberText(decimals, locale, side)?.let { side.mark() + it }
