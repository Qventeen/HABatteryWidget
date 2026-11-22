package io.lumensync.habattery.ui.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.model.IconSlot
import io.lumensync.habattery.domain.model.InverterIcon
import io.lumensync.habattery.domain.model.InverterSettings
import io.lumensync.habattery.domain.model.icon
import io.lumensync.habattery.format.iconSlotRes
import io.lumensync.habattery.widget.drawable
import io.lumensync.habattery.widget.label

/**
 * The icon of each strip place: one row per place with its icon. A tap opens the grid of every
 * inverter icon; picking one saves it at once and redraws the widgets.
 */
@Composable
fun InverterIconsSection(
  inverter: InverterSettings,
  onSave: (IconSlot, InverterIcon) -> Unit,
  modifier: Modifier = Modifier,
) {
  val changed = IconSlot.entries.any { inverter.icon(it) != it.default }
  val summary =
    if (changed) stringResource(R.string.inverter_icons_summary_custom)
    else stringResource(R.string.inverter_icons_summary_default)

  SectionCard(R.string.inverter_icons_title, summary, initiallyExpanded = false, modifier) {
    var picking by rememberSaveable { mutableStateOf<IconSlot?>(null) }
    IconSlot.entries.forEach { slot -> SlotRow(slot, inverter.icon(slot)) { picking = slot } }
    picking?.let { slot ->
      IconPicker(
        title = stringResource(iconSlotRes(slot)),
        selected = inverter.icon(slot),
        default = slot.default,
        onPick = {
          picking = null
          onSave(slot, it)
        },
        onDismiss = { picking = null },
      )
    }
  }
}

@Composable
private fun SlotRow(slot: IconSlot, icon: InverterIcon, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth()
      .heightIn(min = 48.dp)
      .clickable(
        onClickLabel = stringResource(R.string.inverter_icon_change),
        role = Role.Button,
        onClick = onClick,
      ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(stringResource(iconSlotRes(slot)), Modifier.weight(1f).padding(end = 16.dp))
    Icon(painterResource(icon.drawable()), stringResource(icon.label()), Modifier.size(24.dp))
  }
}

/** Every inverter icon as the level icon picker's chips; a dot marks the [default], if any. */
@Composable
internal fun IconPicker(
  title: String,
  selected: InverterIcon,
  default: InverterIcon?,
  onPick: (InverterIcon) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
    },
    title = { Text(title) },
    text = {
      FlowRow(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup()) {
        InverterIcon.entries.forEach { option ->
          val name = stringResource(option.label())
          val isDefault = option == default
          val description =
            if (isDefault) stringResource(R.string.inverter_icon_default, name) else name
          ChoiceChip(
            selected = option == selected,
            description = description,
            onClick = { onPick(option) },
            marked = isDefault,
          ) {
            Icon(
              painterResource(option.drawable()),
              contentDescription = null, // the chip speaks for it
              modifier = Modifier.fillMaxSize(),
            )
          }
        }
      }
    },
  )
}
