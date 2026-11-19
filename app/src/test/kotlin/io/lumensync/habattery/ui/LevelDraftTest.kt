package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.CustomLevels
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LevelDraftTest {
  private val uk = Locale.forLanguageTag("uk")

  private fun draft(id: Int, edge: String) =
    LevelDraft(id, edge, LevelType.NORMAL, null, null, "", null)

  private fun List<LevelDraft>.ids() = map { it.id }

  private fun edgeOf(d: LevelDraft): Double? = d.edgeOrNull(LevelValue.SOC, Locale.US)

  @Test
  fun edgeOrNull_parsesAndRoundsAsTheSave() {
    assertEquals(52.5, draft(1, "52,5").edgeOrNull(LevelValue.VOLTAGE, uk)!!, 0.0)
    assertEquals(12.0, draft(1, "12.4").edgeOrNull(LevelValue.DELTA, Locale.US)!!, 0.0)
    assertEquals(-5.0, draft(1, "-5").edgeOrNull(LevelValue.BATTERY_TEMPERATURE, uk)!!, 0.0)
    assertNull(draft(1, "").edgeOrNull(LevelValue.SOC, Locale.US))
    assertNull(draft(1, "2x").edgeOrNull(LevelValue.SOC, Locale.US))
  }

  @Test
  fun withNewLevel_addsAnEmptyEdgeAboveEveryId() {
    val stored = listOf(ColorLevel(id = 1), ColorLevel(id = 5, from = 20.0))
    // Level 5 was removed from the draft; its id stays taken until the save.
    val added = listOf(draft(1, ""), draft(2, "40")).withNewLevel(stored)
    assertEquals(listOf(1, 2, 6), added.ids())
    assertEquals("", added.last().edgeText)
    assertNull(added.last().defaultName)
  }

  @Test
  fun withNewLevel_emptyLists_startsAtOne() =
    assertEquals(listOf(1), emptyList<LevelDraft>().withNewLevel(emptyList()).ids())

  @Test
  fun withoutLevel_later_keepsTheOrder() {
    val drafts = listOf(draft(1, ""), draft(2, "20"), draft(3, "40"), draft(4, "100"))
    assertEquals(listOf(1, 2, 4), drafts.withoutLevel(2, ::edgeOf).ids())
  }

  @Test
  fun withoutLevel_first_movesTheLowestEdgeToTheFront() {
    val drafts = listOf(draft(1, ""), draft(2, "40"), draft(3, "20"), draft(4, ""))
    assertEquals(listOf(3, 2, 4), drafts.withoutLevel(0, ::edgeOf).ids())
  }

  @Test
  fun withoutLevel_first_noNumber_takesTheNextRow() {
    val drafts = listOf(draft(1, ""), draft(2, ""), draft(3, "x"))
    assertEquals(listOf(2, 3), drafts.withoutLevel(0, ::edgeOf).ids())
  }

  @Test
  fun withoutLevel_onlyLevel_leavesNone() =
    assertEquals(emptyList<Int>(), listOf(draft(1, "")).withoutLevel(0, ::edgeOf).ids())

  private fun level(id: Int, from: Double?) = ColorLevel(id = id, from = from)

  @Test
  fun editorDrafts_ownLevels() {
    val custom = CustomLevels(true, listOf(level(1, null), level(7, 30.0)))
    val drafts = editorDrafts(LevelValue.SOC, custom, null, Locale.US)
    assertEquals(listOf(1, 7), drafts.ids())
    assertEquals(listOf("", "30"), drafts.map { it.edgeText })
  }

  @Test
  fun editorDrafts_leftoverListWhenOff_usesDefaults() {
    val custom = CustomLevels(false, listOf(level(9, null)))
    val drafts = editorDrafts(LevelValue.SOC, custom, null, Locale.US)
    assertEquals(defaultDrafts(LevelValue.SOC, null, Locale.US), drafts)
    assertEquals(listOf("", "20", "40", "100"), drafts.map { it.edgeText })
  }

  @Test
  fun editorDrafts_onButEmpty_usesDefaults() {
    val drafts = editorDrafts(LevelValue.SOC, CustomLevels(true, emptyList()), null, Locale.US)
    assertEquals(defaultDrafts(LevelValue.SOC, null, Locale.US), drafts)
  }

  @Test
  fun defaultDrafts_current100Ah() {
    val drafts = defaultDrafts(LevelValue.CHARGE_CURRENT, 100.0, Locale.US)
    assertEquals(listOf("", "20", "50", "80"), drafts.map { it.edgeText })
  }
}
