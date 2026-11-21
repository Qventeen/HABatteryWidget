package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.FetchRequest
import io.lumensync.habattery.domain.model.PartResult
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.SyncResult
import io.lumensync.habattery.domain.port.BatterySource
import io.lumensync.habattery.domain.port.LevelNotifier
import io.lumensync.habattery.domain.port.ReadingCache
import io.lumensync.habattery.domain.port.SettingsRepository
import io.lumensync.habattery.domain.port.TokenStore
import io.lumensync.habattery.domain.port.WidgetRefresher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The only sync path: fetch, save to the cache, notify, redraw widgets. Runs are serialized. */
class SyncBattery(
  private val settings: SettingsRepository,
  private val tokens: TokenStore,
  private val source: BatterySource,
  private val cache: ReadingCache,
  private val notifier: LevelNotifier,
  private val widgets: WidgetRefresher,
) {
  private val mutex = Mutex()

  suspend operator fun invoke(): SyncResult =
    mutex.withLock {
      var refreshingCleared = false
      try {
        val current = settings.settings.first()
        val token = tokens.get(current.connection.tokenType)
        val result =
          if (token == null || !Validation.isBatteryConfigured(current, hasToken = true)) {
            SyncResult.Failure(SyncError.NotConfigured)
          } else {
            // The cache holds the battery part only, so the inverter is not asked.
            val request = FetchRequest(current.entities, inverter = null)
            when (val battery = source.fetch(current.connection, request, token).battery) {
              is PartResult.Ok -> SyncResult.Success(battery.reading)
              is PartResult.Failed -> SyncResult.Failure(battery.error)
              // A source answers every asked part; a missing one is no usable answer.
              null -> SyncResult.Failure(SyncError.BadResponse)
            }
          }

        // Read once, before the write, so tracking and posting agree on the same answer.
        val enabled = current.notificationsOn && notifier.canPost()
        var alerts = emptyList<LevelAlert>()
        cache.update { state ->
          when (result) {
            is SyncResult.Success -> {
              val r = result.reading
              val outcome = LevelAlerts.evaluate(r, current, enabled, state.levelAlerts)
              alerts = outcome.alerts
              state.copy(
                reading = r,
                error = null,
                refreshingSinceMillis = null,
                levelAlerts = outcome.state,
              )
            }
            is SyncResult.Failure -> state.copy(error = result.error, refreshingSinceMillis = null)
          }
        }
        refreshingCleared = true
        alerts.forEach(notifier::post)
        result
      } finally {
        withContext(NonCancellable) {
          // Best effort: a failing store must not hide the original exception.
          if (!refreshingCleared) {
            runCatching { cache.update { it.copy(refreshingSinceMillis = null) } }
          }
          widgets.refreshAll()
        }
      }
    }
}
