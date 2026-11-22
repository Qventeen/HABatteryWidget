package io.lumensync.habattery.format

import io.lumensync.habattery.domain.LevelSpan
import io.lumensync.habattery.domain.model.LevelSide
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.roundTo
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

/** The numbers of a [LevelSpan] as shown, already rounded and formatted. */
sealed interface SpanText {
  data class AtMost(val number: String) : SpanText

  data class AtLeast(val number: String) : SpanText

  data class Range(val low: String, val high: String) : SpanText

  /** A range whose two ends show the same number. */
  data class Single(val number: String) : SpanText
}

/**
 * The span's numbers rounded to [decimals]; `null` for [LevelSpan.Anything]. The one place that
 * decides the shape, so the tags and the spoken words agree.
 */
fun LevelSpan.spanText(decimals: Int, locale: Locale): SpanText? {
  fun shown(number: Double) = formatDecimal(number.roundTo(decimals), locale)
  return when (this) {
    is LevelSpan.AtOrBelow -> SpanText.AtMost(shown(edge))
    is LevelSpan.AtOrAbove -> SpanText.AtLeast(shown(edge))
    is LevelSpan.Between -> {
      val from = shown(low)
      val to = shown(high)
      if (from == to) SpanText.Single(from) else SpanText.Range(from, to)
    }
    LevelSpan.Anything -> null
  }
}

/**
 * The numbers of the span, rounded to [decimals], without the mark: the edge ("20"), a range
 * ("41–99", or "41" when both ends show the same), or "≤64" / "≥0" for a middle level open on one
 * side. `null` for [LevelSpan.Anything].
 */
fun LevelSpan.numberText(decimals: Int, locale: Locale, side: LevelSide): String? {
  // A ↓ or ↑ mark already says which way the level goes; the middle level's ↕ does not.
  val open = side == LevelSide.MIDDLE
  return when (val text = spanText(decimals, locale)) {
    is SpanText.AtMost -> if (open) "≤" + text.number else text.number
    is SpanText.AtLeast -> if (open) "≥" + text.number else text.number
    is SpanText.Range -> "${text.low}–${text.high}"
    is SpanText.Single -> text.number
    null -> null
  }
}

/** The [side]'s mark, then [numberText]: "↓20", "↕41–99", "↕≤64"; `null` for `Anything`. */
fun LevelSpan.placeText(decimals: Int, locale: Locale, side: LevelSide): String? =
  numberText(decimals, locale, side)?.let { side.mark() + it }

/** An edge as the edge field and the tags show it: at the value's precision, no trailing zeros. */
fun LevelValue.formatEdge(edge: Double, locale: Locale): String =
  formatDecimal(edge.roundTo(decimals), locale)
