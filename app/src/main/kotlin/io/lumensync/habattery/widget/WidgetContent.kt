package io.lumensync.habattery.widget

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.BatteryMath
import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.WidgetLevels
import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.format.Formatters
import kotlin.math.roundToInt

/**
 * The whole widget, drawn from [overview] only. Tapping the card runs [openAppAction]; the ↻ chip
 * runs [refreshAction].
 */
@Composable
fun WidgetContent(overview: BatteryOverview, refreshAction: Action, openAppAction: Action) {
  val context = LocalContext.current
  val formatters = remember(context) { Formatters(context) }
  val size = LocalSize.current
  val reading = overview.reading
  val spec = WidgetLayoutSpec.fromSize(size.height.value)
  val dimens = WidgetDimens.of(spec.tier, narrow = size.width < WidgetDimens.NARROW_WIDTH)

  WidgetCard(dimens, openAppAction) {
    when {
      overview.setupNeeded -> SetupContent()
      reading == null -> NoDataContent(overview, formatters, dimens, refreshAction)
      else -> ReadingContent(overview, reading, dimens, formatters, refreshAction)
    }
  }
}

@Composable
private fun WidgetCard(
  dimens: WidgetDimens,
  openAppAction: Action,
  content: @Composable ColumnScope.() -> Unit,
) {
  Box(
    GlanceModifier.fillMaxSize().background(WidgetColors.border).outerCorners().padding(1.dp)
  ) {
    Column(
      modifier =
        GlanceModifier.fillMaxSize()
          .background(WidgetColors.cardBackground)
          .innerCorners()
          .clickable(openAppAction)
          .padding(horizontal = dimens.padH, vertical = dimens.padV),
      verticalAlignment = Alignment.CenterVertically,
      content = content,
    )
  }
}

@Composable
private fun SetupContent() {
  Text(
    text = LocalContext.current.getString(R.string.error_not_configured),
    modifier = GlanceModifier.fillMaxWidth(),
    style = messageStyle(),
  )
}

@Composable
private fun NoDataContent(
  overview: BatteryOverview,
  formatters: Formatters,
  dimens: WidgetDimens,
  refreshAction: Action,
) {
  Text(
    text = LocalContext.current.getString(R.string.widget_no_data),
    modifier = GlanceModifier.fillMaxWidth(),
    style = messageStyle(),
  )
  overview.error?.let { error ->
    Text(
      text = formatters.error(error),
      modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp),
      style =
        TextStyle(
          color = WidgetColors.secondaryText,
          fontSize = 11.sp,
          textAlign = TextAlign.Center,
        ),
      maxLines = 2,
    )
  }
  Row(
    modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    RefreshChip(overview.refreshing, refreshAction, dimens, WidgetColors.accent)
  }
}

@Composable
private fun ColumnScope.ReadingContent(
  overview: BatteryOverview,
  reading: BatteryReading,
  dimens: WidgetDimens,
  formatters: Formatters,
  refreshAction: Action,
) {
  val context = LocalContext.current
  // Same rounding as the charge text, so its colour and number always agree.
  val chargeColor = WidgetColors.level(WidgetLevels.charge(reading.chargePercent.roundToInt()))

  WidgetHeader(overview, reading, chargeColor, dimens, formatters, refreshAction)

  overview.state?.let { state ->
    Text(
      text = formatters.status(state, reading.powerW, overview.timeRemaining),
      modifier = GlanceModifier.fillMaxWidth().padding(top = dimens.statusTop),
      style =
        TextStyle(
          color = WidgetColors.primaryText,
          fontSize = dimens.statusText,
          fontWeight = FontWeight.Medium,
        ),
      maxLines = dimens.statusLines,
    )
  }

  Spacer(GlanceModifier.height(dimens.barTop))
  LinearProgressIndicator(
    progress = BatteryMath.progress(reading.chargePercent),
    modifier = GlanceModifier.fillMaxWidth().height(dimens.barHeight),
    color = chargeColor,
    backgroundColor = WidgetColors.progressTrack,
  )

  Row(
    modifier = GlanceModifier.fillMaxWidth().padding(vertical = dimens.infoPadV),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val updatedAt = formatters.updatedAt(reading.updatedAtMillis)
    Text(
      text = context.getString(R.string.widget_updated_at, updatedAt),
      modifier = GlanceModifier.defaultWeight(),
      style =
        TextStyle(
          color = WidgetColors.secondaryText,
          fontSize = dimens.smallText,
          fontWeight = FontWeight.Medium,
        ),
      maxLines = 1,
    )
    BatteryMath.deltaMv(reading)?.let { deltaMv ->
      Text(
        text = context.getString(R.string.delta_label, formatters.deltaMv(deltaMv)),
        style =
          TextStyle(
            color = WidgetColors.level(WidgetLevels.delta(deltaMv)),
            fontSize = dimens.smallText,
            fontWeight = FontWeight.Bold,
          ),
        maxLines = 1,
      )
    }
  }

  overview.error?.let { error ->
    Text(
      text = formatters.error(error),
      modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp),
      style =
        TextStyle(
          color = WidgetColors.errorText,
          fontSize = dimens.smallText,
          fontWeight = FontWeight.Medium,
        ),
      maxLines = 1,
    )
  }
}

private fun messageStyle() =
  TextStyle(
    color = WidgetColors.primaryText,
    fontSize = 13.sp,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.Center,
  )

private fun GlanceModifier.outerCorners(): GlanceModifier =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    cornerRadius(android.R.dimen.system_app_widget_background_radius)
  } else {
    this
  }

private fun GlanceModifier.innerCorners(): GlanceModifier =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    cornerRadius(android.R.dimen.system_app_widget_inner_radius)
  } else {
    this
  }
