package io.lumensync.habattery.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.Problem
import io.lumensync.habattery.domain.SyncBattery
import io.lumensync.habattery.domain.Validation
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.AppearanceSettings
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.DisplaySettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.HomeScale
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SensorList
import io.lumensync.habattery.domain.model.TokenType
import io.lumensync.habattery.domain.port.BatterySource
import io.lumensync.habattery.domain.port.ReadingCache
import io.lumensync.habattery.domain.port.SettingsRepository
import io.lumensync.habattery.domain.port.SyncScheduler
import io.lumensync.habattery.domain.port.TokenStore
import io.lumensync.habattery.domain.port.WidgetRefresher
import io.lumensync.habattery.format.parseDecimal
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
  private val settings: SettingsRepository,
  private val tokens: TokenStore,
  cache: ReadingCache,
  private val sync: SyncBattery,
  private val scheduler: SyncScheduler,
  private val widgets: WidgetRefresher,
  private val source: BatterySource,
  private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
  /** Runs in flight (overlapping runs are possible) and the last validation errors. */
  private data class Local(val running: Int = 0, val errors: List<FieldError> = emptyList())

  private val local = MutableStateFlow(Local())

  val uiState: StateFlow<MainUiState> =
    combine(settings.settings, tokens.savedTypes, cache.state, local) { s, saved, c, l ->
        val hasToken = s.connection.tokenType in saved
        val overview = BatteryOverview.build(c, s, hasToken, clock())
        MainUiState(
          settings = s,
          hasToken = hasToken,
          overview = overview,
          widget = WidgetModel.build(overview, s),
          syncing = l.running > 0,
          errors = l.errors,
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

  init {
    refreshNow()
  }

  fun refreshNow() {
    viewModelScope.launch {
      local.update { it.copy(running = it.running + 1) }
      try {
        // On failure the cache already holds the last state; the screen only stops "syncing".
        guarded { sync() }
      } finally {
        local.update { it.copy(running = it.running - 1) }
      }
    }
  }

  fun saveConnection(url: String) {
    val trimmed = url.trim()
    // Validation reads only the URL; the stored token type is kept by copying inside the update.
    save(Validation.connection(ConnectionSettings(trimmed)), syncAfter = true) {
      it.copy(connection = it.connection.copy(url = trimmed))
    }
  }

  /** Saves [token] in the slot of the stored token type. */
  fun saveToken(token: String) {
    val trimmed = token.trim()
    if (trimmed.isEmpty()) return fail(FieldError(Field.TOKEN, Problem.REQUIRED))
    viewModelScope.launch {
      try {
        tokens.set(settings.settings.first().connection.tokenType, trimmed)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        // GeneralSecurityException / ProviderException from the Keystore, or a failing store.
        // No message in state.
        return@launch fail(FieldError(Field.TOKEN, Problem.INVALID))
      }
      guarded { afterSave(syncAfter = true) }
    }
  }

  /** The token type saves at once and syncs: it decides which token is sent. */
  fun saveTokenType(type: TokenType) {
    save(emptyList(), syncAfter = true) {
      it.copy(connection = it.connection.copy(tokenType = type))
    }
  }

  /**
   * Clears only the stored type's token; the widgets redraw. No sync: an empty slot already shows
   * "Set up in the app".
   */
  fun removeToken() {
    viewModelScope.launch {
      guarded {
        tokens.clear(settings.settings.first().connection.tokenType)
        local.update { it.copy(errors = emptyList()) }
        widgets.refreshAll()
      }
    }
  }

  fun saveEntities(entities: EntitySettings) {
    val clean =
      entities.copy(
        prefix = entities.prefix.trim(),
        charge = entities.charge.trim(),
        totalVoltage = entities.totalVoltage.trim(),
        current = entities.current.trim(),
        power = entities.power.trim(),
        capacityRemaining = entities.capacityRemaining.trim(),
        totalCapacity = entities.totalCapacity.trim(),
        averageCellVoltage = entities.averageCellVoltage.trim(),
        deltaCellVoltage = entities.deltaCellVoltage.trim(),
        mosfetTemperature = entities.mosfetTemperature.trim(),
        temperatures = entities.temperatures.map { it.trim() },
        balancing = entities.balancing.trim(),
      )
    save(Validation.entities(clean), syncAfter = true) { it.copy(entities = clean) }
  }

  /**
   * Fill's second step (spec 0004, 5.4): asks HA, with the saved URL and token, for every battery
   * temperature sensor of [prefix]. It changes nothing; the section puts the ids in its draft.
   * [stillCurrent] tells whether the draft prefix is still the one asked for.
   */
  suspend fun findTemperatureSensors(prefix: String, stillCurrent: () -> Boolean): SensorSearch =
    try {
      val connection = settings.settings.first().connection
      val token = tokens.get(connection.tokenType)
      if (connection.url.isBlank() || token == null) {
        SensorSearch.Skipped
      } else {
        val list = source.listTemperatureSensors(connection, prefix, token)
        if (!stillCurrent()) {
          SensorSearch.Superseded
        } else {
          when (list) {
            is SensorList.Failed -> SensorSearch.Failed(list.error)
            is SensorList.Found ->
              if (list.ids.isEmpty()) SensorSearch.NoneFound else SensorSearch.Found(list.ids)
          }
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      // Storage or Keystore failure: no note and no crash (the message could carry stored data).
      SensorSearch.Skipped
    }

  fun saveRefresh(auto: Boolean, intervalText: String) {
    val text = intervalText.trim()
    val minutes = if (text.isEmpty()) null else text.toIntOrNull()
    if (text.isNotEmpty() && minutes == null) {
      return fail(FieldError(Field.INTERVAL, Problem.INVALID))
    }
    val refresh = RefreshSettings(auto, minutes)
    save(Validation.refresh(refresh), syncAfter = false) { it.copy(refresh = refresh) }
  }

  fun setNotifyOnFull(enabled: Boolean) {
    save(emptyList(), syncAfter = false) { it.copy(notifyOnFull = enabled) }
  }

  /** Display choices save at once; only the drawings change, so no sync. */
  fun saveDisplay(display: DisplaySettings) {
    save(emptyList(), syncAfter = false) { it.copy(display = display) }
  }

  /** The theme saves at once; the widgets redraw (no sync). */
  fun saveAppearance(appearance: AppearanceSettings) {
    save(emptyList(), syncAfter = false) { it.copy(appearance = appearance) }
  }

  fun saveFontPercents(percents: FontPercents) {
    val clamped = percents.clamped()
    save(emptyList(), syncAfter = false) { it.copy(fontPercents = clamped) }
  }

  /** The home-screen scale saves at once (clamped to 80–200); the widgets redraw with it. */
  fun saveHomeScale(percent: Int) {
    val clamped = HomeScale.clamp(percent)
    save(emptyList(), syncAfter = false) { it.copy(homeScalePercent = clamped) }
  }

  /** "Reset sizes": fonts 50 %, scale 100 %, saved together. */
  fun resetWidgetSettings() {
    save(emptyList(), syncAfter = false) {
      it.copy(fontPercents = FontPercents(), homeScalePercent = HomeScale.DEFAULT)
    }
  }

  fun saveCapacity(text: String, locale: Locale) {
    val trimmed = text.trim()
    val ah = if (trimmed.isEmpty()) null else parseDecimal(trimmed, locale)
    if (trimmed.isNotEmpty() && ah == null) {
      return fail(FieldError(Field.CAPACITY, Problem.INVALID))
    }
    save(Validation.capacity(ah), syncAfter = false) { it.copy(capacityAh = ah) }
  }

  private fun fail(vararg errors: FieldError) {
    local.update { it.copy(errors = errors.toList()) }
  }

  private fun save(
    errors: List<FieldError>,
    syncAfter: Boolean,
    transform: (Settings) -> Settings,
  ) {
    if (errors.isNotEmpty()) return fail(*errors.toTypedArray())
    viewModelScope.launch {
      // A failed write keeps the stored settings as they were.
      guarded {
        settings.update(transform)
        afterSave(syncAfter)
      }
    }
  }

  /** Runs [block]; a storage or system failure ends it quietly instead of crashing the app. */
  private suspend fun guarded(block: suspend () -> Unit) {
    try {
      block()
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      // No message: it could carry stored data.
    }
  }

  private suspend fun afterSave(syncAfter: Boolean) {
    local.update { it.copy(errors = emptyList()) }
    widgets.refreshAll()
    scheduler.apply(settings.settings.first(), settingsChanged = true)
    if (syncAfter) refreshNow()
  }
}
