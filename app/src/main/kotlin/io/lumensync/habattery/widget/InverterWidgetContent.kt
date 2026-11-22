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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
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
 * The inverter strip (specs 0012, 0016), drawn from [model] and [colors] only, at the sizes of
 * [spec] × [hostScale]: the mode pill, grid input and output voltage, and ↻ with the freshness dot,
 * with a red icon above ↻ on a sync error. Texts are fitted before drawing ([InverterFonts]).
 * Tapping ↻ runs [refresh] and the rest of the card [openApp]; without them (the app's preview)
 * nothing takes taps.
 */
@Composable
fun InverterWidgetContent(
  model: InverterWidgetModel,
  spec: InverterLayoutSpec,
  colors: WidgetColors,
  fonts: InverterFontPercents,
  hostScale: Float,
  openApp: Action?,
  refresh: Action?,
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
      .semantics { testTag = "strip_${spec.name}" },
    contentAlignment = Alignment.Center,
  ) {
    when (model) {
      is InverterWidgetModel.Data -> {
        val texts = StripTexts.of(model, context, formatters)
        val sizes = InverterFonts.data(model.style, texts, spec, hostScale, fonts, fontScale, m)
        Strip(model, texts, sizes, look, refresh)
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
  refresh: Action?,
) {
  val s = look.spec
  // The card has no padding: the ↻ column takes the card's full height, so the ⚠ fits above ↻.
  Row(GlanceModifier.fillMaxSize()) {
    Box(
      GlanceModifier.defaultWeight()
        .fillMaxHeight()
        .padding(start = s.padding.dp, top = s.padding.dp, bottom = s.padding.dp, end = s.gap.dp)
    ) {
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
      }
    }
    RefreshColumn(model, texts.error, look, refresh)
  }
}

/**
 * ↻ with the freshness dot, centred on the card's height as the pills are, and the red sync error
 * icon described by [error] above it. The icon is not a button: it stays outside ↻'s tap area,
 * which spans the column's width. Without [refresh] (the app's preview) ↻ takes no taps and
 * TalkBack does not announce it.
 */
@Composable
private fun RefreshColumn(
  model: InverterWidgetModel.Data,
  error: String?,
  look: WidgetLook<StripSizes>,
  refresh: Action?,
) {
  val context = LocalContext.current
  val s = look.spec
  val c = look.colors
  val description = WidgetTexts.refreshDescription(context, model.refreshing, model.dataAgeMinutes)
  val described =
    if (refresh == null) GlanceModifier else GlanceModifier.semantics { contentDescription = description }
  // Equal weights above and below centre ↻ at any card height; at the design's 59 dp the ⚠ then
  // starts 2.5 dp below the card's top.
  Column(GlanceModifier.width((s.refresh + s.padding).dp).fillMaxHeight()) {
    // The end padding centres the ⚠ over the circle. The bottom padding and the dot's overhang
    // above the circle make the design's gap between them.
    Box(
      GlanceModifier.defaultWeight()
        .fillMaxWidth()
        .padding(end = s.padding.dp, bottom = s.dotOverhang.dp),
      contentAlignment = Alignment.BottomCenter,
    ) {
      if (error != null) WidgetIcon(R.drawable.ic_alert, s.alertIcon.dp, c.bad, error)
    }
    // The overhang below the circle matches the one above, so the circle is the group's centre.
    Box(
      GlanceModifier.fillMaxWidth()
        .height((s.refresh + 2 * s.dotOverhang).dp)
        .tapAction(refresh)
        .then(described)
    ) {
      // Glance aligns all children of a Box alike, so the circle and the dot each get a
      // full-size box with its own alignment, as on the battery widget's ↻.
      Box(GlanceModifier.size((s.refresh + s.dotOverhang).dp)) {
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
          Box(
            GlanceModifier.size(s.refresh.dp).background(c.tile).cornerRadius((s.refresh / 2).dp),
            contentAlignment = Alignment.Center,
          ) {
            // While a sync runs, ↻ only dims; the dot still shows the data's age.
            WidgetIcon(
              R.drawable.ic_refresh,
              s.refreshIcon.dp,
              if (model.refreshing) c.dim else c.sub,
              null,
            )
          }
        }
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
          FreshnessBadge(model.dataLate, s.dot, s.dotRing, c)
        }
      }
    }
    Spacer(GlanceModifier.defaultWeight())
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
    GlanceModifier.fillMaxSize().padding(s.padding.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    WidgetIcon(R.drawable.ic_inv_tower, s.emptyIcon.dp, look.colors.sub, null)
    Spacer(GlanceModifier.width(s.emptyGap.dp))
    Text(title, style = textStyle(look.sp(size), look.colors.text, FontWeight.Medium), maxLines = 1)
  }
}
