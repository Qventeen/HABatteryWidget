package io.lumensync.habattery.ui.sections

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.DefaultTextLevels
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.TextLevelPicker
import io.lumensync.habattery.domain.model.CustomTextLevels
import io.lumensync.habattery.domain.model.InverterIcon
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.TextLevel
import io.lumensync.habattery.domain.model.TextValue
import io.lumensync.habattery.format.modeWord
import io.lumensync.habattery.format.typeLabel
import io.lumensync.habattery.format.typeTagLabel
import io.lumensync.habattery.ui.TextLevelDraft
import io.lumensync.habattery.widget.Shades
import io.lumensync.habattery.widget.WidgetPalette
import io.lumensync.habattery.widget.drawable

/**
 * The inverter modes: which Home Assistant texts each mode takes, and its word, colour, icon, bell
 * and type. The view lists the modes with Default and Edit; Edit opens a draft that [onSave]
 * stores, or keeps open when it returns `false`. [liveText] is the mode text Home Assistant sends
 * now, `null` when there is none.
 */
@Composable
fun ModesSection(
  textLevels: Map<String, CustomTextLevels>,
  liveText: String?,
  errors: List<FieldError>,
  onSave: (TextValue, List<TextLevelDraft>) -> Boolean,
  onUseDefaults: (TextValue) -> Unit,
  modifier: Modifier = Modifier,
) {
  val value = TextValue.MODE
  val stored = textLevels[value.name] ?: CustomTextLevels()
  val own = remember(stored) { TextLevelPicker.ownLevels(stored) }
  val summary =
    stringResource(if (own != null) R.string.modes_summary_custom else R.string.modes_summary_default)

  SectionCard(R.string.modes_title, summary, initiallyExpanded = false, modifier) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDefault by rememberSaveable { mutableStateOf(false) }
    val palette = appPalette()
    if (editing) {
      ModeEditor(
        value = value,
        stored = stored,
        liveText = liveText,
        palette = palette,
        errors = errors,
        onSave = { drafts -> onSave(value, drafts) },
        onClose = { editing = false },
      )
    } else {
      val levels = own ?: DefaultTextLevels.of(value)
      ButtonRow {
        ValueButton(R.string.colors_default, enabled = own != null) { confirmDefault = true }
        ValueButton(R.string.colors_edit) { editing = true }
      }
      levels.forEach { ModeTag(it, palette) }
      NowLine(levels, liveText)
    }
    if (confirmDefault) {
      DefaultModesDialog(
        onConfirm = {
          confirmDefault = false
          onUseDefaults(value)
        },
        onDismiss = { confirmDefault = false },
      )
    }
  }
}

/** The section's buttons, at the end of their row. */
@Composable
internal fun ButtonRow(buttons: @Composable RowScope.() -> Unit) {
  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    verticalAlignment = Alignment.CenterVertically,
    content = buttons,
  )
}

/**
 * One mode: its pill, its word with its texts under it, then the type tag and the bell at the far
 * right, so the bells of all rows form one column. TalkBack reads the row as one item.
 */
@Composable
private fun ModeTag(level: TextLevel, palette: WidgetPalette) {
  val resources = LocalResources.current
  val shades = palette.shades(level.shownColor)
  val word = modeWord(resources, level, liveText = null)
  val texts = subLine(resources, level)
  val type = stringResource(typeLabel(level.type))
  val bell = if (level.notify) stringResource(R.string.colors_tag_bell) else null
  val description = listText(resources, listOfNotNull(word, texts, type, bell))
  Row(
    Modifier.fillMaxWidth().heightIn(min = 48.dp).clearAndSetSemantics {
      contentDescription = description
    },
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ModePill(level.shownIcon, shades)
    Column(Modifier.weight(1f)) {
      Text(word, color = shades.text, style = MaterialTheme.typography.titleSmall)
      Text(
        texts,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TypeTag(level.type, palette)
      Icon(
        painterResource(if (level.notify) R.drawable.ic_bell else R.drawable.ic_bell_off),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint =
          if (level.notify) MaterialTheme.colorScheme.onSurfaceVariant
          else MaterialTheme.colorScheme.outline,
      )
    }
  }
}

/**
 * The [type] as a small tag in the type's own colour, in the style of the level tags. With [menu]
 * it carries a ▾, for the editor's type menu.
 */
@Composable
internal fun TypeTag(
  type: LevelType,
  palette: WidgetPalette,
  modifier: Modifier = Modifier,
  menu: Boolean = false,
) {
  val shades = palette.shades(type.defaultColor)
  Row(
    modifier
      .background(shades.background, RoundedCornerShape(12.dp))
      .padding(start = if (menu) 10.dp else 8.dp, end = if (menu) 4.dp else 8.dp)
      .padding(vertical = if (menu) 4.dp else 2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      stringResource(typeTagLabel(type)),
      color = shades.text,
      style =
        if (menu) MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)
        else MaterialTheme.typography.labelMedium,
      maxLines = 1,
    )
    if (menu) {
      Icon(
        painterResource(R.drawable.ic_expand_more),
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = shades.text,
      )
    }
  }
}

/** A mode's icon in a pill of its colour, like the strip's mode pill. */
@Composable
internal fun ModePill(icon: InverterIcon, shades: Shades) {
  Box(
    Modifier.size(width = 36.dp, height = 28.dp)
      .background(shades.background, CircleShape)
      .border(1.5.dp, shades.ring, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      painterResource(icon.drawable()),
      contentDescription = null,
      modifier = Modifier.size(20.dp),
      tint = shades.text,
    )
  }
}

/** The "Now" line for the text Home Assistant sends now; nothing when there is none. */
@Composable
internal fun NowLine(levels: List<TextLevel>, liveText: String?) {
  nowLine(LocalResources.current, levels, liveText)?.let {
    Text(
      it,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodySmall,
    )
  }
}

/**
 * "Now: “Line” → main": the text Home Assistant sends now and the mode of [levels] it picks. A text
 * no mode lists names the "other" label, not the same text again.
 */
internal fun nowLine(resources: Resources, levels: List<TextLevel>, liveText: String?): String? {
  val text = liveText ?: return null
  val level = TextLevelPicker.match(levels, text) ?: return null
  val target =
    if (level.other) resources.getString(R.string.mode_other_label)
    else modeWord(resources, level, text)
  return resources.getString(R.string.mode_now, text, target)
}

/** The line under a mode's word: its texts, or what "other" takes. */
internal fun subLine(resources: Resources, level: TextLevel): String =
  if (level.other) resources.getString(R.string.mode_other_hint)
  else listText(resources, level.texts)

/** The [parts] joined by the translated list separator; empty for none. */
internal fun listText(resources: Resources, parts: List<String>): String =
  parts.reduceOrNull { text, part -> resources.getString(R.string.colors_list_parts, text, part) }
    .orEmpty()

/** Asks before the own modes go; only [onConfirm] changes anything. */
@Composable
private fun DefaultModesDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = onConfirm) { Text(stringResource(R.string.colors_default_confirm)) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
    },
    title = { Text(stringResource(R.string.modes_default_title)) },
    text = { Text(stringResource(R.string.modes_default_text)) },
  )
}
