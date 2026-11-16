package io.lumensync.habattery.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.ui.PreviewTab.TAB_4X2
import io.lumensync.habattery.ui.PreviewTab.TAB_4X3
import io.lumensync.habattery.ui.PreviewTab.TAB_5X2
import io.lumensync.habattery.ui.PreviewTab.TAB_5X3
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewTabTest {
  private fun size(w: Float, h: Float) = DpSize(w.dp, h.dp)

  @Test
  fun tabs_areTheFourLayoutsInDesignOrder() =
    assertEquals(listOf("4x2", "5x2", "4x3", "5x3"), PreviewTab.entries.map { "${it.columns}x${it.rows}" })

  @Test
  fun tabs_matchTheirLayouts() =
    PreviewTab.entries.forEach { assertEquals("${it.columns}x${it.rows}", it.layout.name) }

  @Test
  fun configured_ownerSizes_openTheirTab() {
    assertEquals(TAB_4X2, PreviewTab.first(size(267.3f, 131.6f), null, null))
    assertEquals(TAB_5X2, PreviewTab.first(size(340f, 132f), null, null))
    assertEquals(TAB_4X3, PreviewTab.first(size(267f, 205f), null, null))
    assertEquals(TAB_5X3, PreviewTab.first(size(340f, 205f), null, null))
  }

  @Test
  fun configured_fiveByFour_opensFiveByThree() = assertEquals(TAB_5X3, PreviewTab.first(size(340f, 278f), null, null))

  /** Android shows a bucket from about 1 dp below it (`ceil(size) + 1 > bucket`). */
  @Test
  fun edges_atEachBucket() =
    PreviewTab.entries.forEach {
      val w = it.layout.bucketWidth
      val h = it.layout.bucketHeight
      assertEquals(it.name, it, PreviewTab.first(size(w, h), null, null))
      assertEquals(it.name, it, PreviewTab.first(size(w - 0.9f, h - 0.9f), null, null))
    }

  @Test
  fun edges_oneDpBelowABucket_openTheSmallerTab() {
    assertEquals(TAB_4X2, PreviewTab.first(size(333f, 126f), null, null))
    assertEquals(TAB_4X2, PreviewTab.first(size(261f, 198f), null, null))
    assertEquals(TAB_4X3, PreviewTab.first(size(333f, 199f), null, null))
    assertEquals(TAB_5X2, PreviewTab.first(size(334f, 198f), null, null))
    assertEquals(TAB_4X2, PreviewTab.first(size(260f, 125f), null, null))
  }

  @Test
  fun configured_winsOverPlaced() = assertEquals(TAB_4X3, PreviewTab.first(size(267f, 205f), null, size(340f, 205f)))

  /** A widget being placed has no size yet: it opens on the smallest, not on another widget's. */
  @Test
  fun configured_withoutSize_opensFourByTwo() = assertEquals(TAB_4X2, PreviewTab.first(size(0f, 0f), null, size(340f, 205f)))

  @Test
  fun tapped_beatsFirstPlaced() = assertEquals(TAB_4X3, PreviewTab.first(null, size(267f, 205f), size(340f, 132f)))

  @Test
  fun configured_beatsTapped() = assertEquals(TAB_5X2, PreviewTab.first(size(340f, 132f), size(267f, 205f), size(267f, 131f)))

  /** Like a configured widget: a tapped widget without a size opens the smallest, not another's. */
  @Test
  fun tapped_withoutSize_opensFourByTwo() = assertEquals(TAB_4X2, PreviewTab.first(null, size(0f, 0f), size(340f, 205f)))

  @Test
  fun named_configuredThenTappedElseNull() {
    assertEquals(TAB_5X2, PreviewTab.named(size(340f, 132f), size(267f, 205f)))
    assertEquals(TAB_4X3, PreviewTab.named(null, size(267f, 205f)))
    assertEquals(null, PreviewTab.named(null, null))
  }

  @Test
  fun noConfigured_firstPlacedWidget() = assertEquals(TAB_5X2, PreviewTab.first(null, null, size(340f, 132f)))

  @Test
  fun noWidget_opensFourByTwo() = assertEquals(TAB_4X2, PreviewTab.first(null, null, null))

  @Test
  fun scale_fits_staysOne() {
    assertEquals(1f, PreviewTab.scale(340f, 358f), 0f)
    assertEquals(1f, PreviewTab.scale(340f, 340f), 0f)
  }

  @Test
  fun scale_narrow_shrinksToTheWidth() = assertEquals(328f / 340f, PreviewTab.scale(340f, 328f), 1e-6f)

  @Test
  fun scale_neverGrows() = assertEquals(1f, PreviewTab.scale(267f, 600f), 0f)
}
