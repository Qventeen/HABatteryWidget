package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.Settings

/** When a reading is late, how old it is, and how long the widget shows "refreshing". */
object Freshness {
  /** Extra time past the sync interval before a reading counts as late. */
  const val GRACE_MS = 300_000L

  /** A reading younger than this does not need a sync on unlock. */
  const val UNLOCK_MIN_AGE_MS = 60_000L

  /** A "refreshing" mark older than this is stale: the work died without clearing it. */
  const val REFRESHING_LIMIT_MS = 120_000L

  private const val MINUTE_MS = 60_000L

  fun isRefreshing(sinceMillis: Long?, nowMillis: Long): Boolean =
    sinceMillis != null && nowMillis - sinceMillis in 0 until REFRESHING_LIMIT_MS

  /** The interval counts only when auto refresh is on and its value is valid; else 15 minutes. */
  fun lateAfterMillis(refresh: RefreshSettings): Long {
    val usable = refresh.auto && Validation.refresh(refresh).isEmpty()
    val minutes = if (usable) refresh.intervalMinutes else null
    return (minutes ?: RefreshSettings.DEFAULT_INTERVAL) * MINUTE_MS + GRACE_MS
  }

  /** A reading dated in the future is fresh. */
  fun isLate(updatedAtMillis: Long, nowMillis: Long, refresh: RefreshSettings): Boolean =
    nowMillis - updatedAtMillis > lateAfterMillis(refresh)

  fun ageMinutes(updatedAtMillis: Long, nowMillis: Long): Long =
    maxOf(0L, nowMillis - updatedAtMillis) / MINUTE_MS

  /**
   * The earliest moment after [nowMillis] when a configured part's reading turns late, or null when
   * no configured part has a reading that is still fresh.
   */
  fun nextLateAt(
    cache: CacheState,
    settings: Settings,
    hasToken: Boolean,
    nowMillis: Long,
  ): Long? =
    configuredUpdateTimes(cache, settings, hasToken)
      .filterNotNull()
      .map { it + lateAfterMillis(settings.refresh) }
      .filter { it > nowMillis }
      .minOrNull()

  /** The reading time of each configured part; null for a part that has no reading. */
  internal fun configuredUpdateTimes(
    cache: CacheState,
    settings: Settings,
    hasToken: Boolean,
  ): List<Long?> = buildList {
    if (Validation.isBatteryConfigured(settings, hasToken)) add(cache.reading?.updatedAtMillis)
    if (Validation.isInverterConfigured(settings, hasToken)) {
      add(cache.inverterReading?.updatedAtMillis)
    }
  }
}
