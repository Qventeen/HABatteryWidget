package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.Settings

object RefreshPolicy {
  /** Minutes between periodic syncs, or null when no periodic work should be scheduled. */
  fun periodicIntervalMinutes(settings: Settings, hasWidgets: Boolean): Int? {
    val refresh = settings.refresh
    val minutes = refresh.intervalMinutes ?: return null
    return if (
      refresh.auto &&
        Validation.refresh(refresh).isEmpty() &&
        (hasWidgets || settings.notificationsOn)
    )
      minutes
    else null
  }

  /**
   * Whether unlocking the phone should start a sync: the setting is on, a widget is placed, a part
   * is configured, no sync runs now, and at least one configured part has no reading or an old one.
   */
  fun unlockSyncWanted(
    settings: Settings,
    cache: CacheState,
    hasToken: Boolean,
    hasWidgets: Boolean,
    nowMillis: Long,
  ): Boolean {
    if (!settings.refresh.onUnlock || !hasWidgets) return false
    if (Freshness.isRefreshing(cache.refreshingSinceMillis, nowMillis)) return false
    val times = Freshness.configuredUpdateTimes(cache, settings, hasToken)
    return times.any { it == null || nowMillis - it >= Freshness.UNLOCK_MIN_AGE_MS }
  }
}
