package com.toeipass.navi.ui

import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toeipass.navi.engine.DefaultPresets
import com.toeipass.navi.engine.RouteBadge
import com.toeipass.navi.engine.RouteResult
import com.toeipass.navi.engine.RouteSegment
import com.toeipass.navi.engine.Station
import com.toeipass.navi.engine.TimeMode
import com.toeipass.navi.engine.TransitDataset
import com.toeipass.navi.ui.theme.SlateBg
import com.toeipass.navi.ui.theme.SlateBorder
import com.toeipass.navi.ui.theme.SlateMuted
import com.toeipass.navi.ui.theme.SlateText
import com.toeipass.navi.ui.theme.ToeiGreen
import com.toeipass.navi.ui.theme.ToeiGreenBorder
import com.toeipass.navi.ui.theme.ToeiGreenDark
import com.toeipass.navi.ui.theme.ToeiGreenLight
import com.toeipass.navi.ui.theme.parseHexColor

private enum class StationPickerTarget {
    FROM, TO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToeiPassScreen(viewModel: ToeiPassViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var pickerTarget by remember { mutableStateOf<StationPickerTarget?>(null) }

    val jsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                if (text != null) {
                    viewModel.importDatasetJson(text)
                }
            } catch (_: Exception) {
                viewModel.importDatasetJson("")
            }
        }
    }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        containerColor = SlateBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ToeiGreen)
                    Spacer(Modifier.height(12.dp))
                    Text("路線データを読み込み中...", color = SlateMuted, fontSize = 14.sp)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                AppHeader()

                SearchFormCard(
                    state = state,
                    onPickFrom = { pickerTarget = StationPickerTarget.FROM },
                    onPickTo = { pickerTarget = StationPickerTarget.TO },
                    onHomeClick = { viewModel.setHomeAsDeparture() },
                    onSwapClick = { viewModel.swapStations() },
                    onPickTime = {
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute -> viewModel.setTime(hourOfDay, minute) },
                            state.timeHour,
                            state.timeMinute,
                            true
                        ).show()
                    },
                    onNowClick = { viewModel.setCurrentTime() },
                    onTimeModeChange = { viewModel.setTimeMode(it) },
                    onToggleToeiPass = { viewModel.toggleToeiFreePass(it) },
                    onToggleAccessible = { viewModel.toggleAccessibleTransfer(it) },
                    onToggleBus = { viewModel.toggleIncludeToeiBus(it) },
                    onSearchClick = { viewModel.runSearch() }
                )

                PresetSection(onSelectPreset = { viewModel.applyPreset(it) })

                if (state.isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ToeiGreen, modifier = Modifier.size(28.dp))
                    }
                } else if (state.fromStationId == state.toStationId) {
                    EmptyMessageCard("出発駅と到着駅に異なる駅を選択してください。")
                } else if (state.routes.isEmpty()) {
                    EmptyMessageCard("条件に合う経路が見つかりませんでした。")
                } else {
                    RouteTabsRow(
                        routes = state.routes,
                        activeIndex = state.activeRouteIndex,
                        onSelectTab = { viewModel.selectRouteTab(it) }
                    )

                    val activeRoute = state.activeRoute
                    val dataset = state.dataset
                    if (activeRoute != null && dataset != null) {
                        RouteDetailCard(
                            route = activeRoute,
                            dataset = dataset,
                            useToeiFreePass = state.useToeiFreePass
                        )
                    }
                }

                DataAdminCard(
                    dataset = state.dataset,
                    isCustomDataset = state.isCustomDataset,
                    onImportJson = { jsonPickerLauncher.launch(arrayOf("application/json", "*/*")) },
                    onResetDefault = { viewModel.loadDefaultDataset() }
                )
            }
        }
    }

    val currentTarget = pickerTarget
    if (currentTarget != null && state.dataset != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { pickerTarget = null },
            sheetState = sheetState,
            containerColor = Color.White
        ) {
            StationPickerSheetContent(
                title = if (currentTarget == StationPickerTarget.FROM) "出発駅を選択" else "到着駅を選択",
                filterStations = { query -> viewModel.filterStations(query) },
                onSelect = { station ->
                    if (currentTarget == StationPickerTarget.FROM) {
                        viewModel.selectFromStation(station.id)
                    } else {
                        viewModel.selectToStation(station.id)
                    }
                    pickerTarget = null
                }
            )
        }
    }
}

@Composable
private fun AppHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(ToeiGreenDark, ToeiGreen)
                )
            )
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "都営無料乗車券",
                        color = ToeiGreenDark,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = "ToeiPassNavi",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "都営交通無料乗車券（都営0円）＋JR・メトロ・私鉄 乗換案内",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SearchFormCard(
    state: ToeiPassUiState,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onHomeClick: () -> Unit,
    onSwapClick: () -> Unit,
    onPickTime: () -> Unit,
    onNowClick: () -> Unit,
    onTimeModeChange: (TimeMode) -> Unit,
    onToggleToeiPass: (Boolean) -> Unit,
    onToggleAccessible: (Boolean) -> Unit,
    onToggleBus: (Boolean) -> Unit,
    onSearchClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SlateBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 出発駅
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "出発",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SlateText,
                    modifier = Modifier.width(34.dp)
                )
                StationSelectorBox(
                    stationName = state.fromStationName,
                    modifier = Modifier.weight(1f),
                    onClick = onPickFrom
                )
                OutlinedButton(
                    onClick = onHomeClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(44.dp),
                    border = BorderStroke(1.dp, ToeiGreenBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = ToeiGreenLight,
                        contentColor = ToeiGreenDark
                    )
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("自宅", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(10.dp))

            // 到着駅
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "到着",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SlateText,
                    modifier = Modifier.width(34.dp)
                )
                StationSelectorBox(
                    stationName = state.toStationName,
                    modifier = Modifier.weight(1f),
                    onClick = onPickTo
                )
                OutlinedButton(
                    onClick = onSwapClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(44.dp),
                    border = BorderStroke(1.dp, SlateBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFF8FAFC),
                        contentColor = SlateText
                    )
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("入替", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))

            // 時刻指定行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier
                        .height(40.dp)
                        .clickable(onClick = onPickTime)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = "時刻選択",
                            tint = ToeiGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = state.formattedTime,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateText
                        )
                    }
                }

                // 出発 / 到着 切替
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFE2E8F0))
                        .padding(2.dp)
                ) {
                    TimeModeButton(
                        label = "出発",
                        selected = state.timeMode == TimeMode.DEPARTURE,
                        onClick = { onTimeModeChange(TimeMode.DEPARTURE) }
                    )
                    TimeModeButton(
                        label = "到着",
                        selected = state.timeMode == TimeMode.ARRIVAL,
                        onClick = { onTimeModeChange(TimeMode.ARRIVAL) }
                    )
                }

                Spacer(Modifier.weight(1f))

                OutlinedButton(
                    onClick = onNowClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(40.dp),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Text("現在時刻", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateText)
                }
            }

            Spacer(Modifier.height(12.dp))

            // オプション切替
            OptionToggleRow(
                label = "都営交通無料乗車券を適用（都営0円）",
                checked = state.useToeiFreePass,
                onCheckedChange = onToggleToeiPass,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OptionToggleRow(
                    label = "🛗 余裕乗換",
                    checked = state.accessibleTransfer,
                    onCheckedChange = onToggleAccessible,
                    modifier = Modifier.weight(1f)
                )
                OptionToggleRow(
                    label = "🚌 都営バス",
                    checked = state.includeToeiBus,
                    onCheckedChange = onToggleBus,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onSearchClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ToeiGreen)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("ルートを検索", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun StationSelectorBox(
    stationName: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.5.dp, SlateBorder)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                text = stationName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = SlateText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.Search,
                contentDescription = "駅を検索",
                tint = SlateMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun TimeModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (selected) ToeiGreenDark else SlateMuted
        )
    }
}

@Composable
private fun OptionToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(10.dp),
        color = if (checked) ToeiGreenLight else Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, if (checked) ToeiGreenBorder else SlateBorder)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (checked) ToeiGreenDark else SlateMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(checkedColor = ToeiGreen),
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

@Composable
private fun PresetSection(onSelectPreset: (com.toeipass.navi.engine.PresetQuery) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            text = "よく使う区間（タップで即検索）",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = SlateMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DefaultPresets.list.forEach { preset ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.clickable { onSelectPreset(preset) }
                ) {
                    Text(
                        text = preset.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteTabsRow(
    routes: List<RouteResult>,
    activeIndex: Int,
    onSelectTab: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        routes.forEachIndexed { idx, route ->
            val isSelected = idx == activeIndex
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) ToeiGreenLight else Color.White,
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) ToeiGreen else SlateBorder
                ),
                modifier = Modifier
                    .width(136.dp)
                    .clickable { onSelectTab(idx) }
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "ルート${idx + 1}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isSelected) ToeiGreenDark else SlateMuted
                        )
                        route.badges.forEach { badge ->
                            RouteBadgeChip(badge)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${route.totalFare}円",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = if (route.totalFare == 0) ToeiGreen else SlateText
                    )
                    Text(
                        text = "${route.totalMinutes}分・乗換${route.transferCount}回",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteBadgeChip(badge: RouteBadge) {
    Surface(
        color = parseHexColor(badge.colorHex),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = badge.text,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun RouteDetailCard(
    route: RouteResult,
    dataset: TransitDataset,
    useToeiFreePass: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SlateBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // ルートヘッダー
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        route.badges.forEach { RouteBadgeChip(it) }
                        Text(
                            text = route.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${route.departTime}発 → ${route.arriveTime}着",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = SlateText
                        )
                        Surface(
                            color = Color(0xFFE2E8F0),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${route.totalMinutes}分",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SlateText,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${route.totalFare}円",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = ToeiGreen
                )
            }

            HorizontalDivider(color = SlateBorder)

            // メタバー
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "乗車 ${route.rideMinutes}分",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateMuted
                )
                Text(
                    text = "乗換 ${route.transferCount}回（1回${route.transferMargin}分換算）",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateMuted
                )
                Text(
                    text = "${route.totalKm}km",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateMuted
                )
            }

            HorizontalDivider(color = Color(0xFFE2E8F0))

            // タイムライン
            Column(modifier = Modifier.padding(14.dp)) {
                route.segments.forEachIndexed { idx, seg ->
                    val lineColor = parseHexColor(seg.lineColor)
                    if (idx == 0) {
                        StationNodeRow(
                            time = seg.depTime,
                            stationName = dataset.getStationName(seg.fromStationId),
                            barrierFree = dataset.getStationBarrierFree(seg.fromStationId),
                            dotColor = lineColor,
                            transferWaitMinutes = null
                        )
                    }

                    SegmentBarRow(
                        segment = seg,
                        lineColor = lineColor,
                        dataset = dataset,
                        useToeiFreePass = useToeiFreePass
                    )

                    StationNodeRow(
                        time = seg.arrTime,
                        stationName = dataset.getStationName(seg.toStationId),
                        barrierFree = dataset.getStationBarrierFree(seg.toStationId),
                        dotColor = lineColor,
                        transferWaitMinutes = seg.transferWaitMinutes
                    )
                }
            }
        }
    }
}

@Composable
private fun StationNodeRow(
    time: String,
    stationName: String,
    barrierFree: String,
    dotColor: Color,
    transferWaitMinutes: Int?
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = time,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = SlateText,
            modifier = Modifier
                .width(46.dp)
                .padding(top = 1.dp)
        )
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(dotColor)
                .border(2.dp, Color.White, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stationName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = SlateText
            )
            if (barrierFree.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Surface(
                    color = Color(0xFFF0FDF4),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                ) {
                    Text(
                        text = "🛗 $barrierFree",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ToeiGreen,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            if (transferWaitMinutes != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "乗換・移動目安 ${transferWaitMinutes}分",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SlateMuted
                )
            }
        }
    }
}

@Composable
private fun SegmentBarRow(
    segment: RouteSegment,
    lineColor: Color,
    dataset: TransitDataset,
    useToeiFreePass: Boolean
) {
    var expanded by remember(segment.lineId, segment.fromStationId, segment.toStationId) {
        mutableStateOf(false)
    }
    val intermediateStops = if (segment.stops.size > 2) {
        segment.stops.subList(1, segment.stops.size - 1)
    } else {
        emptyList()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 4.dp)
    ) {
        Spacer(Modifier.width(51.dp))
        Box(
            modifier = Modifier
                .width(5.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(lineColor)
        )
        Spacer(Modifier.width(14.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (segment.lineCode.isNotEmpty()) {
                        Surface(
                            color = lineColor,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = segment.lineCode,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = segment.lineName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SlateText
                    )
                }
                Spacer(Modifier.height(2.dp))
                val subText = buildString {
                    if (segment.dirHint.isNotEmpty()) {
                        append(segment.dirHint)
                        append("・")
                    }
                    append("乗車${segment.minutes}分 (${segment.km}km)")
                }
                Text(
                    text = subText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = SlateMuted
                )

                if (intermediateStops.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { expanded = !expanded }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = SlateMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = "${segment.stops.size - 1}駅（途中${intermediateStops.size}駅）",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF334155)
                            )
                        }
                    }

                    AnimatedVisibility(visible = expanded) {
                        Column(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF8FAFC))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            intermediateStops.forEach { stop ->
                                Text(
                                    text = "・${dataset.getStationName(stop.stationId)}",
                                    fontSize = 11.sp,
                                    color = SlateMuted
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "直行（1区間）",
                        fontSize = 11.sp,
                        color = SlateMuted
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // 運賃表示
            Column(horizontalAlignment = Alignment.End) {
                val isFreeHighlight = (segment.fare == 0 && (!segment.isThroughFare || (segment.isToeiFree && useToeiFreePass)))
                val priceText = when {
                    segment.isThroughFare && segment.isToeiFree && useToeiFreePass -> "0円"
                    segment.isThroughFare -> "乗継通算"
                    else -> "${segment.fare}円"
                }

                if (isFreeHighlight) {
                    Surface(
                        color = ToeiGreenLight,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = priceText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = ToeiGreenDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Text(
                        text = priceText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = SlateText
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = segment.fareLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SlateMuted
                )
            }
        }
    }
}

@Composable
private fun EmptyMessageCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Text(
            text = message,
            color = SlateMuted,
            fontSize = 14.sp,
            modifier = Modifier.padding(20.dp)
        )
    }
}

@Composable
private fun DataAdminCard(
    dataset: TransitDataset?,
    isCustomDataset: Boolean,
    onImportJson: () -> Unit,
    onResetDefault: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            val stationCount = dataset?.stations?.size ?: 0
            val edgeCount = dataset?.edges?.size ?: 0
            Text(
                text = "路線・運賃データ管理（全${stationCount}駅・${edgeCount}区間収録）",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SlateText
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "完全オフライン動作。ダイヤ改正や運賃改定時は transit-data.json を読み込んで更新できます。",
                fontSize = 12.sp,
                color = SlateMuted
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onImportJson,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Text("JSONデータを読み込む", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateText)
                }
                if (isCustomDataset) {
                    OutlinedButton(
                        onClick = onResetDefault,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, SlateBorder)
                    ) {
                        Text("初期データに戻す", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun StationPickerSheetContent(
    title: String,
    filterStations: (String) -> List<Station>,
    onSelect: (Station) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) { filterStations(query) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp)
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = SlateText
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("駅名・かな・エリアで検索（例: 新宿、ろっぽんぎ）", fontSize = 13.sp) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "クリア")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("該当する駅が見つかりません", color = SlateMuted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filtered, key = { it.id }) { station ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(station) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = station.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateText
                            )
                            if (station.area.isNotEmpty()) {
                                Text(
                                    text = station.area,
                                    fontSize = 11.sp,
                                    color = SlateMuted
                                )
                            }
                        }
                        if (station.barrierFree.isNotEmpty()) {
                            Text(
                                text = "🛗",
                                fontSize = 14.sp,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                }
            }
        }
    }
}
