package io.lumensync.habattery.widget

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
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
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.FontGroup

/** Touch area and visible circle of the two buttons; not scaled. */
private val TOUCH = 44.dp
private val FACE = 36.dp
private val BUTTON_ICON = 18.dp

/**
 * Time left, balancing, the update time (or the sync error), the auto-refresh toggle and ↻. The
 * error takes the update time's place, so nothing else moves.
 */
@Composable
internal fun WidgetTopRow(model: WidgetModel.Data, look: WidgetLook, actions: WidgetActions) {
  val context = LocalContext.current
  val texts = look.texts
  val small = look.font(FontGroup.SMALL)

  Row(GlanceModifier.fillMaxWidth().height(TOUCH), verticalAlignment = Alignment.CenterVertically) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      WidgetIcon(R.drawable.ic_hourglass, look.dp(12f), look.colors.sub, null)
      Text(
        texts.timeLeft(model),
        GlanceModifier.padding(start = look.dp(4f)),
        style = textStyle(small, look.colors.sub),
        maxLines = 1,
      )
    }
    Spacer(GlanceModifier.width(look.dp(10f)))
    WidgetIcon(
      R.drawable.ic_balance,
      look.dp(16f),
      if (model.balancing == true) look.colors.accent else look.colors.dim,
      context.getString(WidgetTexts.balancingDescription(model.balancing)),
    )
    Box(
      GlanceModifier.defaultWeight().padding(start = look.dp(10f), end = look.dp(6f)),
      contentAlignment = Alignment.CenterEnd,
    ) {
      val error = model.error
      if (error == null) {
        Text(
          texts.updatedAt(model),
          style = textStyle(small, look.colors.sub),
          maxLines = 1,
        )
      } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
          WidgetIcon(
            R.drawable.ic_alert,
            look.dp(12f),
            look.colors.bad,
            context.getString(R.string.widget_sync_error),
          )
          Text(
            texts.formatters.error(error),
            GlanceModifier.padding(start = look.dp(3f)),
            style = textStyle(small, look.colors.bad, FontWeight.Medium),
            maxLines = 1,
          )
        }
      }
    }
    CircleButton(
      icon = if (model.autoRefresh) R.drawable.ic_auto_refresh else R.drawable.ic_auto_refresh_off,
      iconColor = if (model.autoRefresh) look.colors.accent else look.colors.dim,
      face = if (model.autoRefresh) look.colors.okBg else look.colors.tile,
      description = context.getString(WidgetTexts.autoDescription(model.autoRefresh)),
      action = actions.toggleAuto,
    )
    Spacer(GlanceModifier.width(look.dp(10f)))
    // While a sync runs, ↻ stays in place and only dims.
    CircleButton(
      icon = R.drawable.ic_refresh,
      iconColor = if (model.refreshing) look.colors.dim else look.colors.sub,
      face = look.colors.tile,
      description = context.getString(WidgetTexts.refreshDescription(model.refreshing)),
      action = actions.refresh,
    )
  }
}

@Composable
private fun CircleButton(
  @DrawableRes icon: Int,
  iconColor: ColorProvider,
  face: ColorProvider,
  description: String,
  action: Action,
) {
  Box(
    GlanceModifier.size(TOUCH).clickable(action).semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      GlanceModifier.size(FACE).background(face).cornerRadius(FACE / 2),
      contentAlignment = Alignment.Center,
    ) {
      WidgetIcon(icon, BUTTON_ICON, iconColor, null)
    }
  }
}
