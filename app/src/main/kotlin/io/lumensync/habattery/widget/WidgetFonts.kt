package io.lumensync.habattery.widget

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.Level
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.WidgetModel.Flow
import io.lumensync.habattery.domain.WidgetModel.Temperatures
import io.lumensync.habattery.domain.model.FontGroup
import io.lumensync.habattery.domain.model.FontPercents

/** A cell pill: its text, its level and, for a temperature, its icon. */
internal data class CellPill(
  val text: String,
  val level: Level,
  @param:DrawableRes val icon: Int? = null,
  @param:StringRes val iconDescription: Int? = null,
)

/** Every text of a widget with data, resolved once per drawing. */
internal data class DataTexts(
  val timeLeft: String,
  val updatedAt: String,
  /** With a sync error the red icon takes the update time's place ([errorDescription] says why). */
  val showError: Boolean,
  val errorDescription: String?,
  val socLabel: String,
  val soc: String,
  /** The ring's content description: "State of charge 55 percent". */
  val ringDescription: String,
  val underSoc: String,
  val voltage: String,
  val main: String,
  /** Average cell, delta, battery temperature (when a sensor is set), MOSFET last. */
  val cells: List<CellPill>,
) {
  companion object {
    fun of(m: WidgetModel.Data, texts: WidgetTexts): DataTexts {
      val battery =
        when (val t = m.temperatures) {
          Temperatures.None -> emptyList()
          is Temperatures.Battery ->
            listOf(
              CellPill(
                texts.temperature(t.celsius),
                t.level,
                R.drawable.ic_thermometer,
                if (t.sensors > 1) R.string.widget_average_temperature
                else R.string.widget_battery_temperature,
              )
            )
        }
      return DataTexts(
        timeLeft = texts.timeLeft(m),
        updatedAt = texts.updatedAt(m),
        showError = m.error != null,
        errorDescription = m.error?.let(texts::syncErrorDescription),
        socLabel = texts.socLabel,
        soc = texts.soc(m),
        ringDescription = texts.ringDescription(m),
        underSoc = texts.underSoc(m),
        voltage = texts.voltage(m),
        main = texts.main(m),
        cells =
          listOf(CellPill(texts.averageCell(m), Level.PLAIN), CellPill(texts.delta(m), m.deltaLevel)) +
            battery +
            CellPill(
              texts.temperature(m.mosfet),
              m.mosfetLevel,
              R.drawable.ic_chip,
              R.string.widget_mosfet_temperature,
            ),
      )
    }
  }
}

/** The shown size (dp) of every text of a widget with data, and where the ring lines sit. */
internal data class DataFonts(
  /** Time left and update time share one size, so they never overlap; the same with an error. */
  val small: Float,
  val ringLabel: Float,
  val soc: Float,
  val underSoc: Float,
  val voltage: Float,
  val main: Float,
  val cells: List<Float>,
  /** Centres of the label, number and kWh / Ah views, dp from the ring centre. */
  val ringCenters: List<Float>,
)

/** The shown size (dp) of the empty state's texts. */
internal data class EmptyFonts(val title: Float, val reason: Float, val button: Float)

/**
 * Spec 0005, 5.5 (approach C1): every text asks for its layout's design size × percent / 50 × the
 * system font scale and is shown at that size, or smaller when the text as it is now does not fit
 * its box. The pill and top-row boxes come from the layout at its bucket size ([LayoutSpec.fitBoxes]):
 * a real widget is never smaller, so the result is safe when the layout stretches. The ring text fits the ring at
 * its design size (see [data]).
 */
internal object WidgetFonts {
  fun data(
    m: WidgetModel.Data,
    texts: DataTexts,
    spec: LayoutSpec,
    percents: FontPercents,
    fontScale: Float,
    measurer: TextMeasurer,
  ): DataFonts {
    val boxes = spec.fitBoxes(texts.cells.size)
    val design = spec.fonts
    fun requested(group: FontGroup, sp: Float) = percents.requestedDp(group, sp, fontScale)
    fun fit(text: String, size: Float, weight: TextWeight, box: Box) =
      TextFit.fit(listOf(text), size, weight, box, measurer)

    // Fitted with the update time also when the error icon shows, so the error moves nothing.
    val topTexts = listOf(texts.timeLeft, texts.updatedAt)
    val small = TextFit.fit(topTexts, requested(FontGroup.SMALL, design.small), TextWeight.NORMAL, boxes.topTexts, measurer)

    // A ring line's size at 50 %: its design size × the system font scale.
    fun fifty(sp: Float) = sp * fontScale
    val requestedRing =
      listOf(
        RingLine(texts.socLabel, requested(FontGroup.SMALL, design.ringLabel), TextWeight.NORMAL, spec.labelLineHeight, fifty(design.ringLabel)),
        RingLine(texts.soc, requested(FontGroup.SOC, design.soc), TextWeight.BOLD, 1f, fifty(design.soc)),
        RingLine(texts.underSoc, requested(FontGroup.UNDER_SOC, design.underSoc), TextWeight.MEDIUM, spec.underLineHeight, fifty(design.underSoc)),
      )
    // The ring's own size, not the bucket's: below the design card the ring shrinks to its row
    // while the fonts stay (5.1), so its text matches the design at the owner's sizes.
    val inner = spec.ring / 2 - spec.ringStroke
    val sizes = TextFit.fitRing(requestedRing, spec.ringTextGap, inner, measurer)
    val ring = requestedRing.mapIndexed { i, line -> line.copy(requested = sizes[i]) }

    val main = requested(FontGroup.MAIN, design.main)
    val pack = boxes.packPill.let { Box(it.width - 2 * spec.packPadding, it.height) }
    val arrow = if (m.flow == Flow.NONE) 0f else spec.arrow + spec.arrowGap

    val cell = requested(FontGroup.CELL, design.cell)
    val cells =
      texts.cells.mapIndexed { i, pill ->
        val outer = if (i == texts.cells.lastIndex) boxes.mosfetPill else boxes.cellPill
        val icon = if (pill.icon != null) spec.tempIcon + spec.tempIconGap else 0f
        fit(pill.text, cell, TextWeight.MEDIUM, Box(outer.width - 2 * spec.cellPadding - icon, outer.height))
      }

    return DataFonts(
      small = small,
      ringLabel = ring[0].requested,
      soc = ring[1].requested,
      underSoc = ring[2].requested,
      voltage = fit(texts.voltage, main, TextWeight.MEDIUM, pack),
      main = fit(texts.main, main, TextWeight.MEDIUM, pack.copy(width = pack.width - arrow)),
      cells = cells,
      ringCenters = RingLayout.centers(ring, spec.ringTextGap, measurer),
    )
  }

  fun empty(
    titleText: String,
    reasonText: String?,
    button: String,
    spec: LayoutSpec,
    percents: FontPercents,
    fontScale: Float,
    measurer: TextMeasurer,
  ): EmptyFonts {
    val box = spec.fitBoxes(cellPills = 4).empty
    val e = spec.empty
    val title = TextFit.fit(listOf(titleText), e.title * fontScale, TextWeight.MEDIUM, box, measurer)
    // The column (icon, title, reason, button) must not be taller than the card: what is left
    // for the reason's text view (font padding included) limits its size.
    val left = box.height - e.icon - 3 * e.gap - e.buttonTop - e.buttonHeight - viewHeight(titleText, title, measurer)
    val reasonSize =
      reasonText?.let {
        val wanted = percents.requestedDp(FontGroup.SMALL, spec.fonts.small, fontScale)
        val perDp = viewHeight(it, wanted, measurer) / wanted
        minOf(TextFit.fit(listOf(it), wanted, TextWeight.MEDIUM, box, measurer), (left / perDp).coerceAtLeast(0f))
      } ?: 0f
    return EmptyFonts(
      title = title,
      reason = reasonSize,
      button =
        TextFit.fit(
          listOf(button),
          e.buttonFont * fontScale,
          TextWeight.MEDIUM,
          Box(box.width - 2 * e.buttonPadding, e.buttonHeight),
          measurer,
        ),
    )
  }

  /** A one-line text view's height at [size]: from the font's top to its bottom. */
  private fun viewHeight(text: String, size: Float, measurer: TextMeasurer): Float =
    measurer.metrics(text, size, TextWeight.MEDIUM).let { it.bottom - it.top }
}
