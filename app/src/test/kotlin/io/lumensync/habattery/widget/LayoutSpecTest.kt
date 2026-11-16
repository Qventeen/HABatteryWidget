package io.lumensync.habattery.widget

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutSpecTest {
  @Test
  fun buckets_areTheDesignSizesMinusSix() {
    assertEquals(listOf(261f to 126f, 334f to 126f, 261f to 199f, 334f to 199f), LayoutSpec.all.map { it.bucketWidth to it.bucketHeight })
  }

  /** README "Card": the design card of each layout, where the app's preview lays it out. */
  @Test
  fun designSizes_areTheReadmeCards() {
    assertEquals(
      listOf(267f to 132f, 340f to 132f, 267f to 205f, 340f to 205f),
      LayoutSpec.all.map { it.designSize.width.value to it.designSize.height.value },
    )
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
    // (247 − 8 − 8 − 56) / 2
    assertEquals(Box(87.5f, 35f), b.packPill)
    assertEquals(Box(56f, 16f), b.cellPill)
    // 247 − (2 + 12 + 4) − 6 − 15 − 6 − 6 − 32 − 6 − 32
    assertEquals(Box(126f, 32f), b.topTexts)
  }

  @Test
  fun boxes5x2_ringTakesTheFullHeight() {
    val b = LayoutSpec.L5x2.boxes(340f, 132f, cellPills = 4)
    // The top row beside the ring: 320 − 112 − 8 = 200; the pack takes what the cells leave.
    assertEquals(Box(200f - 8f - 74f, 35f), b.packPill)
    assertEquals(Box(74f, 16f), b.cellPill)
    assertEquals(200f - 121f, b.topTexts.width, 0f)
  }

  @Test
  fun boxes4x3_gridUnderRingAndPack() {
    val b = LayoutSpec.L4x3.boxes(267f, 205f, cellPills = 4)
    // (247 − 8) / 2
    assertEquals(Box(119.5f, 40f), b.packPill)
    assertEquals(Box(121f, 20f), b.cellPill)
    assertEquals(Box(121f, 20f), b.mosfetPill)
    assertEquals(Box(247f, 20f), LayoutSpec.L4x3.boxes(267f, 205f, cellPills = 3).mosfetPill)
  }

  @Test
  fun boxes5x3_columns() {
    val b = LayoutSpec.L5x3.boxes(340f, 205f, cellPills = 4)
    // (320 − 8 − 8 − 74) / 2
    assertEquals(Box(115f, 63f), b.packPill)
    assertEquals(Box(74f, 28.75f), b.cellPill)
    assertEquals(Box(74f, (133f - 12f) / 3f), LayoutSpec.L5x3.boxes(340f, 205f, cellPills = 3).cellPill)
  }

  @Test
  fun boxes_atTheBucket_pillsShrink() {
    val b = LayoutSpec.L4x2.boxes(261f, 126f, cellPills = 4)
    assertEquals(Box(84.5f, 32f), b.packPill)
    assertEquals(Box(56f, 14.5f), b.cellPill)
  }

  /** Owner, 2026-11-16: ring and pack share the width the cell column leaves (4×2, 5×3, 4×3). */
  @Test
  fun boxes_ringColumnIsThePackWidth() =
    listOf(LayoutSpec.L4x2, LayoutSpec.L5x3, LayoutSpec.L4x3).forEach { spec ->
      listOf(spec.bucketWidth to spec.bucketHeight, spec.bucketWidth + 6 to spec.bucketHeight + 6, 400f to 300f).forEach { (w, h) ->
        val pack = spec.boxes(w, h, cellPills = 4).packPill.width
        val cells = if (spec.cellGrid) 0f else spec.columnGap + spec.cellColumn
        // ring column + gap + pack (+ gap + cells) = the inner width, with ring column = pack
        assertEquals("${spec.name} at $w", w - 2 * spec.padding, pack + spec.columnGap + pack + cells, 1e-4f)
      }
    }

  /** 5×2 keeps a ring column of the design ring; the pack takes the rest beside the cell column. */
  @Test
  fun boxes5x2_packTakesTheRest() {
    val s = LayoutSpec.L5x2
    listOf(334f, 340f, 400f).forEach { w ->
      val expected = w - 2 * s.padding - s.ring - s.columnGap - s.columnGap - s.cellColumn
      assertEquals("at $w", expected, s.boxes(w, 132f, cellPills = 4).packPill.width, 1e-4f)
    }
  }

  /** Android shows a bucket from 1 dp below it, so the texts are fitted 1 dp smaller each way. */
  @Test
  fun fitBoxes_areOneDpBelowTheBucket() =
    LayoutSpec.all.forEach { spec ->
      assertEquals(spec.boxes(spec.bucketWidth - 1f, spec.bucketHeight - 1f, 4), spec.fitBoxes(4))
    }

  /** The home-screen scale: every dp size and font × k; the shape and the line-height ratios stay. */
  @Test
  fun scaled_multipliesEverySizeAndFont() =
    LayoutSpec.all.forEach { spec ->
      val k = LayoutSpec.HOST_SCALE
      val scaled = spec.scaled(k)
      assertTrue(sizes(spec).keys.containsAll(listOf("bucketWidth", "padding", "packRadius", "ring", "cellColumn", "gridPill")))
      assertEquals(spec.name, sizes(spec).mapValues { it.value * k }, sizes(scaled))
      assertEquals(spec.name, sizes(spec.fonts).mapValues { it.value * k }, sizes(scaled.fonts))
      assertEquals(spec.name, sizes(spec.empty).mapValues { it.value * k }, sizes(scaled.empty))
      assertEquals(spec.labelLineHeight, scaled.labelLineHeight)
      assertEquals(spec.underLineHeight, scaled.underLineHeight)
      assertEquals(listOf(spec.name, spec.ringFullHeight, spec.cellGrid), listOf(scaled.name, scaled.ringFullHeight, scaled.cellGrid))
    }

  @Test fun scaled_byOne_isTheSameSpec() = LayoutSpec.all.forEach { assertSame(it, it.scaled(1f)) }

  /**
   * Every non-static `Float` field (a dp or sp size) by name, so a new size cannot miss the scale.
   * The line heights are `Float?` ratios and stay out.
   */
  private fun sizes(o: Any): Map<String, Float> =
    o.javaClass.declaredFields
      .filter { it.type == Float::class.javaPrimitiveType && !Modifier.isStatic(it.modifiers) }
      .onEach { it.isAccessible = true }
      .associate { it.name to it.getFloat(o) }
}
