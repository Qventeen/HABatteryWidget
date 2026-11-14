package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.Settings

object RefreshPolicy {
  /** Minutes between periodic syncs, or null when no periodic work should be scheduled. */
  fun periodicIntervalMinutes(settings: Settings, hasWidgets: Boolean): Int? {
    val refresh = settings.refresh
    val minutes = refresh.intervalMinutes ?: return null
    return if (
      refresh.auto &&
        Validation.refresh(refresh).isEmpty() &&
        (hasWidgets || settings.notifyOnFull)
    )
      minutes
    else null
  }
}
