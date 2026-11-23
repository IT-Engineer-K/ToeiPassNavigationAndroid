package com.toeipass.navi.engine

data class FareTier(
    val maxKm: Double,
    val ic: Int,
    val ticket: Int
)

data class Operator(
    val id: String,
    val name: String,
    val isFreeWithToeiPass: Boolean,
    val badgeColor: String,
    val flatFare: Int? = null,
    val fareTable: List<FareTier> = emptyList()
)

data class TransitLine(
    val id: String,
    val name: String,
    val operatorId: String,
    val color: String,
    val code: String,
    val dirHint: String = ""
)

data class Station(
    val id: String,
    val name: String,
    val kana: String = "",
    val area: String = "",
    val barrierFree: String = ""
)

data class Edge(
    val from: String,
    val to: String,
    val lineId: String,
    val minutes: Int,
    val km: Double
)

data class TransitDataset(
    val updatedAt: String,
    val operators: Map<String, Operator>,
    val lines: Map<String, TransitLine>,
    val stations: List<Station>,
    val edges: List<Edge>
) {
    private val stationMap: Map<String, Station> = stations.associateBy { it.id }

    fun getStation(id: String): Station? = stationMap[id]

    fun getStationName(id: String): String = stationMap[id]?.name ?: id

    fun getStationBarrierFree(id: String): String = stationMap[id]?.barrierFree.orEmpty()

    fun filterStations(query: String): List<Station> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return stations
        return stations.filter { st ->
            st.name.lowercase().contains(q) ||
                st.kana.lowercase().contains(q) ||
                st.area.lowercase().contains(q)
        }
    }
}

enum class FareType {
    IC, TICKET
}

enum class TimeMode {
    DEPARTURE, ARRIVAL
}

enum class SearchMode(
    val badgeText: String,
    val badgeColorHex: String,
    val label: String
) {
    CHEAPEST("安", "#16A34A", "最安（都営無料区間を優先）"),
    FASTEST("早", "#EA580C", "最速（所要時間を優先）"),
    EASIEST("楽", "#0284C7", "乗換少（移動負担を軽減）"),
    BALANCED("標準", "#475569", "時間・運賃バランス")
}

data class SearchOptions(
    val useToeiFreePass: Boolean = true,
    val accessibleTransfer: Boolean = true,
    val includeToeiBus: Boolean = true,
    val fareType: FareType = FareType.IC,
    val timeMode: TimeMode = TimeMode.DEPARTURE,
    val baseTimeMinutes: Int = 9 * 60
)

data class StopPoint(
    val stationId: String,
    val minutesFromStart: Int
)

data class RouteSegment(
    val lineId: String,
    val lineName: String,
    val lineColor: String,
    val lineCode: String,
    val dirHint: String,
    val operatorId: String,
    val operatorName: String,
    val isToeiFree: Boolean,
    val fromStationId: String,
    val toStationId: String,
    val minutes: Int,
    val km: Double,
    val stops: List<StopPoint>,
    val fare: Int,
    val isThroughFare: Boolean,
    val fareLabel: String,
    val depTime: String,
    val arrTime: String,
    val transferWaitMinutes: Int? = null
)

data class RouteBadge(
    val text: String,
    val colorHex: String
)

data class RouteResult(
    val segments: List<RouteSegment>,
    val totalFare: Int,
    val rideMinutes: Int,
    val transferCount: Int,
    val transferMargin: Int,
    val totalMinutes: Int,
    val departTime: String,
    val arriveTime: String,
    val totalKm: Double,
    val signature: String,
    val label: String,
    val badges: List<RouteBadge>
)

data class PresetQuery(
    val fromStationId: String,
    val toStationId: String,
    val label: String
)

object DefaultPresets {
    const val HOME_STATION_ID = "musashi_koganei"
    const val DEFAULT_TO_STATION_ID = "roppongi"

    val list: List<PresetQuery> = listOf(
        PresetQuery("musashi_koganei", "roppongi", "武蔵小金井 → 六本木"),
        PresetQuery("musashi_koganei", "higashi_ginza", "武蔵小金井 → 東銀座"),
        PresetQuery("musashi_koganei", "asakusa", "武蔵小金井 → 浅草"),
        PresetQuery("musashi_koganei", "otemachi", "武蔵小金井 → 大手町"),
        PresetQuery("musashi_koganei", "daiba", "武蔵小金井 → お台場"),
        PresetQuery("musashi_koganei", "haneda_airport", "武蔵小金井 → 羽田空港")
    )
}
