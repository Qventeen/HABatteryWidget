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
import androidx.compose.foundation.layout.height
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
import io.lumensync.habattery.widget.RingLayout
import io.lumensync.habattery.widget.WidgetTexts

/** At 0 % a dot stays, as on the widget. */
private const val MIN_SWEEP = 0.01f

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
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      TextPill(texts.averageCell(model), Level.PLAIN, look)
      TextPill(texts.delta(model), model.deltaLevel, look)
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
          )
      }
      IconPill(R.drawable.ic_chip, R.string.widget_mosfet_temperature, model.mosfet, model.mosfetLevel, look)
    }
  }
}

@Composable
private fun Ring(model: WidgetModel.Data, look: CardLook) {
  val palette = look.palette
  val texts = look.texts
  val description = texts.ringDescription(model)
  val offsets =
    RingLayout.offsets(
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
      .background(palette.pillBackground(level), RoundedCornerShape(look.dp(25f)))
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
private fun TextPill(text: String, level: Level, look: CardLook) {
  SmallPill(level, look) {
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
) {
  val color = look.palette.pillText(level)
  SmallPill(level, look) {
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
private fun SmallPill(level: Level, look: CardLook, content: @Composable () -> Unit) {
  Row(
    Modifier.fillMaxWidth()
      .height(look.dp(19f))
      .background(look.palette.pillBackground(level), RoundedCornerShape(look.dp(9.5f)))
      .padding(horizontal = look.dp(8f)),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    content()
  }
}
