package io.lumensync.habattery.widget

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.WidgetModel.Flow
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelIcon
import kotlin.math.roundToInt

/** Cell pills keep fully round ends; Android clamps the radius to half the pill's height. */
private val CELL_RADIUS = 999.dp

/** The ring bitmaps are drawn this much larger than the design ring, so they stay sharp when the ring grows. */
private const val RING_RENDER_SCALE = 1.25f

/**
 * Time left, balancing, the update time (or the red sync error icon in its place) and ↻ with the
 * freshness dot (specs 0005, 0016). The error takes the update time's place, so nothing else moves.
 */
@Composable
internal fun TopRow(d: DataLook, actions: WidgetActions?) {
  val context = LocalContext.current
  val s = d.spec
  val c = d.colors
  val model = d.model
  val small = d.look.sp(d.fonts.small)

  // Glance keeps at most 10 children in a Row: gaps are padding, not spacers.
  Row(GlanceModifier.fillMaxWidth().height(s.topRow.dp), verticalAlignment = Alignment.CenterVertically) {
    Row(GlanceModifier.padding(start = s.timeStart.dp), verticalAlignment = Alignment.CenterVertically) {
      WidgetIcon(R.drawable.ic_hourglass, s.hourglass.dp, c.sub, null)
      Spacer(GlanceModifier.width(s.hourglassGap.dp))
      Text(d.texts.timeLeft, style = textStyle(small, c.sub), maxLines = 1)
      Spacer(GlanceModifier.width(s.topGap.dp))
      WidgetIcon(
        R.drawable.ic_balance,
        s.statusIcon.dp,
        if (model.balancing == true) c.accent else c.dim,
        context.getString(WidgetTexts.balancingDescription(model.balancing)),
      )
    }
    Box(
      GlanceModifier.defaultWeight().padding(start = s.topGap.dp, end = s.toButtons.dp),
      contentAlignment = Alignment.CenterEnd,
    ) {
      if (d.texts.showError) {
        WidgetIcon(R.drawable.ic_alert, s.statusIcon.dp, c.bad, d.texts.errorDescription)
      } else {
        Text(d.texts.updatedAt, style = textStyle(small, c.sub), maxLines = 1)
      }
    }
    // While a sync runs, ↻ stays in place and only dims; the dot still shows the data's age.
    RefreshButton(
      d,
      iconColor = if (model.refreshing) c.dim else c.sub,
      description = WidgetTexts.refreshDescription(context, model.refreshing, model.dataAgeMinutes),
      action = actions?.refresh,
    )
  }
}

/**
 * ↻ in its touch area. Without [action] (the app's preview) it does nothing, so TalkBack does not
 * announce it.
 */
@Composable
private fun RefreshButton(d: DataLook, iconColor: ColorProvider, description: String, action: Action?) {
  val s = d.spec
  val described =
    if (action == null) GlanceModifier else GlanceModifier.semantics { contentDescription = description }
  Box(
    GlanceModifier.width(s.buttonTouch.dp)
      .height(s.buttonTouch.dp)
      .tapAction(action)
      .then(described),
    contentAlignment = Alignment.Center,
  ) {
    RefreshFace(
      faceSize = s.buttonFace,
      iconSize = s.buttonIcon,
      overhang = s.dotOverhang,
      dot = s.dot,
      dotRing = s.dotRing,
      iconColor = iconColor,
      faceColor = d.colors.tile,
      late = d.model.dataLate,
      colors = d.colors,
    )
  }
}

/**
 * The ↻ circle with the freshness dot on its top-right edge, for both widgets; [late] colours the
 * dot. No touch area: each widget sizes its own and describes it.
 */
@Composable
internal fun RefreshFace(
  faceSize: Float,
  iconSize: Float,
  overhang: Float,
  dot: Float,
  dotRing: Float,
  iconColor: ColorProvider,
  faceColor: ColorProvider,
  late: Boolean,
  colors: WidgetColors,
) {
  // Glance aligns all children of a Box alike, so the face and the dot each get a full-size box
  // with its own alignment. The overhang puts the dot on the circle's edge.
  Box(GlanceModifier.size((faceSize + overhang).dp)) {
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
      Box(
        GlanceModifier.size(faceSize.dp).background(faceColor).cornerRadius((faceSize / 2).dp),
        contentAlignment = Alignment.Center,
      ) {
        WidgetIcon(R.drawable.ic_refresh, iconSize.dp, iconColor, null)
      }
    }
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
      FreshnessBadge(late, dot, dotRing, colors)
    }
  }
}

/**
 * The freshness dot: green, or amber when [late], in a [ring] of the card colour that sets it off
 * from the circle under it. It has no description: ↻ says the data's age.
 */
@Composable
internal fun FreshnessBadge(late: Boolean, dot: Float, ring: Float, colors: WidgetColors) {
  val outer = dot + 2 * ring
  Box(
    GlanceModifier.size(outer.dp).semantics { testTag = if (late) "dot_late" else "dot_fresh" },
    contentAlignment = Alignment.Center,
  ) {
    WidgetIcon(R.drawable.widget_dot, outer.dp, colors.card, null)
    WidgetIcon(R.drawable.widget_dot, dot.dp, if (late) colors.late else colors.fresh, null)
  }
}

/**
 * The SOC ring: track and arc bitmaps at the layout's size and stroke, in a box the caller sizes
 * (5×2: the ring's width; the other layouts: a share of the row). The ring fits the box's shorter
 * side (`ContentScale.Fit`), so it grows or shrinks with it; its text keeps the design ring's
 * sizes. The three lines are placed by [RingLayout].
 */
@Composable
internal fun Ring(d: DataLook, modifier: GlanceModifier) {
  val context = LocalContext.current
  val s = d.spec
  val c = d.colors
  val density = context.resources.displayMetrics.density
  val sizePx = (s.ring * density * RING_RENDER_SCALE).roundToInt().coerceAtLeast(1)
  val strokePx = s.ringStroke * density * RING_RENDER_SCALE
  val description = d.texts.ringDescription

  Box(
    modifier.semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
  ) {
    Image(
      ImageProvider(RingBitmap.track(sizePx, strokePx)),
      contentDescription = null,
      modifier = GlanceModifier.fillMaxSize(),
      contentScale = ContentScale.Fit,
      colorFilter = ColorFilter.tint(c.track),
    )
    Image(
      ImageProvider(RingBitmap.arc(d.model.ringFraction, sizePx, strokePx)),
      contentDescription = null,
      modifier = GlanceModifier.fillMaxSize(),
      contentScale = ContentScale.Fit,
      colorFilter = ColorFilter.tint(c.ring(d.model.ringLook.color)),
    )
    val f = d.fonts
    RingText(d.texts.socLabel, d.look.sp(f.ringLabel), c.sub, FontWeight.Normal, f.ringCenters[0])
    RingText(d.texts.soc, d.look.sp(f.soc), c.text, FontWeight.Bold, f.ringCenters[1])
    RingText(d.texts.underSoc, d.look.sp(f.underSoc), c.text, FontWeight.Medium, f.ringCenters[2])
  }
}

/** A centred line moved by [center] dp: a centred view grows by its padding, so pad twice. */
@Composable
private fun RingText(
  text: String,
  size: TextUnit,
  color: ColorProvider,
  weight: FontWeight,
  center: Float,
) {
  val shift = 2 * center
  Text(
    text,
    if (shift >= 0) GlanceModifier.padding(top = shift.dp) else GlanceModifier.padding(bottom = (-shift).dp),
    style = textStyle(size, color, weight),
    maxLines = 1,
  )
}

/** Voltage over current or power; the pills share the height (spec 0005, B1). */
@Composable
internal fun PackColumn(d: DataLook, modifier: GlanceModifier) {
  Column(modifier) {
    val fill = GlanceModifier.defaultWeight().fillMaxWidth()
    val flow = d.model.flow
    // Both pills show the current's arrow; only the main pill's arrow is described, so TalkBack
    // says the direction once.
    PackPill(d, d.texts.voltage, d.fonts.voltage, d.model.voltageLook.color, flow, describeArrow = false, fill)
    Spacer(GlanceModifier.height(d.spec.packGap.dp))
    PackPill(d, d.texts.main, d.fonts.main, d.model.mainLook.color, flow, describeArrow = true, fill)
  }
}

@Composable
private fun PackPill(
  d: DataLook,
  text: String,
  sizeDp: Float,
  levelColor: LevelColor,
  flow: Flow,
  describeArrow: Boolean,
  modifier: GlanceModifier,
) {
  val context = LocalContext.current
  val s = d.spec
  val color = d.colors.pillText(levelColor)
  Box(
    modifier
      .background(d.colors.pillBackground(levelColor))
      .cornerRadius(s.packRadius.dp)
      .padding(horizontal = s.packPadding.dp),
    contentAlignment = Alignment.Center,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      WidgetTexts.flowDescription(flow)?.let { description ->
        WidgetIcon(
          if (flow == Flow.UP) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down,
          s.arrow.dp,
          color,
          if (describeArrow) context.getString(description) else null,
        )
        Spacer(GlanceModifier.width(s.arrowGap.dp))
      }
      Text(text, style = textStyle(d.look.sp(sizeDp), color, FontWeight.Medium), maxLines = 1)
    }
  }
}

/** 3 or 4 cell pills, a small fixed gap, the pills grow to fill (spec 0005, decision 5). */
@Composable
internal fun CellColumn(d: DataLook, modifier: GlanceModifier) {
  Column(modifier.width(d.spec.cellColumn.dp)) {
    d.texts.cells.forEachIndexed { i, pill ->
      if (i > 0) Spacer(GlanceModifier.height(d.spec.cellGap.dp))
      CellPillView(d, i, pill, GlanceModifier.defaultWeight().fillMaxWidth())
    }
  }
}

/**
 * 4×3: a 2 × 2 grid; row 1 average cell and delta, row 2 battery and MOSFET. With no battery
 * sensor MOSFET takes row 2 alone.
 */
@Composable
internal fun CellGrid(d: DataLook) {
  val s = d.spec
  val cells = d.texts.cells
  Column(GlanceModifier.fillMaxWidth()) {
    cells.indices.chunked(2).forEachIndexed { row, indices ->
      if (row > 0) Spacer(GlanceModifier.height(s.cellGap.dp))
      Row(GlanceModifier.fillMaxWidth().height(s.gridPill.dp)) {
        indices.forEachIndexed { k, i ->
          if (k > 0) Spacer(GlanceModifier.width(s.gridColumnGap.dp))
          CellPillView(d, i, cells[i], GlanceModifier.defaultWeight().fillMaxHeight())
        }
      }
    }
  }
}

@Composable
private fun CellPillView(d: DataLook, index: Int, pill: CellPill, modifier: GlanceModifier) {
  val context = LocalContext.current
  val s = d.spec
  val color = d.colors.pillText(pill.color)
  Box(
    modifier
      .background(d.colors.pillBackground(pill.color))
      .cornerRadius(CELL_RADIUS)
      .padding(horizontal = s.cellPadding.dp),
    contentAlignment = Alignment.Center,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (pill.icon != null) {
        WidgetIcon(pill.icon, s.tempIcon.dp, color, pill.iconDescription?.let(context::getString))
        Spacer(GlanceModifier.width(s.tempIconGap.dp))
      }
      // Not described: TalkBack reads the temperature, which already tells the level.
      pill.levelIcon?.let { level ->
        WidgetIcon(levelIconRes(level), s.tempIcon.dp, color, null)
        Spacer(GlanceModifier.width(s.tempIconGap.dp))
      }
      Text(
        pill.text,
        style = textStyle(d.look.sp(d.fonts.cells[index]), color, FontWeight.Medium),
        maxLines = 1,
      )
    }
  }
}

@DrawableRes
internal fun levelIconRes(icon: LevelIcon): Int =
  when (icon) {
    LevelIcon.SNOWFLAKE -> R.drawable.ic_level_snowflake
    LevelIcon.LEAF -> R.drawable.ic_level_leaf
    LevelIcon.FLAME -> R.drawable.ic_level_flame
    LevelIcon.FLAME_ALERT -> R.drawable.ic_level_flame_alert
  }
