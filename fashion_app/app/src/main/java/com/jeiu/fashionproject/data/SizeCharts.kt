package com.jeiu.fashionproject.data

import com.jeiu.fashionproject.recommend.GarmentSizeRow
import org.json.JSONObject

/** Existing server size_charts rows. No values are generated when the server has no chart data. */
object SizeCharts {
    suspend fun load(): List<GarmentSizeRow>? = BackendApi.getJson("/api/size-charts")?.let { body ->
        runCatching { parse(body) }.getOrNull()
    }

    fun parse(body: String): List<GarmentSizeRow> {
        val rows = JSONObject(body).getJSONArray("rows")
        return List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            val shoulder = if (row.isNull("shoulderCm")) null else
                row.optDouble("shoulderCm").toFloat().takeIf { it.isFinite() && it > 0f }
            GarmentSizeRow(
                brand = row.optString("brand", ""),
                category = row.optString("category", ""),
                sizeLabel = row.optString("sizeLabel", ""),
                shoulderCm = shoulder,
                measureType = row.optString("measureType", ""),
                sourceUrl = row.optString("sourceUrl", "")
            )
        }
    }
}
