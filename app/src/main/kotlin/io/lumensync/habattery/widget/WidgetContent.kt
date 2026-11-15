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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.FontGroup
import io.lumensync.habattery.domain.model.FontSizes
import io.lumensync.habattery.format.Formatters

/** What the widget's tap targets do. */
data class WidgetActions(val openApp: Action, val refresh: Action, val toggleAuto: Action)

/**
 * The whole widget, drawn from [model] and [colors] only (design v4). One layout: fixed sizes are scaled by
 * [WidgetScale]; the two buttons keep their touch size. Tapping the card opens the app.
 */
@Composable
fun WidgetContent(
  model: WidgetModel,
  fontSizes: FontSizes,
  colors: WidgetColors,
  actions: WidgetActions,
) {
  val context = LocalContext.current
  val size = LocalSize.current
  val s = WidgetScale.of(size.width.value, size.height.value)
  val texts = remember(context) { WidgetTexts(context, Formatters(context)) }
  val look =
    WidgetLook(
      s,
      fontSizes,
      context.resources.configuration.fontScale,
      context.resources.displayMetrics,
      texts,
      colors,
    )

  Box(
    GlanceModifier.fillMaxSize()
      .background(look.colors.card)
      .cornerRadius(look.dp(24f)) // Android 12+ only; square before (spec 0003, approach D1)
      .clickable(actions.openApp)
      .padding(look.dp(12f))
  ) {
    when (model) {
      is WidgetModel.Empty -> EmptyContent(model, look, actions.openApp)
      is WidgetModel.Data ->
        Column(GlanceModifier.fillMaxSize()) {
          WidgetTopRow(model, look, actions)
          WidgetBody(
            model,
            look,
            GlanceModifier.defaultWeight().fillMaxWidth().padding(top = look.dp(4f)),
          )
        }
    }
  }
}

/** Sizes for one drawing: [s] scales every fixed size; fonts follow spec 0003, approach E1. */
internal class WidgetLook(
  val s: Float,
  private val fontSizes: FontSizes,
  private val fontScale: Float,
  private val metrics: DisplayMetrics,
  val texts: WidgetTexts,
  val colors: WidgetColors,
) {
  fun dp(value: Float): Dp = (value * s).dp

  /**
   * Glance takes sp only, and the system scales sp by its font scale again. So hand over the sp that
   * shows at the capped effective size: on Android 14+ sp scales non-linearly, so use the
   * converter's exact inverse; before that, divide the font scale back out.
   */
  fun font(group: FontGroup): TextUnit = spFor(shownSize(group)).sp

  private fun spFor(dp: Float): Float =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_SP, dp * metrics.density, metrics)
    } else {
      dp / fontScale
    }

  /** The size the text is shown at, in dp (setting × font scale, capped, × [s]). */
  fun shownSize(group: FontGroup): Float = fontSizes.effective(group, fontScale) * s

  /** A text outside the font groups (the empty state): scaled, and the system scale applies. */
  fun fixedFont(sp: Float): TextUnit = (sp * s).sp
}

@Composable
private fun EmptyContent(model: WidgetModel.Empty, look: WidgetLook, openApp: Action) {
  val context = LocalContext.current
  val title =
    when (model.kind) {
      WidgetModel.EmptyKind.SETUP -> R.string.error_not_configured
      WidgetModel.EmptyKind.NO_DATA -> R.string.widget_no_data
    }
  Column(
    GlanceModifier.fillMaxSize(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    WidgetIcon(R.drawable.ic_battery, look.dp(28f), look.colors.sub, null)
    Text(
      context.getString(title),
      GlanceModifier.padding(top = look.dp(8f)),
      style = textStyle(look.fixedFont(15f), look.colors.text, FontWeight.Medium, TextAlign.Center),
      maxLines = 2,
    )
    if (model.kind == WidgetModel.EmptyKind.NO_DATA) {
      model.error?.let { error ->
        Text(
          look.texts.formatters.error(error),
          GlanceModifier.padding(top = look.dp(8f)),
          style =
            textStyle(look.font(FontGroup.SMALL), look.colors.bad, FontWeight.Medium, TextAlign.Center),
          maxLines = 2,
        )
      }
    }
    Spacer(GlanceModifier.height(look.dp(10f)))
    Box(
      GlanceModifier.height(look.dp(40f))
        .background(look.colors.okBg)
        .cornerRadius(look.dp(20f))
        .clickable(openApp)
        .padding(horizontal = look.dp(20f)),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        context.getString(R.string.widget_open_app),
        style = textStyle(look.fixedFont(14f), look.colors.accent, FontWeight.Medium),
        maxLines = 1,
      )
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
