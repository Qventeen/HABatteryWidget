package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.CustomLevels
import io.lumensync.habattery.domain.LevelBasis
import io.lumensync.habattery.domain.LevelSpan
import io.lumensync.habattery.domain.model.LevelSide
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LevelDraftTest {
  private val uk = Locale.forLanguageTag("uk")

  private fun draft(id: Int, edge: String, side: LevelSide = LevelSide.ABOVE) =
    LevelDraft(id, side, edge, LevelType.NORMAL, null, null, "", null)

  private fun List<LevelDraft>.ids() = map { it.id }

  @Test
  fun edgeOrNull_parsesAndRoundsAsTheSave() {
    assertEquals(52.5, draft(1, "52,5").edgeOrNull(LevelValue.VOLTAGE, uk)!!, 0.0)
    assertEquals(12.0, draft(1, "12.4").edgeOrNull(LevelValue.DELTA, Locale.US)!!, 0.0)
    assertEquals(-5.0, draft(1, "-5").edgeOrNull(LevelValue.BATTERY_TEMPERATURE, uk)!!, 0.0)
    assertNull(draft(1, "").edgeOrNull(LevelValue.SOC, Locale.US))
    assertNull(draft(1, "2x").edgeOrNull(LevelValue.SOC, Locale.US))
  }

  private val noBasis = LevelBasis(null, null)

  @Test
  fun toDraft_keepsTheSide() {
    val below = ColorLevel(id = 1, side = LevelSide.BELOW, edge = 20.0)
    val middle = ColorLevel(id = 2)
    assertEquals(LevelSide.BELOW, below.toDraft(LevelValue.SOC, Locale.US).side)
    assertEquals("20", below.toDraft(LevelValue.SOC, Locale.US).edgeText)
    assertEquals(LevelSide.MIDDLE, middle.toDraft(LevelValue.SOC, Locale.US).side)
    assertEquals("", middle.toDraft(LevelValue.SOC, Locale.US).edgeText)
  }

  @Test
  fun withNewLevel_addsAnAboveLevelWithAnEmptyEdgeAboveEveryId() {
    val stored = listOf(ColorLevel(id = 1), ColorLevel(id = 5, side = LevelSide.ABOVE, edge = 20.0))
    // Level 5 was removed from the draft; its id stays taken until the save.
    val added = listOf(draft(1, "", LevelSide.MIDDLE), draft(2, "40")).withNewLevel(stored)
    assertEquals(listOf(1, 2, 6), added.ids())
    assertEquals(LevelSide.ABOVE, added.last().side)
    assertEquals("", added.last().edgeText)
    assertNull(added.last().defaultName)
  }

  @Test
  fun withNewLevel_emptyLists_startsAtOne() =
    assertEquals(listOf(1), emptyList<LevelDraft>().withNewLevel(emptyList()).ids())

  @Test
  fun withoutLevel_middle_isUnchanged() {
    val drafts = defaultDrafts(LevelValue.SOC, noBasis, Locale.US)
    assertEquals(drafts, drafts.withoutLevel(drafts.indexOfFirst { it.side == LevelSide.MIDDLE }))
  }

  @Test
  fun withoutLevel_removesOnlyThatRow() {
    val drafts = defaultDrafts(LevelValue.SOC, noBasis, Locale.US)
    val rest = drafts.withoutLevel(0)
    assertEquals(drafts.drop(1), rest)
    assertEquals(listOf("40", "", "100"), rest.map { it.edgeText })
  }

  @Test
  fun editorDrafts_ownLevels() {
    val custom =
      CustomLevels(
        true,
        listOf(ColorLevel(id = 7, side = LevelSide.ABOVE, edge = 30.0), ColorLevel(id = 1)),
      )
    val drafts = editorDrafts(LevelValue.SOC, custom, noBasis, Locale.US)
    assertEquals(listOf(1, 7), drafts.ids())
    assertEquals(listOf("", "30"), drafts.map { it.edgeText })
  }

  @Test
  fun editorDrafts_roundTwoShape_usesDefaults() {
    val custom = CustomLevels(true, listOf(ColorLevel(id = 1), ColorLevel(id = 2)))
    assertEquals(
      defaultDrafts(LevelValue.SOC, noBasis, Locale.US),
      editorDrafts(LevelValue.SOC, custom, noBasis, Locale.US),
    )
  }

  @Test
  fun editorDrafts_leftoverListWhenOff_usesDefaults() {
    val custom = CustomLevels(false, listOf(ColorLevel(id = 9)))
    val drafts = editorDrafts(LevelValue.SOC, custom, noBasis, Locale.US)
    assertEquals(defaultDrafts(LevelValue.SOC, noBasis, Locale.US), drafts)
    assertEquals(listOf("20", "40", "", "100"), drafts.map { it.edgeText })
  }

  @Test
  fun editorDrafts_onButEmpty_usesDefaults() {
    val drafts = editorDrafts(LevelValue.SOC, CustomLevels(true, emptyList()), noBasis, Locale.US)
    assertEquals(defaultDrafts(LevelValue.SOC, noBasis, Locale.US), drafts)
  }

  @Test
  fun defaultDrafts_carryTheDefaultBells() =
    assertEquals(
      listOf(true, false, false, true),
      defaultDrafts(LevelValue.SOC, noBasis, Locale.US).map { it.notify },
    )

  @Test
  fun toDraft_keepsTheBell() =
    assertEquals(true, ColorLevel(id = 1, notify = true).toDraft(LevelValue.SOC, Locale.US).notify)

  @Test
  fun withNewLevel_hasNoBell() =
    assertEquals(false, emptyList<LevelDraft>().withNewLevel(emptyList()).last().notify)

  private val ownVoltage =
    CustomLevels(
      true,
      listOf(ColorLevel(id = 1), ColorLevel(id = 2, side = LevelSide.ABOVE, edge = 55.2)),
      margin = 0.2,
    )

  @Test
  fun editorMargin_ownMargin_inTheLocale() =
    assertEquals("0,2", editorMargin(LevelValue.VOLTAGE, ownVoltage, uk))

  @Test
  fun editorMargin_noOwnMargin_isBlank() =
    assertEquals("", editorMargin(LevelValue.VOLTAGE, ownVoltage.copy(margin = null), uk))

  @Test
  fun editorMargin_withoutOwnLevels_isBlank() =
    assertEquals("", editorMargin(LevelValue.VOLTAGE, ownVoltage.copy(on = false), uk))

  @Test
  fun defaultMarginText_followsTheValue() {
    assertEquals("2", defaultMarginText(LevelValue.SOC, noBasis, Locale.US))
    assertEquals("0,1", defaultMarginText(LevelValue.VOLTAGE, noBasis, uk))
    assertEquals("5,3", defaultMarginText(LevelValue.CHARGE_CURRENT, LevelBasis(105.0, null), uk))
    assertEquals("1", defaultMarginText(LevelValue.DISCHARGE_CURRENT, noBasis, Locale.US))
  }

  @Test
  fun defaultDrafts_current100Ah() {
    val drafts = defaultDrafts(LevelValue.CHARGE_CURRENT, LevelBasis(100.0, null), Locale.US)
    assertEquals(
      listOf(LevelSide.BELOW, LevelSide.MIDDLE, LevelSide.ABOVE, LevelSide.ABOVE),
      drafts.map { it.side },
    )
    assertEquals(listOf("20", "", "50", "80"), drafts.map { it.edgeText })
  }

  @Test
  fun defaultDrafts_voltage16cells() {
    val drafts = defaultDrafts(LevelValue.VOLTAGE, LevelBasis(null, 16), Locale.US)
    assertEquals(listOf("48", "51.2", "", "55.2", "56.8", "58.4"), drafts.map { it.edgeText })
  }

  private val below = LevelSide.BELOW
  private val middle = LevelSide.MIDDLE

  @Test
  fun middleSpan_gapOfOneNumber_isBetweenTheSame() =
    assertEquals(
      LevelSpan.Between(41.0, 41.0),
      middleSpan(
        LevelValue.SOC,
        listOf(draft(1, "40", below), draft(2, "", middle), draft(3, "42")),
        Locale.US,
      ),
    )

  @Test
  fun middleSpan_emptyLimit_isLeftOut() =
    assertEquals(
      LevelSpan.AtOrAbove(21.0),
      middleSpan(
        LevelValue.SOC,
        listOf(draft(1, "20", below), draft(2, "", middle), draft(3, "")),
        Locale.US,
      ),
    )

  @Test
  fun middleSpan_breakingRule_isNull() =
    assertNull(
      middleSpan(
        LevelValue.SOC,
        listOf(draft(1, "40", below), draft(2, "", middle), draft(3, "41")),
        Locale.US,
      )
    )

  @Test
  fun middleSpan_notANumber_isLeftOut() =
    assertEquals(
      LevelSpan.AtOrBelow(99.0),
      middleSpan(
        LevelValue.SOC,
        listOf(draft(1, "4a", below), draft(2, "", middle), draft(3, "100")),
        Locale.US,
      ),
    )

  @Test
  fun middleSpan_onlyTheMiddle_isAnythingWithoutAFloor() =
    assertEquals(
      LevelSpan.Anything,
      middleSpan(
        LevelValue.BATTERY_TEMPERATURE,
        listOf(draft(1, "", middle)),
        Locale.US,
      ),
    )
}
