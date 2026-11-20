package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.LevelSide

/** The shown numbers a level covers. */
sealed interface LevelSpan {
  data class AtOrBelow(val edge: Double) : LevelSpan

  data class AtOrAbove(val edge: Double) : LevelSpan

  data class Between(val low: Double, val high: Double) : LevelSpan

  data object Anything : LevelSpan
}

object LevelSpans {
  /**
   * What [level] covers within [levels]. A middle level lies one step (one unit of [decimals]) away
   * from the nearest ↓ and ↑ edges; with no edge on a side it reaches [floor], or anything when the
   * value has no floor. A broken list may give `Between` with `low > high`; this never throws.
   */
  fun of(levels: List<ColorLevel>, level: ColorLevel, decimals: Int, floor: Double?): LevelSpan {
    val edge = level.edge
    when (level.side) {
      LevelSide.BELOW -> if (edge != null) return LevelSpan.AtOrBelow(edge)
      LevelSide.ABOVE -> if (edge != null) return LevelSpan.AtOrAbove(edge)
      LevelSide.MIDDLE -> Unit
    }
    val lowEdge =
      levels.filter { it.side == LevelSide.BELOW }.mapNotNull { it.edge }.maxOrNull()
    val highEdge =
      levels.filter { it.side == LevelSide.ABOVE }.mapNotNull { it.edge }.minOrNull()
    val low = lowEdge?.let { stepsToDouble(it.toSteps(decimals) + 1, decimals) }
    val high = highEdge?.let { stepsToDouble(it.toSteps(decimals) - 1, decimals) }
    return when {
      low != null && high != null -> LevelSpan.Between(low, high)
      high != null -> LevelSpan.AtOrBelow(high)
      low != null -> LevelSpan.AtOrAbove(low)
      else -> floor?.let { LevelSpan.AtOrAbove(it) } ?: LevelSpan.Anything
    }
  }
}
