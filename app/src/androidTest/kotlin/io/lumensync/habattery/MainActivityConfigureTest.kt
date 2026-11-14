package io.lumensync.habattery

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** On API 26–30 the launcher removes a new widget unless its configure activity returns OK. */
@RunWith(AndroidJUnit4::class)
class MainActivityConfigureTest {
  @Test
  fun configureIntent_returnsOkWithWidgetId() {
    val intent =
      Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, WIDGET_ID)

    // No close(): on an already finished activity it waits out its 45 s timeout.
    val scenario = ActivityScenario.launchActivityForResult<MainActivity>(intent)
    scenario.onActivity { it.finish() }
    val result = scenario.result

    assertEquals(Activity.RESULT_OK, result.resultCode)
    val id =
      result.resultData.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID,
      )
    assertEquals(WIDGET_ID, id)
  }

  private companion object {
    const val WIDGET_ID = 42
  }
}
