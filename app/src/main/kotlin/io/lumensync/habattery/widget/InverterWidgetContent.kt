package io.lumensync.habattery.widget

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.InverterWidgetModel
import io.lumensync.habattery.domain.ModeLook
import io.lumensync.habattery.domain.VoltageLook
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.InverterFontPercents
import io.lumensync.habattery.domain.model.ModeStyle
import io.lumensync.habattery.format.Formatters

/** Every strip pill is fully round; Android clamps the radius to half the pill's height. */
private val PILL_RADIUS = 999.dp

/**
 * The inverter strip (spec 0012), drawn from [model] and [colors] only, at the sizes of [spec] ×
 * [hostScale]: the mode pill, grid input and output voltage, and on a sync error a red icon at
 * the end. Texts are fitted before drawing ([InverterFonts]). Tapping the card runs [openApp];
 * without it (the app's preview) nothing takes taps.
 */
@Composable
fun InverterWidgetContent(
  model: InverterWidgetModel,
  spec: InverterLayoutSpec,
  colors: WidgetColors,
  fonts: InverterFontPercents,
  hostScale: Float,
  openApp: Action?,
  measurer: TextMeasurer? = null,
) {
  val context = LocalContext.current
  val s = spec.design.scaled(hostScale)
  val m = measurer ?: remember(context) { PaintTextMeasurer(context) }
  val formatters = remember(context) { Formatters(context) }
  val fontScale = context.resources.configuration.fontScale
  val look = WidgetLook(s, colors, context.resources.displayMetrics, fontScale)
  Box(
    GlanceModifier.fillMaxSize()
      .background(colors.card)
      .cornerRadius(s.corner.dp) // Android 12+ only; square before, as the battery widget
      .tapAction(openApp)
      .padding(s.padding.dp)
      .semantics { testTag = "strip_${spec.name}" },
    contentAlignment = Alignment.Center,
  ) {
    when (model) {
      is InverterWidgetModel.Data -> {
        val texts = StripTexts.of(model, context, formatters)
        val sizes = InverterFonts.data(model.style, texts, spec, hostScale, fonts, fontScale, m)
        Strip(model, texts, sizes, look)
      }
      is InverterWidgetModel.Empty -> {
        val title =
          context.getString(
            when (model.kind) {
              WidgetModel.EmptyKind.SETUP -> R.string.inverter_setup
              WidgetModel.EmptyKind.NO_DATA -> R.string.inverter_no_data
            }
          )
        EmptyStrip(title, InverterFonts.empty(title, spec, hostScale, fontScale, m), look)
      }
    }
  }
}

@Composable
private fun Strip(
  model: InverterWidgetModel.Data,
  texts: StripTexts,
  sizes: StripFonts,
  look: WidgetLook<StripSizes>,
) {
  val s = look.spec
  Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
    ModePill(model.mode, model.style, texts.word, sizes, look)
    Spacer(GlanceModifier.width(s.gap.dp))
    val share = GlanceModifier.defaultWeight()
    ValuePill(model.grid, texts.grid, R.string.inverter_grid_voltage, sizes.value, look, share)
    Spacer(GlanceModifier.width(s.gap.dp))
    ValuePill(
      model.output,
      texts.output,
      R.string.inverter_output_voltage,
      sizes.value,
      look,
      share,
    )
    if (texts.error != null) {
      Spacer(GlanceModifier.width(s.gap.dp))
      WidgetIcon(R.drawable.ic_alert, s.alertIcon.dp, look.colors.bad, texts.error)
    }
  }
}

/**
 * The mode at its content width: the big icon, described by the [word] it stands for, or the
 * icon with the [word] under or beside it. The padding bounds the word, so a longer one ends with
 * "…" (Glance ellipsizes a one-line text at its end).
 */
@Composable
private fun ModePill(
  mode: ModeLook,
  style: ModeStyle,
  word: String,
  sizes: StripFonts,
  look: WidgetLook<StripSizes>,
) {
  val s = look.spec
  val color = look.colors.pillText(mode.color)
  val bigIcon = style == ModeStyle.BIG_ICON
  Box(
    GlanceModifier.width(sizes.modeWidth.dp)
      .fillMaxHeight()
      .background(look.colors.pillBackground(mode.color))
      .cornerRadius(PILL_RADIUS)
      .padding(horizontal = (if (bigIcon) s.bigPadding else s.wordPadding).dp),
    contentAlignment = Alignment.Center,
  ) {
    val icon = mode.icon.drawable()
    val text: @Composable () -> Unit = {
      Text(word, style = textStyle(look.sp(sizes.word), color, FontWeight.Medium), maxLines = 1)
    }
    when {
      bigIcon -> WidgetIcon(icon, s.bigIcon.dp, color, word)
      s.wordUnderIcon ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          WidgetIcon(icon, s.wordIcon.dp, color, null)
          text()
        }
      else ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          WidgetIcon(icon, s.wordIcon.dp, color, null)
          Spacer(GlanceModifier.width(s.wordGap.dp))
          text()
        }
    }
  }
}

/** A voltage with its icon, described by the value's [name], so TalkBack says which one it is. */
@Composable
private fun ValuePill(
  voltage: VoltageLook,
  text: String,
  @StringRes name: Int,
  size: Float,
  look: WidgetLook<StripSizes>,
  modifier: GlanceModifier,
) {
  val context = LocalContext.current
  val s = look.spec
  val color = look.colors.pillText(voltage.color)
  Box(
    modifier
      .fillMaxHeight()
      .background(look.colors.pillBackground(voltage.color))
      .cornerRadius(PILL_RADIUS)
      .padding(horizontal = s.valuePadding.dp),
    contentAlignment = Alignment.Center,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      WidgetIcon(voltage.icon.drawable(), s.valueIcon.dp, color, context.getString(name))
      Spacer(GlanceModifier.width(s.valueIconGap.dp))
      Text(text, style = textStyle(look.sp(size), color, FontWeight.Medium), maxLines = 1)
    }
  }
}

/** Setup needed or no data: the tower icon and one line. Tapping the card opens the app. */
@Composable
private fun EmptyStrip(title: String, size: Float, look: WidgetLook<StripSizes>) {
  val s = look.spec
  Row(
    GlanceModifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    WidgetIcon(R.drawable.ic_inv_tower, s.emptyIcon.dp, look.colors.sub, null)
    Spacer(GlanceModifier.width(s.emptyGap.dp))
    Text(title, style = textStyle(look.sp(size), look.colors.text, FontWeight.Medium), maxLines = 1)
  }
}
