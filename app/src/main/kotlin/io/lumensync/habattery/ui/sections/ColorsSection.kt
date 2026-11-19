package io.lumensync.habattery.ui.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.LevelPicker
import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.CustomLevels
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelIcon
import io.lumensync.habattery.domain.model.LevelName
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.MAX_LEVELS
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.customLevels
import io.lumensync.habattery.format.Formatters
import io.lumensync.habattery.ui.LevelDraft
import io.lumensync.habattery.ui.edgeOrNull
import io.lumensync.habattery.ui.toDraft
import io.lumensync.habattery.ui.withNewLevel
import io.lumensync.habattery.ui.withoutLevel
import io.lumensync.habattery.widget.WidgetPalette
import io.lumensync.habattery.widget.levelIconRes
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The colour levels of every value, one value open at a time. The "Custom colours" switch
 * saves at once; an edited list saves with Save. With the switch off a value shows its built-in
 * levels, read only. [capacityAh] gives the built-in current edges; with [cellCount] each voltage
 * edge also shows per cell.
 */
@Composable
fun ColorsSection(
  colorLevels: Map<String, CustomLevels>,
  capacityAh: Double?,
  cellCount: Int?,
  errors: List<FieldError>,
  onSetCustom: (LevelValue, Boolean) -> Unit,
  onSaveLevels: (LevelValue, List<LevelDraft>, Locale) -> Unit,
  onReset: (LevelValue) -> Unit,
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
    var open by rememberSaveable { mutableStateOf<LevelValue?>(null) }
    val context = LocalContext.current
    val formatters = remember(context) { Formatters(context) }
    val palette = appPalette()
    LevelValue.entries.forEach { value ->
      // Keyed by value: by call order, a value opened in the same tap that closes another would
      // take over the closed value's draft and errors.
      key(value) {
        val isOpen = open == value
        ValueHeader(value, isOpen) { open = if (isOpen) null else value }
        if (isOpen) {
          val ranges = RangeText(value, cellCount, formatters)
          Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val on = settings.customLevels(value).on
            val levels = LevelPicker.effective(value, settings, capacityAh)
            SwitchRow(stringResource(R.string.colors_custom), on) { onSetCustom(value, it) }
            if (on) {
              LevelEditor(value, levels, ranges, palette, errors, onSaveLevels, onReset)
            } else {
              val upper = levels.drop(1).mapNotNull { it.from }.minOrNull()
              levels.forEachIndexed { index, level ->
                LevelLine(value, level, ranges.of(index, level.from, upper), palette)
              }
            }
          }
        }
      }
    }
  }
}

/** One value: its name and a chevron; a tap opens or closes its levels. */
@Composable
private fun ValueHeader(value: LevelValue, expanded: Boolean, onToggle: () -> Unit) {
  val name = stringResource(valueLabel(value))
  val actionLabel =
    stringResource(if (expanded) R.string.section_hide else R.string.section_show, name)
  val stateText =
    stringResource(if (expanded) R.string.section_expanded else R.string.section_collapsed)
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = 48.dp)
        .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onToggle)
        .semantics { stateDescription = stateText },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
    Icon(
      painter =
        painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
      contentDescription = null, // the row speaks for it
    )
  }
}

/**
 * A level's range as text, such as "from 20%", with the units the widget shows. A voltage edge also
 * shows per cell when the cell count is known, as the average cell pill uses it.
 */
private class RangeText(
  private val value: LevelValue,
  private val cellCount: Int?,
  private val formatters: Formatters,
) {
  /** The first level is "below [upper]", the lowest later edge; a later one is "from [edge]". */
  @Composable
  fun of(index: Int, edge: Double?, upper: Double?): String? =
    if (index == 0) upper?.let { range(R.string.colors_below, it) }
    else edge?.let { range(R.string.colors_from, it) }

  @Composable
  private fun range(@StringRes bound: Int, edge: Double): String {
    val range = stringResource(bound, format(edge))
    val cells = cellCount?.takeIf { value == LevelValue.VOLTAGE } ?: return range
    val perCell =
      LevelPicker.perCell(listOf(ColorLevel(from = edge)), cells).single().from ?: return range
    return stringResource(
      R.string.colors_two_parts,
      range,
      stringResource(R.string.colors_per_cell, formatters.cellVoltage(perCell)),
    )
  }

  private fun format(edge: Double): String =
    when (value) {
      LevelValue.SOC -> formatters.chargePercent(edge)
      LevelValue.VOLTAGE -> formatters.voltage(edge)
      LevelValue.CHARGE_CURRENT,
      LevelValue.DISCHARGE_CURRENT -> formatters.current(edge)
      LevelValue.DELTA -> formatters.deltaMv(edge.roundToInt())
      LevelValue.BATTERY_TEMPERATURE,
      LevelValue.MOSFET_TEMPERATURE -> formatters.temperature(edge)
    }
}

/** A built-in level, read only: its colour, its icon, then "range · name · type". */
@Composable
private fun LevelLine(
  value: LevelValue,
  level: ColorLevel,
  range: String?,
  palette: WidgetPalette,
) {
  val shades = palette.shades(level.shownColor)
  val name = level.name.ifBlank { defaultNameText(level.defaultName) }
  val type = stringResource(typeLabel(level.type))
  val colorName = stringResource(colorLabel(level.shownColor))
  Row(
    Modifier.semantics(mergeDescendants = true) {},
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier.size(12.dp)
        .background(shades.text, CircleShape)
        .semantics { contentDescription = colorName }
    )
    level.icon?.takeIf { value.hasIcons }?.let {
      Icon(
        painterResource(levelIconRes(it)),
        contentDescription = stringResource(iconLabel(it)),
        modifier = Modifier.size(16.dp),
        tint = shades.text,
      )
    }
    Text(
      if (range == null) stringResource(R.string.colors_two_parts, name, type)
      else stringResource(R.string.colors_three_parts, range, name, type),
      style = MaterialTheme.typography.bodyMedium,
    )
  }
}

/**
 * The owner's levels of [value] as a draft over [stored]. The rows stay in draft order until a save
 * stores a new list, so the index of an edge error still names its row.
 */
@Composable
private fun LevelEditor(
  value: LevelValue,
  stored: List<ColorLevel>,
  ranges: RangeText,
  palette: WidgetPalette,
  errors: List<FieldError>,
  onSave: (LevelValue, List<LevelDraft>, Locale) -> Unit,
  onReset: (LevelValue) -> Unit,
) {
  val locale = LocalConfiguration.current.locales[0]
  // A reset that stores the same list again does not change the key, yet must drop the draft.
  var resets by remember { mutableIntStateOf(0) }
  // Plain remember: losing an unsaved draft on rotation is accepted.
  var drafts by
    remember(value, stored, resets) { mutableStateOf(stored.map { it.toDraft(value, locale) }) }
  // The screen has one error list. Only this editor's own save gives its edge errors, and their
  // indexes no longer match once a row is removed.
  var ownErrors by remember(value) { mutableStateOf(false) }
  var confirmReset by rememberSaveable { mutableStateOf(false) }

  val edges = drafts.map { it.edgeOrNull(value, locale) }
  // The first level reaches up to the lowest edge; the save sorts the others by edge.
  val upper = edges.drop(1).filterNotNull().minOrNull()
  drafts.forEachIndexed { index, draft ->
    // Keyed by id: after a removal, the fields' own state stays with its level, not its position.
    key(draft.id) {
      LevelRow(
        value = value,
        index = index,
        draft = draft,
        range = ranges.of(index, edges[index], upper),
        error = if (ownErrors) errors.errorFor(Field.LEVEL_EDGE, index) else null,
        canRemove = drafts.size > 1,
        palette = palette,
        onChange = { changed -> drafts = drafts.toMutableList().also { it[index] = changed } },
        onRemove = {
          drafts = drafts.withoutLevel(index) { it.edgeOrNull(value, locale) }
          ownErrors = false
        },
      )
    }
  }
  HorizontalDivider()
  OutlinedButton(
    onClick = { drafts = drafts.withNewLevel(stored) },
    enabled = drafts.size < MAX_LEVELS,
  ) {
    Text(stringResource(R.string.colors_add_level))
  }
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    SaveButton {
      ownErrors = true
      onSave(value, drafts, locale)
    }
    OutlinedButton(onClick = { confirmReset = true }) {
      Text(stringResource(R.string.colors_reset))
    }
  }

  if (confirmReset) {
    AlertDialog(
      onDismissRequest = { confirmReset = false },
      confirmButton = {
        TextButton(
          onClick = {
            confirmReset = false
            ownErrors = false
            resets++
            onReset(value)
          }
        ) {
          Text(stringResource(R.string.colors_reset_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmReset = false }) {
          Text(stringResource(R.string.dialog_cancel))
        }
      },
      title = {
        Text(stringResource(R.string.colors_reset_title, stringResource(valueLabel(value))))
      },
      text = { Text(stringResource(R.string.colors_reset_text)) },
    )
  }
}

/** One level in the editor: range, remove, edge, name, type, colour, and the icon if it has one. */
@Composable
private fun LevelRow(
  value: LevelValue,
  index: Int,
  draft: LevelDraft,
  range: String?,
  error: FieldError?,
  canRemove: Boolean,
  palette: WidgetPalette,
  onChange: (LevelDraft) -> Unit,
  onRemove: () -> Unit,
) {
  val shown = draft.color ?: draft.type.defaultColor
  HorizontalDivider()
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(range.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
    IconButton(onClick = onRemove, enabled = canRemove) {
      Icon(
        painterResource(R.drawable.ic_close),
        contentDescription = stringResource(R.string.colors_remove_level, index + 1),
      )
    }
  }
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    // The first level is open below: the save ignores its edge.
    if (index > 0) {
      OutlinedTextField(
        value = draft.edgeText,
        onValueChange = { onChange(draft.copy(edgeText = it)) },
        modifier = Modifier.weight(1f),
        label = { Text(stringResource(R.string.colors_edge)) },
        supportingText = error?.let { { Text(fieldErrorText(it)) } },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = edgeKeyboard(value)),
      )
    }
    // No label: with one, Material shows the placeholder only while the field has focus, and the
    // untouched default name must stay visible.
    val placeholder =
      draft.defaultName?.let { defaultNameText(it) } ?: stringResource(R.string.colors_name)
    OutlinedTextField(
      value = draft.name,
      onValueChange = { onChange(draft.copy(name = it)) },
      modifier = Modifier.weight(1.5f),
      placeholder = { Text(placeholder) },
      singleLine = true,
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
  }
  TypeMenu(draft.type) { onChange(draft.copy(type = it)) }
  ColorChips(draft.type, shown, palette) { onChange(draft.copy(color = it)) }
  if (value.hasIcons) {
    IconChips(draft.icon, palette.shades(shown).text) { onChange(draft.copy(icon = it)) }
  }
}

@Composable
private fun TypeMenu(type: LevelType, onSelect: (LevelType) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    OutlinedButton(onClick = { expanded = true }) {
      Text(stringResource(typeLabel(type)))
      Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      LevelType.entries.forEach { option ->
        DropdownMenuItem(
          text = { Text(stringResource(typeLabel(option))) },
          onClick = {
            expanded = false
            onSelect(option)
          },
        )
      }
    }
  }
}

/**
 * Plain and the eight colours. The [type]'s own colour carries a dot; picking it stores no colour,
 * so the level keeps following its type.
 */
@Composable
private fun ColorChips(
  type: LevelType,
  shown: LevelColor,
  palette: WidgetPalette,
  onSelect: (LevelColor?) -> Unit,
) {
  val outline = MaterialTheme.colorScheme.outlineVariant
  FlowRow(Modifier.fillMaxWidth().selectableGroup()) {
    LevelColor.entries.forEach { option ->
      val name = stringResource(colorLabel(option))
      val isDefault = option == type.defaultColor
      ChoiceChip(
        selected = option == shown,
        description = if (isDefault) stringResource(R.string.colors_color_default, name) else name,
        marked = isDefault,
        onClick = { onSelect(option.takeUnless { isDefault }) },
      ) {
        Box(
          Modifier.fillMaxSize()
            .background(palette.shades(option).text, CircleShape)
            .border(1.dp, outline, CircleShape)
        )
      }
    }
  }
}

/** No icon and the four level icons, drawn in the level's colour. */
@Composable
private fun IconChips(selected: LevelIcon?, tint: Color, onSelect: (LevelIcon?) -> Unit) {
  val outline = MaterialTheme.colorScheme.outlineVariant
  FlowRow(Modifier.fillMaxWidth().selectableGroup()) {
    (listOf(null) + LevelIcon.entries).forEach { option ->
      ChoiceChip(
        selected = option == selected,
        description = stringResource(iconLabel(option)),
        onClick = { onSelect(option) },
      ) {
        Box(
          Modifier.fillMaxSize().border(1.dp, outline, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          option?.let {
            Icon(
              painterResource(levelIconRes(it)),
              contentDescription = null, // the chip speaks for it
              modifier = Modifier.size(16.dp),
              tint = tint,
            )
          }
        }
      }
    }
  }
}

/** A round radio chip: a ring when [selected], a dot below when [marked]. */
@Composable
private fun ChoiceChip(
  selected: Boolean,
  description: String,
  onClick: () -> Unit,
  marked: Boolean = false,
  content: @Composable () -> Unit,
) {
  val ring = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
  val dot = if (marked) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent
  Column(
    Modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
      .semantics { contentDescription = description }
      .padding(2.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(Modifier.size(32.dp).border(2.dp, ring, CircleShape).padding(4.dp)) { content() }
    Box(Modifier.padding(top = 2.dp).size(4.dp).background(dot, CircleShape))
  }
}

/** The widget palette of the app's theme (the widget follows the same theme choice). */
@Composable
private fun appPalette(): WidgetPalette =
  if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) WidgetPalette.Dark
  else WidgetPalette.Light

@Composable
private fun defaultNameText(name: LevelName?): String =
  name?.let { stringResource(nameLabel(it)) }.orEmpty()

// Temperature edges can be negative; some keyboards (Samsung) give a decimal pad no minus key.
private fun edgeKeyboard(value: LevelValue): KeyboardType =
  when (value) {
    LevelValue.BATTERY_TEMPERATURE,
    LevelValue.MOSFET_TEMPERATURE -> KeyboardType.Text
    LevelValue.SOC,
    LevelValue.VOLTAGE,
    LevelValue.CHARGE_CURRENT,
    LevelValue.DISCHARGE_CURRENT,
    LevelValue.DELTA -> KeyboardType.Decimal
  }

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
private fun typeLabel(type: LevelType): Int =
  when (type) {
    LevelType.CRITICAL -> R.string.level_type_critical
    LevelType.WARNING -> R.string.level_type_warning
    LevelType.NORMAL -> R.string.level_type_normal
    LevelType.INFO -> R.string.level_type_info
  }

@StringRes
private fun colorLabel(color: LevelColor): Int =
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
private fun iconLabel(icon: LevelIcon?): Int =
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
