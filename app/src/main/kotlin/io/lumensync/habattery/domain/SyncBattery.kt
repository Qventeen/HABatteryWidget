package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.FetchRequest
import io.lumensync.habattery.domain.model.FetchResult
import io.lumensync.habattery.domain.model.PartResult
import io.lumensync.habattery.domain.model.SyncError
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

  /**
   * Both parts of the result are set. A part that is not configured is not fetched and comes back
   * as `Failed(NotConfigured)`; its last reading stays in the cache.
   */
  suspend operator fun invoke(): FetchResult =
    mutex.withLock {
      var refreshingCleared = false
      try {
        val current = settings.settings.first()
        val token = tokens.get(current.connection.tokenType)
        val hasToken = token != null
        val request =
          FetchRequest(
            battery = current.entities.takeIf { Validation.isBatteryConfigured(current, hasToken) },
            inverter =
              current.inverter.entities.takeIf {
                Validation.isInverterConfigured(current, hasToken)
              },
          )
        val answer =
          if (token != null && (request.battery != null || request.inverter != null)) {
            source.fetch(current.connection, request, token)
          } else {
            null
          }
        val battery = partOf(asked = request.battery != null, answer?.battery)
        val inverter = partOf(asked = request.inverter != null, answer?.inverter)

        // Read once, before the write, so tracking and posting agree on the same answer.
        val enabled = current.notificationsOn && notifier.canPost()
        var alerts = emptyList<LevelAlert>()
        cache.update { state ->
          val batteryOutcome =
            if (battery is PartResult.Ok) {
              LevelAlerts.evaluate(battery.reading, current, enabled, state.levelAlerts)
            } else {
              null
            }
          // The inverter check starts from the battery's new states, so the write keeps both.
          val batteryStates = batteryOutcome?.state ?: state.levelAlerts
          val inverterOutcome =
            if (inverter is PartResult.Ok) {
              LevelAlerts.evaluateInverter(inverter.reading, current, enabled, batteryStates)
            } else {
              null
            }
          alerts = batteryOutcome?.alerts.orEmpty() + inverterOutcome?.alerts.orEmpty()
          state.copy(
            reading = battery.readingOr(state.reading),
            error = battery.errorOrNull(),
            inverterReading = inverter.readingOr(state.inverterReading),
            inverterError = inverter.errorOrNull(),
            refreshingSinceMillis = null,
            levelAlerts = inverterOutcome?.state ?: batteryStates,
          )
        }
        refreshingCleared = true
        alerts.forEach(notifier::post)
        FetchResult(battery, inverter)
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

/** A source answers every asked part, so an asked part it left out is no usable answer. */
private fun <T> partOf(asked: Boolean, answer: PartResult<T>?): PartResult<T> =
  when {
    !asked -> PartResult.Failed(SyncError.NotConfigured)
    answer == null -> PartResult.Failed(SyncError.BadResponse)
    else -> answer
  }

/** A failed part keeps [last]. */
private fun <T> PartResult<T>.readingOr(last: T?): T? = if (this is PartResult.Ok) reading else last

private fun PartResult<*>.errorOrNull(): SyncError? = (this as? PartResult.Failed)?.error
