package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.model.ColorLevel
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
}
