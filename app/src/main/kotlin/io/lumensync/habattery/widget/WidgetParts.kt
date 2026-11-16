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
import androidx.glance.action.clickable
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.Level
import io.lumensync.habattery.domain.WidgetModel.Flow
import kotlin.math.roundToInt

/** Cell pills keep fully round ends; Android clamps the radius to half the pill's height. */
private val CELL_RADIUS = 999.dp

/**
 * Time left, balancing, the update time (or the red sync error icon in its place), the auto-refresh
 * toggle and ↻ (spec 0005, 5.3). The error takes the update time's place, so nothing else moves.
 */
@Composable
internal fun TopRow(d: DataLook, actions: WidgetActions) {
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
    CircleButton(
      d,
      icon = if (model.autoRefresh) R.drawable.ic_auto_refresh else R.drawable.ic_auto_refresh_off,
      iconColor = if (model.autoRefresh) c.accent else c.dim,
      face = if (model.autoRefresh) c.okBg else c.tile,
      description = context.getString(WidgetTexts.autoDescription(model.autoRefresh)),
      action = actions.toggleAuto,
      growEnd = s.buttonGap / 2,
    )
    // While a sync runs, ↻ stays in place and only dims.
    CircleButton(
      d,
      icon = R.drawable.ic_refresh,
      iconColor = if (model.refreshing) c.dim else c.sub,
      face = c.tile,
      description = context.getString(WidgetTexts.refreshDescription(model.refreshing)),
      action = actions.refresh,
      growStart = s.buttonGap / 2,
    )
  }
}

/**
 * A circle in its touch area. The touch area grows by [growStart] / [growEnd] toward the other
 * button, so the two touch areas touch while the circles stay where the design has them.
 */
@Composable
private fun CircleButton(
  d: DataLook,
  @DrawableRes icon: Int,
  iconColor: ColorProvider,
  face: ColorProvider,
  description: String,
  action: Action,
  growStart: Float = 0f,
  growEnd: Float = 0f,
) {
  val s = d.spec
  Box(
    GlanceModifier.width((s.buttonTouch + growStart + growEnd).dp)
      .height(s.buttonTouch.dp)
      .clickable(action)
      .semantics { contentDescription = description }
      .padding(start = growStart.dp, end = growEnd.dp),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      GlanceModifier.size(s.buttonFace.dp).background(face).cornerRadius((s.buttonFace / 2).dp),
      contentAlignment = Alignment.Center,
    ) {
      WidgetIcon(icon, s.buttonIcon.dp, iconColor, null)
    }
  }
}

/**
 * The SOC ring: track and arc bitmaps at the layout's size and stroke, in a box as wide as the ring
 * and as high as its row. Below the design size the ring shrinks to the row (`ContentScale.Fit`);
 * above it, it stays at the design size, centred. The three lines are placed by [RingLayout].
 */
@Composable
internal fun Ring(d: DataLook, modifier: GlanceModifier) {
  val context = LocalContext.current
  val s = d.spec
  val c = d.colors
  val density = context.resources.displayMetrics.density
  val sizePx = (s.ring * density).roundToInt().coerceAtLeast(1)
  val strokePx = s.ringStroke * density
  val description = context.resources.getQuantityString(R.plurals.widget_soc_description, d.model.socPercent, d.model.socPercent)

  Box(
    modifier.width(s.ring.dp).semantics { contentDescription = description },
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
      colorFilter = ColorFilter.tint(c.ring(d.model.ringLevel)),
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
    PackPill(d, d.texts.voltage, d.fonts.voltage, Level.PLAIN, Flow.NONE, fill)
    Spacer(GlanceModifier.height(d.spec.packGap.dp))
    PackPill(d, d.texts.main, d.fonts.main, d.model.mainLevel, d.model.flow, fill)
  }
}

@Composable
private fun PackPill(
  d: DataLook,
  text: String,
  sizeDp: Float,
  level: Level,
  flow: Flow,
  modifier: GlanceModifier,
) {
  val context = LocalContext.current
  val s = d.spec
  val color = d.colors.pillText(level)
  Box(
    modifier
      .background(d.colors.pillBackground(level))
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
          context.getString(description),
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
  val color = d.colors.pillText(pill.level)
  Box(
    modifier
      .background(d.colors.pillBackground(pill.level))
      .cornerRadius(CELL_RADIUS)
      .padding(horizontal = s.cellPadding.dp),
    contentAlignment = Alignment.Center,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (pill.icon != null) {
        WidgetIcon(pill.icon, s.tempIcon.dp, color, pill.iconDescription?.let(context::getString))
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
