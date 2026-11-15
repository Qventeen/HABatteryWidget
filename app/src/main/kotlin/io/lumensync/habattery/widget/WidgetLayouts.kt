package io.lumensync.habattery.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width

/*
 * The four v5 layouts (docs/design/README.md). Fixed parts use the design sizes; the pack column
 * and the pills fill by weight, so a bigger widget stretches the layout (spec 0005, B1).
 */

/** 4×2: the top row over the full width; ring, pack and cell column below it. */
@Composable
internal fun Layout4x2(d: DataLook, actions: WidgetActions) = TopRowOverColumns(d, actions)

/** 5×3: as 4×2 with the 5×3 sizes; the ring sits in the middle of its row. */
@Composable
internal fun Layout5x3(d: DataLook, actions: WidgetActions) = TopRowOverColumns(d, actions)

/** 5×2 (layout B): the ring over the full card height, the top row beside it. */
@Composable
internal fun Layout5x2(d: DataLook, actions: WidgetActions) {
  val s = d.spec
  Row(GlanceModifier.fillMaxSize()) {
    Ring(d, GlanceModifier.fillMaxHeight())
    Spacer(GlanceModifier.width(s.columnGap.dp))
    Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
      TopRow(d, actions)
      Spacer(GlanceModifier.height(s.bodyTop.dp))
      Row(GlanceModifier.defaultWeight().fillMaxWidth()) {
        PackColumn(d, GlanceModifier.defaultWeight().fillMaxHeight())
        Spacer(GlanceModifier.width(s.columnGap.dp))
        CellColumn(d, GlanceModifier.fillMaxHeight())
      }
    }
  }
}

/** 4×3: the top row, ring and pack below it, then the 2 × 2 cell grid. */
@Composable
internal fun Layout4x3(d: DataLook, actions: WidgetActions) {
  val s = d.spec
  Column(GlanceModifier.fillMaxSize()) {
    TopRow(d, actions)
    Spacer(GlanceModifier.height(s.bodyTop.dp))
    Row(GlanceModifier.defaultWeight().fillMaxWidth()) {
      Ring(d, GlanceModifier.fillMaxHeight())
      Spacer(GlanceModifier.width(s.columnGap.dp))
      PackColumn(d, GlanceModifier.defaultWeight().fillMaxHeight())
    }
    Spacer(GlanceModifier.height(s.gridGap.dp))
    CellGrid(d)
  }
}

@Composable
private fun TopRowOverColumns(d: DataLook, actions: WidgetActions) {
  val s = d.spec
  Column(GlanceModifier.fillMaxSize()) {
    TopRow(d, actions)
    Spacer(GlanceModifier.height(s.bodyTop.dp))
    Row(GlanceModifier.defaultWeight().fillMaxWidth()) {
      Ring(d, GlanceModifier.fillMaxHeight())
      Spacer(GlanceModifier.width(s.columnGap.dp))
      PackColumn(d, GlanceModifier.defaultWeight().fillMaxHeight())
      Spacer(GlanceModifier.width(s.columnGap.dp))
      CellColumn(d, GlanceModifier.fillMaxHeight())
    }
  }
}
