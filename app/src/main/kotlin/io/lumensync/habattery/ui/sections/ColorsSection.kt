package io.lumensync.habattery.ui.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.customLevels
import io.lumensync.habattery.format.SpanText
import io.lumensync.habattery.format.numberText
import io.lumensync.habattery.format.placeText
import io.lumensync.habattery.format.spanText
import io.lumensync.habattery.ui.LevelDraft
import io.lumensync.habattery.widget.WidgetPalette
import io.lumensync.habattery.widget.levelIconRes
import java.util.Locale

/**
 * The colour levels of every value as tags, with Default and Edit; one editor is open at a time.
 * [basis] gives the built-in edges; with its cells each voltage tag also shows per cell.
 * [onSaveLevels] returns `false` when a field has an error, and the editor stays open.
 */
@Composable
fun ColorsSection(
  colorLevels: Map<String, CustomLevels>,
  basis: LevelBasis,
  errors: List<FieldError>,
  onSaveLevels: (LevelValue, List<LevelDraft>, Locale) -> Boolean,
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
        if (editing == value) {
          ValueRow(value)
          LevelEditor(
            value = value,
            stored = settings.customLevels(value),
            shown = levels,
            basis = basis,
            palette = palette,
            errors = errors,
            onSave = { drafts, locale -> onSaveLevels(value, drafts, locale) },
            onClose = { editing = null },
          )
        } else {
          ValueRow(value) {
            ValueButton(R.string.colors_default, enabled = own[value] != null) {
              confirmDefault = value
            }
            ValueButton(R.string.colors_edit) { editing = value }
          }
          LevelTags(value, levels, basis.cells, palette)
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

/** A value's name, then its [buttons]. The name wraps, so a long name keeps the buttons in view. */
@Composable
private fun ValueRow(value: LevelValue, buttons: @Composable RowScope.() -> Unit = {}) {
  Row(
    Modifier.fillMaxWidth().heightIn(min = 48.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      stringResource(valueLabel(value)),
      Modifier.weight(1f).semantics { heading() },
      style = MaterialTheme.typography.titleSmall,
    )
    buttons()
  }
}

/** A value's button, with the narrow text-button padding so two fit beside a long name. */
@Composable
private fun ValueButton(@StringRes label: Int, enabled: Boolean = true, onClick: () -> Unit) {
  OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    contentPadding = ButtonDefaults.TextButtonContentPadding,
  ) {
    Text(stringResource(label))
  }
}

/**
 * The [levels] of [value], in shown order, as pills in the widget's look. With [cells], each
 * voltage tag also shows its numbers per cell.
 */
@Composable
private fun LevelTags(
  value: LevelValue,
  levels: List<ColorLevel>,
  cells: Int?,
  palette: WidgetPalette,
) {
  val locale = LocalConfiguration.current.locales[0]
  val cellLevels =
    cells?.takeIf { value == LevelValue.VOLTAGE }?.let { LevelPicker.perCell(levels, it) }
  FlowRow(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    levels.forEachIndexed { index, level ->
      val span = LevelSpans.of(levels, level, value.decimals, value.floor)
      val perCell =
        cellLevels?.let { all ->
          // perCell keeps the order of the levels, so the same index is the same level.
          LevelSpans.of(all, all[index], LevelPicker.CELL_DECIMALS, LevelValue.VOLTAGE.floor)
            .numberText(LevelPicker.CELL_DECIMALS, locale, level.side)
        }
      LevelTag(value, level, span, perCell, locale, palette)
    }
  }
}

/**
 * One level: its icon and "place · per cell · name", such as "↓20 % · Low". The colour shows the
 * type, so only TalkBack says it, and TalkBack reads the place in words. A level with no name shows
 * its type word instead.
 */
@Composable
private fun LevelTag(
  value: LevelValue,
  level: ColorLevel,
  span: LevelSpan,
  perCell: String?,
  locale: Locale,
  palette: WidgetPalette,
) {
  val shades = palette.shades(level.shownColor)
  val unit = stringResource(unitLabel(value))
  val name = level.name.ifBlank { defaultNameText(level.defaultName) }.ifBlank { null }
  val type = stringResource(typeLabel(level.type))
  val place =
    span.placeText(value.decimals, locale, level.side)?.let {
      stringResource(R.string.colors_place, it, unit)
    }
  val cellText = perCell?.let { stringResource(R.string.colors_per_cell, it) }
  val text = joined(R.string.colors_two_parts, place, cellText, name ?: type)
  val words = spanWords(span, value.decimals, unit, locale)
  val description = joined(R.string.colors_list_parts, words, name, type)
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
  name?.let { stringResource(nameLabel(it)) }.orEmpty()

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
internal fun unitLabel(value: LevelValue): Int =
  when (value) {
    LevelValue.SOC -> R.string.colors_unit_percent
    LevelValue.VOLTAGE -> R.string.colors_unit_volts
    LevelValue.CHARGE_CURRENT,
    LevelValue.DISCHARGE_CURRENT -> R.string.colors_unit_amps
    LevelValue.DELTA -> R.string.colors_unit_millivolts
    LevelValue.BATTERY_TEMPERATURE,
    LevelValue.MOSFET_TEMPERATURE -> R.string.colors_unit_celsius
  }

@StringRes
internal fun typeLabel(type: LevelType): Int =
  when (type) {
    LevelType.CRITICAL -> R.string.level_type_critical
    LevelType.WARNING -> R.string.level_type_warning
    LevelType.NORMAL -> R.string.level_type_normal
    LevelType.INFO -> R.string.level_type_info
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

@StringRes
private fun nameLabel(name: LevelName): Int =
  when (name) {
    LevelName.LOW -> R.string.level_name_low
    LevelName.GETTING_LOW -> R.string.level_name_getting_low
    LevelName.NORMAL -> R.string.level_name_normal
    LevelName.FULL -> R.string.level_name_full
    LevelName.FREEZING -> R.string.level_name_freezing
    LevelName.COOL -> R.string.level_name_cool
    LevelName.WARM -> R.string.level_name_warm
    LevelName.HOT -> R.string.level_name_hot
    LevelName.BALANCED -> R.string.level_name_balanced
    LevelName.HIGH -> R.string.level_name_high
    LevelName.VERY_HIGH -> R.string.level_name_very_high
    LevelName.LIGHT -> R.string.level_name_light
  }
