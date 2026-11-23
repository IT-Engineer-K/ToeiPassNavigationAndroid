package com.toeipass.navi.engine

import java.util.Locale
import java.util.PriorityQueue
import kotlin.math.roundToInt

/**
 * 都営交通無料乗車券（乗車証）専用 ルート検索・運賃・時刻・バリアフリー計算エンジン
 * Web版 router.js のアルゴリズムを純粋 Kotlin として実装。
 */
class TransitRouter(private val dataset: TransitDataset) {

    private data class Step(
        val from: String,
        val to: String,
        val lineId: String,
        val minutes: Int,
        val km: Double
    )

    private data class NeighborEdge(
        val to: String,
        val lineId: String,
        val minutes: Int,
        val km: Double
    )

    private data class QueueNode(
        val stationId: String,
        val steps: List<Step>,
        val visited: Set<String>,
        val score: Long
    ) : Comparable<QueueNode> {
        override fun compareTo(other: QueueNode): Int = score.compareTo(other.score)
    }

    private data class MutableSegment(
        val lineId: String,
        val lineName: String,
        val lineColor: String,
        val lineCode: String,
        val dirHint: String,
        val operatorId: String,
        val operatorName: String,
        val isToeiFree: Boolean,
        val fromStationId: String,
        var toStationId: String,
        var minutes: Int,
        var km: Double,
        val stops: MutableList<StopPoint>,
        var fare: Int = 0,
        var isThroughFare: Boolean = false,
        var fareLabel: String = "",
        var depTime: String = "",
        var arrTime: String = "",
        var transferWaitMinutes: Int? = null
    )

    private data class EvaluatedPath(
        val segments: List<RouteSegment>,
        val totalFare: Int,
        val rideMinutes: Int,
        val transferCount: Int,
        val transferMargin: Int,
        val totalMinutes: Int,
        val departTime: String,
        val arriveTime: String,
        val totalKm: Double
    )

    fun calculateOperatorFare(operatorId: String, km: Double, options: SearchOptions): Int {
        val op = dataset.operators[operatorId] ?: return 0

        if (op.isFreeWithToeiPass && options.useToeiFreePass) {
            return 0
        }

        if (operatorId == "TOEI") {
            return when {
                km <= 4.0 -> 180
                km <= 9.0 -> 220
                km <= 15.0 -> 270
                km <= 21.0 -> 320
                else -> 380
            }
        }

        if (operatorId == "TOEI_BUS" || operatorId == "KEIO_BUS") {
            return op.flatFare ?: 210
        }

        if (op.fareTable.isNotEmpty()) {
            val row = op.fareTable.firstOrNull { km <= it.maxKm } ?: op.fareTable.last()
            return if (options.fareType == FareType.TICKET) row.ticket else row.ic
        }

        return 0
    }

    fun formatMinutesToTime(totalMins: Int): String {
        val normalized = ((totalMins % 1440) + 1440) % 1440
        val hh = normalized / 60
        val mm = normalized % 60
        return String.format(Locale.US, "%02d:%02d", hh, mm)
    }

    private fun round1(value: Double): Double = (value * 10.0).roundToInt() / 10.0

    private fun evaluatePath(steps: List<Step>, options: SearchOptions): EvaluatedPath? {
        if (steps.isEmpty()) return null

        val segments = ArrayList<MutableSegment>()
        for (step in steps) {
            val lineObj = dataset.lines[step.lineId] ?: return null
            val opObj = dataset.operators[lineObj.operatorId] ?: return null
            val lastSeg = segments.lastOrNull()
            if (lastSeg != null && lastSeg.lineId == step.lineId) {
                lastSeg.toStationId = step.to
                lastSeg.minutes += step.minutes
                lastSeg.km = round1(lastSeg.km + step.km)
                lastSeg.stops.add(StopPoint(step.to, lastSeg.minutes))
            } else {
                segments.add(
                    MutableSegment(
                        lineId = step.lineId,
                        lineName = lineObj.name,
                        lineColor = lineObj.color,
                        lineCode = lineObj.code,
                        dirHint = lineObj.dirHint,
                        operatorId = lineObj.operatorId,
                        operatorName = opObj.name,
                        isToeiFree = opObj.isFreeWithToeiPass,
                        fromStationId = step.from,
                        toStationId = step.to,
                        minutes = step.minutes,
                        km = round1(step.km),
                        stops = mutableListOf(
                            StopPoint(step.from, 0),
                            StopPoint(step.to, step.minutes)
                        )
                    )
                )
            }
        }

        var totalFare = 0
        var i = 0
        while (i < segments.size) {
            val currentOp = segments[i].operatorId
            val opObj = dataset.operators[currentOp]
            val isFreePassApplied = (opObj?.isFreeWithToeiPass == true) && options.useToeiFreePass

            if (currentOp == "TOEI_BUS" || currentOp == "KEIO_BUS") {
                val fare = calculateOperatorFare(currentOp, segments[i].km, options)
                segments[i].fare = fare
                segments[i].fareLabel = if (isFreePassApplied) {
                    "都営無料乗車券"
                } else {
                    opObj?.name ?: currentOp
                }
                totalFare += fare
                i++
            } else {
                var combinedKm = segments[i].km
                var j = i + 1
                while (j < segments.size && segments[j].operatorId == currentOp) {
                    combinedKm += segments[j].km
                    j++
                }
                val fare = calculateOperatorFare(currentOp, combinedKm, options)
                segments[i].fare = fare
                segments[i].fareLabel = if (isFreePassApplied) {
                    "都営無料乗車券"
                } else {
                    String.format(Locale.US, "%s (%.1fkm)", opObj?.name ?: currentOp, combinedKm)
                }

                for (k in (i + 1) until j) {
                    segments[k].fare = 0
                    segments[k].isThroughFare = true
                    segments[k].fareLabel = if (isFreePassApplied) {
                        "都営無料乗車券"
                    } else {
                        "${opObj?.name ?: currentOp} 乗継通算"
                    }
                }
                totalFare += fare
                i = j
            }
        }

        val transferMargin = if (options.accessibleTransfer) 9 else 5
        val rideMinutes = segments.sumOf { it.minutes }
        val transferCount = maxOf(0, segments.size - 1)
        val totalMinutes = rideMinutes + transferCount * transferMargin

        val baseMins = options.baseTimeMinutes
        val isArrivalMode = options.timeMode == TimeMode.ARRIVAL
        val departTotalMins = if (isArrivalMode) baseMins - totalMinutes else baseMins
        val arriveTotalMins = if (isArrivalMode) baseMins else baseMins + totalMinutes

        var cursorMins = departTotalMins
        for (idx in segments.indices) {
            val seg = segments[idx]
            seg.depTime = formatMinutesToTime(cursorMins)
            cursorMins += seg.minutes
            seg.arrTime = formatMinutesToTime(cursorMins)
            if (idx < segments.lastIndex) {
                seg.transferWaitMinutes = transferMargin
                cursorMins += transferMargin
            }
        }

        val immutableSegments = segments.map { s ->
            RouteSegment(
                lineId = s.lineId,
                lineName = s.lineName,
                lineColor = s.lineColor,
                lineCode = s.lineCode,
                dirHint = s.dirHint,
                operatorId = s.operatorId,
                operatorName = s.operatorName,
                isToeiFree = s.isToeiFree,
                fromStationId = s.fromStationId,
                toStationId = s.toStationId,
                minutes = s.minutes,
                km = s.km,
                stops = s.stops.toList(),
                fare = s.fare,
                isThroughFare = s.isThroughFare,
                fareLabel = s.fareLabel,
                depTime = s.depTime,
                arrTime = s.arrTime,
                transferWaitMinutes = s.transferWaitMinutes
            )
        }

        return EvaluatedPath(
            segments = immutableSegments,
            totalFare = totalFare,
            rideMinutes = rideMinutes,
            transferCount = transferCount,
            transferMargin = transferMargin,
            totalMinutes = totalMinutes,
            departTime = formatMinutesToTime(departTotalMins),
            arriveTime = formatMinutesToTime(arriveTotalMins),
            totalKm = round1(segments.sumOf { it.km })
        )
    }

    private fun buildAdjacencyList(includeToeiBus: Boolean): Map<String, List<NeighborEdge>> {
        val adj = HashMap<String, MutableList<NeighborEdge>>(dataset.stations.size)
        for (st in dataset.stations) {
            adj[st.id] = ArrayList()
        }
        for (edge in dataset.edges) {
            val line = dataset.lines[edge.lineId] ?: continue
            if (!includeToeiBus && line.operatorId == "TOEI_BUS") {
                continue
            }
            val fromList = adj[edge.from]
            val toList = adj[edge.to]
            if (fromList != null && toList != null) {
                fromList.add(NeighborEdge(edge.to, edge.lineId, edge.minutes, edge.km))
                toList.add(NeighborEdge(edge.from, edge.lineId, edge.minutes, edge.km))
            }
        }
        return adj
    }

    private fun findBestRouteByMode(
        fromId: String,
        toId: String,
        options: SearchOptions,
        mode: SearchMode,
        adj: Map<String, List<NeighborEdge>>
    ): EvaluatedPath? {
        val queue = PriorityQueue<QueueNode>()
        queue.add(
            QueueNode(
                stationId = fromId,
                steps = emptyList(),
                visited = setOf(fromId),
                score = 0L
            )
        )

        val bestScoreAtState = HashMap<String, Long>()
        var bestResult: EvaluatedPath? = null
        var bestFinalScore = Long.MAX_VALUE

        while (queue.isNotEmpty()) {
            val current = queue.poll() ?: break
            if (current.score >= bestFinalScore) continue

            if (current.stationId == toId) {
                val evaluated = evaluatePath(current.steps, options)
                if (evaluated != null) {
                    bestFinalScore = current.score
                    bestResult = evaluated
                }
                continue
            }

            if (current.steps.size >= 42) continue

            val neighbors = adj[current.stationId] ?: continue
            for (edge in neighbors) {
                if (edge.to in current.visited) continue

                val nextSteps = ArrayList<Step>(current.steps.size + 1)
                nextSteps.addAll(current.steps)
                nextSteps.add(Step(current.stationId, edge.to, edge.lineId, edge.minutes, edge.km))

                val evaluated = evaluatePath(nextSteps, options) ?: continue
                val score = when (mode) {
                    SearchMode.CHEAPEST ->
                        evaluated.totalFare * 1000L + evaluated.totalMinutes * 10L + evaluated.transferCount * 20L
                    SearchMode.FASTEST ->
                        evaluated.totalMinutes * 1000L + evaluated.totalFare * 2L + evaluated.transferCount * 40L
                    SearchMode.EASIEST ->
                        evaluated.transferCount * 15000L + evaluated.totalMinutes * 80L + evaluated.totalFare * 5L
                    SearchMode.BALANCED ->
                        evaluated.totalFare.toLong() + evaluated.totalMinutes * 10L + evaluated.transferCount * 45L
                }

                val stateKey = "${edge.to}__${edge.lineId}"
                val prevBest = bestScoreAtState[stateKey]
                if (prevBest != null && prevBest <= score) {
                    continue
                }
                bestScoreAtState[stateKey] = score

                val nextVisited = HashSet<String>(current.visited.size + 1)
                nextVisited.addAll(current.visited)
                nextVisited.add(edge.to)

                queue.add(
                    QueueNode(
                        stationId = edge.to,
                        steps = nextSteps,
                        visited = nextVisited,
                        score = score
                    )
                )
            }
        }

        return bestResult
    }

    fun searchRoutes(
        fromId: String,
        toId: String,
        options: SearchOptions = SearchOptions()
    ): List<RouteResult> {
        if (fromId.isBlank() || toId.isBlank() || fromId == toId) return emptyList()

        val adj = buildAdjacencyList(options.includeToeiBus)
        val results = ArrayList<RouteResult>()

        for (mode in SearchMode.values()) {
            val evaluated = findBestRouteByMode(fromId, toId, options, mode, adj) ?: continue
            val signature = evaluated.segments.joinToString("|") {
                "${it.lineId}:${it.fromStationId}->${it.toStationId}"
            }

            val existingIdx = results.indexOfFirst { it.signature == signature }
            if (existingIdx >= 0) {
                val existing = results[existingIdx]
                if (existing.badges.none { it.text == mode.badgeText }) {
                    results[existingIdx] = existing.copy(
                        badges = existing.badges + RouteBadge(mode.badgeText, mode.badgeColorHex)
                    )
                }
            } else {
                results.add(
                    RouteResult(
                        segments = evaluated.segments,
                        totalFare = evaluated.totalFare,
                        rideMinutes = evaluated.rideMinutes,
                        transferCount = evaluated.transferCount,
                        transferMargin = evaluated.transferMargin,
                        totalMinutes = evaluated.totalMinutes,
                        departTime = evaluated.departTime,
                        arriveTime = evaluated.arriveTime,
                        totalKm = evaluated.totalKm,
                        signature = signature,
                        label = mode.label,
                        badges = listOf(RouteBadge(mode.badgeText, mode.badgeColorHex))
                    )
                )
            }
        }

        return results
    }
}
