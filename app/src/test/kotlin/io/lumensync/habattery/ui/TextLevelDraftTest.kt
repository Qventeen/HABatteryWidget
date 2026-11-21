package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.DefaultTextLevels
import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.Problem
import io.lumensync.habattery.domain.model.CustomTextLevels
import io.lumensync.habattery.domain.model.InverterIcon
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.TextLevel
import io.lumensync.habattery.domain.model.TextValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLevelDraftTest {
  private val builtIn = DefaultTextLevels.of(TextValue.MODE)
  private val drafts = builtIn.map { it.toDraft() }

  private fun List<TextLevelDraft>.typed(id: Int, text: String) =
    map { if (it.id == id) it.copy(typed = text) else it }

  private fun List<TextLevelDraft>.byId(id: Int) = single { it.id == id }

  @Test
  fun editorDrafts_noOwnList_copiesBuiltIn() =
    assertEquals(drafts, textEditorDrafts(TextValue.MODE, CustomTextLevels()))

  @Test
  fun editorDrafts_ownList_isUsed() {
    val own = builtIn.map { if (it.id == 3) it.copy(name = "Grid") else it }
    val result = textEditorDrafts(TextValue.MODE, CustomTextLevels(on = true, levels = own))
    assertEquals("Grid", result.byId(3).name)
  }

  @Test
  fun withNewMode_beforeOther_bellOff() {
    val result = drafts.withNewMode(builtIn)

    assertEquals(9, result.size)
    val added = result[7]
    assertEquals(9, added.id)
    assertEquals(emptyList<String>(), added.texts)
    assertEquals("", added.name)
    assertEquals(LevelType.INFO, added.type)
    assertNull(added.color)
    assertEquals(InverterIcon.HELP, added.icon)
    assertFalse(added.notify)
    assertFalse(added.other)
    assertTrue(result.last().other)
  }

  @Test
  fun withNewMode_idAboveStored() =
    assertEquals(21, drafts.withNewMode(listOf(TextLevel(id = 20))).first { it.id > 8 }.id)

  @Test
  fun withoutMode_otherStays() {
    assertEquals(drafts, drafts.withoutMode(8))
    val result = drafts.withoutMode(5)
    assertEquals(7, result.size)
    assertTrue(result.none { it.id == 5 })
  }

  @Test
  fun withTypedText_addsTrimmed() {
    val add = drafts.typed(3, " Line ").withTypedText(3)

    assertNull(add.problem)
    assertEquals(listOf("Mains", "Line"), add.drafts.byId(3).texts)
    assertEquals("", add.drafts.byId(3).typed)
  }

  @Test
  fun withTypedText_blank_noChange() {
    val typed = drafts.typed(3, "  ")
    val add = typed.withTypedText(3)

    assertNull(add.problem)
    assertEquals(typed, add.drafts)
  }

  @Test
  fun withTypedText_duplicate() {
    val typed = drafts.typed(4, "mains")
    val add = typed.withTypedText(4)

    assertEquals(Problem.DUPLICATE, add.problem)
    assertEquals(typed, add.drafts)
  }

  @Test
  fun withTypedText_noValue() =
    assertEquals(Problem.INVALID, drafts.typed(3, "unknown").withTypedText(3).problem)

  @Test
  fun withoutText_removes() =
    assertEquals(emptyList<String>(), drafts.withoutText(3, "Mains").byId(3).texts)

  @Test
  fun saveErrors_typedAboveItsChip_atTypedRow() =
    assertEquals(
      listOf(FieldError(Field.TEXT_LEVEL_TEXTS, Problem.DUPLICATE, 1)),
      drafts.typed(2, "Mains").saveErrors(),
    )

  @Test
  fun saveErrors_typedThenAddedBelow_atTypedRow() {
    val added = drafts.withNewMode(builtIn).typed(1, "Line").typed(9, "Line").withTypedText(9)

    assertNull(added.problem)
    assertEquals(
      listOf(FieldError(Field.TEXT_LEVEL_TEXTS, Problem.DUPLICATE, 0)),
      added.drafts.saveErrors(),
    )
  }

  @Test
  fun saveErrors_valid_isEmpty() =
    assertEquals(emptyList<FieldError>(), drafts.typed(3, "Line").saveErrors())

  @Test
  fun toLevel_includesTypedText() {
    val draft = drafts.withNewMode(builtIn)[7].copy(typed = " Utility ")
    assertEquals(listOf("Utility"), draft.toLevel().texts)
  }

  @Test
  fun toLevel_roundTrips() = assertEquals(builtIn, drafts.map { it.toLevel() })
}
