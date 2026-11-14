package io.lumensync.habattery.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.format.Formatters

/** Top row: charge %, the metrics with the refresh mode below them, and the ↻ chip. */
@Composable
internal fun WidgetHeader(
  overview: BatteryOverview,
  reading: BatteryReading,
  chargeColor: ColorProvider,
  dimens: WidgetDimens,
  formatters: Formatters,
  refreshAction: Action,
) {
  Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
      text = formatters.chargePercent(reading.chargePercent),
      style =
        TextStyle(color = chargeColor, fontSize = dimens.chargeText, fontWeight = FontWeight.Bold),
      maxLines = 1,
    )
    Column(modifier = GlanceModifier.defaultWeight().padding(start = dimens.gap)) {
      Metrics(reading, formatters, dimens)
      Text(
        text = formatters.refreshMode(overview.refresh),
        style = TextStyle(color = WidgetColors.secondaryText, fontSize = dimens.smallText),
        maxLines = 1,
      )
    }
    RefreshChip(overview.refreshing, refreshAction, dimens, WidgetColors.secondaryText)
  }
}

/**
 * The ↻ chip. While a sync runs it stays ↻ but dimmed, so its width never changes and it never cuts
 * the metrics. Screen readers hear "Refresh" or "Refreshing…".
 */
@Composable
internal fun RefreshChip(
  refreshing: Boolean,
  refreshAction: Action,
  dimens: WidgetDimens,
  textColor: ColorProvider,
) {
  val context = LocalContext.current
  val description =
    context.getString(if (refreshing) R.string.widget_refreshing else R.string.widget_refresh_action)
  Box(
    GlanceModifier.background(WidgetColors.chipBackground)
      .cornerRadius(dimens.chipCorner)
      .clickable(refreshAction)
      .padding(horizontal = dimens.chipPadH, vertical = dimens.chipPadV)
      .semantics { contentDescription = description }
  ) {
    Text(
      text = context.getString(R.string.widget_refresh_symbol),
      style =
        TextStyle(
          color = if (refreshing) WidgetColors.dimmedText else textColor,
          fontSize = dimens.chipText,
          fontWeight = FontWeight.Bold,
        ),
      maxLines = 1,
    )
  }
}

/**
 * Current and total voltage in one row; a missing value is left out. Power is not here: the status
 * line already shows it.
 */
@Composable
private fun Metrics(reading: BatteryReading, formatters: Formatters, dimens: WidgetDimens) {
  val values =
    listOfNotNull(
      reading.currentA?.let(formatters::current),
      reading.totalVoltageV?.let(formatters::voltage),
    )
  Row {
    values.forEachIndexed { index, value ->
      Text(
        text = value,
        modifier = if (index == 0) GlanceModifier else GlanceModifier.padding(start = 6.dp),
        style =
          TextStyle(
            color = WidgetColors.primaryText,
            fontSize = dimens.metricsText,
            fontWeight = FontWeight.Bold,
          ),
        maxLines = 1,
      )
    }
  }
}
