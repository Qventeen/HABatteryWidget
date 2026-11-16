package io.lumensync.habattery.ui.sections

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.FontGroup
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.ThemeChoice
import io.lumensync.habattery.format.Formatters
import io.lumensync.habattery.widget.WidgetPalette
import io.lumensync.habattery.widget.WidgetTexts
import kotlin.math.min

/**
 * The widget's layout drawn in Compose, from the same [WidgetModel] (spec 0003, 5.9). It scales by
 * its own width. ↻ runs [onRefresh]; the toggle runs [onToggleAuto]. Without data: the message only.
 */
/** Test tag of the overview card on the main screen. */
const val OVERVIEW_CARD_TAG = "overview-card"

@Composable
fun OverviewCard(
  model: WidgetModel,
  fontPercents: FontPercents,
  theme: ThemeChoice,
  syncing: Boolean,
  onRefresh: () -> Unit,
  onToggleAuto: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val texts = remember(context) { WidgetTexts(context, Formatters(context)) }
  val palette =
    if (theme.isDark(isSystemInDarkTheme())) WidgetPalette.Dark else WidgetPalette.Light
  val density = LocalDensity.current

  BoxWithConstraints(modifier.fillMaxWidth()) {
    val look = CardLook(CardScale.of(maxWidth.value), fontPercents, density.fontScale, palette, texts)
    val shape = RoundedCornerShape(look.dp(24f))
    when (model) {
      // A plain message: it follows the system text size like the rest of the screen.
      is WidgetModel.Empty ->
        EmptyCard(model, look, Modifier.fillMaxWidth().background(palette.card, shape))
      is WidgetModel.Data ->
        // The widget layout's sizes already include the system font scale (capped): turn it off.
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
          Column(
            Modifier.fillMaxWidth()
              .height(CardScale.heightDp(look.s).dp)
              .background(palette.card, shape)
              .padding(look.dp(12f))
          ) {
            TopRow(model, look, syncing, onRefresh, onToggleAuto)
            CardBody(model, look, Modifier.weight(1f).fillMaxWidth().padding(top = look.dp(4f)))
          }
        }
    }
  }
}

/**
 * Sizes for one drawing: [s] scales fixed sizes. The fonts are the v4 sizes × the system font
 * scale, capped at the v4 maximum, × percent / 50, without fitting (spec 0005, 5.7; spec 0006
 * replaces the card).
 */
internal class CardLook(
  val s: Float,
  private val fontPercents: FontPercents,
  private val fontScale: Float,
  val palette: WidgetPalette,
  val texts: WidgetTexts,
) {
  fun dp(value: Float): Dp = (value * s).dp

  /** The shown size in dp; the card runs with font scale 1, so sp equals it. */
  fun shownSize(group: FontGroup): Float {
    val capped = min(V4_SIZES.getValue(group) * fontScale, V4_MAX.getValue(group))
    return fontPercents.requestedDp(group, capped, 1f) * s
  }

  fun font(group: FontGroup): TextUnit = shownSize(group).sp
}

@Composable
private fun EmptyCard(model: WidgetModel.Empty, look: CardLook, modifier: Modifier) {
  val palette = look.palette
  Column(
    modifier.padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    val message =
      when (model.kind) {
        WidgetModel.EmptyKind.SETUP -> R.string.overview_setup
        WidgetModel.EmptyKind.NO_DATA -> R.string.widget_no_data
      }
    Text(
      stringResource(message),
      color = palette.text,
      fontSize = 15.sp,
      fontWeight = FontWeight.Medium,
      textAlign = TextAlign.Center,
    )
    if (model.kind == WidgetModel.EmptyKind.NO_DATA) {
      model.error?.let {
        Text(
          look.texts.formatters.error(it),
          color = palette.bad,
          fontSize = 13.sp,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@Composable
private fun TopRow(
  model: WidgetModel.Data,
  look: CardLook,
  syncing: Boolean,
  onRefresh: () -> Unit,
  onToggleAuto: () -> Unit,
) {
  val palette = look.palette
  val texts = look.texts
  val small = look.font(FontGroup.SMALL)
  Row(Modifier.fillMaxWidth().height(TOUCH), verticalAlignment = Alignment.CenterVertically) {
    CardIcon(R.drawable.ic_hourglass, look.dp(12f), palette.sub, null)
    Spacer(Modifier.width(look.dp(4f)))
    Text(texts.timeLeft(model), color = palette.sub, fontSize = small, maxLines = 1)
    Spacer(Modifier.width(look.dp(10f)))
    CardIcon(
      R.drawable.ic_balance,
      look.dp(16f),
      if (model.balancing == true) palette.accent else palette.dim,
      stringResource(WidgetTexts.balancingDescription(model.balancing)),
    )
    Box(
      Modifier.weight(1f).padding(start = look.dp(10f), end = look.dp(6f)),
      contentAlignment = Alignment.CenterEnd,
    ) {
      val error = model.error
      if (error == null) {
        Text(texts.updatedAt(model), color = palette.sub, fontSize = small, maxLines = 1)
      } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
          CardIcon(
            R.drawable.ic_alert,
            look.dp(12f),
            palette.bad,
            texts.syncErrorDescription(error),
          )
          Spacer(Modifier.width(look.dp(3f)))
          Text(
            texts.formatters.error(error),
            color = palette.bad,
            fontSize = small,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
    CircleButton(
      icon = if (model.autoRefresh) R.drawable.ic_auto_refresh else R.drawable.ic_auto_refresh_off,
      iconColor = if (model.autoRefresh) palette.accent else palette.dim,
      face = if (model.autoRefresh) palette.okBg else palette.tile,
      description = stringResource(WidgetTexts.autoDescription(model.autoRefresh)),
      onClick = onToggleAuto,
    )
    Spacer(Modifier.width(look.dp(10f)))
    val busy = syncing || model.refreshing
    CircleButton(
      icon = R.drawable.ic_refresh,
      iconColor = if (busy) palette.dim else palette.sub,
      face = palette.tile,
      description = stringResource(WidgetTexts.refreshDescription(busy)),
      onClick = onRefresh,
    )
  }
}

/** The v4 design sizes (sp) of the card's five font groups. */
private val V4_SIZES =
  mapOf(
    FontGroup.SOC to 40f,
    FontGroup.UNDER_SOC to 12f,
    FontGroup.MAIN to 22f,
    FontGroup.CELL to 14f,
    FontGroup.SMALL to 10.5f,
  )

/** The v4 maximum sizes (sp): the system font scale grows the card's text up to these. */
private val V4_MAX =
  mapOf(
    FontGroup.SOC to 42f,
    FontGroup.UNDER_SOC to 12f,
    FontGroup.MAIN to 24f,
    FontGroup.CELL to 15f,
    FontGroup.SMALL to 12f,
  )

private val TOUCH = 44.dp
private val FACE = 36.dp
private val BUTTON_ICON = 18.dp

@Composable
private fun CircleButton(
  @DrawableRes icon: Int,
  iconColor: Color,
  face: Color,
  description: String,
  onClick: () -> Unit,
) {
  Box(
    Modifier.size(TOUCH)
      .clip(CircleShape)
      .clickable(role = Role.Button, onClick = onClick)
      .semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
  ) {
    Box(Modifier.size(FACE).background(face, CircleShape), contentAlignment = Alignment.Center) {
      CardIcon(icon, BUTTON_ICON, iconColor, null)
    }
  }
}

@Composable
internal fun CardIcon(@DrawableRes id: Int, size: Dp, tint: Color, description: String?) {
  Icon(painterResource(id), contentDescription = description, modifier = Modifier.size(size), tint = tint)
}
