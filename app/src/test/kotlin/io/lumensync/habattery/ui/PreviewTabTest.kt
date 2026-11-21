package io.lumensync.habattery.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.ui.PreviewTab.TAB_4X2
import io.lumensync.habattery.ui.PreviewTab.TAB_4X3
import io.lumensync.habattery.ui.PreviewTab.TAB_5X2
import io.lumensync.habattery.ui.PreviewTab.TAB_5X3
import io.lumensync.habattery.widget.BatteryWidgetReceiver
import io.lumensync.habattery.widget.InverterWidgetReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PreviewTabTest {
  private fun size(w: Float, h: Float) = DpSize(w.dp, h.dp)

  private fun battery(w: Float, h: Float) = NamedWidget(BatteryWidgetReceiver::class.java.name, size(w, h))

  private fun strip(w: Float, h: Float) = NamedWidget(InverterWidgetReceiver::class.java.name, size(w, h))

  @Test
  fun tabs_areTheFourLayoutsInDesignOrder() =
    assertEquals(listOf("4x2", "5x2", "4x3", "5x3"), PreviewTab.entries.map { "${it.columns}x${it.rows}" })

  @Test
  fun tabs_matchTheirLayouts() =
    PreviewTab.entries.forEach { assertEquals("${it.columns}x${it.rows}", it.layout.name) }

  @Test
  fun opened_ownerSizes_openTheirTab() {
    assertEquals(TAB_4X2, PreviewTab.first(battery(267.3f, 131.6f), null))
    assertEquals(TAB_5X2, PreviewTab.first(battery(340f, 132f), null))
    assertEquals(TAB_4X3, PreviewTab.first(battery(267f, 205f), null))
    assertEquals(TAB_5X3, PreviewTab.first(battery(340f, 205f), null))
  }

  @Test
  fun opened_fiveByFour_opensFiveByThree() = assertEquals(TAB_5X3, PreviewTab.first(battery(340f, 278f), null))

  /** Android shows a bucket from about 1 dp below it (`ceil(size) + 1 > bucket`). */
  @Test
  fun edges_atEachBucket() =
    PreviewTab.entries.forEach {
      val w = it.layout.bucketWidth
      val h = it.layout.bucketHeight
      assertEquals(it.name, it, PreviewTab.first(battery(w, h), null))
      assertEquals(it.name, it, PreviewTab.first(battery(w - 0.9f, h - 0.9f), null))
    }

  @Test
  fun edges_oneDpBelowABucket_openTheSmallerTab() {
    assertEquals(TAB_4X2, PreviewTab.first(battery(333f, 126f), null))
    assertEquals(TAB_4X2, PreviewTab.first(battery(261f, 198f), null))
    assertEquals(TAB_4X3, PreviewTab.first(battery(333f, 199f), null))
    assertEquals(TAB_5X2, PreviewTab.first(battery(334f, 198f), null))
    assertEquals(TAB_4X2, PreviewTab.first(battery(260f, 125f), null))
  }

  @Test
  fun opened_winsOverPlaced() = assertEquals(TAB_4X3, PreviewTab.first(battery(267f, 205f), size(340f, 205f)))

  /** A widget being placed has no size yet: it opens on the smallest, not on another widget's. */
  @Test
  fun opened_withoutSize_opensFourByTwo() = assertEquals(TAB_4X2, PreviewTab.first(battery(0f, 0f), size(340f, 205f)))

  @Test
  fun named_batteryWidgetElseNull() {
    assertEquals(TAB_5X2, PreviewTab.named(battery(340f, 132f)))
    assertEquals(null, PreviewTab.named(null))
  }

  /** A strip's one-row size is no battery layout: it names no tab. */
  @Test
  fun named_strip_namesNoTab() {
    assertEquals(null, PreviewTab.named(strip(188f, 53f)))
    assertEquals(null, PreviewTab.named(strip(334f, 53f)))
  }

  /** A widget whose provider Android no longer knows names no tab either. */
  @Test
  fun named_unknownProvider_namesNoTab() = assertEquals(null, PreviewTab.named(NamedWidget(null, size(340f, 132f))))

  @Test
  fun openedStrip_opensTheFirstPlacedBatteryWidget() =
    assertEquals(TAB_5X2, PreviewTab.first(strip(261f, 53f), size(340f, 132f)))

  @Test
  fun openedStrip_noBatteryWidget_opensFourByTwo() = assertEquals(TAB_4X2, PreviewTab.first(strip(334f, 53f), null))

  @Test
  fun noOpened_firstPlacedWidget() = assertEquals(TAB_5X2, PreviewTab.first(null, size(340f, 132f)))

  @Test
  fun noWidget_opensFourByTwo() = assertEquals(TAB_4X2, PreviewTab.first(null, null))

  @Test
  fun scale_fits_staysOne() {
    assertEquals(1f, PreviewTab.scale(340f, 358f), 0f)
    assertEquals(1f, PreviewTab.scale(340f, 340f), 0f)
  }

  @Test
  fun scale_narrow_shrinksToTheWidth() = assertEquals(328f / 340f, PreviewTab.scale(340f, 328f), 1e-6f)

  @Test
  fun scale_neverGrows() = assertEquals(1f, PreviewTab.scale(267f, 600f), 0f)

  /** A widget tap while the app is open: a new request with a higher count. */
  @Test
  fun next_namedTab_isANewRequest() =
    assertEquals(PreviewRequest(TAB_5X3, 3), PreviewRequest(TAB_4X2, 2).next(TAB_5X3))

  /** The launcher icon names no widget: the request stays, so the card keeps its tab. */
  @Test
  fun next_noneNamed_keepsTheRequest() {
    val request = PreviewRequest(TAB_4X3, 2)
    assertSame(request, request.next(null))
  }

  @Test
  fun tabFor_firstDrawing_showsTheFirstTab() = assertEquals(TAB_4X3, PreviewRequest(TAB_4X3).tabFor(TAB_4X3, seenCount = 0))

  @Test
  fun tabFor_newRequest_switchesToItsTab() = assertEquals(TAB_5X3, PreviewRequest(TAB_5X3, 1).tabFor(TAB_4X2, seenCount = 0))

  /** The card left and came back (a scrolled-away list item): the user's tab stays. */
  @Test
  fun tabFor_seenRequest_keepsTheUserTab() = assertEquals(TAB_4X2, PreviewRequest(TAB_5X3, 1).tabFor(TAB_4X2, seenCount = 1))

  /** Spec 0006: tap, the user picks a tab, rotation (count 0 again), then another tap. */
  @Test
  fun tapRotationTap_keepsTheUserTabUntilTheNextTap() {
    var request = PreviewRequest(TAB_4X2)
    var tab = request.tab
    var seen = request.count
    fun arrive(new: PreviewRequest) {
      request = new
      tab = request.tabFor(tab, seen)
      seen = request.count
    }

    arrive(request.next(TAB_5X3))
    assertEquals(TAB_5X3, tab)
    tab = TAB_4X3 // the user picks a tab
    arrive(PreviewRequest(TAB_4X2)) // the new activity after rotation
    assertEquals(TAB_4X3, tab)
    arrive(request.next(TAB_5X2))
    assertEquals(TAB_5X2, tab)
  }
}
