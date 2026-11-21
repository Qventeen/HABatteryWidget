package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.DefaultTextLevels
import io.lumensync.habattery.domain.Problem
import io.lumensync.habattery.domain.TextLevelPicker
import io.lumensync.habattery.domain.model.CustomTextLevels
import io.lumensync.habattery.domain.model.InverterIcon
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.ModeText
import io.lumensync.habattery.domain.model.TextLevel
import io.lumensync.habattery.domain.model.TextLevelName
import io.lumensync.habattery.domain.model.TextValue

/**
 * One mode while the owner edits it. [typed] is the text in its "Add text" field, not yet a chip;
 * it still counts when the owner saves, so a text typed but not added is not lost.
 */
data class TextLevelDraft(
  val id: Int,
  val other: Boolean,
  val texts: List<String>,
  val typed: String = "",
  val name: String,
  val defaultName: TextLevelName?,
  val type: LevelType,
  val color: LevelColor?,
  val icon: InverterIcon,
  val notify: Boolean,
)

/** The drafts after the + button; [problem] is why the typed text was not added, if it was not. */
data class TextAdd(val drafts: List<TextLevelDraft>, val problem: Problem?)

fun TextLevel.toDraft(): TextLevelDraft =
  TextLevelDraft(
    id = id,
    other = other,
    texts = texts,
    name = name,
    defaultName = defaultName,
    type = type,
    color = color,
    icon = shownIcon,
    notify = notify,
  )

fun TextLevelDraft.toLevel(): TextLevel =
  TextLevel(
    id = id,
    texts = texts + listOfNotNull(typed.trim().takeIf { it.isNotEmpty() }),
    other = other,
    name = name.trim(),
    defaultName = defaultName,
    type = type,
    color = color,
    icon = icon.name,
    notify = notify,
  )

/** The drafts the editor opens with: the own items, or a copy of the built-in ones. */
fun textEditorDrafts(value: TextValue, custom: CustomTextLevels): List<TextLevelDraft> =
  (TextLevelPicker.ownLevels(custom) ?: DefaultTextLevels.of(value)).map { it.toDraft() }

/**
 * The drafts with a new mode just above "other": no texts, no name, Info, bell off (owner). Its id
 * is above every id in the drafts and in [stored], so the id of a mode removed from this draft is
 * not given out again before the save.
 */
fun List<TextLevelDraft>.withNewMode(stored: List<TextLevel>): List<TextLevelDraft> {
  val id = ((map { it.id } + stored.map { it.id }).maxOrNull() ?: 0) + 1
  val added =
    TextLevelDraft(
      id = id,
      other = false,
      texts = emptyList(),
      name = "",
      defaultName = null,
      type = LevelType.INFO,
      color = null,
      icon = InverterIcon.HELP,
      notify = false,
    )
  val at = indexOfFirst { it.other }.takeIf { it >= 0 } ?: size
  return take(at) + added + drop(at)
}

/** The drafts without the mode [id]; "other" cannot go. */
fun List<TextLevelDraft>.withoutMode(id: Int): List<TextLevelDraft> =
  filterNot { it.id == id && !it.other }

/**
 * The drafts with the typed text of mode [id] added as a chip. A blank field changes nothing; a
 * no-value text or one any mode already has stays in the field with its problem.
 */
fun List<TextLevelDraft>.withTypedText(id: Int): TextAdd {
  val text = firstOrNull { it.id == id }?.typed?.trim().orEmpty()
  return when {
    text.isEmpty() -> TextAdd(this, null)
    ModeText.isNoValue(text) -> TextAdd(this, Problem.INVALID)
    any { draft -> draft.texts.any { it.trim().equals(text, ignoreCase = true) } } ->
      TextAdd(this, Problem.DUPLICATE)
    else ->
      TextAdd(map { if (it.id == id) it.copy(texts = it.texts + text, typed = "") else it }, null)
  }
}

fun List<TextLevelDraft>.withoutText(id: Int, text: String): List<TextLevelDraft> =
  map { if (it.id == id) it.copy(texts = it.texts - text) else it }
