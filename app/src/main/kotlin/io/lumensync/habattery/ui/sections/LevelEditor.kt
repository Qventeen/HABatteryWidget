package io.lumensync.habattery.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
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
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.CustomLevels
import io.lumensync.habattery.domain.LevelBasis
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelIcon
import io.lumensync.habattery.domain.model.LevelSide
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.MAX_LEVELS
import io.lumensync.habattery.ui.LevelDraft
import io.lumensync.habattery.ui.defaultDrafts
import io.lumensync.habattery.ui.edgeOrNull
import io.lumensync.habattery.ui.editorDrafts
import io.lumensync.habattery.ui.withNewLevel
import io.lumensync.habattery.ui.withoutLevel
import io.lumensync.habattery.widget.WidgetPalette
import io.lumensync.habattery.widget.levelIconRes
import java.util.Locale

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
 * The owner's levels of [value] as a draft: the [stored] own levels, or the built-in ones. Nothing
 * is stored before Save; [onSave] returns `false` when a field has an error, and the editor stays
 * open. The rows stay in draft order, so the index of an edge error still names its row. [shown]
 * are the levels the tags show; a new level's id stays above theirs.
 */
@Composable
internal fun LevelEditor(
  value: LevelValue,
  stored: CustomLevels,
  shown: List<ColorLevel>,
  basis: LevelBasis,
  palette: WidgetPalette,
  errors: List<FieldError>,
  onSave: (List<LevelDraft>, Locale) -> Boolean,
  onClose: () -> Unit,
) {
  val locale = LocalConfiguration.current.locales[0]
  // Plain remember: losing an unsaved draft on rotation is accepted. Not keyed by the basis: a
  // sync may bring a new one while the owner types.
  var drafts by
    remember(value, stored) { mutableStateOf(editorDrafts(value, stored, basis, locale)) }
  var resets by remember { mutableIntStateOf(0) }
  // The screen has one error list. Only this editor's own save gives its edge errors, and their
  // indexes no longer match once a row is removed or the draft is reset.
  var ownErrors by remember(value) { mutableStateOf(false) }

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
        resets++
        ownErrors = false
      }
    ) {
      Text(stringResource(R.string.colors_reset))
    }
  }
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    TextButton(onClick = onClose) { Text(stringResource(R.string.dialog_cancel)) }
    SaveButton {
      ownErrors = true
      if (onSave(drafts, locale)) onClose()
    }
  }
}

/**
 * One level in the editor: edge, name, type and remove, colour, and the icon if it has one.
 * [onUpdate] takes a change to apply to the level's current draft.
 */
@Composable
private fun LevelRow(
  value: LevelValue,
  index: Int,
  draft: LevelDraft,
  error: FieldError?,
  palette: WidgetPalette,
  onUpdate: ((LevelDraft) -> LevelDraft) -> Unit,
  onRemove: () -> Unit,
) {
  val shown = draft.color ?: draft.type.defaultColor
  HorizontalDivider()
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    // The middle level has no edge.
    if (draft.side != LevelSide.MIDDLE) {
      LevelTextField(
        text = draft.edgeText,
        onTextChange = { text -> onUpdate { it.copy(edgeText = text) } },
        label = stringResource(R.string.colors_edge),
        keyboardOptions = KeyboardOptions(keyboardType = edgeKeyboard(value)),
        modifier = Modifier.weight(1f),
        error = error,
      )
    }
    LevelTextField(
      text = draft.name,
      onTextChange = { text -> onUpdate { it.copy(name = text) } },
      label = stringResource(R.string.colors_name),
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
      modifier = Modifier.weight(1.5f),
      placeholder = draft.defaultName?.let { defaultNameText(it) },
    )
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    TypeMenu(draft.type) { type -> onUpdate { it.copy(type = type) } }
    Spacer(Modifier.weight(1f))
    if (draft.side != LevelSide.MIDDLE) {
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

/**
 * An outlined field whose label stays on the border, so the [placeholder] shows inside while the
 * field is empty. In Material 3 1.4 only the state-based field takes a label position, so the field
 * keeps its own text: it starts from [text], reports each change to [onTextChange], and ignores a
 * later [text].
 */
@Composable
private fun LevelTextField(
  text: String,
  onTextChange: (String) -> Unit,
  label: String,
  keyboardOptions: KeyboardOptions,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  error: FieldError? = null,
) {
  // Plain remember, like the draft: an unsaved edit may be lost on rotation.
  val state = remember { TextFieldState(text) }
  val report by rememberUpdatedState(onTextChange)
  LaunchedEffect(state) { snapshotFlow { state.text.toString() }.collect { report(it) } }
  OutlinedTextField(
    state = state,
    modifier = modifier,
    labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
    label = { Text(label) },
    placeholder = placeholder?.let { { Text(it) } },
    supportingText = error?.let { { Text(fieldErrorText(it)) } },
    isError = error != null,
    keyboardOptions = keyboardOptions,
    lineLimits = TextFieldLineLimits.SingleLine,
  )
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
