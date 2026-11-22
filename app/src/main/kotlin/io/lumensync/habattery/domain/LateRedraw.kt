package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.port.ReadingCache
import io.lumensync.habattery.domain.port.SettingsRepository
import io.lumensync.habattery.domain.port.SyncScheduler
import io.lumensync.habattery.domain.port.TokenStore
import kotlinx.coroutines.flow.first

/**
 * Asks for one widget redraw at [Freshness.nextLateAt], so a green dot turns amber on time: a widget
 * redraws only when something asks. Asks for nothing when no configured part's reading is fresh.
 */
class LateRedraw(
  private val settings: SettingsRepository,
  private val cache: ReadingCache,
  private val tokens: TokenStore,
  private val scheduler: SyncScheduler,
  private val clock: () -> Long,
) {
  suspend operator fun invoke() {
    val current = settings.settings.first()
    val hasToken = current.connection.tokenType in tokens.savedTypes.first()
    Freshness.nextLateAt(cache.state.first(), current, hasToken, clock())?.let(scheduler::redrawAt)
  }
}
