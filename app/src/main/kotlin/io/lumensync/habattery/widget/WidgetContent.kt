package io.lumensync.habattery.widget

import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.format.Formatters

/** What the widget's tap targets do. */
data class WidgetActions(val openApp: Action, val refresh: Action, val toggleAuto: Action)

/** Taps run [action]; without one (the app's preview) this part takes no taps. */
internal fun GlanceModifier.tapAction(action: Action?): GlanceModifier =
  if (action == null) this else clickable(action)

/**
 * The whole widget, drawn from [model] and [colors] only (design v5). The layout is the one made
 * for `LocalSize` (the `SizeMode.Responsive` bucket, spec 0005, A1); its texts are fitted before
 * drawing ([WidgetFonts]). Tapping the card opens the app. Without [actions] (the app's preview,
 * spec 0006) nothing takes taps and the empty state has no "Open app" button. [hostScale]
 * multiplies every size and font of the layout, for a home screen that draws widgets smaller than
 * their reported size ([LayoutSpec.scaled]).
 */
@Composable
fun WidgetContent(
  model: WidgetModel,
  fontPercents: FontPercents,
  colors: WidgetColors,
  actions: WidgetActions?,
  hostScale: Float = 1f,
  measurer: TextMeasurer? = null,
) {
  val context = LocalContext.current
  val size = LocalSize.current
  // The layout is picked by the reported size; the scale changes its numbers only.
  val layout = LayoutSpec.forSize(size.width.value, size.height.value)
  val spec = layout.scaled(hostScale)
  val texts = remember(context) { WidgetTexts(context, Formatters(context)) }
  val m = measurer ?: remember(context) { PaintTextMeasurer(context) }
  val fontScale = context.resources.configuration.fontScale
  val look = WidgetLook(spec, colors, context.resources.displayMetrics, fontScale)

  val card =
    GlanceModifier.fillMaxSize()
      .background(colors.card)
      .cornerRadius(spec.corner.dp) // Android 12+ only; square before (spec 0003, approach D1)
      .tapAction(actions?.openApp)
      .padding(spec.padding.dp)
      .semantics { testTag = "layout_${spec.name}" }
  val content: @Composable () -> Unit = {
    when (model) {
      is WidgetModel.Empty -> EmptyContent(model, look, texts, fontPercents, m, actions?.openApp)
      is WidgetModel.Data -> {
        val dataTexts = DataTexts.of(model, texts)
        val fonts = WidgetFonts.data(model, dataTexts, spec, fontPercents, fontScale, m)
        val d = DataLook(model, look, dataTexts, fonts)
        when (layout) {
          LayoutSpec.L5x2 -> Layout5x2(d, actions)
          LayoutSpec.L4x3 -> Layout4x3(d, actions)
          LayoutSpec.L5x3 -> Layout5x3(d, actions)
          else -> Layout4x2(d, actions)
        }
      }
    }
  }
  // 4×3 gets its own root type, so its root layout differs from 4×2 / 5×3. A launcher that moves
  // to 4×3 must then inflate fresh instead of reapplying onto the other size's views, where
  // Glance's index-based view ids collide (HyperOS: an empty body until the next update).
  if (layout == LayoutSpec.L4x3) Column(card) { content() } else Box(card) { content() }
}

/**
 * The sizes and the colours of one drawing, and the font size Glance needs. [S] holds the sizes:
 * a [LayoutSpec] for the battery widget, [StripSizes] for the inverter strip.
 */
internal class WidgetLook<S>(
  val spec: S,
  val colors: WidgetColors,
  private val metrics: DisplayMetrics,
  private val fontScale: Float,
) {
  /**
   * Glance takes sp only, and the system scales sp by its font scale again. So hand over the sp
   * that shows at [dp] (the shown size already holds the font scale): on Android 14+ sp scales
   * non-linearly, so use the converter's inverse; before that, divide the font scale back out.
   *
   * The system's float math can bring the sp back a hair larger than [dp]. A fitted size often sits
   * just under a whole pixel, where the text gets a pixel or more wider, so step the sp down until
   * it never shows larger than [dp].
   */
  fun sp(dp: Float): TextUnit {
    val px = dp * metrics.density
    var sp =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_SP, px, metrics)
      } else {
        dp / fontScale
      }
    while (sp > 0f && TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics) > px) {
      sp = Math.nextDown(sp)
    }
    return sp.sp
  }
}

/** Everything the parts of a widget with data draw from. */
internal class DataLook(
  val model: WidgetModel.Data,
  val look: WidgetLook<LayoutSpec>,
  val texts: DataTexts,
  val fonts: DataFonts,
) {
  val spec: LayoutSpec
    get() = look.spec

  val colors: WidgetColors
    get() = look.colors
}

/**
 * Setup needed or no data: icon, title, the reason (no data only) and "Open app" (spec 0005, 5.3);
 * without [openApp] (the app's preview) no button.
 */
@Composable
private fun EmptyContent(
  model: WidgetModel.Empty,
  look: WidgetLook<LayoutSpec>,
  texts: WidgetTexts,
  percents: FontPercents,
  measurer: TextMeasurer,
  openApp: Action?,
) {
  val context = LocalContext.current
  val e = look.spec.empty
  val colors = look.colors
  val title = texts.emptyTitle(model.kind)
  val reason = model.error?.takeIf { model.kind == WidgetModel.EmptyKind.NO_DATA }?.let(texts.formatters::error)
  val fonts =
    WidgetFonts.empty(
      title,
      reason,
      texts.openApp,
      look.spec,
      percents,
      context.resources.configuration.fontScale,
      measurer,
    )
  Column(
    GlanceModifier.fillMaxSize(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    WidgetIcon(R.drawable.ic_battery, e.icon.dp, colors.sub, null)
    Spacer(GlanceModifier.height(e.gap.dp))
    Text(
      title,
      style = textStyle(look.sp(fonts.title), colors.text, FontWeight.Medium, TextAlign.Center),
      maxLines = 1,
    )
    if (reason != null) {
      Spacer(GlanceModifier.height(e.gap.dp))
      Text(
        reason,
        style = textStyle(look.sp(fonts.reason), colors.bad, FontWeight.Medium, TextAlign.Center),
        maxLines = 1,
      )
    }
    if (openApp != null) {
      Spacer(GlanceModifier.height((e.gap + e.buttonTop).dp))
      Box(
        GlanceModifier.height(e.buttonHeight.dp)
          .background(colors.okBg)
          .cornerRadius((e.buttonHeight / 2).dp)
          .clickable(openApp)
          .padding(horizontal = e.buttonPadding.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          texts.openApp,
          style = textStyle(look.sp(fonts.button), colors.accent, FontWeight.Medium),
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
internal fun WidgetIcon(
  @DrawableRes id: Int,
  size: Dp,
  tint: ColorProvider,
  description: String?,
  modifier: GlanceModifier = GlanceModifier,
) {
  Image(
    ImageProvider(id),
    contentDescription = description,
    modifier = modifier.size(size),
    colorFilter = ColorFilter.tint(tint),
  )
}

internal fun textStyle(
  size: TextUnit,
  color: ColorProvider,
  weight: FontWeight = FontWeight.Normal,
  align: TextAlign? = null,
) = TextStyle(color = color, fontSize = size, fontWeight = weight, textAlign = align)
