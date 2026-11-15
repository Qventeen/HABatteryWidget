package io.lumensync.habattery.widget

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import kotlin.math.roundToInt

/**
 * The ring (SOC), the pack column (voltage, current or power) and the cell column (average cell,
 * delta, battery temperature, MOSFET last). Importance drops from left to right.
 */
@Composable
internal fun WidgetBody(model: WidgetModel.Data, look: WidgetLook, modifier: GlanceModifier) {
  val texts = look.texts

  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Ring(model, look)
    Spacer(GlanceModifier.width(look.dp(10f)))
    Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
      BigPill(
        texts.voltage(model),
        Level.PLAIN,
        Flow.NONE,
        look,
        GlanceModifier.defaultWeight().fillMaxWidth(),
      )
      Spacer(GlanceModifier.height(look.dp(6f)))
      BigPill(
        texts.main(model),
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
  val texts = look.texts
  val density = context.resources.displayMetrics.density
  val sizeDp = 104f * look.s
  val arc =
    RingBitmap.arc(
      model.ringFraction,
      sizePx = (sizeDp * density).roundToInt().coerceAtLeast(1),
      strokePx = 8f * look.s * density,
    )
  val description = texts.ringDescription(model)

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
      texts.soc(model),
      style = textStyle(look.font(FontGroup.SOC), WidgetColors.text, FontWeight.Bold),
      maxLines = 1,
    )
    Text(
      texts.underSoc(model),
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
      WidgetTexts.flowDescription(flow)?.let { description ->
        WidgetIcon(
          if (flow == Flow.UP) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down,
          look.dp(16f),
          color,
          context.getString(description),
        )
        Spacer(GlanceModifier.width(look.dp(4f)))
      }
      Text(text, style = textStyle(look.font(FontGroup.MAIN), color, FontWeight.Medium), maxLines = 1)
    }
  }
}

/** The right column: 3 or 4 pills, MOSFET always last. */
private fun cellPills(model: WidgetModel.Data, look: WidgetLook): List<@Composable () -> Unit> {
  val temperatures: List<@Composable () -> Unit> =
    when (val t = model.temperatures) {
      Temperatures.None -> emptyList()
      is Temperatures.Battery ->
        listOf {
          TemperaturePill(
            R.drawable.ic_thermometer,
            if (t.sensors > 1) R.string.widget_average_temperature
            else R.string.widget_battery_temperature,
            t.celsius,
            t.level,
            look,
          )
        }
    }
  return listOf<@Composable () -> Unit>(
    { TextPill(look.texts.averageCell(model), Level.PLAIN, look) },
    { TextPill(look.texts.delta(model), model.deltaLevel, look) },
  ) +
    temperatures +
    {
      TemperaturePill(
        R.drawable.ic_chip,
        R.string.widget_mosfet_temperature,
        model.mosfet,
        model.mosfetLevel,
        look,
      )
    }
}

@Composable
private fun TextPill(text: String, level: Level, look: WidgetLook) {
  SmallPill(level, look) {
    Text(
      text,
      style = textStyle(look.font(FontGroup.CELL), WidgetColors.pillText(level), FontWeight.Medium),
      maxLines = 1,
    )
  }
}

/** Icon, a 3 dp space, the value. */
@Composable
private fun TemperaturePill(
  @DrawableRes icon: Int,
  @StringRes iconDescription: Int,
  celsius: Double?,
  level: Level,
  look: WidgetLook,
) {
  val context = LocalContext.current
  val color = WidgetColors.pillText(level)
  SmallPill(level, look) {
    WidgetIcon(icon, look.dp(13f), color, context.getString(iconDescription))
    Text(
      look.texts.temperature(celsius),
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
