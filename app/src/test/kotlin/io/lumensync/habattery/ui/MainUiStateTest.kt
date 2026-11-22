package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.InverterWidgetModel
import io.lumensync.habattery.domain.WidgetModel.EmptyKind
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.TokenType
import io.lumensync.habattery.testing.configuredSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiStateTest {
  private fun state(
    error: SyncError?,
    type: TokenType,
    inverterError: SyncError? = null,
  ): MainUiState {
    val settings = configuredSettings(tokenType = type)
    return MainUiState(
      settings = settings,
      hasToken = true,
      overview = BatteryOverview.build(CacheState(error = error), settings, true, 0L),
      inverter = InverterWidgetModel.Empty(EmptyKind.NO_DATA, inverterError),
    )
  }

  @Test
  fun showTokenTypeHint_onlyUnauthorizedAndAdmin() {
    assertTrue(state(SyncError.Unauthorized, TokenType.ADMIN).showTokenTypeHint)
    assertFalse(state(SyncError.Unauthorized, TokenType.USER).showTokenTypeHint)
    assertFalse(state(SyncError.Unreachable, TokenType.ADMIN).showTokenTypeHint)
    assertFalse(state(null, TokenType.ADMIN).showTokenTypeHint)
  }

  @Test
  fun showTokenTypeHint_inverterUnauthorized() {
    val notConfigured = SyncError.NotConfigured

    assertTrue(state(notConfigured, TokenType.ADMIN, SyncError.Unauthorized).showTokenTypeHint)
    assertFalse(state(notConfigured, TokenType.USER, SyncError.Unauthorized).showTokenTypeHint)
    assertFalse(state(notConfigured, TokenType.ADMIN, SyncError.Unreachable).showTokenTypeHint)
  }

  @Test
  fun connectionMissing_noUrlOrNoToken() {
    val settings = configuredSettings()
    val noUrl = settings.copy(connection = settings.connection.copy(url = " "))

    assertFalse(MainUiState(settings = settings, hasToken = true).connectionMissing)
    assertTrue(MainUiState(settings = noUrl, hasToken = true).connectionMissing)
    assertTrue(MainUiState(settings = settings, hasToken = false).connectionMissing)
  }

  /** Unlike the battery's setup rule, a blank charge entity leaves the connection set up. */
  @Test
  fun connectionMissing_ignoresTheEntities() {
    val settings = configuredSettings(battery = false)

    assertFalse(MainUiState(settings = settings, hasToken = true).connectionMissing)
  }
}
