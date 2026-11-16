package io.lumensync.habattery.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LayoutSpecTest {
  @Test
  fun buckets_areTheDesignSizesMinusSix() {
    assertEquals(listOf(261f to 126f, 334f to 126f, 261f to 199f, 334f to 199f), LayoutSpec.all.map { it.bucketWidth to it.bucketHeight })
  }

  @Test
  fun forSize_eachBucketAndOwnerSize_picksItsLayout() {
    LayoutSpec.all.forEach { spec ->
      assertSame(spec.name, spec, LayoutSpec.forSize(spec.bucketWidth, spec.bucketHeight))
      assertSame(spec.name, spec, LayoutSpec.forSize(spec.bucketWidth + 6, spec.bucketHeight + 6))
    }
  }

  @Test fun forSize_fiveByFour_stretchesFiveByThree() = assertSame(LayoutSpec.L5x3, LayoutSpec.forSize(340f, 278f))

  @Test fun forSize_betweenBuckets_picksTheFittingOne() = assertSame(LayoutSpec.L4x2, LayoutSpec.forSize(300f, 150f))

  /** The platform allows about 1 dp of rounding (`ceil(size) + 1 > bucket`). */
  @Test fun forSize_halfDpShort_stillFits() = assertSame(LayoutSpec.L5x2, LayoutSpec.forSize(333.4f, 130f))

  @Test fun forSize_tooSmall_takesTheSmallest() = assertSame(LayoutSpec.L4x2, LayoutSpec.forSize(200f, 100f))

  @Test
  fun boxes4x2_atTheDesignSize_matchTheReadme() {
    val b = LayoutSpec.L4x2.boxes(267f, 132f, cellPills = 4)
    assertEquals(Box(89f, 35f), b.packPill)
    assertEquals(Box(66f, 16f), b.cellPill)
    // 247 − (2 + 12 + 4) − 6 − 15 − 6 − 6 − 32 − 6 − 32
    assertEquals(Box(126f, 32f), b.topTexts)
  }

  @Test
  fun boxes5x2_ringTakesTheFullHeight() {
    val b = LayoutSpec.L5x2.boxes(340f, 132f, cellPills = 4)
    assertEquals(Box(108f, 35f), b.packPill)
    assertEquals(Box(84f, 16f), b.cellPill)
    assertEquals(200f - 121f, b.topTexts.width, 0f)
  }

  @Test
  fun boxes4x3_gridUnderRingAndPack() {
    val b = LayoutSpec.L4x3.boxes(267f, 205f, cellPills = 4)
    assertEquals(Box(153f, 40f), b.packPill)
    assertEquals(Box(121f, 20f), b.cellPill)
    assertEquals(Box(121f, 20f), b.mosfetPill)
    assertEquals(Box(247f, 20f), LayoutSpec.L4x3.boxes(267f, 205f, cellPills = 3).mosfetPill)
  }

  @Test
  fun boxes5x3_columns() {
    val b = LayoutSpec.L5x3.boxes(340f, 205f, cellPills = 4)
    assertEquals(Box(112f, 63f), b.packPill)
    assertEquals(Box(84f, 28.75f), b.cellPill)
    assertEquals(Box(84f, (133f - 12f) / 3f), LayoutSpec.L5x3.boxes(340f, 205f, cellPills = 3).cellPill)
  }

  @Test
  fun boxes_atTheBucket_pillsShrink() {
    val b = LayoutSpec.L4x2.boxes(261f, 126f, cellPills = 4)
    assertEquals(Box(83f, 32f), b.packPill)
    assertEquals(Box(66f, 14.5f), b.cellPill)
  }

  /** Android shows a bucket from 1 dp below it, so the texts are fitted 1 dp smaller each way. */
  @Test
  fun fitBoxes_areOneDpBelowTheBucket() =
    LayoutSpec.all.forEach { spec ->
      assertEquals(spec.boxes(spec.bucketWidth - 1f, spec.bucketHeight - 1f, 4), spec.fitBoxes(4))
    }
}
