package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.DefaultLevels
import io.lumensync.habattery.domain.LevelBasis
import io.lumensync.habattery.domain.LevelPicker
import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.CustomLevels
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelIcon
import io.lumensync.habattery.domain.model.LevelName
import io.lumensync.habattery.domain.model.LevelSide
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.roundTo
import io.lumensync.habattery.format.formatEdge
import io.lumensync.habattery.format.parseDecimal
import java.util.Locale

/**
 * One level while the owner edits it: the edge is text, so a half-typed number survives. The
 * middle level has no edge, so its text is empty.
 */
data class LevelDraft(
  val id: Int,
  val side: LevelSide,
  val edgeText: String,
  val type: LevelType,
  val color: LevelColor?,
  val icon: LevelIcon?,
  val name: String,
  val defaultName: LevelName?,
)

fun ColorLevel.toDraft(value: LevelValue, locale: Locale): LevelDraft =
  LevelDraft(
    id = id,
    side = side,
    edgeText = edge?.let { value.formatEdge(it, locale) } ?: "",
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
 * The drafts with a new last level: above, with an empty edge. Its id is above every id in the
 * drafts and in [stored], so the id of a level removed from this draft is not given out again
 * before the save.
 */
fun List<LevelDraft>.withNewLevel(stored: List<ColorLevel>): List<LevelDraft> {
  val id = ((map { it.id } + stored.map { it.id }).maxOrNull() ?: 0) + 1
  return this + LevelDraft(id, LevelSide.ABOVE, "", LevelType.NORMAL, null, null, "", null)
}

/** The drafts without the row at [index]; no other edge moves. The middle row cannot go. */
fun List<LevelDraft>.withoutLevel(index: Int): List<LevelDraft> =
  if (getOrNull(index)?.side == LevelSide.MIDDLE) this else filterIndexed { i, _ -> i != index }

/** The built-in levels of [value] as drafts, with edges scaled to [basis]. */
fun defaultDrafts(value: LevelValue, basis: LevelBasis, locale: Locale): List<LevelDraft> =
  DefaultLevels.copyForCustom(value, basis).map { it.toDraft(value, locale) }

/** The drafts the editor opens with: the own levels, or the built-in ones. */
fun editorDrafts(
  value: LevelValue,
  custom: CustomLevels,
  basis: LevelBasis,
  locale: Locale,
): List<LevelDraft> =
  LevelPicker.ownLevels(value, custom)?.map { it.toDraft(value, locale) }
    ?: defaultDrafts(value, basis, locale)
