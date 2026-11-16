package io.lumensync.habattery.ui

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReportedWidgetSizeTest {
  private val options =
    Bundle().apply {
      putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 267)
      putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 400)
      putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 131)
      putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 205)
    }

  @Test
  fun portrait_isMinWidthByMaxHeight() = assertEquals(DpSize(267.dp, 205.dp), reportedWidgetSize(options, portrait = true))

  @Test
  fun landscape_isMaxWidthByMinHeight() = assertEquals(DpSize(400.dp, 131.dp), reportedWidgetSize(options, portrait = false))

  @Test
  fun missingKeys_are0x0() {
    assertEquals(DpSize(0.dp, 0.dp), reportedWidgetSize(Bundle(), portrait = true))
    assertEquals(DpSize(0.dp, 0.dp), reportedWidgetSize(Bundle(), portrait = false))
  }
}
