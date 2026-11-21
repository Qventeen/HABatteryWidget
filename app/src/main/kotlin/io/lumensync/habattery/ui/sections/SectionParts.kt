package io.lumensync.habattery.ui.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.model.FontPercents
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Keyboard for entity IDs and URLs: no auto-correct, no capitals. */
internal val IdKeyboard = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii)

/**
 * A card for one settings section. The header (title, [summary], chevron) is one button that folds
 * the card; the content is not composed while it is folded. The fold state survives rotation.
 */
@Composable
internal fun SectionCard(
  @StringRes title: Int,
  summary: String,
  initiallyExpanded: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
  val titleText = stringResource(title)
  val actionLabel =
    stringResource(if (expanded) R.string.section_hide else R.string.section_show, titleText)
  val stateText =
    stringResource(if (expanded) R.string.section_expanded else R.string.section_collapsed)
  OutlinedCard(modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = actionLabel, role = Role.Button) { expanded = !expanded }
            .semantics { stateDescription = stateText },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
          Text(titleText, style = MaterialTheme.typography.titleMedium)
          Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Icon(
          painter =
            painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
          contentDescription = null, // the row speaks for it
        )
      }
      if (expanded) content()
    }
  }
}

/**
 * Saves the section. It also clears focus, which hides the keyboard: a field left focused would open
 * it again when the user comes back to the app. A validation error stays visible below its field.
 */
@Composable
internal fun SaveButton(enabled: Boolean = true, onClick: () -> Unit) {
  val focusManager = LocalFocusManager.current
  Button(
    enabled = enabled,
    onClick = {
      focusManager.clearFocus()
      onClick()
    }
  ) {
    Text(stringResource(R.string.action_save))
  }
}

/** A one-line input. Below it: the error from the last save, else the [hint]. */
@Composable
internal fun SettingsTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  error: FieldError?,
  hint: String? = null,
  keyboardOptions: KeyboardOptions = IdKeyboard,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  trailingIcon: @Composable (() -> Unit)? = null,
) {
  val below = if (error != null) fieldErrorText(error) else hint
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = Modifier.fillMaxWidth(),
    label = { Text(label) },
    supportingText = below?.let { { Text(it) } },
    isError = error != null,
    singleLine = true,
    keyboardOptions = keyboardOptions,
    visualTransformation = visualTransformation,
    trailingIcon = trailingIcon,
  )
}

/**
 * An ⓘ button for a field's trailing icon. The tip is persistent: it stays until a tap outside, so
 * a long text can be read, and TalkBack can read it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InfoTip(@StringRes tip: Int, @StringRes description: Int) {
  val state = rememberTooltipState(isPersistent = true)
  val scope = rememberCoroutineScope()
  TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = { RichTooltip { Text(stringResource(tip)) } },
    state = state,
  ) {
    IconButton(onClick = { scope.launch { state.show() } }) {
      Icon(painterResource(R.drawable.ic_info), contentDescription = stringResource(description))
    }
  }
}

/** A label with a switch; the whole row toggles. */
@Composable
internal fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, modifier = Modifier.weight(1f).padding(end = 16.dp))
    Switch(checked = checked, onCheckedChange = null)
  }
}

/** The error the last save reported for [field] (and [index] for one list item), if any. */
internal fun List<FieldError>.errorFor(field: Field, index: Int? = null): FieldError? =
  firstOrNull { it.field == field && it.index == index }

/** A titled row of segmented buttons; one is selected. */
@Composable
internal fun <T> ChoiceRow(
  @StringRes title: Int,
  options: List<T>,
  selected: T,
  label: (T) -> Int,
  modifier: Modifier = Modifier,
  onSelect: (T) -> Unit,
) {
  Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
  SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
    options.forEachIndexed { index, option ->
      SegmentedButton(
        selected = option == selected,
        onClick = { onSelect(option) },
        shape = SegmentedButtonDefaults.itemShape(index, options.size),
      ) {
        Text(stringResource(label(option)))
      }
    }
  }
}

/**
 * One font size: its [label], the percent, and a slider from 10 to 100 % in steps of 5. It saves
 * when released.
 */
@Composable
internal fun FontSlider(@StringRes label: Int, stored: Int, onSave: (Int) -> Unit) {
  var dragValue by rememberSaveable { mutableStateOf<Int?>(null) }
  val value = dragValue ?: stored
  val name = stringResource(label)
  val shown = stringResource(R.string.widget_font_value, value)
  Row(Modifier.fillMaxWidth()) {
    Text(name, Modifier.weight(1f))
    Text(shown)
  }
  Slider(
    value = value.toFloat(),
    onValueChange = { dragValue = it.roundToInt() },
    onValueChangeFinished = {
      onSave(dragValue ?: stored)
      dragValue = null
    },
    valueRange = FontPercents.MIN.toFloat()..FontPercents.MAX.toFloat(),
    steps = (FontPercents.MAX - FontPercents.MIN) / FontPercents.STEP - 1,
    modifier =
      Modifier.semantics {
        contentDescription = name
        stateDescription = shown
      },
  )
}
