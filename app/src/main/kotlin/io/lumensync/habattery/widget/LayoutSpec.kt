package io.lumensync.habattery.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** Design font sizes (sp) of one layout, per text role (README "Font sizes"). */
data class DesignFonts(
  val soc: Float,
  val underSoc: Float,
  /** The "SOC" label in the ring; it takes the small group's percent (spec 0005, 5.4). */
  val ringLabel: Float,
  val main: Float,
  val cell: Float,
  val small: Float,
)

/** The empty state (setup needed, no data): the 2-row or the 3-row sizes of the canvas. */
data class EmptySpec(
  val icon: Float,
  val gap: Float,
  val title: Float,
  val buttonHeight: Float,
  val buttonPadding: Float,
  val buttonFont: Float,
  /** Extra space above the button (3-row boards). */
  val buttonTop: Float,
)

/** The inner sizes (dp) of a layout's parts at one widget size; the texts are fitted to them. */
data class LayoutBoxes(
  /** The space the time left and the update time share in the top row. */
  val topTexts: Box,
  val packPill: Box,
  val cellPill: Box,
  /** In the 4×3 grid with 3 pills, MOSFET takes row 2 alone. */
  val mosfetPill: Box,
  val empty: Box,
)

/**
 * One v5 layout (`docs/design/README.md`, canvas boards v5): every number in dp, written once.
 * [bucketWidth] × [bucketHeight] is its `SizeMode.Responsive` size: the design card − 6 dp each way
 * (spec 0005, 5.1). Shapes: [ringFullHeight] puts the ring over the full card height with the top
 * row beside it (5×2); [cellGrid] puts the cell pills in a 2 × 2 grid under ring and pack (4×3).
 */
data class LayoutSpec(
  val name: String,
  val bucketWidth: Float,
  val bucketHeight: Float,
  val ringFullHeight: Boolean = false,
  val cellGrid: Boolean = false,
  val padding: Float = 10f,
  val corner: Float = 24f,
  /** Corner radius of the central (pack) pills; owner, 2026-11-16. */
  val packRadius: Float = 16f,
  /** Between ring, pack column and cell column. */
  val columnGap: Float = 8f,
  /** Under the top row. */
  val bodyTop: Float = 4f,
  // Top row: [hourglass, time left] gap [balancing] gap [update time or error] toButtons [⟳] buttonGap [↻]
  val topRow: Float,
  val topGap: Float,
  val toButtons: Float,
  /** Space between the two circles; the touch areas grow by half of it each, so they touch. */
  val buttonGap: Float,
  val timeStart: Float = 2f,
  val hourglass: Float,
  val hourglassGap: Float = 4f,
  /** Balancing icon; the sync error icon has the same size (spec 0005, 5.3). */
  val statusIcon: Float,
  val buttonTouch: Float,
  val buttonFace: Float,
  val buttonIcon: Float,
  // Ring and its text stack (canvas CSS: gap, line heights; null = normal).
  val ring: Float,
  val ringStroke: Float,
  val ringTextGap: Float,
  val labelLineHeight: Float?,
  val underLineHeight: Float?,
  // Pack pills: voltage over current or power.
  val packGap: Float,
  val packPadding: Float,
  val arrow: Float,
  val arrowGap: Float,
  // Cell pills: a column of [cellColumn] wide pills, or the 4×3 grid.
  val cellColumn: Float = 0f,
  val cellGap: Float,
  val cellPadding: Float,
  val tempIcon: Float,
  val tempIconGap: Float,
  val gridGap: Float = 0f,
  val gridPill: Float = 0f,
  val gridColumnGap: Float = 0f,
  val fonts: DesignFonts,
  val empty: EmptySpec,
) {
  val bucket: DpSize
    get() = DpSize(bucketWidth.dp, bucketHeight.dp)

  /** Height of the 2 × 2 cell grid (4×3). */
  val gridHeight: Float
    get() = 2 * gridPill + cellGap

  /**
   * The boxes the texts are fitted to: the bucket less [PLATFORM_ROUNDING_DP] each way, because
   * Android shows a bucket from about 1 dp below it (`ceil(size) + 1 > bucket`).
   */
  fun fitBoxes(cellPills: Int): LayoutBoxes =
    boxes(bucketWidth - PLATFORM_ROUNDING_DP, bucketHeight - PLATFORM_ROUNDING_DP, cellPills)

  /** The boxes at a widget of [width] × [height] dp with [cellPills] (3 or 4) cell pills. */
  fun boxes(width: Float, height: Float, cellPills: Int): LayoutBoxes {
    val innerW = width - 2 * padding
    val innerH = height - 2 * padding
    // Ring and pack share the width left by the cell column equally (owner, 2026-11-16: ring as
    // the base, about 40 : 35 : 25); 5×2 keeps its full-height ring beside the top row.
    val ringColumn =
      when {
        ringFullHeight -> ring
        cellGrid -> (innerW - columnGap) / 2
        else -> (innerW - 2 * columnGap - cellColumn) / 2
      }
    val topRowWidth = if (ringFullHeight) innerW - ringColumn - columnGap else innerW
    val topFixed =
      timeStart + hourglass + hourglassGap + topGap + statusIcon + topGap + toButtons +
        2 * buttonTouch + buttonGap
    val body = innerH - topRow - bodyTop - if (cellGrid) gridGap + gridHeight else 0f
    val packWidth =
      when {
        ringFullHeight -> topRowWidth - columnGap - cellColumn
        else -> ringColumn
      }
    val cell =
      if (cellGrid) Box((innerW - gridColumnGap) / 2, gridPill)
      else Box(cellColumn, (body - (cellPills - 1) * cellGap) / cellPills)
    return LayoutBoxes(
      topTexts = Box(topRowWidth - topFixed, topRow),
      packPill = Box(packWidth, (body - packGap) / 2),
      cellPill = cell,
      mosfetPill = if (cellGrid && cellPills == 3) Box(innerW, gridPill) else cell,
      empty = Box(innerW, innerH),
    )
  }

  /**
   * Every size × [k]: the bucket (so the fit boxes are the space the home screen really gives),
   * paddings, gaps, icons, ring and fonts; line-height ratios and the shape stay. A launcher that
   * draws widgets smaller (HyperOS about 0.78) gives the layout 1 / 0.78 of the reported size
   * ([HOST_SCALE]).
   */
  fun scaled(k: Float): LayoutSpec =
    if (k == 1f) this
    else copy(
      bucketWidth = bucketWidth * k,
      bucketHeight = bucketHeight * k,
      padding = padding * k,
      corner = corner * k,
      packRadius = packRadius * k,
      columnGap = columnGap * k,
      bodyTop = bodyTop * k,
      topRow = topRow * k,
      topGap = topGap * k,
      toButtons = toButtons * k,
      buttonGap = buttonGap * k,
      timeStart = timeStart * k,
      hourglass = hourglass * k,
      hourglassGap = hourglassGap * k,
      statusIcon = statusIcon * k,
      buttonTouch = buttonTouch * k,
      buttonFace = buttonFace * k,
      buttonIcon = buttonIcon * k,
      ring = ring * k,
      ringStroke = ringStroke * k,
      ringTextGap = ringTextGap * k,
      packGap = packGap * k,
      packPadding = packPadding * k,
      arrow = arrow * k,
      arrowGap = arrowGap * k,
      cellColumn = cellColumn * k,
      cellGap = cellGap * k,
      cellPadding = cellPadding * k,
      tempIcon = tempIcon * k,
      tempIconGap = tempIconGap * k,
      gridGap = gridGap * k,
      gridPill = gridPill * k,
      gridColumnGap = gridColumnGap * k,
      fonts = DesignFonts(
        soc = fonts.soc * k,
        underSoc = fonts.underSoc * k,
        ringLabel = fonts.ringLabel * k,
        main = fonts.main * k,
        cell = fonts.cell * k,
        small = fonts.small * k,
      ),
      empty = EmptySpec(
        icon = empty.icon * k,
        gap = empty.gap * k,
        title = empty.title * k,
        buttonHeight = empty.buttonHeight * k,
        buttonPadding = empty.buttonPadding * k,
        buttonFont = empty.buttonFont * k,
        buttonTop = empty.buttonTop * k,
      ),
    )

  companion object {
    /**
     * The home-screen scale the widget uses for now (owner, 2026-11-16): the owner's HyperOS
     * launcher draws widgets at about 78 %, so the layout really gets about 1.28 × the reported
     * size. Spec 0007 replaces it with a stored Widget setting (default 100 %).
     */
    const val HOST_SCALE = 1.28f

    /** How far below a bucket Android still shows it (`RemoteViews.fitsIn`). */
    const val PLATFORM_ROUNDING_DP = 1f

    private val EMPTY_2_ROWS = EmptySpec(22f, 5f, 14f, 34f, 18f, 13f, 0f)
    private val EMPTY_3_ROWS = EmptySpec(28f, 8f, 15f, 40f, 20f, 14f, 2f)

    val L4x2 =
      LayoutSpec(
        name = "4x2",
        bucketWidth = 261f,
        bucketHeight = 126f,
        topRow = 32f,
        topGap = 6f,
        toButtons = 6f,
        buttonGap = 6f,
        hourglass = 12f,
        statusIcon = 15f,
        buttonTouch = 32f,
        buttonFace = 30f,
        buttonIcon = 16f,
        ring = 76f,
        ringStroke = 7f,
        ringTextGap = 1f,
        labelLineHeight = 1.1f,
        underLineHeight = 1.2f,
        packGap = 6f,
        packPadding = 4f,
        arrow = 14f,
        arrowGap = 3f,
        cellColumn = 56f,
        cellGap = 4f,
        cellPadding = 4f,
        tempIcon = 11f,
        tempIconGap = 3f,
        fonts = DesignFonts(soc = 28f, underSoc = 10f, ringLabel = 8.5f, main = 17f, cell = 11f, small = 10.5f),
        empty = EMPTY_2_ROWS,
      )

    val L5x2 =
      L4x2.copy(
        name = "5x2",
        bucketWidth = 334f,
        ringFullHeight = true,
        ring = 112f,
        ringStroke = 8f,
        arrow = 16f,
        arrowGap = 4f,
        cellColumn = 74f,
        cellPadding = 5f,
        fonts = DesignFonts(soc = 40f, underSoc = 12f, ringLabel = 10.5f, main = 21f, cell = 12f, small = 10.5f),
      )

    val L4x3 =
      LayoutSpec(
        name = "4x3",
        bucketWidth = 261f,
        bucketHeight = 199f,
        cellGrid = true,
        topRow = 44f,
        topGap = 8f,
        toButtons = 4f,
        buttonGap = 0f,
        hourglass = 12f,
        statusIcon = 16f,
        buttonTouch = 44f,
        buttonFace = 36f,
        buttonIcon = 18f,
        ring = 86f,
        ringStroke = 7f,
        ringTextGap = 1f,
        labelLineHeight = null,
        underLineHeight = null,
        packGap = 6f,
        packPadding = 6f,
        arrow = 16f,
        arrowGap = 4f,
        cellGap = 4f,
        cellPadding = 4f,
        tempIcon = 13f,
        tempIconGap = 4f,
        gridGap = 7f,
        gridPill = 20f,
        gridColumnGap = 5f,
        fonts = DesignFonts(soc = 32f, underSoc = 11f, ringLabel = 9.5f, main = 22f, cell = 13.5f, small = 10.5f),
        empty = EMPTY_3_ROWS,
      )

    val L5x3 =
      LayoutSpec(
        name = "5x3",
        bucketWidth = 334f,
        bucketHeight = 199f,
        topRow = 48f,
        topGap = 10f,
        toButtons = 6f,
        buttonGap = 0f,
        hourglass = 13f,
        statusIcon = 17f,
        buttonTouch = 48f,
        buttonFace = 40f,
        buttonIcon = 20f,
        ring = 108f,
        ringStroke = 8f,
        ringTextGap = 2f,
        labelLineHeight = null,
        underLineHeight = null,
        packGap = 7f,
        packPadding = 4f,
        arrow = 17f,
        arrowGap = 4f,
        cellColumn = 74f,
        cellGap = 6f,
        cellPadding = 5f,
        tempIcon = 14f,
        tempIconGap = 4f,
        fonts = DesignFonts(soc = 42f, underSoc = 12.5f, ringLabel = 11f, main = 22f, cell = 15.5f, small = 11f),
        empty = EMPTY_3_ROWS,
      )

    val all = listOf(L4x2, L5x2, L4x3, L5x3)

    /**
     * The layout Android 12+ shows for a widget of [width] × [height] dp (`RemoteViews`'
     * best-fit rule): of the buckets that fit (`ceil(size) + 1 > bucket`), the closest; none fits →
     * the smallest. `LocalSize` is a bucket in `SizeMode.Responsive`, so this only matters in tests
     * and on Android 8–11, where Glance passes its own closest bucket.
     */
    fun forSize(width: Float, height: Float): LayoutSpec =
      all
        .filter { ceil(width) + 1 > it.bucketWidth && ceil(height) + 1 > it.bucketHeight }
        .minByOrNull {
          val dx = it.bucketWidth - width
          val dy = it.bucketHeight - height
          dx * dx + dy * dy
        } ?: L4x2
  }
}
