package io.lumensync.habattery.ui.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.LevelPicker
import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.CustomLevels
import io.lumensync.habattery.domain.model.LevelBasis
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelIcon
import io.lumensync.habattery.domain.model.LevelName
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.customLevels
import io.lumensync.habattery.format.formatDecimal
import io.lumensync.habattery.ui.LevelDraft
import io.lumensync.habattery.ui.formatEdge
import io.lumensync.habattery.widget.WidgetPalette
import io.lumensync.habattery.widget.levelIconRes
import java.util.Locale
import kotlinx.coroutines.launch

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
  val customNames =
    LevelValue.entries
      .filter { settings.customLevels(it).on }
      .map { stringResource(valueLabel(it)) }
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
        val stored = settings.customLevels(value)
        val levels = LevelPicker.effective(value, settings, basis)
        if (editing == value) {
          ValueRow(value, palette)
          LevelEditor(
            value = value,
            stored = stored,
            shown = levels,
            basis = basis,
            palette = palette,
            errors = errors,
            onSave = { drafts, locale -> onSaveLevels(value, drafts, locale) },
            onClose = { editing = null },
          )
        } else {
          ValueRow(value, palette) {
            ValueButton(R.string.colors_default, enabled = stored.on) { confirmDefault = value }
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

/**
 * A value's title with its unit and the from tag, then its [buttons]. The title wraps, so long
 * titles keep the tag and the buttons on screen.
 */
@Composable
private fun ValueRow(
  value: LevelValue,
  palette: WidgetPalette,
  buttons: @Composable RowScope.() -> Unit = {},
) {
  Row(
    Modifier.fillMaxWidth().heightIn(min = 48.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      Modifier.weight(1f),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        stringResource(
          R.string.colors_value_title,
          stringResource(valueLabel(value)),
          stringResource(unitLabel(value)),
        ),
        Modifier.weight(1f, fill = false).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
      )
      FromTag(palette)
    }
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
 * "↑ from" in the plain pill; a tap shows the hint that every level goes up from its number. The
 * hint stays until the next tap elsewhere: the timed tooltip goes before the sentence can be read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FromTag(palette: WidgetPalette) {
  val shades = palette.shades(LevelColor.PLAIN)
  val hint = stringResource(R.string.colors_from_hint)
  val state = rememberTooltipState(isPersistent = true)
  val scope = rememberCoroutineScope()
  TooltipBox(
    positionProvider =
      TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = { PlainTooltip { Text(hint) } },
    state = state,
    // Only the tap shows the hint; a long press would add a second TalkBack action for it.
    enableUserInput = false,
  ) {
    Row(
      Modifier.minimumInteractiveComponentSize()
        .clip(CircleShape)
        .background(shades.background)
        .clickable(
          onClickLabel = stringResource(R.string.colors_from_hint_action),
          role = Role.Button,
        ) {
          scope.launch { state.show() }
        }
        .clearAndSetSemantics { contentDescription = hint }
        .padding(horizontal = 8.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        painterResource(R.drawable.ic_arrow_up),
        contentDescription = null,
        modifier = Modifier.size(14.dp),
        tint = shades.text,
      )
      Text(
        stringResource(R.string.colors_from_tag),
        color = shades.text,
        style = MaterialTheme.typography.labelMedium,
      )
    }
  }
}

/** The [levels] of [value] as pills in the widget's look; the first level is open below. */
@Composable
private fun LevelTags(
  value: LevelValue,
  levels: List<ColorLevel>,
  cells: Int?,
  palette: WidgetPalette,
) {
  val locale = LocalConfiguration.current.locales[0]
  FlowRow(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    levels.forEachIndexed { index, level ->
      LevelTag(value, level, level.from?.takeIf { index > 0 }, cells, locale, palette)
    }
  }
}

/**
 * One level: its icon and "[start] · name", without the unit (the title has it). The colour shows
 * the type, so only TalkBack says it; a level with no name shows its type word instead.
 */
@Composable
private fun LevelTag(
  value: LevelValue,
  level: ColorLevel,
  start: Double?,
  cells: Int?,
  locale: Locale,
  palette: WidgetPalette,
) {
  val shades = palette.shades(level.shownColor)
  val name = level.name.ifBlank { defaultNameText(level.defaultName) }.ifBlank { null }
  val type = stringResource(typeLabel(level.type))
  val number = start?.let { value.formatEdge(it, locale) }
  val perCell = start?.let { perCellText(value, it, cells, locale) }
  val text = joined(number, perCell, name ?: type)
  val from =
    number?.let { stringResource(R.string.colors_from, it, stringResource(unitLabel(value))) }
  val description = joined(from, perCell, name, type)
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

/** A voltage level's [start] per cell, such as "3.2 per cell"; `null` without [cells]. */
@Composable
private fun perCellText(value: LevelValue, start: Double, cells: Int?, locale: Locale): String? {
  if (value != LevelValue.VOLTAGE || cells == null) return null
  val edge = LevelPicker.perCell(listOf(ColorLevel(from = start)), cells).single().from
  return edge?.let { stringResource(R.string.colors_per_cell, formatDecimal(it, locale)) }
}

/** The [parts] that are not null, joined as " · " in the app's language. One must be there. */
@Composable
private fun joined(vararg parts: String?): String =
  parts.filterNotNull().reduce { text, part ->
    stringResource(R.string.colors_two_parts, text, part)
  }

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
private fun unitLabel(value: LevelValue): Int =
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
