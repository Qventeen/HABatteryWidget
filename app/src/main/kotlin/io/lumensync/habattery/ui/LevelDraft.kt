package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelIcon
import io.lumensync.habattery.domain.model.LevelName
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.roundTo
import io.lumensync.habattery.format.formatDecimal
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
