package com.homeassistant.batterywidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.view.WindowManager
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import androidx.glance.currentState
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
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.homeassistant.batterywidget.MainActivity
import com.homeassistant.batterywidget.data.BatteryChargeMode
import com.homeassistant.batterywidget.data.BatteryFormatters
import com.homeassistant.batterywidget.data.BatterySnapshot
import com.homeassistant.batterywidget.data.HomeAssistantClient
import com.homeassistant.batterywidget.data.SettingsStore
import com.homeassistant.batterywidget.notifications.BatteryFullNotificationManager
import kotlin.math.max

internal enum class WidgetDensityTier {
    SUMMARY_ONLY,
    COMPACT,
    REGULAR,
    LARGE,
}

internal data class WidgetLayoutSpec(
    val tier: WidgetDensityTier,
    val showCells: Boolean,
    val cellRows: Int,
    val useRowSpacers: Boolean,
) {
    companion object {
        fun fromSize(heightDp: Float, cellCount: Int): WidgetLayoutSpec {
            val cellRows = (cellCount + 1) / 2
            if (cellCount <= 0) {
                val tier = when {
                    heightDp < 135f -> WidgetDensityTier.SUMMARY_ONLY
                    heightDp < 215f -> WidgetDensityTier.COMPACT
                    else -> WidgetDensityTier.REGULAR
                }
                return WidgetLayoutSpec(
                    tier = tier,
                    showCells = false,
                    cellRows = 0,
                    useRowSpacers = false,
                )
            }

            val minCompactHeight = max(125f, 84f + cellRows * 15f)
            if (heightDp < minCompactHeight) {
                return WidgetLayoutSpec(
                    tier = WidgetDensityTier.SUMMARY_ONLY,
                    showCells = false,
                    cellRows = cellRows,
                    useRowSpacers = false,
                )
            }

            val minRegularHeight = max(215f, 134f + cellRows * 23f)
            val minLargeHeight = max(275f, 152f + cellRows * 26f)
            val tier = when {
                heightDp >= minLargeHeight -> WidgetDensityTier.LARGE
                heightDp >= minRegularHeight -> WidgetDensityTier.REGULAR
                else -> WidgetDensityTier.COMPACT
            }
            return WidgetLayoutSpec(
                tier = tier,
                showCells = true,
                cellRows = cellRows,
                useRowSpacers = cellRows in 2..5,
            )
        }
    }
}

class BatteryGlanceWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = SettingsStore(context)
        val refreshConfig = store.loadRefreshConfig()
        var initialSnapshot = store.loadCachedSnapshot()
        val now = System.currentTimeMillis()
        val staleWindowMs = max(60_000L, refreshConfig.sanitizedIntervalMinutes * 60_000L)
        val shouldFetchOnWake = initialSnapshot == null ||
            !initialSnapshot.hasValidData ||
            (refreshConfig.autoRefreshEnabled && (now - initialSnapshot.updatedAtMillis) > staleWindowMs)
        if (shouldFetchOnWake) {
            val fresh = HomeAssistantClient.fetchBatterySnapshot(
                store.loadConfig(),
                store.loadEntitiesConfig(),
            )
            fresh.onSuccess { snap ->
                store.saveCachedSnapshot(snap)
                BatteryFullNotificationManager.handleFreshSnapshot(context, snap)
                initialSnapshot = snap
                updateAppWidgetState(context, id) { prefs ->
                    prefs[KEY_SNAPSHOT_JSON] = snap.toJsonString()
                    prefs[KEY_REFRESHING] = false
                    prefs[KEY_AUTO_REFRESH] = refreshConfig.autoRefreshEnabled
                    prefs[KEY_INTERVAL_MINUTES] = refreshConfig.sanitizedIntervalMinutes
                }
            }.onFailure { err ->
                val msg = err.message ?: "Немає зв'язку"
                val fallback = (initialSnapshot ?: BatterySnapshot(
                    socPercent = -1.0,
                    powerWatts = 0.0,
                    currentAmps = 0.0,
                    totalVoltage = 0.0,
                    deltaVolts = 0.0,
                    minCellIndex = 0,
                    maxCellIndex = 0,
                    timeStatus = "",
                    cellVoltages = emptyList(),
                    updatedAtMillis = 0L,
                )).copy(errorMessage = msg)
                initialSnapshot = fallback
            }
        }

        // Pre-render the shared progress bar bitmap once outside provideContent so
        // SizeMode.Responsive buckets share a single compact Bitmap instance.
        var cachedKey: Pair<Int, Boolean>? = null
        var cachedBarBitmap: Bitmap? = null

        val preIsDark = isNightMode(context)
        val preSoc = initialSnapshot?.socInt ?: 0
        if (initialSnapshot?.hasValidData == true) {
            cachedBarBitmap = createProgressBarBitmap(context, preSoc, preIsDark)
            cachedKey = preSoc to preIsDark
        }

        provideContent {
            val prefs = currentState<Preferences>()
            val jsonRaw = prefs[KEY_SNAPSHOT_JSON]
            val isRefreshing = prefs[KEY_REFRESHING] ?: false
            val autoRefresh = prefs[KEY_AUTO_REFRESH] ?: refreshConfig.autoRefreshEnabled
            val intervalMinutes = prefs[KEY_INTERVAL_MINUTES] ?: refreshConfig.sanitizedIntervalMinutes
            val snapshot = jsonRaw?.let(BatterySnapshot::fromJsonString) ?: initialSnapshot
            val currentContext = LocalContext.current
            val isDark = isNightMode(currentContext)

            val barBitmap = if (snapshot?.hasValidData == true) {
                val key = snapshot.socInt to isDark
                if (cachedKey != key || cachedBarBitmap == null) {
                    cachedBarBitmap = createProgressBarBitmap(currentContext, snapshot.socInt, isDark)
                    cachedKey = key
                }
                cachedBarBitmap
            } else {
                null
            }

            BatteryWidgetContent(
                snapshot = snapshot,
                isRefreshing = isRefreshing,
                autoRefreshEnabled = autoRefresh,
                intervalMinutes = intervalMinutes,
                barBitmap = barBitmap,
            )
        }
    }

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable,
    ) {
        val fallback = RemoteViews(context.packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, "Помилка віджета батареї")
        }
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, fallback)
    }

    @Composable
    private fun BatteryWidgetContent(
        snapshot: BatterySnapshot?,
        isRefreshing: Boolean,
        autoRefreshEnabled: Boolean,
        intervalMinutes: Int,
        barBitmap: Bitmap?,
    ) {
        val size = LocalSize.current
        val isNarrow = size.width < 235.dp
        val cellCount = snapshot?.cellVoltages?.size ?: 0
        val spec = WidgetLayoutSpec.fromSize(size.height.value, cellCount)
        val isCompact = spec.tier == WidgetDensityTier.COMPACT
        val isLarge = spec.tier == WidgetDensityTier.LARGE

        val horizontalPad = when {
            isNarrow -> 8.dp
            isCompact -> 10.dp
            else -> 12.dp
        }
        val verticalPad = when (spec.tier) {
            WidgetDensityTier.SUMMARY_ONLY -> 6.dp
            WidgetDensityTier.COMPACT -> 5.dp
            WidgetDensityTier.REGULAR -> 9.dp
            WidgetDensityTier.LARGE -> 11.dp
        }

        val cardModifier = GlanceModifier
            .fillMaxSize()
            .background(borderColor)
            .applyWidgetCornerRadius()
            .padding(1.dp)

        val innerModifier = GlanceModifier
            .fillMaxSize()
            .background(cardBgColor)
            .applyInnerCornerRadius()
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = horizontalPad, vertical = verticalPad)

        Box(modifier = cardModifier) {
            Column(
                modifier = innerModifier,
                verticalAlignment = if (!spec.showCells) Alignment.CenterVertically else Alignment.Top,
            ) {
                if (snapshot == null || !snapshot.hasValidData) {
                    EmptyOrErrorContent(
                        errorMessage = snapshot?.errorMessage,
                        isRefreshing = isRefreshing,
                    )
                } else {
                    val status = BatteryFormatters.formatStatusLine(snapshot)
                    val deltaMv = BatteryFormatters.computeDeltaMv(snapshot)
                    val (minCell, maxCell) = BatteryFormatters.resolveMinMaxCells(snapshot)
                    val socColor = socAccentColor(snapshot.socInt)
                    val modeText = if (autoRefreshEnabled) "авто $intervalMinutes хв" else "вручну"
                    val errNote = if (snapshot.errorMessage != null) " • офлайн" else ""

                    val socFontSize = when (spec.tier) {
                        WidgetDensityTier.SUMMARY_ONLY -> 24.sp
                        WidgetDensityTier.COMPACT -> 22.sp
                        WidgetDensityTier.REGULAR -> 30.sp
                        WidgetDensityTier.LARGE -> 34.sp
                    }
                    val metricsFontSize = when {
                        isCompact && isNarrow -> 11.sp
                        isCompact -> 12.sp
                        isLarge -> 13.5.sp
                        else -> 13.sp
                    }
                    val statusFontSize = when {
                        isCompact -> 10.5.sp
                        isLarge -> 12.5.sp
                        else -> 12.sp
                    }

                    // Top section (<= 3 children inside Column, respecting Glance's 10-child container limit)
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        // Row 1: Hero SOC % + Total V / Current + Refresh pill
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${snapshot.socInt}%",
                                style = TextStyle(
                                    color = socColor,
                                    fontSize = socFontSize,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )

                            Spacer(modifier = GlanceModifier.width(if (isCompact || isNarrow) 6.dp else 10.dp))

                            Column(modifier = GlanceModifier.defaultWeight()) {
                                Text(
                                    text = "${BatteryFormatters.formatTotalVoltage(snapshot.totalVoltage)} • ${BatteryFormatters.formatCurrent(snapshot.currentAmps)}",
                                    style = TextStyle(
                                        color = primaryTextColor,
                                        fontSize = metricsFontSize,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    maxLines = 1,
                                )
                                if (!spec.showCells) {
                                    Text(
                                        text = "Δ $deltaMv mV • $modeText$errNote",
                                        style = TextStyle(
                                            color = deltaAccentColor(deltaMv),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        maxLines = 1,
                                    )
                                } else if (!isCompact) {
                                    val packLabel = "${snapshot.cellVoltages.size}S"
                                    Text(
                                        text = "$packLabel • $modeText$errNote",
                                        style = TextStyle(
                                            color = secondaryTextColor,
                                            fontSize = 11.sp,
                                        ),
                                        maxLines = 1,
                                    )
                                }
                            }

                            // Tap-to-refresh chip
                            Box(
                                modifier = GlanceModifier
                                    .background(chipBgColor)
                                    .cornerRadius(if (isCompact) 8.dp else 10.dp)
                                    .clickable(actionRunCallback<RefreshBatteryWidgetAction>())
                                    .padding(
                                        horizontal = if (isCompact || isNarrow) 6.dp else 8.dp,
                                        vertical = if (isCompact) 2.5.dp else 4.dp,
                                    ),
                            ) {
                                val timeLabel = BatteryFormatters.formatTimestamp(snapshot.updatedAtMillis)
                                val label = when {
                                    isRefreshing -> "↻ ..."
                                    isNarrow -> "↻$timeLabel"
                                    else -> "↻ $timeLabel"
                                }
                                Text(
                                    text = label,
                                    style = TextStyle(
                                        color = secondaryTextColor,
                                        fontSize = if (isCompact) 10.sp else 11.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    maxLines = 1,
                                )
                            }
                        }

                        // Row 2: Power W/kW + Remaining time status
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(top = if (isCompact) 1.dp else 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val arrowColor = when (status.mode) {
                                BatteryChargeMode.DISCHARGING -> dischargeArrowColor
                                BatteryChargeMode.CHARGING, BatteryChargeMode.FULL -> greenColor
                                BatteryChargeMode.IDLE -> secondaryTextColor
                            }
                            Text(
                                text = "${status.arrow} ",
                                style = TextStyle(
                                    color = arrowColor,
                                    fontSize = statusFontSize,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Text(
                                text = status.text,
                                style = TextStyle(
                                    color = primaryTextColor,
                                    fontSize = statusFontSize,
                                    fontWeight = FontWeight.Medium,
                                ),
                                maxLines = 1,
                            )
                        }

                        if (barBitmap != null) {
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(top = if (isCompact) 3.dp else if (isLarge) 6.dp else 5.dp),
                            ) {
                                Image(
                                    provider = ImageProvider(barBitmap),
                                    contentDescription = "Заряд ${snapshot.socInt}%",
                                    contentScale = ContentScale.FillBounds,
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .height(if (isCompact) 5.dp else if (isLarge) 9.dp else 8.dp),
                                )
                            }
                        }
                    }

                    if (spec.showCells && snapshot.cellVoltages.isNotEmpty()) {
                        // Cells section (<= 3 children inside Column)
                        Column(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .defaultWeight()
                                .padding(top = if (isCompact) 3.dp else if (isLarge) 8.dp else 6.dp),
                        ) {
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(dividerColor),
                            ) {}

                            // Cells header + Delta mV
                            val cellsHeaderFontSize = if (isCompact) 9.5.sp else 11.sp
                            val cellsHeaderText = if (isCompact) {
                                "Комірки ${snapshot.cellVoltages.size}S • $modeText$errNote"
                            } else {
                                "Комірки (${snapshot.cellVoltages.size}S)"
                            }
                            val deltaLabel = if (isCompact || isNarrow) "Δ $deltaMv mV" else "Δ напруга: $deltaMv mV"

                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(
                                        top = if (isCompact) 2.dp else 6.dp,
                                        bottom = if (isCompact) 2.dp else 4.dp,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = cellsHeaderText,
                                    style = TextStyle(
                                        color = secondaryTextColor,
                                        fontSize = cellsHeaderFontSize,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    maxLines = 1,
                                    modifier = GlanceModifier.defaultWeight(),
                                )
                                Text(
                                    text = deltaLabel,
                                    style = TextStyle(
                                        color = deltaAccentColor(deltaMv),
                                        fontSize = cellsHeaderFontSize,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    maxLines = 1,
                                )
                            }

                            // 2-column cell grid: Left Column (01..half), Right Column (half+1..N)
                            val cells = snapshot.cellVoltages
                            val split = (cells.size + 1) / 2
                            val badgeFontSize = if (isCompact) 9.sp else 10.sp
                            val voltageFontSize = when {
                                isCompact -> 10.5.sp
                                isLarge -> 12.5.sp
                                else -> 12.sp
                            }
                            val tilePadH = if (isCompact || isNarrow) 5.dp else 8.dp
                            val tilePadV = when (spec.tier) {
                                WidgetDensityTier.COMPACT -> 1.dp
                                WidgetDensityTier.REGULAR -> 2.5.dp
                                WidgetDensityTier.LARGE -> 3.5.dp
                                WidgetDensityTier.SUMMARY_ONLY -> 1.dp
                            }

                            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                                CellColumn(
                                    indices = (0 until split).toList(),
                                    cells = cells,
                                    minCell = minCell,
                                    maxCell = maxCell,
                                    useRowSpacers = spec.useRowSpacers,
                                    badgeFontSize = badgeFontSize,
                                    voltageFontSize = voltageFontSize,
                                    tilePadH = tilePadH,
                                    tilePadV = tilePadV,
                                    isCompact = isCompact,
                                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                                )
                                Spacer(modifier = GlanceModifier.width(if (isCompact || isNarrow) 6.dp else 8.dp))
                                CellColumn(
                                    indices = (split until cells.size).toList(),
                                    cells = cells,
                                    minCell = minCell,
                                    maxCell = maxCell,
                                    useRowSpacers = spec.useRowSpacers,
                                    badgeFontSize = badgeFontSize,
                                    voltageFontSize = voltageFontSize,
                                    tilePadH = tilePadH,
                                    tilePadV = tilePadV,
                                    isCompact = isCompact,
                                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CellColumn(
        indices: List<Int>,
        cells: List<Double>,
        minCell: Int,
        maxCell: Int,
        useRowSpacers: Boolean,
        badgeFontSize: TextUnit,
        voltageFontSize: TextUnit,
        tilePadH: Dp,
        tilePadV: Dp,
        isCompact: Boolean,
        modifier: GlanceModifier,
    ) {
        Column(modifier = modifier) {
            if (indices.size > 10) {
                indices.chunked(10).forEach { chunk ->
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        chunk.forEach { idx ->
                            CellBadgeItem(
                                cellNumber = idx + 1,
                                voltage = cells[idx],
                                minCell = minCell,
                                maxCell = maxCell,
                                badgeFontSize = badgeFontSize,
                                voltageFontSize = voltageFontSize,
                                tilePadH = tilePadH,
                                tilePadV = tilePadV,
                                isCompact = isCompact,
                                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 2.dp),
                            )
                        }
                    }
                }
            } else {
                indices.forEachIndexed { pos, idx ->
                    CellBadgeItem(
                        cellNumber = idx + 1,
                        voltage = cells[idx],
                        minCell = minCell,
                        maxCell = maxCell,
                        badgeFontSize = badgeFontSize,
                        voltageFontSize = voltageFontSize,
                        tilePadH = tilePadH,
                        tilePadV = tilePadV,
                        isCompact = isCompact,
                        modifier = if (useRowSpacers) {
                            GlanceModifier.fillMaxWidth()
                        } else {
                            GlanceModifier.fillMaxWidth().padding(bottom = if (isCompact) 1.5.dp else 3.dp)
                        },
                    )
                    if (useRowSpacers && pos < indices.lastIndex) {
                        Spacer(modifier = GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }

    @Composable
    private fun CellBadgeItem(
        cellNumber: Int,
        voltage: Double,
        minCell: Int,
        maxCell: Int,
        badgeFontSize: TextUnit,
        voltageFontSize: TextUnit,
        tilePadH: Dp,
        tilePadV: Dp,
        isCompact: Boolean,
        modifier: GlanceModifier,
    ) {
        val numberLabel = cellNumber.toString().padStart(2, '0')
        val isMin = cellNumber == minCell
        val isMax = cellNumber == maxCell
        val valueColor = when {
            isMin && minCell != maxCell -> minCellColor
            isMax && minCell != maxCell -> maxCellColor
            else -> primaryTextColor
        }
        val weight = if ((isMin || isMax) && minCell != maxCell) FontWeight.Bold else FontWeight.Medium

        Row(
            modifier = modifier
                .background(cellTileBgColor)
                .cornerRadius(if (isCompact) 7.dp else 8.dp)
                .padding(horizontal = tilePadH, vertical = tilePadV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier
                    .background(cellBadgeBgColor)
                    .cornerRadius(if (isCompact) 5.dp else 6.dp)
                    .padding(horizontal = if (isCompact) 5.dp else 6.dp, vertical = if (isCompact) 0.dp else 1.dp),
            ) {
                Text(
                    text = numberLabel,
                    style = TextStyle(
                        color = whiteProvider,
                        fontSize = badgeFontSize,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Spacer(modifier = GlanceModifier.width(4.dp))
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "${BatteryFormatters.formatCellVoltage(voltage)} V",
                style = TextStyle(
                    color = valueColor,
                    fontSize = voltageFontSize,
                    fontWeight = weight,
                ),
                maxLines = 1,
            )
        }
    }

    @Composable
    private fun EmptyOrErrorContent(
        errorMessage: String?,
        isRefreshing: Boolean,
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isRefreshing) "Завантаження даних HA..." else "Немає даних від Home Assistant",
                style = TextStyle(
                    color = primaryTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = errorMessage,
                    style = TextStyle(
                        color = secondaryTextColor,
                        fontSize = 11.sp,
                    ),
                    maxLines = 2,
                )
            }
            Spacer(modifier = GlanceModifier.height(8.dp))
            Box(
                modifier = GlanceModifier
                    .background(chipBgColor)
                    .cornerRadius(10.dp)
                    .clickable(actionRunCallback<RefreshBatteryWidgetAction>())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "↻ Оновити",
                    style = TextStyle(
                        color = greenColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }

    private fun GlanceModifier.applyWidgetCornerRadius(): GlanceModifier =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cornerRadius(android.R.dimen.system_app_widget_background_radius)
        } else {
            cornerRadius(16.dp)
        }

    private fun GlanceModifier.applyInnerCornerRadius(): GlanceModifier =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cornerRadius(android.R.dimen.system_app_widget_inner_radius)
        } else {
            cornerRadius(15.dp)
        }

    companion object {
        val KEY_REVISION = intPreferencesKey("widget_revision")
        val KEY_SNAPSHOT_JSON = stringPreferencesKey("snapshot_json")
        val KEY_REFRESHING = booleanPreferencesKey("is_refreshing")
        val KEY_AUTO_REFRESH = booleanPreferencesKey("auto_refresh_enabled")
        val KEY_INTERVAL_MINUTES = intPreferencesKey("interval_minutes")

        private val cardBgColor = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1C1C1E))
        private val borderColor = ColorProvider(day = Color(0xFFE0E0E0), night = Color(0xFF2E2E32))
        private val primaryTextColor = ColorProvider(day = Color(0xFF1C1C1F), night = Color(0xFFF2F2F7))
        private val secondaryTextColor = ColorProvider(day = Color(0xFF5F6368), night = Color(0xFFA0A4AB))
        private val dividerColor = ColorProvider(day = Color(0xFFEAECEF), night = Color(0xFF2C2C30))
        private val chipBgColor = ColorProvider(day = Color(0xFFEEF1F4), night = Color(0xFF2C2E33))
        private val cellTileBgColor = ColorProvider(day = Color(0xFFF5F7F9), night = Color(0xFF25262B))
        private val cellBadgeBgColor = ColorProvider(day = Color(0xFF0E5A6D), night = Color(0xFF14748A))
        private val whiteProvider = ColorProvider(day = Color.White, night = Color.White)

        private val greenColor = ColorProvider(day = Color(0xFF4CAF50), night = Color(0xFF4CAF50))
        private val amberColor = ColorProvider(day = Color(0xFFFB8C00), night = Color(0xFFFFB74D))
        private val redColor = ColorProvider(day = Color(0xFFE53935), night = Color(0xFFEF5350))
        private val dischargeArrowColor = ColorProvider(day = Color(0xFFE91E63), night = Color(0xFFF06292))
        private val minCellColor = ColorProvider(day = Color(0xFFD32F2F), night = Color(0xFFFF6B6B))
        private val maxCellColor = ColorProvider(day = Color(0xFF1976D2), night = Color(0xFF64B5F6))

        private fun socAccentColor(soc: Int): GlanceColorProvider = when {
            soc < 15 -> redColor
            soc < 25 -> amberColor
            else -> greenColor
        }

        private fun deltaAccentColor(deltaMv: Int): GlanceColorProvider = when {
            deltaMv > 40 -> redColor
            deltaMv > 15 -> amberColor
            else -> greenColor
        }

        private fun isNightMode(context: Context): Boolean =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        private fun calculateWidgetMemoryCapBytes(context: Context): Long {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = context.getSystemService(WindowManager::class.java)
                    ?.currentWindowMetrics
                    ?.bounds
                if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                    return 6L * bounds.width().toLong() * bounds.height().toLong()
                }
            }
            val dm = context.resources.displayMetrics
            return 6L * dm.widthPixels.toLong() * dm.heightPixels.toLong()
        }

        /**
         * Renders a rounded pill progress bar bitmap kept far below the widget bitmap cap
         * (480x16 ARGB_8888 = 30 KB), shared across all responsive size buckets.
         */
        private fun createProgressBarBitmap(context: Context, socPercent: Int, isDark: Boolean): Bitmap? {
            val widthPx = 480
            val heightPx = 16
            val bytesNeeded = widthPx.toLong() * heightPx.toLong() * 4L
            if (bytesNeeded > calculateWidgetMemoryCapBytes(context)) return null

            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val radius = heightPx / 2f
            val rect = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())

            val trackColor = if (isDark) Color(0xFF32343A).toArgb() else Color(0xFFE5E7EB).toArgb()
            val fillColor = when {
                socPercent < 15 -> Color(0xFFE53935).toArgb()
                socPercent < 25 -> Color(0xFFFF9800).toArgb()
                else -> Color(0xFF4CAF50).toArgb()
            }

            val clipPath = Path().apply {
                addRoundRect(rect, radius, radius, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(clipPath)

            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = trackColor }
            canvas.drawRect(rect, trackPaint)

            val clamped = socPercent.coerceIn(0, 100)
            if (clamped > 0) {
                val fillWidth = max(heightPx.toFloat(), widthPx * (clamped / 100f))
                val fillRect = RectF(0f, 0f, fillWidth, heightPx.toFloat())
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
                canvas.drawRoundRect(fillRect, radius, radius, fillPaint)
            }
            canvas.restore()
            return bitmap
        }
    }
}
