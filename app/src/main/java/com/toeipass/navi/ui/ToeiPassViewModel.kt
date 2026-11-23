package com.toeipass.navi.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.toeipass.navi.engine.DefaultPresets
import com.toeipass.navi.engine.FareType
import com.toeipass.navi.engine.PresetQuery
import com.toeipass.navi.engine.RouteResult
import com.toeipass.navi.engine.SearchOptions
import com.toeipass.navi.engine.Station
import com.toeipass.navi.engine.TimeMode
import com.toeipass.navi.engine.TransitDataParser
import com.toeipass.navi.engine.TransitDataset
import com.toeipass.navi.engine.TransitRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class ToeiPassUiState(
    val isLoading: Boolean = true,
    val isSearching: Boolean = false,
    val dataset: TransitDataset? = null,
    val isCustomDataset: Boolean = false,
    val fromStationId: String = DefaultPresets.HOME_STATION_ID,
    val toStationId: String = DefaultPresets.DEFAULT_TO_STATION_ID,
    val timeHour: Int = 9,
    val timeMinute: Int = 0,
    val timeMode: TimeMode = TimeMode.DEPARTURE,
    val useToeiFreePass: Boolean = true,
    val accessibleTransfer: Boolean = true,
    val includeToeiBus: Boolean = true,
    val fareType: FareType = FareType.IC,
    val routes: List<RouteResult> = emptyList(),
    val activeRouteIndex: Int = 0,
    val statusMessage: String? = null
) {
    val fromStationName: String
        get() = dataset?.getStationName(fromStationId) ?: fromStationId

    val toStationName: String
        get() = dataset?.getStationName(toStationId) ?: toStationId

    val formattedTime: String
        get() = "%02d:%02d".format(timeHour, timeMinute)

    val activeRoute: RouteResult?
        get() = routes.getOrNull(activeRouteIndex) ?: routes.firstOrNull()
}

class ToeiPassViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ToeiPassUiState())
    val uiState: StateFlow<ToeiPassUiState> = _uiState.asStateFlow()

    private var router: TransitRouter? = null
    private var searchJob: Job? = null

    init {
        setCurrentTime(triggerSearch = false)
        loadDefaultDataset()
    }

    fun loadDefaultDataset() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            try {
                val jsonText = withContext(Dispatchers.IO) {
                    getApplication<Application>().assets
                        .open("transit-data.json")
                        .bufferedReader(Charsets.UTF_8)
                        .use { it.readText() }
                }
                val parsed = withContext(Dispatchers.Default) {
                    TransitDataParser.parse(jsonText)
                }
                router = TransitRouter(parsed)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        dataset = parsed,
                        isCustomDataset = false
                    )
                }
                runSearch()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "路線データの読み込みに失敗しました: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun importDatasetJson(jsonText: String) {
        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.Default) {
                    TransitDataParser.parse(jsonText)
                }
                if (parsed.stations.isEmpty() || parsed.edges.isEmpty()) {
                    _uiState.update { it.copy(statusMessage = "有効な駅・区間データが含まれていません。") }
                    return@launch
                }
                router = TransitRouter(parsed)
                _uiState.update {
                    it.copy(
                        dataset = parsed,
                        isCustomDataset = true,
                        statusMessage = "路線データを更新しました（全${parsed.stations.size}駅・${parsed.edges.size}区間）"
                    )
                }
                runSearch()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(statusMessage = "JSONファイルの形式が正しくありません。")
                }
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun selectFromStation(stationId: String) {
        _uiState.update { it.copy(fromStationId = stationId) }
        runSearch()
    }

    fun selectToStation(stationId: String) {
        _uiState.update { it.copy(toStationId = stationId) }
        runSearch()
    }

    fun setHomeAsDeparture() {
        _uiState.update { it.copy(fromStationId = DefaultPresets.HOME_STATION_ID) }
        runSearch()
    }

    fun swapStations() {
        _uiState.update {
            it.copy(
                fromStationId = it.toStationId,
                toStationId = it.fromStationId
            )
        }
        runSearch()
    }

    fun applyPreset(preset: PresetQuery) {
        _uiState.update {
            it.copy(
                fromStationId = preset.fromStationId,
                toStationId = preset.toStationId
            )
        }
        runSearch()
    }

    fun setTime(hour: Int, minute: Int) {
        _uiState.update { it.copy(timeHour = hour, timeMinute = minute) }
        runSearch()
    }

    fun setCurrentTime(triggerSearch: Boolean = true) {
        val cal = Calendar.getInstance()
        _uiState.update {
            it.copy(
                timeHour = cal.get(Calendar.HOUR_OF_DAY),
                timeMinute = cal.get(Calendar.MINUTE)
            )
        }
        if (triggerSearch) runSearch()
    }

    fun setTimeMode(mode: TimeMode) {
        _uiState.update { it.copy(timeMode = mode) }
        runSearch()
    }

    fun toggleToeiFreePass(enabled: Boolean) {
        _uiState.update { it.copy(useToeiFreePass = enabled) }
        runSearch()
    }

    fun toggleAccessibleTransfer(enabled: Boolean) {
        _uiState.update { it.copy(accessibleTransfer = enabled) }
        runSearch()
    }

    fun toggleIncludeToeiBus(enabled: Boolean) {
        _uiState.update { it.copy(includeToeiBus = enabled) }
        runSearch()
    }

    fun selectRouteTab(index: Int) {
        _uiState.update { it.copy(activeRouteIndex = index) }
    }

    fun filterStations(query: String): List<Station> {
        return _uiState.value.dataset?.filterStations(query) ?: emptyList()
    }

    fun runSearch() {
        val currentRouter = router ?: return
        val state = _uiState.value
        if (state.fromStationId == state.toStationId) {
            _uiState.update { it.copy(routes = emptyList(), activeRouteIndex = 0, isSearching = false) }
            return
        }

        val options = SearchOptions(
            useToeiFreePass = state.useToeiFreePass,
            accessibleTransfer = state.accessibleTransfer,
            includeToeiBus = state.includeToeiBus,
            fareType = state.fareType,
            timeMode = state.timeMode,
            baseTimeMinutes = state.timeHour * 60 + state.timeMinute
        )

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val results = withContext(Dispatchers.Default) {
                currentRouter.searchRoutes(state.fromStationId, state.toStationId, options)
            }
            _uiState.update {
                it.copy(
                    routes = results,
                    activeRouteIndex = 0,
                    isSearching = false
                )
            }
        }
    }
}
