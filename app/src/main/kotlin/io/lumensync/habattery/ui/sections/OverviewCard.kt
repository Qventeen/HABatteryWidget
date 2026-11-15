package io.lumensync.habattery.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.BatteryMath
import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.format.Formatters

/** The same values as the widget, from the same [BatteryOverview], or the setup text. */
@Composable
fun OverviewCard(overview: BatteryOverview, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val formatters = remember(context) { Formatters(context) }
  val reading = overview.reading

  Card(modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      when {
        overview.setupNeeded -> Text(stringResource(R.string.overview_setup))
        reading == null -> Text(stringResource(R.string.widget_no_data))
        else -> ReadingDetails(overview, reading, formatters)
      }
      if (!overview.setupNeeded) {
        overview.error?.let { Text(formatters.error(it), color = MaterialTheme.colorScheme.error) }
      }
    }
  }
}

@Composable
private fun ReadingDetails(
  overview: BatteryOverview,
  reading: BatteryReading,
  formatters: Formatters,
) {
  val secondary = MaterialTheme.colorScheme.onSurfaceVariant
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      formatters.chargePercent(reading.chargePercent),
      style = MaterialTheme.typography.displaySmall,
      fontWeight = FontWeight.Bold,
    )
    Column(Modifier.weight(1f).padding(start = 16.dp)) {
      overview.state?.let { state ->
        Text(
          formatters.status(state, reading.powerW, overview.timeRemaining),
          style = MaterialTheme.typography.titleMedium,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        reading.currentA?.let { Text(formatters.current(it)) }
        reading.totalVoltageV?.let { Text(formatters.voltage(it)) }
      }
      Text(
        formatters.refreshMode(overview.refresh),
        style = MaterialTheme.typography.bodySmall,
        color = secondary,
      )
    }
  }
  LinearProgressIndicator(
    progress = { BatteryMath.progress(reading.chargePercent) },
    modifier = Modifier.fillMaxWidth(),
  )
  Row(Modifier.fillMaxWidth()) {
    Text(
      stringResource(R.string.widget_updated_at, formatters.updatedAt(reading.updatedAtMillis)),
      modifier = Modifier.weight(1f),
      style = MaterialTheme.typography.bodySmall,
      color = secondary,
    )
    BatteryMath.deltaMv(reading)?.let { deltaMv ->
      Text(
        stringResource(R.string.delta_label, formatters.deltaMv(deltaMv)),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}
