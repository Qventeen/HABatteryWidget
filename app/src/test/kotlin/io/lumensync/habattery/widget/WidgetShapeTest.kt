package io.lumensync.habattery.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.unit.DpSize
import androidx.datastore.dataStoreFile
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.runComposition
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.domain.EntityFill
import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.testing.configuredSettings
import io.lumensync.habattery.testing.inverterReading
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Each size of a widget keeps one view shape whatever the data shows. Glance gives every new
 * shape its own root layout id, and a home screen that gets a new layout id builds the views anew
 * instead of updating them; HyperOS then draws the new views without its widget scale.
 */
@OptIn(ExperimentalGlanceApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class WidgetShapeTest {
  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  private val settings =
    configuredSettings(inverter = true).copy(entities = EntityFill.fromPrefix("jk", EntitySettings()))

  /** The check can fail: the empty state and the data have different shapes. */
  @Test
  fun aNewShape_getsANewLayoutId() {
    val (data, empty) =
      layoutIds(LayoutSpec.L5x2.bucket, true, false) { hasToken ->
        BatteryWidgetContent(CacheState(reading()), settings, hasToken, NOW, appWidgetId = 1, FakeMeasurer)
      }
    assertNotEquals(data, empty)
  }

  @Test
  fun battery_chargingAndIdle_keepOneShape() =
    assertBatteryShape(CacheState(reading(currentA = 5.0)), CacheState(reading(currentA = 0.0)))

  @Test
  fun battery_syncError_keepsTheShape() =
    assertBatteryShape(CacheState(reading()), CacheState(reading(), error = SyncError.Unreachable))

  @Test
  fun battery_temperatureWithoutLevel_keepsTheShape() =
    assertBatteryShape(CacheState(reading()), CacheState(reading(temperature = null)))

  @Test
  fun battery_everydayChanges_keepTheShape() {
    val base = CacheState(reading())
    assertBatteryShape(base, CacheState(reading(currentA = -8.0)))
    assertBatteryShape(base, base.copy(refreshingSinceMillis = NOW))
    assertBatteryShape(base, CacheState(reading().copy(balancing = true, updatedAtMillis = NOW - 3_600_000L)))
  }

  @Test
  fun strip_everydayChanges_keepTheShape() {
    val base = CacheState(inverterReading = inverterReading())
    assertStripShape(base, CacheState(inverterReading = inverterReading().copy(mode = "Off-Grid", gridVoltageV = 0.0)))
    assertStripShape(base, base.copy(refreshingSinceMillis = NOW))
  }

  @Test
  fun strip_syncError_keepsTheShape() =
    assertStripShape(
      CacheState(inverterReading = inverterReading()),
      CacheState(inverterReading = inverterReading(), inverterError = SyncError.Unreachable),
    )

  private fun reading(currentA: Double = 5.0, temperature: Double? = 23.0) =
    BatteryReading(
      chargePercent = 99.0,
      powerW = currentA * 53.6,
      currentA = currentA,
      totalVoltageV = 53.6,
      capacityRemainingAh = 99.0,
      totalCapacityAh = 100.0,
      averageCellVoltageV = 3.35,
      deltaCellVoltageV = 0.003,
      mosfetTemperatureC = 24.0,
      temperaturesC = listOf(temperature),
      balancing = false,
      updatedAtMillis = NOW - 60_000L,
    )

  private fun assertBatteryShape(before: CacheState, after: CacheState) {
    for (spec in LayoutSpec.all) {
      val (first, second) =
        layoutIds(spec.bucket, before, after) { cache ->
          BatteryWidgetContent(cache, settings, hasToken = true, NOW, appWidgetId = 1, FakeMeasurer)
        }
      assertEquals("layout id at ${spec.name}", first, second)
    }
  }

  private fun assertStripShape(before: CacheState, after: CacheState) {
    for (spec in InverterLayoutSpec.entries) {
      val (first, second) =
        layoutIds(spec.bucket, before, after) { cache ->
          InverterStrip(cache, settings, hasToken = true, NOW, appWidgetId = 1, FakeMeasurer)
        }
      assertEquals("layout id at ${spec.name}", first, second)
    }
  }

  /**
   * The root layout ids of two drawings of one live session, [first] and then [second], as the
   * home screen widget's session redraws on new data. Glance stores a drawing's layouts only after
   * it hands out the views, and the next drawing numbers its layout from that store, so the change
   * waits until the store exists. The store's file name is Glance's own.
   */
  private fun <T> layoutIds(
    bucket: DpSize,
    first: T,
    second: T,
    content: @Composable (T) -> Unit,
  ): Pair<Int, Int> = runBlocking {
    val appWidgetId = nextWidgetId.getAndDecrement()
    val glanceId =
      checkNotNull(
        GlanceAppWidgetManager(context)
          .getGlanceIdBy(Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
      )
    val shown = mutableStateOf(first)
    val drawn = Channel<Int>(Channel.UNLIMITED)
    val session = launch {
      PreviewWidget { content(shown.value) }
        .runComposition(context, glanceId, sizes = listOf(bucket))
        .collect { drawn.send(it.layoutId) }
    }
    withTimeout(TIMEOUT_MS) {
      val firstId = drawn.receive()
      val store = context.dataStoreFile("appWidgetLayout-$appWidgetId")
      while (!store.exists()) delay(POLL_MS)
      shown.value = second
      Snapshot.sendApplyNotifications()
      val secondId = drawn.receive()
      session.cancelAndJoin()
      firstId to secondId
    }
  }

  private companion object {
    const val NOW = 1_800_000_000_000L
    const val TIMEOUT_MS = 10_000L
    const val POLL_MS = 10L

    /**
     * Glance keeps a store per widget id for the life of the process, bound to the context it
     * was opened with, so every drawing pair takes an id no test used before. Ids below -1 are
     * not looked up among the placed widgets.
     */
    val nextWidgetId = AtomicInteger(-3)
  }
}
