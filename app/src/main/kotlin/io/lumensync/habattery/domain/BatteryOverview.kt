package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.BatteryState
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.TimeRemaining

/** One view model for the widget and the screen's preview card. */
data class BatteryOverview(
  val setupNeeded: Boolean,
  val reading: BatteryReading?,
  val state: BatteryState?,
  val timeRemaining: TimeRemaining?,
  val error: SyncError?,
  val refreshing: Boolean,
  val refresh: RefreshSettings,
) {
  companion object {
    fun build(
      cache: CacheState,
      settings: Settings,
      hasToken: Boolean,
      nowMillis: Long,
    ): BatteryOverview {
      val reading = cache.reading
      val since = cache.refreshingSinceMillis
      return BatteryOverview(
        setupNeeded = !Validation.isBatteryConfigured(settings, hasToken),
        reading = reading,
        state = reading?.let { BatteryMath.state(it.chargePercent, it.currentA) },
        timeRemaining =
          reading?.let {
            BatteryMath.timeRemaining(it, BatteryMath.fullCapacityAh(it, settings.capacityAh))
          },
        error = cache.error,
        refreshing = Freshness.isRefreshing(since, nowMillis),
        refresh = settings.refresh,
      )
    }
  }
}
