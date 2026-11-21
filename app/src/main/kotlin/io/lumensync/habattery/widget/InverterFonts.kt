package io.lumensync.habattery.widget

import android.content.Context
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.InverterWidgetModel
import io.lumensync.habattery.domain.VoltageLook
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.InverterFontPercents
import io.lumensync.habattery.domain.model.ModeStyle
import io.lumensync.habattery.format.Formatters
import io.lumensync.habattery.format.modeWord

/** Every text of a strip with data, resolved once per drawing. */
internal data class StripTexts(
  /** The mode word (`modeWord`), or "—" when there is no value. */
  val word: String,
  val grid: String,
  val output: String,
  /** The sync error icon's content description; null without an error. */
  val error: String?,
) {
  companion object {
    fun of(m: InverterWidgetModel.Data, context: Context, formatters: Formatters): StripTexts {
      val missing = context.getString(R.string.value_missing)
      fun volts(v: VoltageLook) = v.volts?.let(formatters::wholeVolts) ?: missing
      return StripTexts(
        word = m.mode.level?.let { modeWord(context.resources, it, m.mode.text) } ?: missing,
        grid = volts(m.grid),
        output = volts(m.output),
        error =
          m.error?.let { context.getString(R.string.widget_sync_error, formatters.error(it)) },
      )
    }
  }
}

/** The shown sizes (dp) of a strip with data, and the mode pill's width. */
internal data class StripFonts(
  /** 0 with the big icon, which shows no word. */
  val word: Float,
  val value: Float,
  val modeWidth: Float,
)

/**
 * The strip's texts fitted as the battery widget's ([WidgetFonts]): each asks for its design size ×
 * percent / 50 × the system font scale and is shown at that size, or smaller when it does not fit
 * its box at the bucket ([InverterLayoutSpec.fitInner]).
 */
internal object InverterFonts {
  /**
   * The mode pill keeps its content width; the two voltage pills share what is left, less the
   * sync error icon. Both values show at one size, the largest that fits both pills.
   *
   * The word is not shrunk below its 50 % size to fit the word box: a longer word ends with "…"
   * there, as the design has it, instead of turning tiny. A word asking for less keeps its size.
   */
  fun data(
    style: ModeStyle,
    texts: StripTexts,
    spec: InverterLayoutSpec,
    hostScale: Float,
    percents: InverterFontPercents,
    fontScale: Float,
    measurer: TextMeasurer,
  ): StripFonts {
    val s = spec.design.scaled(hostScale)
    val inner = spec.fitInner(hostScale)
    fun requested(design: Float, percent: Int) = design * percent / FontPercents.DEFAULT * fontScale
    fun fit(text: String, size: Float, box: Box) =
      TextFit.fit(listOf(text), size, TextWeight.MEDIUM, box, measurer)

    var word = 0f
    val content =
      if (style == ModeStyle.BIG_ICON) {
        s.bigIcon + 2 * s.bigPadding
      } else {
        val box = Box(s.wordBox, if (s.wordUnderIcon) inner.height - s.wordIcon else inner.height)
        val wanted = requested(s.wordFont, percents.word)
        val byHeight = fit(texts.word, wanted, Box(Float.POSITIVE_INFINITY, box.height))
        val fifty = requested(s.wordFont, FontPercents.DEFAULT)
        word = maxOf(fit(texts.word, wanted, box), minOf(wanted, fifty, byHeight))
        // The slack the box kept, so the drawn word is not cut by pixel rounding.
        val measured = measurer.width(texts.word, word, TextWeight.MEDIUM)
        val wordWidth = minOf(measured + TextFit.WIDTH_SLACK_DP, s.wordBox)
        val inside =
          if (s.wordUnderIcon) maxOf(s.wordIcon, wordWidth) else s.wordIcon + s.wordGap + wordWidth
        inside + 2 * s.wordPadding
      }
    val modeWidth = maxOf(content, s.modeMinWidth)

    val alert = if (texts.error != null) s.gap + s.alertIcon else 0f
    val pill = (inner.width - modeWidth - 2 * s.gap - alert) / 2
    val valueBox = Box(pill - 2 * s.valuePadding - s.valueIcon - s.valueIconGap, inner.height)
    val wanted = requested(s.valueFont, percents.value)
    val value = minOf(fit(texts.grid, wanted, valueBox), fit(texts.output, wanted, valueBox))
    return StripFonts(word, value, modeWidth)
  }

  /** The empty state's line beside its icon; no font setting, as the battery widget's. */
  fun empty(
    title: String,
    spec: InverterLayoutSpec,
    hostScale: Float,
    fontScale: Float,
    measurer: TextMeasurer,
  ): Float {
    val s = spec.design.scaled(hostScale)
    val inner = spec.fitInner(hostScale)
    val box = Box(inner.width - s.emptyIcon - s.emptyGap, inner.height)
    return TextFit.fit(listOf(title), s.emptyFont * fontScale, TextWeight.MEDIUM, box, measurer)
  }
}
