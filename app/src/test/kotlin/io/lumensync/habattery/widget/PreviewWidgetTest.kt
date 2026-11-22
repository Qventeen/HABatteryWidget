package io.lumensync.habattery.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.InverterWidgetModel
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.InverterFontPercents
import io.lumensync.habattery.domain.model.ThemeChoice
import io.lumensync.habattery.testing.configuredSettings
import io.lumensync.habattery.testing.inverterReading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class PreviewWidgetTest {
  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  private val settings = configuredSettings()

  private val model =
    WidgetModel.build(
      BatteryOverview.build(
        CacheState(BatteryReading(chargePercent = 54.6, updatedAtMillis = NOW - 60_000L), null),
        settings,
        hasToken = true,
        NOW,
      ),
      settings,
    )

  private fun widget() =
    PreviewWidget { WidgetContent(model, FontPercents(), WidgetColors(ThemeChoice.SYSTEM), actions = null) }

  private fun strip() =
    PreviewWidget {
      InverterWidgetContent(
        InverterWidgetModel.from(
          CacheState(inverterReading = inverterReading()),
          configuredSettings(inverter = true),
          hasToken = true,
          NOW,
        ),
        InverterLayoutSpec.S3X1,
        WidgetColors(ThemeChoice.SYSTEM),
        InverterFontPercents(),
        hostScale = 1f,
        openApp = null,
        refresh = null,
      )
    }

  /** All drawings share one widget id: overlapping ones wait for each other, so neither fails. */
  @Test
  fun overlappingDrawings_bothFinish() = runBlocking {
    val first = async(Dispatchers.Default) { widget().views(context, LayoutSpec.L4x2.bucket) }
    val second = async(Dispatchers.Default) { widget().views(context, LayoutSpec.L5x3.bucket) }
    assertNotNull(first.await())
    assertNotNull(second.await())
  }

  /** The strip's preview shares that id and waits for a battery drawing, too. */
  @Test
  fun overlappingBatteryAndStrip_bothFinish() = runBlocking {
    val battery = async(Dispatchers.Default) { widget().views(context, LayoutSpec.L4x2.bucket) }
    val inverter = async(Dispatchers.Default) { strip().views(context, InverterLayoutSpec.S3X1.bucket) }
    assertNotNull(battery.await())
    assertNotNull(inverter.await())
  }

  @Test
  fun previewOrNull_passesItsResult() = assertEquals("views", previewOrNull("test") { "views" })

  /** A broken drawing must not crash the app: the card shows the error layout instead. */
  @Test
  fun previewOrNull_failure_givesNull() = assertNull(previewOrNull<String>("test") { error("broken") })

  @Test
  fun previewOrNull_cancellation_stillCancels() {
    assertThrows(CancellationException::class.java) {
      previewOrNull<String>("test") { throw CancellationException("newer drawing") }
    }
  }

  private companion object {
    const val NOW = 1_800_000_000_000L
  }
}
