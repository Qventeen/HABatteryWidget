package io.lumensync.habattery.ui.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.LevelBasis
import io.lumensync.habattery.domain.LevelPicker
import io.lumensync.habattery.domain.LevelSpan
import io.lumensync.habattery.domain.LevelSpans
import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.CustomLevels
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelIcon
import io.lumensync.habattery.domain.model.LevelName
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.customLevels
import io.lumensync.habattery.format.SpanText
import io.lumensync.habattery.format.levelNameRes
import io.lumensync.habattery.format.nameOrNull
import io.lumensync.habattery.format.numberText
import io.lumensync.habattery.format.placeWithUnit
import io.lumensync.habattery.format.spanText
import io.lumensync.habattery.format.typeLabel
import io.lumensync.habattery.format.unitLabel
import io.lumensync.habattery.ui.LevelDraft
import io.lumensync.habattery.widget.WidgetPalette
import io.lumensync.habattery.widget.levelIconRes
import java.util.Locale

/**
 * The levels of every value as tags, with Default and Edit; one editor is open at a time.
 * [basis] gives the built-in edges.
 * [onSaveLevels] returns `false` when a field has an error, and the editor stays open.
 */
@Composable
fun ColorsSection(
  colorLevels: Map<String, CustomLevels>,
  basis: LevelBasis,
  errors: List<FieldError>,
  onSaveLevels: (LevelValue, List<LevelDraft>, margin: String, Locale) -> Boolean,
  onUseDefaults: (LevelValue) -> Unit,
  modifier: Modifier = Modifier,
) {
  // The level helpers take Settings; they read only the level lists.
  val settings = remember(colorLevels) { Settings(colorLevels = colorLevels) }
  // The one own-levels check per value; the summary, the Default button and the tags share it.
  val own =
    remember(settings) {
      LevelValue.entries.associateWith { LevelPicker.ownLevels(it, settings.customLevels(it)) }
    }
  val customNames =
    LevelValue.entries.filter { own[it] != null }.map { stringResource(valueLabel(it)) }
  val summary =
    if (customNames.isEmpty()) stringResource(R.string.colors_summary_default)
    else stringResource(R.string.colors_summary_custom, customNames.joinToString(", "))

  SectionCard(R.string.colors_title, summary, initiallyExpanded = false, modifier) {
    var editing by rememberSaveable { mutableStateOf<LevelValue?>(null) }
    var confirmDefault by rememberSaveable { mutableStateOf<LevelValue?>(null) }
    val palette = appPalette()
    LevelValue.entries.forEach { value ->
      // Keyed by value: by call order, an editor opened in the same tap that closes another would
      // take over the closed editor's draft and errors.
      key(value) {
        val levels = LevelPicker.effective(value, own[value], basis)
        // Above the editor branch, so the fold state survives an open editor.
        var folded by rememberSaveable { mutableStateOf(true) }
        if (editing == value) {
          LevelEditor(
            value = value,
            stored = settings.customLevels(value),
            shown = levels,
            basis = basis,
            palette = palette,
            errors = errors,
            onSave = { drafts, margin, locale -> onSaveLevels(value, drafts, margin, locale) },
            onClose = { editing = null },
            header = { buttons -> ValueRow(value, buttons = buttons) },
          )
        } else {
          ValueRow(value, expanded = !folded, onToggle = { folded = !folded }) {
            ValueButton(R.string.colors_default, enabled = own[value] != null) {
              confirmDefault = value
            }
            ValueButton(R.string.colors_edit) { editing = value }
          }
          if (!folded) LevelTags(value, levels, palette)
        }
      }
    }
    confirmDefault?.let { value ->
      DefaultDialog(
        value = value,
        onConfirm = {
          confirmDefault = null
          onUseDefaults(value)
        },
        onDismiss = { confirmDefault = null },
      )
    }
  }
}

/**
 * A value's name, then its [buttons]. The name wraps, so a long name keeps the buttons in view.
 * With [onToggle] the name and an arrow form one button that folds the value's tags; [expanded]
 * says whether they show.
 */
@Composable
private fun ValueRow(
  value: LevelValue,
  expanded: Boolean = false,
  onToggle: (() -> Unit)? = null,
  buttons: @Composable RowScope.() -> Unit = {},
) {
  val name = stringResource(valueLabel(value))
  var toggle: Modifier = Modifier.heightIn(min = 48.dp)
  if (onToggle != null) {
    val action =
      stringResource(if (expanded) R.string.section_hide else R.string.section_show, name)
    val state =
      stringResource(if (expanded) R.string.section_expanded else R.string.section_collapsed)
    toggle =
      toggle
        .clickable(onClickLabel = action, role = Role.Button, onClick = onToggle)
        .semantics { stateDescription = state }
  }
  Row(
    Modifier.fillMaxWidth().heightIn(min = 48.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(Modifier.weight(1f).then(toggle), verticalAlignment = Alignment.CenterVertically) {
      Text(
        name,
        Modifier.weight(1f, fill = false).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
      )
      if (onToggle != null) {
        Icon(
          painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
          contentDescription = null, // the row speaks for it
        )
      }
    }
    buttons()
  }
}

/**
 * A value's button, with the narrow text-button padding so two fit beside a long name. [filled]
 * marks the primary action.
 */
@Composable
internal fun ValueButton(
  @StringRes label: Int,
  enabled: Boolean = true,
  filled: Boolean = false,
  onClick: () -> Unit,
) {
  val padding = ButtonDefaults.TextButtonContentPadding
  if (filled) {
    Button(onClick = onClick, enabled = enabled, contentPadding = padding) {
      Text(stringResource(label))
    }
  } else {
    OutlinedButton(onClick = onClick, enabled = enabled, contentPadding = padding) {
      Text(stringResource(label))
    }
  }
}

/** The [levels] of [value], in shown order, as pills in the widget's look, one per line. */
@Composable
private fun LevelTags(value: LevelValue, levels: List<ColorLevel>, palette: WidgetPalette) {
  val locale = LocalConfiguration.current.locales[0]
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    levels.forEach { level ->
      val span = LevelSpans.of(levels, level, value.decimals, value.floor)
      LevelTag(value, level, span, locale, palette)
    }
  }
}

/**
 * One level: its icon, "place · name", such as "↓20 % · Low", and a bell when the level notifies.
 * The colour shows the type, so only TalkBack says it, and TalkBack reads the place in words. A
 * level with no name shows its type word instead.
 */
@Composable
private fun LevelTag(
  value: LevelValue,
  level: ColorLevel,
  span: LevelSpan,
  locale: Locale,
  palette: WidgetPalette,
) {
  val shades = palette.shades(level.shownColor)
  val unit = stringResource(unitLabel(value))
  val resources = LocalResources.current
  val name = level.nameOrNull(resources)
  val type = stringResource(typeLabel(level.type))
  val place = span.placeWithUnit(value.decimals, unit, locale, level.side, resources)
  val text = joined(R.string.colors_two_parts, place, name ?: type)
  val words = spanWords(span, value.decimals, unit, locale)
  val bell = if (level.notify) stringResource(R.string.colors_tag_bell) else null
  val description = joined(R.string.colors_list_parts, words, name, type, bell)
  Row(
    Modifier.background(shades.background, CircleShape)
      .padding(horizontal = 10.dp, vertical = 4.dp)
      .clearAndSetSemantics { contentDescription = description },
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    level.icon?.takeIf { value.hasIcons }?.let {
      Icon(
        painterResource(levelIconRes(it)),
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = shades.text,
      )
    }
    Text(text, color = shades.text, style = MaterialTheme.typography.labelLarge)
    if (level.notify) {
      Icon(
        painterResource(R.drawable.ic_bell),
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = shades.text,
      )
    }
  }
}

/**
 * The [span] in words with the [unit], such as "20 % and below", for TalkBack; `null` for
 * `Anything`. The tags and the editor's middle row read the same words.
 */
@Composable
internal fun spanWords(span: LevelSpan, decimals: Int, unit: String, locale: Locale): String? =
  when (val text = span.spanText(decimals, locale)) {
    is SpanText.AtMost -> stringResource(R.string.colors_place_below, text.number, unit)
    is SpanText.AtLeast -> stringResource(R.string.colors_place_above, text.number, unit)
    is SpanText.Range -> stringResource(R.string.colors_place_between, text.low, text.high, unit)
    is SpanText.Single -> stringResource(R.string.colors_place, text.number, unit)
    null -> null
  }

/** The [parts] that are not null, joined pairwise by the [join] string. One must be there. */
@Composable
private fun joined(@StringRes join: Int, vararg parts: String?): String =
  parts.filterNotNull().reduce { text, part -> stringResource(join, text, part) }

/** Asks before [value] drops its own levels; only [onConfirm] changes anything. */
@Composable
private fun DefaultDialog(value: LevelValue, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = onConfirm) { Text(stringResource(R.string.colors_default_confirm)) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
    },
    title = {
      Text(stringResource(R.string.colors_default_title, stringResource(valueLabel(value))))
    },
    text = { Text(stringResource(R.string.colors_default_text)) },
  )
}

/** The widget palette of the app's theme (the widget follows the same theme choice). */
@Composable
internal fun appPalette(): WidgetPalette =
  if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) WidgetPalette.Dark
  else WidgetPalette.Light

@Composable
internal fun defaultNameText(name: LevelName?): String =
  name?.let { stringResource(levelNameRes(it)) }.orEmpty()

@StringRes
private fun valueLabel(value: LevelValue): Int =
  when (value) {
    LevelValue.SOC -> R.string.colors_value_soc
    LevelValue.VOLTAGE -> R.string.colors_value_voltage
    LevelValue.CHARGE_CURRENT -> R.string.colors_value_charge_current
    LevelValue.DISCHARGE_CURRENT -> R.string.colors_value_discharge_current
    LevelValue.DELTA -> R.string.colors_value_delta
    LevelValue.BATTERY_TEMPERATURE -> R.string.widget_battery_temperature
    LevelValue.MOSFET_TEMPERATURE -> R.string.widget_mosfet_temperature
  }

@StringRes
internal fun colorLabel(color: LevelColor): Int =
  when (color) {
    LevelColor.PLAIN -> R.string.level_color_plain
    LevelColor.RED -> R.string.level_color_red
    LevelColor.ORANGE -> R.string.level_color_orange
    LevelColor.AMBER -> R.string.level_color_amber
    LevelColor.GREEN -> R.string.level_color_green
    LevelColor.TEAL -> R.string.level_color_teal
    LevelColor.BLUE -> R.string.level_color_blue
    LevelColor.VIOLET -> R.string.level_color_violet
    LevelColor.GREY -> R.string.level_color_grey
  }

@StringRes
internal fun iconLabel(icon: LevelIcon?): Int =
  when (icon) {
    null -> R.string.level_icon_none
    LevelIcon.SNOWFLAKE -> R.string.level_icon_snowflake
    LevelIcon.LEAF -> R.string.level_icon_leaf
    LevelIcon.FLAME -> R.string.level_icon_flame
    LevelIcon.FLAME_ALERT -> R.string.level_icon_flame_alert
  }
