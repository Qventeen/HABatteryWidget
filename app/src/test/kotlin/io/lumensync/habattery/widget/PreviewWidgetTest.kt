package io.lumensync.habattery.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.ThemeChoice
import io.lumensync.habattery.testing.configuredSettings
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

  private fun widget() = PreviewWidget(model, FontPercents(), WidgetColors(ThemeChoice.SYSTEM))

  /** All drawings share one widget id: overlapping ones wait for each other, so neither fails. */
  @Test
  fun overlappingDrawings_bothFinish() = runBlocking {
    val first = async(Dispatchers.Default) { widget().views(context, LayoutSpec.L4x2) }
    val second = async(Dispatchers.Default) { widget().views(context, LayoutSpec.L5x3) }
    assertNotNull(first.await())
    assertNotNull(second.await())
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
