package io.lumensync.habattery.ui.sections

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.Level
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.WidgetModel.Flow
import io.lumensync.habattery.domain.WidgetModel.Temperatures
import io.lumensync.habattery.domain.model.FontGroup
import io.lumensync.habattery.widget.WidgetTexts

/** At 0 % a dot stays, as on the widget. */
private const val MIN_SWEEP = 0.01f

/** Roboto's normal line height, per unit of font size. */
private const val LINE_HEIGHT = 1.17f

/** Offsets (dp) of the label's and the under-SOC line's centres from the ring centre. */
private data class RingTextOffsets(val label: Float, val under: Float)

/**
 * The v4 ring text (CSS: SOC number with line-height 1, 2 dp gaps): each line is placed by its
 * offset from the number, because Android text boxes are taller than the CSS lines.
 */
private fun cardRingOffsets(socDp: Float, labelDp: Float, underDp: Float, gapDp: Float) =
  RingTextOffsets(
    label = socDp / 2 + gapDp + labelDp * LINE_HEIGHT / 2,
    under = socDp / 2 + gapDp + underDp * LINE_HEIGHT / 2,
  )

/** Ring, pack column and cell column, as on the widget. */
@Composable
internal fun CardBody(model: WidgetModel.Data, look: CardLook, modifier: Modifier) {
  val texts = look.texts
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Ring(model, look)
    Spacer(Modifier.width(look.dp(10f)))
    Column(
      Modifier.weight(1f).fillMaxHeight(),
      verticalArrangement = Arrangement.spacedBy(look.dp(6f)),
    ) {
      BigPill(texts.voltage(model), Level.PLAIN, Flow.NONE, look, Modifier.weight(1f))
      BigPill(texts.main(model), model.mainLevel, model.flow, look, Modifier.weight(1f))
    }
    Spacer(Modifier.width(look.dp(6f)))
    Column(
      Modifier.width(look.dp(90f)).fillMaxHeight(),
      verticalArrangement = Arrangement.spacedBy(look.dp(4f)),
    ) {
      // The pills fill the column with small gaps, as on the widget (owner, 2026-11-16).
      val fill = Modifier.weight(1f)
      TextPill(texts.averageCell(model), Level.PLAIN, look, fill)
      TextPill(texts.delta(model), model.deltaLevel, look, fill)
      when (val t = model.temperatures) {
        Temperatures.None -> Unit
        is Temperatures.Battery ->
          IconPill(
            R.drawable.ic_thermometer,
            if (t.sensors > 1) R.string.widget_average_temperature
            else R.string.widget_battery_temperature,
            t.celsius,
            t.level,
            look,
            fill,
          )
      }
      IconPill(R.drawable.ic_chip, R.string.widget_mosfet_temperature, model.mosfet, model.mosfetLevel, look, fill)
    }
  }
}

@Composable
private fun Ring(model: WidgetModel.Data, look: CardLook) {
  val palette = look.palette
  val texts = look.texts
  val description = texts.ringDescription(model)
  val offsets =
    cardRingOffsets(
      socDp = look.shownSize(FontGroup.SOC),
      labelDp = look.shownSize(FontGroup.SMALL),
      underDp = look.shownSize(FontGroup.UNDER_SOC),
      gapDp = 2f * look.s,
    )
  Box(
    Modifier.size(look.dp(104f)).semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
  ) {
    val track = palette.track
    val ring = palette.ringColor(model.ringLevel)
    val strokeDp = look.dp(8f)
    Canvas(Modifier.size(look.dp(104f))) {
      val stroke = strokeDp.toPx()
      val inset = stroke / 2
      val arcSize = Size(size.width - stroke, size.height - stroke)
      drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
      drawArc(
        ring,
        -90f,
        maxOf(MIN_SWEEP, model.ringFraction * 360f),
        false,
        Offset(inset, inset),
        arcSize,
        style = Stroke(stroke, cap = StrokeCap.Round),
      )
    }
    Text(
      stringResource(R.string.widget_soc_label),
      Modifier.offset(y = (-offsets.label).dp),
      color = palette.sub,
      fontSize = look.font(FontGroup.SMALL),
      maxLines = 1,
    )
    Text(
      texts.soc(model),
      color = palette.text,
      fontSize = look.font(FontGroup.SOC),
      fontWeight = FontWeight.Bold,
      maxLines = 1,
    )
    Text(
      texts.underSoc(model),
      Modifier.offset(y = offsets.under.dp),
      color = palette.text,
      fontSize = look.font(FontGroup.UNDER_SOC),
      fontWeight = FontWeight.Medium,
      maxLines = 1,
    )
  }
}

@Composable
private fun BigPill(text: String, level: Level, flow: Flow, look: CardLook, modifier: Modifier) {
  val palette = look.palette
  val color = palette.pillText(level)
  Row(
    modifier
      .fillMaxWidth()
      .background(palette.pillBackground(level), RoundedCornerShape(look.dp(16f)))
      .padding(horizontal = look.dp(6f)),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    WidgetTexts.flowDescription(flow)?.let { description ->
      CardIcon(
        if (flow == Flow.UP) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down,
        look.dp(16f),
        color,
        stringResource(description),
      )
      Spacer(Modifier.width(look.dp(4f)))
    }
    Text(
      text,
      color = color,
      fontSize = look.font(FontGroup.MAIN),
      fontWeight = FontWeight.Medium,
      maxLines = 1,
      softWrap = false,
    )
  }
}

@Composable
private fun TextPill(text: String, level: Level, look: CardLook, modifier: Modifier) {
  SmallPill(level, look, modifier) {
    Text(
      text,
      color = look.palette.pillText(level),
      fontSize = look.font(FontGroup.CELL),
      fontWeight = FontWeight.Medium,
      maxLines = 1,
      softWrap = false,
    )
  }
}

/** Icon, a 3 dp space, the value. */
@Composable
private fun IconPill(
  @DrawableRes icon: Int,
  @StringRes iconDescription: Int,
  celsius: Double?,
  level: Level,
  look: CardLook,
  modifier: Modifier,
) {
  val color = look.palette.pillText(level)
  SmallPill(level, look, modifier) {
    CardIcon(icon, look.dp(13f), color, stringResource(iconDescription))
    Spacer(Modifier.width(look.dp(3f)))
    Text(
      look.texts.temperature(celsius),
      color = color,
      fontSize = look.font(FontGroup.CELL),
      fontWeight = FontWeight.Medium,
      maxLines = 1,
      softWrap = false,
    )
  }
}

@Composable
private fun SmallPill(level: Level, look: CardLook, modifier: Modifier, content: @Composable () -> Unit) {
  Row(
    modifier.fillMaxWidth()
      .background(look.palette.pillBackground(level), RoundedCornerShape(percent = 50))
      .padding(horizontal = look.dp(8f)),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    content()
  }
}
