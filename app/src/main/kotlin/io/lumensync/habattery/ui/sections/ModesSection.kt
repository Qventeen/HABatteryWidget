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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
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
      val now = nowIndex(levels, liveText)
      levels.forEachIndexed { index, level ->
        ModeTag(level, palette, nowText = liveText.takeIf { index == now })
      }
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
 * right, so the bells of all rows form one column. [nowText] is the text Home Assistant sends now
 * when this is its mode: the row then has a ring and a "now" badge. TalkBack reads the row as one
 * item.
 */
@Composable
private fun ModeTag(level: TextLevel, palette: WidgetPalette, nowText: String?) {
  val resources = LocalResources.current
  val shades = palette.shades(level.shownColor)
  val word = modeWord(resources, level, liveText = null)
  val texts = subLine(resources, level, nowText)
  val type = stringResource(typeLabel(level.type))
  val bell = if (level.notify) stringResource(R.string.colors_tag_bell) else null
  val now = nowText?.let { stringResource(R.string.mode_now) }
  val description = listText(resources, listOfNotNull(word, texts, type, bell, now))
  Row(
    Modifier.fillMaxWidth()
      .clearAndSetSemantics { contentDescription = description }
      .nowRing(shades, nowText != null)
      .heightIn(min = 48.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ModePill(level.shownIcon, shades)
    Column(Modifier.weight(1f)) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          word,
          Modifier.weight(1f, fill = false),
          color = shades.text,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (nowText != null) NowBadge(shades)
      }
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

/**
 * The row's inner padding and, for the mode Home Assistant sends [now], a ring in the mode's
 * colour. Every row has the padding, so the rows stay aligned.
 */
internal fun Modifier.nowRing(shades: Shades, now: Boolean): Modifier =
  border(1.5.dp, if (now) shades.ring else Color.Transparent, RoundedCornerShape(14.dp))
    .padding(horizontal = 8.dp, vertical = 4.dp)

/** The "now" badge after the word of the mode Home Assistant sends now. */
@Composable
internal fun NowBadge(shades: Shades) {
  Text(
    stringResource(R.string.mode_now),
    Modifier.background(shades.ring, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp),
    color = MaterialTheme.colorScheme.surface,
    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
    maxLines = 1,
  )
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

/** The line under a mode's word: its texts, or what "other" takes. */
/**
 * The position in [levels] of the mode [liveText] picks; `null` for no text or a no-value text. A
 * text no mode lists picks "other".
 */
internal fun nowIndex(levels: List<TextLevel>, liveText: String?): Int? {
  val now = liveText?.let { TextLevelPicker.match(levels, it) } ?: return null
  return levels.indexOfFirst { it === now }.takeIf { it >= 0 }
}

/**
 * The line under a mode's word: its texts, or what "other" takes. When "other" is the current
 * mode, [nowText] replaces that, since its word does not say which text came.
 */
internal fun subLine(resources: Resources, level: TextLevel, nowText: String? = null): String =
  when {
    !level.other -> listText(resources, level.texts)
    nowText != null -> resources.getString(R.string.mode_live_text, nowText)
    else -> resources.getString(R.string.mode_other_hint)
  }

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
