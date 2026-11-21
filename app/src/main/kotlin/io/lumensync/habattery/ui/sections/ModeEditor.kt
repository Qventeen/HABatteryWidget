package io.lumensync.habattery.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.DefaultTextLevels
import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.Problem
import io.lumensync.habattery.domain.model.CustomTextLevels
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.MAX_TEXT_LEVELS
import io.lumensync.habattery.domain.model.TextValue
import io.lumensync.habattery.format.modeWord
import io.lumensync.habattery.format.typeLabel
import io.lumensync.habattery.ui.TextLevelDraft
import io.lumensync.habattery.ui.saveErrors
import io.lumensync.habattery.ui.textEditorDrafts
import io.lumensync.habattery.ui.toLevel
import io.lumensync.habattery.ui.withNewMode
import io.lumensync.habattery.ui.withTypedText
import io.lumensync.habattery.ui.withoutMode
import io.lumensync.habattery.ui.withoutText
import io.lumensync.habattery.widget.WidgetPalette
import io.lumensync.habattery.widget.drawable

/**
 * The modes as a draft: the [stored] own modes, or a copy of the built-in ones. Nothing is stored
 * before Save; [onSave] returns `false` when a mode has an error, and the editor stays open with
 * the first mode in error opened. One mode is open at a time.
 */
@Composable
internal fun ModeEditor(
  value: TextValue,
  stored: CustomTextLevels,
  liveText: String?,
  palette: WidgetPalette,
  errors: List<FieldError>,
  onSave: (List<TextLevelDraft>) -> Boolean,
  onClose: () -> Unit,
) {
  val focusManager = LocalFocusManager.current
  // Plain remember, like the level editor: an unsaved draft may be lost on rotation.
  var drafts by remember(stored) { mutableStateOf(textEditorDrafts(value, stored)) }
  var openId by remember(stored) { mutableStateOf<Int?>(null) }
  // Why the + button kept a mode's typed text; it goes when that text changes.
  var addProblems by remember(stored) { mutableStateOf(emptyMap<Int, Problem>()) }
  // The screen has one error list. Only this editor's own save gives its errors, and their indexes
  // no longer match once a mode is deleted.
  var ownErrors by remember { mutableStateOf(false) }
  val save: () -> Unit = {
    focusManager.clearFocus()
    ownErrors = true
    if (onSave(drafts)) {
      onClose()
    } else {
      // The same check the save ran: its errors reach the screen only on the next frame.
      drafts.saveErrors()
        .mapNotNull { it.index }
        .minOrNull()
        ?.let { openId = drafts[it].id }
    }
  }
  fun update(id: Int, change: (TextLevelDraft) -> TextLevelDraft) {
    drafts = drafts.map { if (it.id == id) change(it) else it }
  }

  ButtonRow {
    ValueButton(R.string.dialog_cancel, onClick = onClose)
    ValueButton(R.string.action_save, filled = true, onClick = save)
  }
  // The text fields keep their own text, so the rows are built again when the stored modes change
  // under the open editor.
  key(stored) {
    drafts.forEachIndexed { index, draft ->
      key(draft.id) {
        ModeRow(
          value = value,
          draft = draft,
          open = openId == draft.id,
          error = if (ownErrors) errors.errorFor(Field.TEXT_LEVEL_TEXTS, index) else null,
          addProblem = addProblems[draft.id],
          palette = palette,
          onToggle = { openId = if (openId == draft.id) null else draft.id },
          onUpdate = { change -> update(draft.id, change) },
          onTyped = { text ->
            update(draft.id) { it.copy(typed = text) }
            addProblems = addProblems - draft.id
          },
          onAddText = {
            val add = drafts.withTypedText(draft.id)
            drafts = add.drafts
            addProblems =
              add.problem?.let { addProblems + (draft.id to it) } ?: (addProblems - draft.id)
          },
          onRemoveText = { text -> drafts = drafts.withoutText(draft.id, text) },
          onDelete = {
            drafts = drafts.withoutMode(draft.id)
            openId = null
            ownErrors = false
          },
        )
      }
    }
  }
  HorizontalDivider()
  if (drafts.size < MAX_TEXT_LEVELS) {
    OutlinedButton(
      onClick = {
        val before = drafts.map { it.id }.toSet()
        drafts = drafts.withNewMode(stored.levels)
        openId = drafts.firstOrNull { it.id !in before }?.id
      }
    ) {
      Icon(painterResource(R.drawable.ic_add), contentDescription = null)
      Text(stringResource(R.string.mode_add))
    }
  }
  if (ownErrors) {
    errors.errorFor(Field.TEXT_LEVEL_TEXTS)?.let {
      Text(
        fieldErrorText(it),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
  NowLine(drafts.map { it.toLevel() }, liveText)
  ButtonRow {
    TextButton(onClick = onClose) { Text(stringResource(R.string.dialog_cancel)) }
    SaveButton(onClick = save)
  }
}

/**
 * One mode of the draft: its pill and word (a button that opens it), its type tag menu and its bell
 * at the far right. Open,
 * it shows the icon, the name, the texts as chips with an "Add text" field, the colours and Delete;
 * "other" has no name, texts or Delete. A save [error] shows under "Add text", or as a mark on the
 * closed row; [addProblem] is why the + button kept the typed text.
 */
@Composable
private fun ModeRow(
  value: TextValue,
  draft: TextLevelDraft,
  open: Boolean,
  error: FieldError?,
  addProblem: Problem?,
  palette: WidgetPalette,
  onToggle: () -> Unit,
  onUpdate: ((TextLevelDraft) -> TextLevelDraft) -> Unit,
  onTyped: (String) -> Unit,
  onAddText: () -> Unit,
  onRemoveText: (String) -> Unit,
  onDelete: () -> Unit,
) {
  val resources = LocalResources.current
  val shown = draft.color ?: draft.type.defaultColor
  val shades = palette.shades(shown)
  val word = modeWord(resources, draft.toLevel(), liveText = null)
    .ifEmpty { stringResource(R.string.value_missing) }
  val errorText = error?.let { fieldErrorText(it) }
  HorizontalDivider()
  Row(verticalAlignment = Alignment.CenterVertically) {
    val action = stringResource(if (open) R.string.section_hide else R.string.section_show, word)
    Row(
      Modifier.weight(1f)
        .heightIn(min = 48.dp)
        .clickable(onClickLabel = action, role = Role.Button, onClick = onToggle),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ModePill(draft.icon, shades)
      Text(
        word,
        Modifier.weight(1f, fill = false),
        color = shades.text,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (errorText != null && !open) {
        Icon(
          painterResource(R.drawable.ic_alert),
          contentDescription = errorText,
          modifier = Modifier.size(18.dp),
          tint = MaterialTheme.colorScheme.error,
        )
      }
      Icon(
        painterResource(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
        contentDescription = null, // the row speaks for it
      )
    }
    ModeTypeMenu(draft.type, palette) { type -> onUpdate { it.copy(type = type) } }
    BellToggle(draft.notify, word) { on -> onUpdate { it.copy(notify = on) } }
  }
  if (open) {
    Column(
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ModeIconButton(value, draft, word, shades.text, onUpdate)
        if (!draft.other) {
          val defaultWord =
            modeWord(resources, draft.toLevel().copy(name = ""), liveText = null)
              .ifEmpty { stringResource(R.string.value_missing) }
          LevelTextField(
            text = draft.name,
            onTextChange = { text -> onUpdate { it.copy(name = text) } },
            label = stringResource(R.string.colors_name),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.weight(1f),
            placeholder = defaultWord,
            supportingText = stringResource(R.string.mode_name_hint, defaultWord),
          )
        }
      }
      if (!draft.other) {
        ModeTexts(draft, errorText, addProblem, onTyped, onAddText, onRemoveText)
      }
      ColorChips(draft.type, shown, palette, colors = ModeColors) { color ->
        onUpdate { it.copy(color = color) }
      }
      if (!draft.other) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onDelete) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = null)
            Text(stringResource(R.string.mode_delete))
          }
        }
      }
    }
  }
}

/** The type tag as a button; its menu lists the full type words. */
@Composable
private fun ModeTypeMenu(type: LevelType, palette: WidgetPalette, onSelect: (LevelType) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    TypeTag(
      type,
      palette,
      Modifier.minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(12.dp))
        .clickable(role = Role.Button) { expanded = true },
      menu = true,
    )
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

/** The mode's icon as a button; it opens the grid of every inverter icon. */
@Composable
private fun ModeIconButton(
  value: TextValue,
  draft: TextLevelDraft,
  word: String,
  tint: Color,
  onUpdate: ((TextLevelDraft) -> TextLevelDraft) -> Unit,
) {
  var picking by remember { mutableStateOf(false) }
  OutlinedButton(
    onClick = { picking = true },
    modifier = Modifier.padding(top = 8.dp),
    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
  ) {
    Icon(
      painterResource(draft.icon.drawable()),
      contentDescription = stringResource(R.string.mode_icon_button, word),
      tint = tint,
    )
    Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null)
  }
  if (picking) {
    IconPicker(
      title = word,
      selected = draft.icon,
      // Only the built-in modes have a default icon to mark.
      default = DefaultTextLevels.of(value).firstOrNull { it.id == draft.id }?.shownIcon,
      onPick = { icon ->
        picking = false
        onUpdate { it.copy(icon = icon) }
      },
      onDismiss = { picking = false },
    )
  }
}

/**
 * The mode's texts as chips (✕ removes one), then the "Add text" field and its + button. Under the
 * field: why + kept the text, else the save [errorText], else the hint.
 */
@Composable
private fun ModeTexts(
  draft: TextLevelDraft,
  errorText: String?,
  addProblem: Problem?,
  onTyped: (String) -> Unit,
  onAddText: () -> Unit,
  onRemoveText: (String) -> Unit,
) {
  Text(
    stringResource(R.string.mode_texts_label),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.labelMedium,
  )
  if (draft.texts.isNotEmpty()) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      draft.texts.forEach { text ->
        InputChip(
          selected = false,
          onClick = { onRemoveText(text) },
          label = { Text(text) },
          trailingIcon = {
            Icon(
              painterResource(R.drawable.ic_close),
              contentDescription = stringResource(R.string.mode_remove_text, text),
              modifier = Modifier.size(18.dp),
            )
          },
        )
      }
    }
  }
  val problemText =
    addProblem?.let { fieldErrorText(FieldError(Field.TEXT_LEVEL_TEXTS, it)) } ?: errorText
  Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    // The field keeps its own text: built again after a chip comes or goes, so an added text
    // leaves it empty and a kept one stays.
    key(draft.texts) {
      LevelTextField(
        text = draft.typed,
        onTextChange = onTyped,
        label = stringResource(R.string.mode_add_text_label),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
        // The text under the line is hidden from TalkBack, so the field carries the problem.
        modifier = Modifier.weight(1f).semantics { problemText?.let { error(it) } },
        placeholder = stringResource(R.string.mode_add_text_placeholder),
        isError = problemText != null,
        supportingText = problemText ?: stringResource(R.string.mode_add_text_hint),
        onKeyboardAction = { onAddText() },
      )
    }
    FilledIconButton(onClick = onAddText, modifier = Modifier.padding(top = 8.dp)) {
      Icon(
        painterResource(R.drawable.ic_add),
        contentDescription = stringResource(R.string.mode_add_text_label),
      )
    }
  }
}
