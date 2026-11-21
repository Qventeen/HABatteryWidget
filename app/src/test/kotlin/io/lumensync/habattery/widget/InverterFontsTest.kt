package io.lumensync.habattery.widget

import io.lumensync.habattery.domain.model.InverterFontPercents
import io.lumensync.habattery.domain.model.ModeStyle
import io.lumensync.habattery.widget.InverterLayoutSpec.S3X1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The strip's fitting with a fake measurer (every character half the size wide). */
class InverterFontsTest {
  private fun texts(
    word: String = "battery",
    grid: String = "0 V",
    output: String = "230 V",
    error: String? = null,
  ) = StripTexts(word, grid, output, error)

  private fun fonts(
    style: ModeStyle = ModeStyle.ICON_WORD,
    texts: StripTexts = texts(),
    spec: InverterLayoutSpec = S3X1,
    percents: InverterFontPercents = InverterFontPercents(),
  ) = InverterFonts.data(style, texts, spec, hostScale = 1f, percents, fontScale = 1f, FakeMeasurer)

  @Test
  fun fiftyPercent_shortTexts_showTheDesignSizes() =
    InverterLayoutSpec.entries.forEach { spec ->
      val f = fonts(spec = spec)
      assertEquals(spec.name, spec.design.wordFont, f.word, 0f)
      assertEquals(spec.name, spec.design.valueFont, f.value, 0f)
    }

  @Test
  fun bigIcon_modePillIsIconAndPadding() =
    assertEquals(
      listOf(44f, 56f, 56f),
      InverterLayoutSpec.entries.map { fonts(ModeStyle.BIG_ICON, spec = it).modeWidth },
    )

  /** "main" with its padding is narrower than the design pill is high: the pill keeps that width. */
  @Test
  fun shortWord_modePillKeepsItsMinimumWidth() =
    assertEquals(43f, fonts(texts = texts("main")).modeWidth, 0f)

  /** A word longer than its box keeps its 50 % size and takes the whole box: it ends with "…". */
  @Test
  fun longWord_keepsItsFiftyPercentSize_atTheWidestPill() {
    val f = fonts(texts = texts("Battery discharge"))
    assertEquals(10.5f, f.word, 0f)
    assertEquals(44f + 2 * 8f, f.modeWidth, 0f)
  }

  /** A raised word shrinks to its box, but not below its 50 % size. */
  @Test
  fun raisedWord_shrinksToItsBox() {
    val f = fonts(percents = InverterFontPercents(word = 100))
    assertEquals((44f - TextFit.WIDTH_SLACK_DP) / (0.5f * "battery".length), f.word, 0.02f)
  }

  /** At 3×1 the word sits under the 20 dp icon: only the height left below it counts. */
  @Test
  fun wordUnderIcon_fitsTheHeightBelowTheIcon() {
    val f = fonts(texts = texts("fault"), percents = InverterFontPercents(word = 100))
    val below = S3X1.fitInner(1f).height - S3X1.design.wordIcon
    assertEquals((below - TextFit.HEIGHT_SLACK_DP) / 1.06f, f.word, 0.02f)
  }

  /** "0 V" could be larger, but both values show at the size "230 V" fits. */
  @Test
  fun values_shareOneSize() {
    val hundred = InverterFontPercents(value = 100)
    val mixed = fonts(ModeStyle.BIG_ICON, texts("x", "0 V", "230 V"), percents = hundred).value
    assertTrue(mixed < 25f)
    assertEquals(fonts(ModeStyle.BIG_ICON, texts("x", "230 V", "230 V"), percents = hundred).value, mixed, 0f)
  }

  @Test
  fun syncError_takesRoomFromTheValues() {
    val hundred = InverterFontPercents(value = 100)
    val withError = fonts(ModeStyle.BIG_ICON, texts(error = "Sync error: x"), percents = hundred).value
    assertTrue(withError < fonts(ModeStyle.BIG_ICON, texts(), percents = hundred).value)
  }

  @Test
  fun empty_longTitle_shrinksBesideTheIcon() {
    val title = "Налаштуйте в застосунку"
    val room = S3X1.fitInner(1f).width - S3X1.design.emptyIcon - S3X1.design.emptyGap
    val size = InverterFonts.empty(title, S3X1, hostScale = 1f, fontScale = 1f, FakeMeasurer)
    assertEquals((room - TextFit.WIDTH_SLACK_DP) / (0.5f * title.length), size, 0.01f)
  }
}
