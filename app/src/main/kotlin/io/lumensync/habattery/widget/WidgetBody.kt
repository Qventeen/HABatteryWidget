package io.lumensync.habattery.widget

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.Level
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.WidgetModel.Flow
import io.lumensync.habattery.domain.WidgetModel.Temperatures
import io.lumensync.habattery.domain.model.FontGroup
import io.lumensync.habattery.domain.model.MainValue
import io.lumensync.habattery.domain.model.UnderSoc
import kotlin.math.roundToInt

/**
 * The ring (SOC), the pack column (voltage, current or power) and the cell column (average cell,
 * delta, battery temperature, MOSFET last). Importance drops from left to right.
 */
@Composable
internal fun WidgetBody(model: WidgetModel.Data, look: WidgetLook, modifier: GlanceModifier) {
  val context = LocalContext.current
  val f = look.formatters
  val missing = context.getString(R.string.value_missing)

  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Ring(model, look)
    Spacer(GlanceModifier.width(look.dp(10f)))
    Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
      BigPill(
        model.voltage?.let(f::voltage) ?: missing,
        Level.PLAIN,
        Flow.NONE,
        look,
        GlanceModifier.defaultWeight().fillMaxWidth(),
      )
      Spacer(GlanceModifier.height(look.dp(6f)))
      BigPill(
        model.mainValue?.let {
          when (model.main) {
            MainValue.CURRENT -> f.current(it)
            MainValue.POWER -> f.power(it)
          }
        } ?: missing,
        model.mainLevel,
        model.flow,
        look,
        GlanceModifier.defaultWeight().fillMaxWidth(),
      )
    }
    Spacer(GlanceModifier.width(look.dp(6f)))
    Column(GlanceModifier.width(look.dp(90f)).fillMaxHeight()) {
      val pills = cellPills(model, look)
      pills.forEachIndexed { index, pill ->
        if (index > 0) Spacer(GlanceModifier.defaultWeight())
        pill()
      }
    }
  }
}

@Composable
private fun Ring(model: WidgetModel.Data, look: WidgetLook) {
  val context = LocalContext.current
  val f = look.formatters
  val density = context.resources.displayMetrics.density
  val sizeDp = 104f * look.s
  val arc =
    RingBitmap.arc(
      model.ringFraction,
      sizePx = (sizeDp * density).roundToInt().coerceAtLeast(1),
      strokePx = 8f * look.s * density,
    )
  val description = context.getString(R.string.widget_soc_description, model.socPercent)
  val under =
    model.underSocValue?.let {
      when (model.underSoc) {
        UnderSoc.KWH -> f.kilowattHours(it)
        UnderSoc.AH -> f.ampHours(it)
      }
    } ?: context.getString(R.string.value_missing)

  Box(
    GlanceModifier.size(look.dp(104f)).semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
  ) {
    Image(
      ImageProvider(R.drawable.ring_track),
      contentDescription = null,
      modifier = GlanceModifier.fillMaxSize(),
      colorFilter = ColorFilter.tint(WidgetColors.track),
    )
    Image(
      ImageProvider(arc),
      contentDescription = null,
      modifier = GlanceModifier.fillMaxSize(),
      colorFilter = ColorFilter.tint(WidgetColors.ring(model.ringLevel)),
    )
    // Each line is centred, then moved by padding on one side (a centred view grows by the padding).
    val offsets =
      RingLayout.offsets(
        socDp = look.shownSize(FontGroup.SOC),
        labelDp = look.shownSize(FontGroup.SMALL),
        underDp = look.shownSize(FontGroup.UNDER_SOC),
        gapDp = 2f * look.s,
      )
    Text(
      context.getString(R.string.widget_soc_label),
      GlanceModifier.padding(bottom = (2 * offsets.label).dp),
      style = textStyle(look.font(FontGroup.SMALL), WidgetColors.sub),
      maxLines = 1,
    )
    Text(
      f.socNumber(model.socPercent),
      style = textStyle(look.font(FontGroup.SOC), WidgetColors.text, FontWeight.Bold),
      maxLines = 1,
    )
    Text(
      under,
      GlanceModifier.padding(top = (2 * offsets.under).dp),
      style = textStyle(look.font(FontGroup.UNDER_SOC), WidgetColors.text, FontWeight.Medium),
      maxLines = 1,
    )
  }
}

@Composable
private fun BigPill(
  text: String,
  level: Level,
  flow: Flow,
  look: WidgetLook,
  modifier: GlanceModifier,
) {
  val context = LocalContext.current
  val color = WidgetColors.pillText(level)
  Box(
    modifier
      .background(WidgetColors.pillBackground(level))
      .cornerRadius(look.dp(25f))
      .padding(horizontal = look.dp(6f)),
    contentAlignment = Alignment.Center,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (flow != Flow.NONE) {
        val up = flow == Flow.UP
        WidgetIcon(
          if (up) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down,
          look.dp(16f),
          color,
          context.getString(if (up) R.string.widget_charging else R.string.widget_discharging),
        )
        Spacer(GlanceModifier.width(look.dp(4f)))
      }
      Text(text, style = textStyle(look.font(FontGroup.MAIN), color, FontWeight.Medium), maxLines = 1)
    }
  }
}

/** The right column: 3 to 5 pills, MOSFET always last. */
private fun cellPills(model: WidgetModel.Data, look: WidgetLook): List<@Composable () -> Unit> {
  val temperatures: List<@Composable () -> Unit> =
    when (val t = model.temperatures) {
      Temperatures.None -> emptyList()
      is Temperatures.One ->
        listOf {
          TemperaturePill(
            R.drawable.ic_thermometer,
            R.string.widget_battery_temperature,
            null,
            null,
            t.celsius,
            t.level,
            look,
          )
        }
      is Temperatures.MaxMin ->
        listOf(
          {
            TemperaturePill(
              R.drawable.ic_thermometer,
              null,
              R.drawable.ic_arrow_up,
              R.string.widget_highest_temperature,
              t.max,
              t.maxLevel,
              look,
            )
          },
          {
            TemperaturePill(
              R.drawable.ic_thermometer,
              null,
              R.drawable.ic_arrow_down,
              R.string.widget_lowest_temperature,
              t.min,
              t.minLevel,
              look,
            )
          },
        )
    }
  return listOf<@Composable () -> Unit>(
    { TextPill(model.averageCellVoltage?.let(look.formatters::cellVoltage), Level.PLAIN, look) },
    { DeltaPill(model, look) },
  ) +
    temperatures +
    {
      TemperaturePill(
        R.drawable.ic_chip,
        R.string.widget_mosfet_temperature,
        null,
        null,
        model.mosfet,
        model.mosfetLevel,
        look,
      )
    }
}

@Composable
private fun DeltaPill(model: WidgetModel.Data, look: WidgetLook) {
  val context = LocalContext.current
  val value =
    model.deltaMv?.let(look.formatters::deltaMv) ?: context.getString(R.string.value_missing)
  TextPill(context.getString(R.string.delta_label, value), model.deltaLevel, look)
}

@Composable
private fun TextPill(text: String?, level: Level, look: WidgetLook) {
  val shown = text ?: LocalContext.current.getString(R.string.value_missing)
  SmallPill(level, look) {
    Text(
      shown,
      style = textStyle(look.font(FontGroup.CELL), WidgetColors.pillText(level), FontWeight.Medium),
      maxLines = 1,
    )
  }
}

/** Icon, a 10 dp arrow slot (empty without an arrow, so the numbers line up), the value. */
@Composable
private fun TemperaturePill(
  @DrawableRes icon: Int,
  iconDescription: Int?,
  @DrawableRes arrow: Int?,
  arrowDescription: Int?,
  celsius: Double?,
  level: Level,
  look: WidgetLook,
) {
  val context = LocalContext.current
  val color = WidgetColors.pillText(level)
  SmallPill(level, look) {
    WidgetIcon(icon, look.dp(13f), color, iconDescription?.let(context::getString))
    if (arrow == null) {
      Spacer(GlanceModifier.width(look.dp(10f)))
    } else {
      WidgetIcon(arrow, look.dp(10f), color, arrowDescription?.let(context::getString))
    }
    Text(
      celsius?.let(look.formatters::temperature) ?: context.getString(R.string.value_missing),
      GlanceModifier.padding(start = look.dp(3f)),
      style = textStyle(look.font(FontGroup.CELL), color, FontWeight.Medium),
      maxLines = 1,
    )
  }
}

@Composable
private fun SmallPill(level: Level, look: WidgetLook, content: @Composable () -> Unit) {
  Box(
    GlanceModifier.fillMaxWidth()
      .height(look.dp(19f))
      .background(WidgetColors.pillBackground(level))
      .cornerRadius(look.dp(9.5f))
      .padding(horizontal = look.dp(8f)),
    contentAlignment = Alignment.Center,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) { content() }
  }
}
