package com.xiaoyuanzhu.deepseekwidget

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Internal DeepSeek dashboard API (session-token authenticated).
 * Token expires; extract from browser DevTools → Application → Cookies → "session_id" (or similar).
 * Pass the raw Cookie header string, e.g. "session_id=abc123; ..."
 */

data class UsageStats(
    val totalTokensMonth: Long = 0,
    val totalCostMonth: Double = 0.0,
    val topModel: String = "",
    val dailyAmounts: List<DailyAmount> = emptyList(),
    val fetched: Boolean = false,
    val error: String? = null
)

data class DailyAmount(
    val date: String,       // "2026-06-01"
    val tokens: Long,
    val cost: Double
)

object DeepSeekUsageApi {
    private const val BASE = "https://platform.deepseek.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun fetchAll(dashboardCookie: String): UsageStats {
        val now = Calendar.getInstance()
        val month = now.get(Calendar.MONTH) + 1
        val year = now.get(Calendar.YEAR)

        try {
            val summary = fetchUserSummary(dashboardCookie)
            val amounts = fetchUsageAmount(dashboardCookie, month, year)
            val costs = fetchUsageCost(dashboardCookie, month, year)

            val dailyCostMap = costs.associate { it.first to it.second }
            val dailyList = amounts.map { (date, tokens) ->
                DailyAmount(date, tokens, dailyCostMap[date] ?: 0.0)
            }.sortedBy { it.date }

            return UsageStats(
                totalTokensMonth = amounts.sumOf { it.second },
                totalCostMonth = costs.sumOf { it.second },
                topModel = summary.first,
                dailyAmounts = dailyList,
                fetched = true
            )
        } catch (e: Exception) {
            return UsageStats(
                fetched = true,
                error = e.message ?: "Unknown error fetching dashboard data"
            )
        }
    }

    // Returns Pair<topModelName, rawJsonString>
    private fun fetchUserSummary(cookie: String): Pair<String, String> {
        val body = get("$BASE/api/v0/users/get_user_summary", cookie)
            ?: return "" to ""
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonObject ?: return "" to ""
        // Best effort: look for model/most_used_model fields
        val model = data["model"]?.jsonPrimitive?.content
            ?: data["most_used_model"]?.jsonPrimitive?.content
            ?: data["top_model"]?.jsonPrimitive?.content ?: ""
        return model to body
    }

    // Returns list of Pair<dateString, tokenCount>
    private fun fetchUsageAmount(cookie: String, month: Int, year: Int): List<Pair<String, Long>> {
        val body = get("$BASE/api/v0/usage/amount?month=$month&year=$year", cookie)
            ?: return emptyList()
        return parseDataList(body) { entry ->
            val date = entry.jsonObject["date"]?.jsonPrimitive?.content
                ?: entry.jsonObject["day"]?.jsonPrimitive?.content
                ?: entry.jsonObject["time"]?.jsonPrimitive?.content
                ?: return@parseDataList null
            val tokens = entry.jsonObject["amount"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: entry.jsonObject["tokens"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: entry.jsonObject["total_tokens"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: return@parseDataList null
            date to tokens
        }
    }

    // Returns list of Pair<dateString, costDouble>
    private fun fetchUsageCost(cookie: String, month: Int, year: Int): List<Pair<String, Double>> {
        val body = get("$BASE/api/v0/usage/cost?month=$month&year=$year", cookie)
            ?: return emptyList()
        return parseDataList(body) { entry ->
            val date = entry.jsonObject["date"]?.jsonPrimitive?.content
                ?: entry.jsonObject["day"]?.jsonPrimitive?.content
                ?: return@parseDataList null
            val cost = entry.jsonObject["cost"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: entry.jsonObject["amount"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: return@parseDataList null
            date to cost
        }
    }

    private fun <T> parseDataList(rawJson: String, mapper: (kotlinx.serialization.json.JsonElement) -> T?): List<T> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val data = root["data"] ?: return emptyList()
        return when {
            data is kotlinx.serialization.json.JsonArray -> data.mapNotNull { mapper(it) }
            data.jsonObject["list"] != null -> data.jsonObject["list"]!!.jsonArray.mapNotNull { mapper(it) }
            data.jsonObject["items"] != null -> data.jsonObject["items"]!!.jsonArray.mapNotNull { mapper(it) }
            data.jsonObject["records"] != null -> data.jsonObject["records"]!!.jsonArray.mapNotNull { mapper(it) }
            else -> emptyList()
        }
    }

    private fun get(url: String, cookie: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .header("User-Agent", "Mozilla/5.0")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null
        return response.body?.string()
    }
}
