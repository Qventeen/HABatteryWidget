package com.homeassistant.batterywidget.ui.main

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.homeassistant.batterywidget.R
import com.homeassistant.batterywidget.data.BatteryChargeMode
import com.homeassistant.batterywidget.data.BatteryFormatters
import com.homeassistant.batterywidget.data.BatterySnapshot
import com.homeassistant.batterywidget.data.SettingsStore
import com.homeassistant.batterywidget.notifications.BatteryFullNotificationManager
import com.homeassistant.batterywidget.ui.theme.HABatteryWidgetTheme
import com.homeassistant.batterywidget.widget.BatteryGlanceWidgetReceiver
import com.homeassistant.batterywidget.widget.WidgetSyncWorker

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val viewModel: MainScreenViewModel = viewModel {
        MainScreenViewModel(settingsStore = SettingsStore(appContext))
    }

    LaunchedEffect(Unit) {
        WidgetSyncWorker.applyRefreshSchedule(appContext, forceReschedule = false)
        viewModel.refresh(appContext)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var hasNotificationPermission by remember {
        mutableStateOf(BatteryFullNotificationManager.canPostNotifications(appContext))
    }
    var showPermissionHint by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, appContext) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = BatteryFullNotificationManager.canPostNotifications(appContext)
                hasNotificationPermission = granted
                if (granted) {
                    showPermissionHint = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val canPost = granted && BatteryFullNotificationManager.canPostNotifications(appContext)
        hasNotificationPermission = canPost
        if (canPost) {
            showPermissionHint = false
            viewModel.setNotifyOnFull(true)
        } else {
            showPermissionHint = true
            viewModel.onNotificationPermissionDenied()
        }
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MainScreenContent(
        state = state,
        hasNotificationPermission = hasNotificationPermission,
        showNotificationPermissionHint = showPermissionHint,
        onRefresh = { viewModel.refresh(appContext) },
        onAutoRefreshChange = viewModel::setAutoRefresh,
        onIntervalInputChange = viewModel::updateIntervalInput,
        onSaveRefreshSettings = { viewModel.saveRefreshSettings(appContext) },
        onNotifyOnFullChange = { checked ->
            if (!checked) {
                showPermissionHint = false
                viewModel.setNotifyOnFull(false)
            } else if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !BatteryFullNotificationManager.hasRuntimePermission(appContext)
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (!BatteryFullNotificationManager.canPostNotifications(appContext)) {
                hasNotificationPermission = false
                showPermissionHint = true
                viewModel.onNotificationPermissionDenied()
            } else {
                hasNotificationPermission = true
                showPermissionHint = false
                viewModel.setNotifyOnFull(true)
            }
        },
        onOpenNotificationSettings = {
            BatteryFullNotificationManager.openAppNotificationSettings(appContext)
        },
        onPrimaryUrlChange = viewModel::updatePrimaryUrl,
        onFallbackUrlChange = viewModel::updateFallbackUrl,
        onTokenChange = viewModel::updateAccessToken,
        onSaveSettings = { viewModel.saveSettingsAndRefresh(appContext) },
        entitiesCallbacks = HaEntitiesCallbacks(
            onSocEntityChange = viewModel::updateSocEntity,
            onPowerEntityChange = viewModel::updatePowerEntity,
            onFallbackPowerEntityChange = viewModel::updateFallbackPowerEntity,
            onCurrentEntityChange = viewModel::updateCurrentEntity,
            onTotalVoltageEntityChange = viewModel::updateTotalVoltageEntity,
            onDeltaVoltageEntityChange = viewModel::updateDeltaVoltageEntity,
            onMinCellEntityChange = viewModel::updateMinCellEntity,
            onMaxCellEntityChange = viewModel::updateMaxCellEntity,
            onTimeStatusEntityChange = viewModel::updateTimeStatusEntity,
            onCellEntityChange = viewModel::updateCellEntity,
            onAddCellEntity = viewModel::addCellEntity,
            onRemoveCellEntity = viewModel::removeCellEntity,
            onSaveEntities = { viewModel.saveEntitiesAndRefresh(appContext) },
            onResetDefaultEntities = { viewModel.resetDefaultEntities(appContext) },
        ),
        onPinWidget = {
            val manager = AppWidgetManager.getInstance(appContext)
            val provider = ComponentName(appContext, BatteryGlanceWidgetReceiver::class.java)
            if (manager.isRequestPinAppWidgetSupported) {
                manager.requestPinAppWidget(provider, null, null)
            } else {
                Toast.makeText(
                    context,
                    "Утримуйте порожнє місце на екрані → Віджети → Акумулятор EVE 8S",
                    Toast.LENGTH_LONG,
                ).show()
            }
        },
        modifier = modifier,
    )
}

internal data class HaEntitiesCallbacks(
    val onSocEntityChange: (String) -> Unit = {},
    val onPowerEntityChange: (String) -> Unit = {},
    val onFallbackPowerEntityChange: (String) -> Unit = {},
    val onCurrentEntityChange: (String) -> Unit = {},
    val onTotalVoltageEntityChange: (String) -> Unit = {},
    val onDeltaVoltageEntityChange: (String) -> Unit = {},
    val onMinCellEntityChange: (String) -> Unit = {},
    val onMaxCellEntityChange: (String) -> Unit = {},
    val onTimeStatusEntityChange: (String) -> Unit = {},
    val onCellEntityChange: (Int, String) -> Unit = { _, _ -> },
    val onAddCellEntity: () -> Unit = {},
    val onRemoveCellEntity: (Int) -> Unit = {},
    val onSaveEntities: () -> Unit = {},
    val onResetDefaultEntities: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MainScreenContent(
    state: MainUiState,
    hasNotificationPermission: Boolean = true,
    showNotificationPermissionHint: Boolean = false,
    onRefresh: () -> Unit,
    onAutoRefreshChange: (Boolean) -> Unit,
    onIntervalInputChange: (String) -> Unit,
    onSaveRefreshSettings: () -> Unit,
    onNotifyOnFullChange: (Boolean) -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onPrimaryUrlChange: (String) -> Unit,
    onFallbackUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onSaveSettings: () -> Unit,
    entitiesCallbacks: HaEntitiesCallbacks = HaEntitiesCallbacks(),
    onPinWidget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showConnectionSettings by remember { mutableStateOf(false) }
    var showEntitiesSettings by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BatteryPreviewCard(
                snapshot = state.snapshot,
                isLoading = state.isLoading,
            )

            if (!state.statusMessage.isNullOrBlank()) {
                Text(
                    text = state.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.statusMessage.startsWith("Помилка")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color(0xFF4CAF50)
                    },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onRefresh,
                    enabled = !state.isLoading,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                ) {
                    Text(if (state.isLoading) "Оновлення..." else "Оновити зараз")
                }

                OutlinedButton(
                    onClick = onPinWidget,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Додати віджет")
                }
            }

            // Widget update mode & manual interval card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Оновлення віджета",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val autoSelected = state.autoRefreshEnabled
                        FilterChip(
                            selected = autoSelected,
                            onClick = { onAutoRefreshChange(true) },
                            label = { Text("Автооновлення") },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = !autoSelected,
                            onClick = { onAutoRefreshChange(false) },
                            label = { Text("Вручну") },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (state.autoRefreshEnabled) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            listOf(1, 5, 15, 30, 60).forEach { preset ->
                                FilterChip(
                                    selected = state.intervalInput == preset.toString(),
                                    onClick = { onIntervalInputChange(preset.toString()) },
                                    label = { Text("$preset хв") },
                                )
                            }
                        }

                        OutlinedTextField(
                            value = state.intervalInput,
                            onValueChange = onIntervalInputChange,
                            label = { Text("Частота автооновлення (хв, 1–1440)") },
                            suffix = { Text("хв") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        val parsedMins = state.intervalInput.toIntOrNull() ?: 15
                        val hint = if (parsedMins >= 15) {
                            "Фонове оновлення через WorkManager кожні $parsedMins хв (оптимально для батареї телефону)."
                        } else {
                            "Часте оновлення кожні $parsedMins хв через AlarmManager (у режимі сну Doze Android може згладжувати інтервал)."
                        }
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "Автоматичне фонове оновлення вимкнено. Натискайте ↻ на віджеті або кнопку «Оновити зараз».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Button(
                        onClick = onSaveRefreshSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Застосувати режим оновлення")
                    }
                }
            }

            // 100% battery charge notification card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Сповіщення",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .toggleable(
                                value = state.notifyOnFullEnabled,
                                role = Role.Checkbox,
                                onValueChange = onNotifyOnFullChange,
                            )
                            .padding(vertical = 4.dp)
                            .testTag("notify_full_checkbox"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = state.notifyOnFullEnabled,
                            onCheckedChange = null,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Сповіщати про зарядку до 100%",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Text(
                        text = "Надсилає сповіщення один раз, коли заряд досягне 100% (повторно — після зниження ≤ 98%).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (state.notifyOnFullEnabled && !state.autoRefreshEnabled) {
                        Text(
                            text = "Автооновлення вимкнено («Вручну») — у фоні перевірка не виконується.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF9800),
                        )
                    }

                    if (!hasNotificationPermission && (state.notifyOnFullEnabled || showNotificationPermissionHint)) {
                        Text(
                            text = "Системний дозвіл на сповіщення вимкнено.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(
                            onClick = onOpenNotificationSettings,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Відкрити налаштування сповіщень")
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { showConnectionSettings = !showConnectionSettings },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (showConnectionSettings) "Сховати налаштування HA" else "Налаштування підключення HA")
            }

            if (showConnectionSettings) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Home Assistant API",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        OutlinedTextField(
                            value = state.primaryUrl,
                            onValueChange = onPrimaryUrlChange,
                            label = { Text("Основний URL (Wi-Fi)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.fallbackUrl,
                            onValueChange = onFallbackUrlChange,
                            label = { Text("Резервний / зовнішній URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.accessToken,
                            onValueChange = onTokenChange,
                            label = { Text("Long-Lived Access Token") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = onSaveSettings,
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Зберегти та перевірити")
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { showEntitiesSettings = !showEntitiesSettings },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("toggle_entities_settings"),
            ) {
                Text(if (showEntitiesSettings) "Сховати сутності HA" else "Сутності Home Assistant")
            }

            if (showEntitiesSettings) {
                HaEntitiesCard(
                    state = state,
                    callbacks = entitiesCallbacks,
                )
            }
        }
    }
}

@Composable
private fun HaEntitiesCard(
    state: MainUiState,
    callbacks: HaEntitiesCallbacks,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Сутності Home Assistant",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Вкажіть entity_id сенсорів, які показуються в додатку та на віджеті. Необов'язкові поля можна залишити порожніми.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            EntityTextField(
                value = state.socEntity,
                onValueChange = callbacks.onSocEntityChange,
                label = "Заряд батареї (%)*",
                tag = "entity_soc",
            )
            EntityTextField(
                value = state.powerEntity,
                onValueChange = callbacks.onPowerEntityChange,
                label = "Потужність (W)",
                tag = "entity_power",
            )
            EntityTextField(
                value = state.fallbackPowerEntity,
                onValueChange = callbacks.onFallbackPowerEntityChange,
                label = "Резервна потужність (W, опціонально)",
                tag = "entity_fallback_power",
            )
            EntityTextField(
                value = state.currentEntity,
                onValueChange = callbacks.onCurrentEntityChange,
                label = "Струм (A)",
                tag = "entity_current",
            )
            EntityTextField(
                value = state.totalVoltageEntity,
                onValueChange = callbacks.onTotalVoltageEntityChange,
                label = "Загальна напруга (V)",
                tag = "entity_total_voltage",
            )
            EntityTextField(
                value = state.timeStatusEntity,
                onValueChange = callbacks.onTimeStatusEntityChange,
                label = "Статус / залишок часу (опціонально)",
                tag = "entity_time_status",
            )
            EntityTextField(
                value = state.deltaVoltageEntity,
                onValueChange = callbacks.onDeltaVoltageEntityChange,
                label = "Дельта комірок (V, опціонально)",
                tag = "entity_delta_voltage",
            )
            EntityTextField(
                value = state.minCellEntity,
                onValueChange = callbacks.onMinCellEntityChange,
                label = "Індекс мін. комірки (опціонально)",
                tag = "entity_min_cell",
            )
            EntityTextField(
                value = state.maxCellEntity,
                onValueChange = callbacks.onMaxCellEntityChange,
                label = "Індекс макс. комірки (опціонально)",
                tag = "entity_max_cell",
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Комірки (${state.cellEntities.size}S)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = callbacks.onAddCellEntity,
                    enabled = state.cellEntities.size < MainScreenViewModel.MAX_CELL_ENTITIES,
                ) {
                    Text("+ Додати")
                }
            }

            if (state.cellEntities.isEmpty()) {
                Text(
                    text = "Комірки не вказані — відображатимуться лише загальні показники батареї.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.cellEntities.forEachIndexed { index, entityId ->
                    val cellLabel = (index + 1).toString().padStart(2, '0')
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0E5A6D))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = cellLabel,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        OutlinedTextField(
                            value = entityId,
                            onValueChange = { callbacks.onCellEntityChange(index, it) },
                            label = { Text("Комірка $cellLabel") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("entity_cell_$index"),
                        )
                        TextButton(
                            onClick = { callbacks.onRemoveCellEntity(index) },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text("✕")
                        }
                    }
                }
            }

            Button(
                onClick = callbacks.onSaveEntities,
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_entities_button"),
            ) {
                Text("Зберегти сутності та оновити")
            }

            OutlinedButton(
                onClick = callbacks.onResetDefaultEntities,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Скинути за замовчуванням")
            }
        }
    }
}

@Composable
private fun EntityTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    )
}

@Composable
private fun BatteryPreviewCard(
    snapshot: BatterySnapshot?,
    isLoading: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (snapshot == null || !snapshot.hasValidData) {
                Text(
                    text = if (isLoading) "Отримання даних з Home Assistant..." else "Немає даних для відображення",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                val status = BatteryFormatters.formatStatusLine(snapshot)
                val deltaMv = BatteryFormatters.computeDeltaMv(snapshot)
                val (minCell, maxCell) = BatteryFormatters.resolveMinMaxCells(snapshot)
                val accentColor = when {
                    snapshot.socInt < 15 -> Color(0xFFE53935)
                    snapshot.socInt < 25 -> Color(0xFFFF9800)
                    else -> Color(0xFF4CAF50)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${snapshot.socInt}%",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${BatteryFormatters.formatTotalVoltage(snapshot.totalVoltage)} • ${BatteryFormatters.formatCurrent(snapshot.currentAmps)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Оновлено: ${BatteryFormatters.formatTimestamp(snapshot.updatedAtMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val arrowColor = when (status.mode) {
                        BatteryChargeMode.DISCHARGING -> Color(0xFFE91E63)
                        BatteryChargeMode.CHARGING, BatteryChargeMode.FULL -> Color(0xFF4CAF50)
                        BatteryChargeMode.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = "${status.arrow} ",
                        color = arrowColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = status.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { (snapshot.socInt / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = accentColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )

                if (snapshot.cellVoltages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Комірки (${snapshot.cellVoltages.size}S)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        val deltaColor = when {
                            deltaMv > 40 -> Color(0xFFE53935)
                            deltaMv > 15 -> Color(0xFFFF9800)
                            else -> Color(0xFF4CAF50)
                        }
                        Text(
                            text = "Δ напруга: $deltaMv mV",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = deltaColor,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val cells = snapshot.cellVoltages
                    val rows = (cells.size + 1) / 2
                    for (r in 0 until rows) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CellRowItem(
                                cellNumber = r + 1,
                                voltage = cells[r],
                                minCell = minCell,
                                maxCell = maxCell,
                                modifier = Modifier.weight(1f),
                            )
                            val rightIdx = r + rows
                            if (rightIdx < cells.size) {
                                CellRowItem(
                                    cellNumber = rightIdx + 1,
                                    voltage = cells[rightIdx],
                                    minCell = minCell,
                                    maxCell = maxCell,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CellRowItem(
    cellNumber: Int,
    voltage: Double,
    minCell: Int,
    maxCell: Int,
    modifier: Modifier = Modifier,
) {
    val label = cellNumber.toString().padStart(2, '0')
    val isMin = cellNumber == minCell
    val isMax = cellNumber == maxCell
    val valueColor = when {
        isMin && minCell != maxCell -> Color(0xFFD32F2F)
        isMax && minCell != maxCell -> Color(0xFF1976D2)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0E5A6D))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "${BatteryFormatters.formatCellVoltage(voltage)} V",
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = if (isMin || isMax) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    HABatteryWidgetTheme {
        MainScreenContent(
            state = MainUiState(
                snapshot = BatterySnapshot(
                    socPercent = 87.0,
                    powerWatts = -226.0,
                    currentAmps = -8.5,
                    totalVoltage = 26.54,
                    deltaVolts = 0.001,
                    minCellIndex = 1,
                    maxCellIndex = 5,
                    timeStatus = "🔴 До 0%: 31г 54хв | 226W",
                    cellVoltages = listOf(3.317, 3.317, 3.317, 3.317, 3.318, 3.317, 3.317, 3.317),
                    updatedAtMillis = 1794300000000L,
                ),
            ),
            onRefresh = {},
            onAutoRefreshChange = {},
            onIntervalInputChange = {},
            onSaveRefreshSettings = {},
            onPrimaryUrlChange = {},
            onFallbackUrlChange = {},
            onTokenChange = {},
            onSaveSettings = {},
            onPinWidget = {},
        )
    }
}
