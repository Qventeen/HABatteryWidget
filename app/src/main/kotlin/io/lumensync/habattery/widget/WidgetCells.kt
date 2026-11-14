package io.lumensync.habattery.widget

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.model.CellStats
import io.lumensync.habattery.format.Formatters

/** A Glance container shows at most 10 children; longer cell columns are split into chunks. */
private const val MAX_CHILDREN = 10

/** Cell voltages in two columns: cells 1..half on the left, the rest on the right. */
@Composable
internal fun CellGrid(
  cells: List<Double?>,
  stats: CellStats?,
  spec: WidgetLayoutSpec,
  dimens: WidgetDimens,
  formatters: Formatters,
  modifier: GlanceModifier,
) {
  val split = (cells.size + 1) / 2
  // Highlight min and max only when they are different cells.
  val highlight = stats?.takeIf { it.minIndex != it.maxIndex }
  Row(modifier = modifier.fillMaxWidth()) {
    val spacers = spec.useRowSpacers
    val column = GlanceModifier.defaultWeight()
    CellColumn(cells, 0 until split, highlight, spacers, dimens, formatters, column)
    Spacer(GlanceModifier.width(dimens.gap))
    CellColumn(cells, split until cells.size, highlight, spacers, dimens, formatters, column)
  }
}

@Composable
private fun CellColumn(
  cells: List<Double?>,
  indexes: IntRange,
  highlight: CellStats?,
  useRowSpacers: Boolean,
  dimens: WidgetDimens,
  formatters: Formatters,
  modifier: GlanceModifier,
) {
  Column(modifier = modifier.fillMaxHeight()) {
    if (useRowSpacers) {
      // Few rows: spread them over the height.
      indexes.forEach { index ->
        val tile = GlanceModifier.fillMaxWidth()
        CellTile(index + 1, cells[index], highlight, dimens, formatters, tile)
        if (index < indexes.last) Spacer(GlanceModifier.defaultWeight())
      }
    } else {
      indexes.chunked(MAX_CHILDREN).forEach { chunk ->
        Column(modifier = GlanceModifier.fillMaxWidth()) {
          chunk.forEach { index ->
            CellTile(
              index + 1,
              cells[index],
              highlight,
              dimens,
              formatters,
              GlanceModifier.fillMaxWidth().padding(bottom = dimens.tileBottom),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CellTile(
  number: Int,
  volts: Double?,
  highlight: CellStats?,
  dimens: WidgetDimens,
  formatters: Formatters,
  modifier: GlanceModifier,
) {
  val highlightColor =
    when (number) {
      highlight?.minIndex -> WidgetColors.minCell
      highlight?.maxIndex -> WidgetColors.maxCell
      else -> null
    }
  Row(
    modifier =
      modifier
        .background(WidgetColors.cellTileBackground)
        .cornerRadius(dimens.tileCorner)
        .padding(horizontal = dimens.tilePadH, vertical = dimens.tilePadV),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        GlanceModifier.width(dimens.badgeWidth)
          .background(WidgetColors.cellBadgeBackground)
          .cornerRadius(dimens.badgeCorner),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = LocalContext.current.getString(R.string.cell_label, number),
        style =
          TextStyle(
            color = WidgetColors.cellBadgeText,
            fontSize = dimens.badgeText,
            fontWeight = FontWeight.Bold,
          ),
        maxLines = 1,
      )
    }
    Text(
      text = formatters.cellVoltage(volts),
      modifier = GlanceModifier.defaultWeight(),
      style =
        TextStyle(
          color = highlightColor ?: WidgetColors.primaryText,
          fontSize = dimens.cellText,
          fontWeight = if (highlightColor != null) FontWeight.Bold else FontWeight.Medium,
          textAlign = TextAlign.End,
        ),
      maxLines = 1,
    )
  }
}
