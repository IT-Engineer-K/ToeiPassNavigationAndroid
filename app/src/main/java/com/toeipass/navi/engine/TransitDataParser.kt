package com.toeipass.navi.engine

import org.json.JSONArray
import org.json.JSONObject

object TransitDataParser {

    fun parse(jsonText: String): TransitDataset {
        val root = JSONObject(jsonText)
        val updatedAt = root.optString("updatedAt", "")

        val operatorsObj = root.getJSONObject("operators")
        val operators = LinkedHashMap<String, Operator>()
        val opKeys = operatorsObj.keys()
        while (opKeys.hasNext()) {
            val key = opKeys.next()
            val obj = operatorsObj.getJSONObject(key)
            val tiers = mutableListOf<FareTier>()
            val fareArray: JSONArray? = obj.optJSONArray("fareTable")
            if (fareArray != null) {
                for (i in 0 until fareArray.length()) {
                    val row = fareArray.getJSONObject(i)
                    tiers.add(
                        FareTier(
                            maxKm = row.getDouble("maxKm"),
                            ic = row.getInt("ic"),
                            ticket = row.getInt("ticket")
                        )
                    )
                }
            }
            operators[key] = Operator(
                id = obj.optString("id", key),
                name = obj.getString("name"),
                isFreeWithToeiPass = obj.optBoolean("isFreeWithToeiPass", false),
                badgeColor = obj.optString("badgeColor", "#475569"),
                flatFare = if (obj.has("flatFare") && !obj.isNull("flatFare")) obj.getInt("flatFare") else null,
                fareTable = tiers
            )
        }

        val linesObj = root.getJSONObject("lines")
        val lines = LinkedHashMap<String, TransitLine>()
        val lineKeys = linesObj.keys()
        while (lineKeys.hasNext()) {
            val key = lineKeys.next()
            val obj = linesObj.getJSONObject(key)
            lines[key] = TransitLine(
                id = obj.optString("id", key),
                name = obj.getString("name"),
                operatorId = obj.getString("operator"),
                color = obj.optString("color", "#64748B"),
                code = obj.optString("code", ""),
                dirHint = obj.optString("dirHint", "")
            )
        }

        val stationsArray = root.getJSONArray("stations")
        val stations = ArrayList<Station>(stationsArray.length())
        for (i in 0 until stationsArray.length()) {
            val obj = stationsArray.getJSONObject(i)
            stations.add(
                Station(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    kana = obj.optString("kana", ""),
                    area = obj.optString("area", ""),
                    barrierFree = obj.optString("barrierFree", "")
                )
            )
        }

        val edgesArray = root.getJSONArray("edges")
        val edges = ArrayList<Edge>(edgesArray.length())
        for (i in 0 until edgesArray.length()) {
            val obj = edgesArray.getJSONObject(i)
            edges.add(
                Edge(
                    from = obj.getString("from"),
                    to = obj.getString("to"),
                    lineId = obj.getString("line"),
                    minutes = obj.getInt("minutes"),
                    km = obj.getDouble("km")
                )
            )
        }

        return TransitDataset(
            updatedAt = updatedAt,
            operators = operators,
            lines = lines,
            stations = stations,
            edges = edges
        )
    }
}
