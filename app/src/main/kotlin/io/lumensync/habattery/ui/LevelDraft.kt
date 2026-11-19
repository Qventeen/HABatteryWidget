package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.DefaultLevels
import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.CustomLevels
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelIcon
import io.lumensync.habattery.domain.model.LevelName
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.roundTo
import io.lumensync.habattery.format.formatDecimal
import io.lumensync.habattery.format.parseDecimal
import java.util.Locale

/** One level while the owner edits it: the edge is text, so a half-typed number survives. */
data class LevelDraft(
  val id: Int,
  val edgeText: String,
  val type: LevelType,
  val color: LevelColor?,
  val icon: LevelIcon?,
  val name: String,
  val defaultName: LevelName?,
)

/** The first level has no edge, so its text is empty. */
fun ColorLevel.toDraft(value: LevelValue, locale: Locale): LevelDraft =
  LevelDraft(
    id = id,
    edgeText = from?.let { formatDecimal(it.roundTo(value.decimals), locale) } ?: "",
    type = type,
    color = color,
    icon = icon,
    name = name,
    defaultName = defaultName,
  )

/** The edge typed in [locale], rounded as the save rounds it; `null` when it is not a number. */
fun LevelDraft.edgeOrNull(value: LevelValue, locale: Locale): Double? =
  parseDecimal(edgeText, locale)?.roundTo(value.decimals)

/**
 * The drafts with a new last level that has an empty edge. Its id is above every id in the drafts
 * and in [stored], so the id of a level removed from this draft is not given out again before the
 * save.
 */
fun List<LevelDraft>.withNewLevel(stored: List<ColorLevel>): List<LevelDraft> {
  val id = ((map { it.id } + stored.map { it.id }).maxOrNull() ?: 0) + 1
  return this + LevelDraft(id, "", LevelType.NORMAL, null, null, "", null)
}

/**
 * The drafts without the level at [index]. A later level's range goes to the level below it, as
 * the edges already say. When the first level goes, the level with the lowest [edgeOf] moves to the
 * front: the save ignores the first edge, so that level's range grows down.
 */
fun List<LevelDraft>.withoutLevel(index: Int, edgeOf: (LevelDraft) -> Double?): List<LevelDraft> {
  val rest = filterIndexed { i, _ -> i != index }
  if (index != 0 || rest.isEmpty()) return rest
  val lowest = rest.indices.minBy { edgeOf(rest[it]) ?: Double.POSITIVE_INFINITY }
  return listOf(rest[lowest]) + rest.filterIndexed { i, _ -> i != lowest }
}

/** The built-in levels of [value] as drafts, with current edges scaled to [capacityAh]. */
fun defaultDrafts(value: LevelValue, capacityAh: Double?, locale: Locale): List<LevelDraft> =
  DefaultLevels.copyForCustom(value, capacityAh).map { it.toDraft(value, locale) }

/**
 * The drafts the editor opens with: the own levels, or the built-in ones. A list left over from
 * a build with a switch is ignored while [CustomLevels.on] is false.
 */
fun editorDrafts(
  value: LevelValue,
  custom: CustomLevels,
  capacityAh: Double?,
  locale: Locale,
): List<LevelDraft> =
  if (custom.on && custom.levels.isNotEmpty()) custom.levels.map { it.toDraft(value, locale) }
  else defaultDrafts(value, capacityAh, locale)
