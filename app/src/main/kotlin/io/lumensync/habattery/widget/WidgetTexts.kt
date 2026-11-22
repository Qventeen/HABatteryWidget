package io.lumensync.habattery.widget

import android.content.Context
import androidx.annotation.StringRes
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.MainValue
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.UnderSoc
import io.lumensync.habattery.format.Formatters

/** The widget's texts of a [WidgetModel]; "—" when missing. */
class WidgetTexts(private val context: Context, val formatters: Formatters) {
  val missing: String
    get() = context.getString(R.string.value_missing)

  val socLabel: String
    get() = context.getString(R.string.widget_soc_label)

  val openApp: String
    get() = context.getString(R.string.widget_open_app)

  /** The sync error icon's content description: "Sync error: <reason>" (spec 0005, 5.3). */
  fun syncErrorDescription(error: SyncError): String =
    context.getString(R.string.widget_sync_error, formatters.error(error))

  fun emptyTitle(kind: WidgetModel.EmptyKind): String =
    context.getString(
      when (kind) {
        WidgetModel.EmptyKind.SETUP -> R.string.error_not_configured
        WidgetModel.EmptyKind.NO_DATA -> R.string.widget_no_data
      }
    )

  fun timeLeft(m: WidgetModel.Data): String = m.timeLeft?.let(formatters::duration) ?: missing

  fun updatedAt(m: WidgetModel.Data): String = formatters.updatedAt(m.updatedAtMillis)

  fun soc(m: WidgetModel.Data): String = formatters.socNumber(m.socPercent)

  fun underSoc(m: WidgetModel.Data): String =
    m.underSocValue?.let {
      when (m.underSoc) {
        UnderSoc.KWH -> formatters.kilowattHours(it)
        UnderSoc.AH -> formatters.ampHours(it)
      }
    } ?: missing

  fun voltage(m: WidgetModel.Data): String = m.voltage?.let(formatters::voltage) ?: missing

  fun main(m: WidgetModel.Data): String =
    m.mainValue?.let {
      when (m.main) {
        MainValue.CURRENT -> formatters.current(it)
        MainValue.POWER -> formatters.power(it)
      }
    } ?: missing

  fun averageCell(m: WidgetModel.Data): String =
    m.averageCellVoltage?.let(formatters::cellVoltage) ?: missing

  fun delta(m: WidgetModel.Data): String =
    context.getString(R.string.delta_label, m.deltaMv?.let(formatters::deltaMv) ?: missing)

  fun temperature(celsius: Double?): String = celsius?.let(formatters::temperature) ?: missing

  fun ringDescription(m: WidgetModel.Data): String =
    context.resources.getQuantityString(R.plurals.widget_soc_description, m.socPercent, m.socPercent)

  companion object {
    @StringRes
    fun balancingDescription(balancing: Boolean?): Int =
      when (balancing) {
        true -> R.string.widget_balancing_on
        false -> R.string.widget_balancing_off
        null -> R.string.widget_balancing_unknown
      }

    @StringRes
    fun flowDescription(flow: WidgetModel.Flow): Int? =
      when (flow) {
        WidgetModel.Flow.UP -> R.string.widget_charging
        WidgetModel.Flow.DOWN -> R.string.widget_discharging
        WidgetModel.Flow.NONE -> null
      }

    @StringRes
    fun refreshDescription(refreshing: Boolean): Int =
      if (refreshing) R.string.widget_refreshing else R.string.widget_refresh_action
  }
}
