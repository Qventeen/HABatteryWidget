package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.port.ReadingCache
import io.lumensync.habattery.domain.port.SettingsRepository
import io.lumensync.habattery.domain.port.SyncScheduler
import io.lumensync.habattery.domain.port.TokenStore
import io.lumensync.habattery.domain.port.WidgetRefresher
import kotlinx.coroutines.flow.first

/** Starts a sync when the phone is unlocked and [RefreshPolicy.unlockSyncWanted] allows it. */
class UnlockRefresh(
  private val settings: SettingsRepository,
  private val cache: ReadingCache,
  private val tokens: TokenStore,
  private val scheduler: SyncScheduler,
  private val widgets: WidgetRefresher,
  private val hasWidgets: suspend () -> Boolean,
  private val clock: () -> Long,
) {
  /**
   * Marks the widgets as refreshing, redraws them and queues a sync that waits for a network.
   * Returns false, changing nothing, when no sync is wanted.
   */
  suspend operator fun invoke(): Boolean {
    val current = settings.settings.first()
    val now = clock()
    val hasToken = current.connection.tokenType in tokens.savedTypes.first()
    if (!RefreshPolicy.unlockSyncWanted(current, cache.state.first(), hasToken, hasWidgets(), now)) {
      return false
    }
    cache.update { it.copy(refreshingSinceMillis = now) }
    widgets.refreshAll()
    scheduler.syncWhenOnline()
    return true
  }
}
