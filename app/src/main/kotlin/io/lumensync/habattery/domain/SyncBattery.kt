package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.SyncResult
import io.lumensync.habattery.domain.port.BatterySource
import io.lumensync.habattery.domain.port.FullChargeNotifier
import io.lumensync.habattery.domain.port.ReadingCache
import io.lumensync.habattery.domain.port.SettingsRepository
import io.lumensync.habattery.domain.port.TokenStore
import io.lumensync.habattery.domain.port.WidgetRefresher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The only sync path: fetch, save to the cache, notify, redraw widgets. Runs are serialized. */
class SyncBattery(
  private val settings: SettingsRepository,
  private val tokens: TokenStore,
  private val source: BatterySource,
  private val cache: ReadingCache,
  private val notifier: FullChargeNotifier,
  private val widgets: WidgetRefresher,
) {
  private val mutex = Mutex()

  suspend operator fun invoke(): SyncResult =
    mutex.withLock {
      val current = settings.settings.first()
      val token = tokens.get()
      val result =
        if (token == null || !Validation.isConfigured(current, hasToken = true)) {
          SyncResult.Failure(SyncError.NotConfigured)
        } else {
          source.fetch(current.connection, current.entities, token)
        }

      var notify = false
      cache.update { state ->
        when (result) {
          is SyncResult.Success -> {
            val r = result.reading
            val decision =
              FullChargeRule.evaluate(
                state = BatteryMath.state(r.chargePercent, r.powerW),
                chargePercent = r.chargePercent,
                enabled = current.notifyOnFull,
                alreadyNotified = state.fullChargeNotified,
              )
            notify = decision.notify
            state.copy(
              reading = r,
              error = null,
              refreshingSinceMillis = null,
              fullChargeNotified = decision.notified,
            )
          }
          is SyncResult.Failure -> state.copy(error = result.error, refreshingSinceMillis = null)
        }
      }
      if (notify) notifier.showFullCharge()
      widgets.refreshAll()
      result
    }
}
