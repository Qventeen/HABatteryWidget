package io.lumensync.habattery.widget

import android.content.Context
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.glance.text.Text
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StoredContentTest {
  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  @Test
  fun drawingNow_isTheTick() = runGlanceAppWidgetUnitTest {
    setContext(context)
    val tick = MutableStateFlow(START + 1_000L)
    provideComposable { Text(drawingNow(tick, sessionStart = START).toString()) }
    onNode(hasTextEqualTo("${START + 1_000L}")).assertExists()
  }

  /** The tick moves only on a redraw, so a session started long after it has an old one. */
  @Test
  fun drawingNow_newSession_agesAgainstItsStart() = runGlanceAppWidgetUnitTest {
    setContext(context)
    val tick = MutableStateFlow(START - 3_600_000L)
    provideComposable { Text(drawingNow(tick, sessionStart = START).toString()) }
    onNode(hasTextEqualTo("$START")).assertExists()
  }

  private companion object {
    const val START = 1_800_000_000_000L
  }
}
