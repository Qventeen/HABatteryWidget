package io.lumensync.habattery.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.LevelBasis
import io.lumensync.habattery.domain.LevelSpan
import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.CustomLevels
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelIcon
import io.lumensync.habattery.domain.model.LevelSide
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.MAX_LEVELS
import io.lumensync.habattery.format.mark
import io.lumensync.habattery.format.placeWithUnit
import io.lumensync.habattery.format.shownName
import io.lumensync.habattery.format.typeLabel
import io.lumensync.habattery.format.unitLabel
import io.lumensync.habattery.ui.LevelDraft
import io.lumensync.habattery.ui.defaultDrafts
import io.lumensync.habattery.ui.defaultMarginText
import io.lumensync.habattery.ui.editorDrafts
import io.lumensync.habattery.ui.editorMargin
import io.lumensync.habattery.ui.middleSpan
import io.lumensync.habattery.ui.withNewLevel
import io.lumensync.habattery.ui.withoutLevel
import io.lumensync.habattery.widget.WidgetPalette
import io.lumensync.habattery.widget.levelIconRes
import java.util.Locale
import kotlinx.coroutines.launch

/** The picker's colours. Teal is left out: 0012 keeps it for the inverter, a stored teal stays. */
private val PickerColors =
  listOf(
    LevelColor.PLAIN,
    LevelColor.RED,
    LevelColor.ORANGE,
    LevelColor.AMBER,
    LevelColor.GREEN,
    LevelColor.BLUE,
    LevelColor.VIOLET,
    LevelColor.GREY,
  )

/**
 * The owner's levels of [value] as a draft: the [stored] own levels, or the built-in ones, and the
 * re-arm margin, blank for the default of [basis]. Nothing is stored before Save; [onSave] takes
 * the drafts and the margin text and returns `false` when a field has an error, and the editor stays
 * open. The rows stay in draft order, so the index of an edge error still names its row. [shown]
 * are the levels the tags show; a new level's id stays above theirs. The [header] draws the value's
 * name row and places the Cancel and Save buttons it is given, so they stay in view above the rows;
 * they act like the ones at the bottom.
 */
@Composable
internal fun LevelEditor(
  value: LevelValue,
  stored: CustomLevels,
  shown: List<ColorLevel>,
  basis: LevelBasis,
  palette: WidgetPalette,
  errors: List<FieldError>,
  onSave: (List<LevelDraft>, margin: String, Locale) -> Boolean,
  onClose: () -> Unit,
  header: @Composable (buttons: @Composable RowScope.() -> Unit) -> Unit,
) {
  val locale = LocalConfiguration.current.locales[0]
  val focusManager = LocalFocusManager.current
  // Plain remember: losing an unsaved draft on rotation is accepted. Not keyed by the basis: a
  // sync may bring a new one while the owner types.
  var drafts by
    remember(value, stored) { mutableStateOf(editorDrafts(value, stored, basis, locale)) }
  var margin by remember(value, stored) { mutableStateOf(editorMargin(value, stored, locale)) }
  var resets by remember { mutableIntStateOf(0) }
  // The screen has one error list. Only this editor's own save gives its edge and margin errors,
  // and the edge indexes no longer match once a row is removed or the draft is reset.
  var ownErrors by remember(value) { mutableStateOf(false) }
  val unit = stringResource(unitLabel(value))
  val middle = middleSpan(value, drafts, locale)
  val middleRange = middleRangeText(middle, value.decimals, unit, locale)
  val middleWords = middle?.let { spanWords(it, value.decimals, unit, locale) }
  // The one save, for both Save buttons. It clears focus, which hides the keyboard.
  val save = {
    focusManager.clearFocus()
    ownErrors = true
    if (onSave(drafts, margin, locale)) onClose()
  }

  header {
    ValueButton(R.string.dialog_cancel, onClick = onClose)
    ValueButton(
      R.string.action_save,
      filled = true,
      onClick = save,
    )
  }

  // The text fields keep their own text, and the built-in levels reuse ids, so the rows are built
  // again whenever the drafts are: on a reset, and when the stored levels change under the open
  // editor.
  key(stored, resets) {
    drafts.forEachIndexed { index, draft ->
      // Keyed by id: after a removal, the fields' own state stays with its level, not its position.
      key(draft.id) {
        LevelRow(
          value = value,
          index = index,
          draft = draft,
          error = if (ownErrors) errors.errorFor(Field.LEVEL_EDGE, index) else null,
          unit = unit,
          middleRange = middleRange,
          middleWords = middleWords,
          palette = palette,
          // On the current draft by id: a text field reports its change a moment later, when the
          // index and the other fields of the draft it saw may be old.
          onUpdate = { update ->
            drafts = drafts.map { if (it.id == draft.id) update(it) else it }
          },
          onRemove = {
            drafts = drafts.withoutLevel(index)
            ownErrors = false
          },
        )
      }
    }
  }
  HorizontalDivider()
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    OutlinedButton(
      onClick = { drafts = drafts.withNewLevel(shown) },
      enabled = drafts.size < MAX_LEVELS,
    ) {
      Text(stringResource(R.string.colors_add_level))
    }
    OutlinedButton(
      onClick = {
        drafts = defaultDrafts(value, basis, locale)
        margin = ""
        resets++
        ownErrors = false
      }
    ) {
      Text(stringResource(R.string.colors_reset))
    }
  }
  // Rebuilt like the rows: the field keeps its own text and would not show the reset.
  key(stored, resets) {
    MarginField(
      margin = margin,
      error = if (ownErrors) errors.errorFor(Field.LEVEL_MARGIN) else null,
      unit = unit,
      defaultMargin = defaultMarginText(value, basis, locale),
      onMarginChange = { margin = it },
    )
  }
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    TextButton(onClick = onClose) { Text(stringResource(R.string.dialog_cancel)) }
    SaveButton(onClick = save)
  }
}

/**
 * The value's re-arm margin (hysteresis), in its [unit]. An info button inside the field opens a
 * tip. Under it: the [error] of the last save, else the [defaultMargin] that a blank field keeps.
 */
@Composable
private fun MarginField(
  margin: String,
  error: FieldError?,
  unit: String,
  defaultMargin: String,
  onMarginChange: (String) -> Unit,
) {
  LevelTextField(
    text = margin,
    onTextChange = onMarginChange,
    label = stringResource(R.string.level_margin),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    modifier = Modifier.fillMaxWidth(),
    suffix = unit,
    isError = error != null,
    supportingText =
      error?.let { fieldErrorText(it) }
        ?: stringResource(
          R.string.level_margin_default,
          stringResource(R.string.colors_place, defaultMargin, unit),
        ),
    trailingIcon = { MarginInfo() },
  )
}

/**
 * The info button of the margin field. A tap shows a rich tip that stays until a tap outside; a
 * persistent tip is also what TalkBack can read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarginInfo() {
  val tip = rememberTooltipState(isPersistent = true)
  val scope = rememberCoroutineScope()
  TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = { RichTooltip { Text(stringResource(R.string.level_margin_tip)) } },
    state = tip,
  ) {
    IconButton(onClick = { scope.launch { tip.show() } }) {
      Icon(
        painterResource(R.drawable.ic_info),
        contentDescription = stringResource(R.string.level_margin_info),
      )
    }
  }
}

/**
 * One level in the editor, in four parts. The first line holds the name, on a ↓ or ↑ level the
 * limit with its [unit] after the number, and the bell; the middle level has no limit and shows
 * [middleRange] instead, which TalkBack reads as [middleWords]. A limit [error] shows under that
 * line. The second line holds the ↓/↑ choice, the type and the remove button; the middle level has
 * only its type. Last come the colour and, if the value has icons, the icon. [onUpdate] takes a
 * change to apply to the level's current draft.
 */
@Composable
private fun LevelRow(
  value: LevelValue,
  index: Int,
  draft: LevelDraft,
  error: FieldError?,
  unit: String,
  middleRange: String,
  middleWords: String?,
  palette: WidgetPalette,
  onUpdate: ((LevelDraft) -> LevelDraft) -> Unit,
  onRemove: () -> Unit,
) {
  val shown = draft.color ?: draft.type.defaultColor
  val edged = draft.side != LevelSide.MIDDLE
  val errorText = error?.let { fieldErrorText(it) }
  // The bell reads the name the tag would show, also while the name field is empty.
  val bellName =
    ColorLevel(type = draft.type, name = draft.name, defaultName = draft.defaultName)
      .shownName(LocalResources.current)
  HorizontalDivider()
  Row(verticalAlignment = Alignment.CenterVertically) {
    // No gap before the bell: its touch area already pads the icon, and the name needs the width.
    Row(
      Modifier.weight(1f),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LevelTextField(
        text = draft.name,
        onTextChange = { text -> onUpdate { it.copy(name = text) } },
        label = stringResource(R.string.colors_name),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.weight(1f),
        placeholder = draft.defaultName?.let { defaultNameText(it) },
      )
      if (edged) {
        LevelTextField(
          text = draft.edgeText,
          onTextChange = { text -> onUpdate { it.copy(edgeText = text) } },
          label = stringResource(R.string.colors_edge),
          keyboardOptions = KeyboardOptions(keyboardType = edgeKeyboard(value)),
          // The error text under the line is hidden from TalkBack, so the field carries it.
          modifier = Modifier.width(120.dp).semantics { errorText?.let { error(it) } },
          suffix = unit,
          isError = error != null,
        )
      } else {
        Text(
          middleRange,
          Modifier.semantics { middleWords?.let { contentDescription = it } },
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    }
    BellToggle(draft.notify, bellName) { on -> onUpdate { it.copy(notify = on) } }
  }
  errorText?.let {
    Text(
      it,
      Modifier.padding(start = 16.dp).clearAndSetSemantics {},
      color = MaterialTheme.colorScheme.error,
      style = MaterialTheme.typography.bodySmall,
    )
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    if (edged) {
      SideChoice(draft.side) { side -> onUpdate { it.copy(side = side) } }
      Spacer(Modifier.width(8.dp))
    }
    TypeMenu(draft.type, Modifier.weight(1f)) { type -> onUpdate { it.copy(type = type) } }
    if (edged) {
      IconButton(onClick = onRemove) {
        Icon(
          painterResource(R.drawable.ic_close),
          contentDescription = stringResource(R.string.colors_remove_level, index + 1),
        )
      }
    }
  }
  ColorChips(draft.type, shown, palette) { color -> onUpdate { it.copy(color = color) } }
  if (value.hasIcons) {
    IconChips(draft.icon, palette.shades(shown).text) { icon -> onUpdate { it.copy(icon = icon) } }
  }
}

/** The ↓ and ↑ buttons of a level; TalkBack reads them in words. */
@Composable
private fun SideChoice(side: LevelSide, onSelect: (LevelSide) -> Unit) {
  val options =
    listOf(
      LevelSide.BELOW to R.string.colors_side_below,
      LevelSide.ABOVE to R.string.colors_side_above,
    )
  SingleChoiceSegmentedButtonRow {
    options.forEachIndexed { i, (option, words) ->
      val description = stringResource(words)
      SegmentedButton(
        selected = option == side,
        onClick = { onSelect(option) },
        shape = SegmentedButtonDefaults.itemShape(i, options.size),
        // Material sets a 58 dp minimum width after this modifier; 48 dp, still the touch minimum,
        // keeps the pair narrow enough for the type menu beside it.
        modifier = Modifier.width(48.dp).semantics { contentDescription = description },
        // No check mark: the selected button is already filled.
        icon = {},
        label = { Text(option.mark()) },
      )
    }
  }
}

/** A level's bell; TalkBack reads it as a checkbox named after the level's [name]. */
@Composable
private fun BellToggle(on: Boolean, name: String, onChange: (Boolean) -> Unit) {
  IconToggleButton(checked = on, onCheckedChange = onChange) {
    Icon(
      painterResource(if (on) R.drawable.ic_bell else R.drawable.ic_bell_off),
      contentDescription = stringResource(R.string.level_bell, name),
    )
  }
}

/**
 * An outlined field whose label stays on the border, so the [placeholder] shows inside while the
 * field is empty. In Material 3 1.4 only the state-based field takes a label position, so the field
 * keeps its own text: it starts from [text], reports each change to [onTextChange], and ignores a
 * later [text]. The label and the [placeholder] keep to one line and cut their end when the field
 * is too narrow; TalkBack still reads them whole.
 */
@Composable
private fun LevelTextField(
  text: String,
  onTextChange: (String) -> Unit,
  label: String,
  keyboardOptions: KeyboardOptions,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  suffix: String? = null,
  isError: Boolean = false,
  supportingText: String? = null,
  trailingIcon: (@Composable () -> Unit)? = null,
) {
  // Plain remember, like the draft: an unsaved edit may be lost on rotation.
  val state = remember { TextFieldState(text) }
  val report by rememberUpdatedState(onTextChange)
  LaunchedEffect(state) { snapshotFlow { state.text.toString() }.collect { report(it) } }
  OutlinedTextField(
    state = state,
    modifier = modifier,
    labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    placeholder = placeholder?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
    suffix = suffix?.let { { Text(it) } },
    supportingText = supportingText?.let { { Text(it) } },
    trailingIcon = trailingIcon,
    isError = isError,
    keyboardOptions = keyboardOptions,
    lineLimits = TextFieldLineLimits.SingleLine,
  )
}

/**
 * The type button. The [modifier] gives the menu its width: the button keeps its natural width at
 * the start and cuts its text only when that width is smaller.
 */
@Composable
private fun TypeMenu(type: LevelType, modifier: Modifier = Modifier, onSelect: (LevelType) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  Box(modifier) {
    OutlinedButton(
      onClick = { expanded = true },
      contentPadding = PaddingValues(start = 16.dp, end = 12.dp),
    ) {
      Text(
        stringResource(typeLabel(type)),
        Modifier.weight(1f, fill = false),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
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
 * Plain (an empty ring, like "no icon") and the colours. The [type]'s own colour carries a dot;
 * picking it stores no colour, so the level keeps following its type. A [shown] colour the picker
 * does not offer marks no chip.
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
    PickerColors.forEach { option ->
      val name = stringResource(colorLabel(option))
      val isDefault = option == type.defaultColor
      ChoiceChip(
        selected = option == shown,
        description = if (isDefault) stringResource(R.string.colors_color_default, name) else name,
        marked = isDefault,
        onClick = { onSelect(option.takeUnless { isDefault }) },
      ) {
        val fill = if (option == LevelColor.PLAIN) Color.Transparent else palette.shades(option).text
        Box(Modifier.fillMaxSize().background(fill, CircleShape).border(1.dp, outline, CircleShape))
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

/**
 * The middle level's range, such as "↕41–99 %", from [span]; [R.string.colors_range_unknown] when
 * it is `null`. With no other level and no floor the range is anything and only the mark shows.
 */
@Composable
private fun middleRangeText(span: LevelSpan?, decimals: Int, unit: String, locale: Locale): String {
  if (span == null) return stringResource(R.string.colors_range_unknown)
  return span.placeWithUnit(decimals, unit, locale, LevelSide.MIDDLE, LocalResources.current)
    ?: LevelSide.MIDDLE.mark()
}
