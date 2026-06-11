package com.xiaoyuanzhu.deepseekwidget

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Calendar
import java.util.concurrent.TimeUnit

@Serializable
data class UsageStats(
    val totalTokensMonth: Long = 0,
    val totalCostMonth: Double = 0.0,
    val topModel: String = "",
    val dailyAmounts: List<DailyAmount> = emptyList(),
    val fetched: Boolean = false,
    val error: String? = null,
    val todayTokens: Long = 0,
    val todayCacheHitTokens: Long = 0,
    val todayCacheMissTokens: Long = 0,
    val todayResponseTokens: Long = 0,
    val todayCost: Double = 0.0
)

@Serializable
data class DailyAmount(
    val date: String,
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

    fun fetchAll(usageToken: String): UsageStats {
        val now = Calendar.getInstance()
        val month = now.get(Calendar.MONTH) + 1
        val year = now.get(Calendar.YEAR)

        return try {
            val amountBody = get("$BASE/api/v0/usage/amount?month=$month&year=$year", usageToken)
            val costBody = get("$BASE/api/v0/usage/cost?month=$month&year=$year", usageToken)

            if (amountBody == null && costBody == null) {
                return UsageStats(fetched = true, error = "Token 无效或 API 不可达")
            }

            val amountBiz = amountBody?.let { body ->
                json.parseToJsonElement(body).jsonObject["data"]
                    ?.jsonObject?.get("biz_data")?.jsonObject
            }
            val costBiz = costBody?.let { body ->
                json.parseToJsonElement(body).jsonObject["data"]
                    ?.jsonObject?.get("biz_data")?.jsonArray?.firstOrNull()?.jsonObject
            }

            // Total tokens: sum all usage[*].amount across all models in total[]
            val totalTokens = amountBiz?.sumUsageTokens("total") ?: 0L
            val totalCost = costBiz?.sumUsageCost("total") ?: 0.0

            // Top model: model with highest total token usage
            val topModel = amountBiz?.get("total")?.jsonArray?.maxByOrNull { modelEntry ->
                sumModelTokens(modelEntry)
            }?.jsonObject?.get("model")?.jsonPrimitive?.content ?: ""

            // Daily breakdown
            val amountDays = amountBiz?.get("days")?.jsonArray ?: JsonArray(emptyList())
            val costDaysMap = costBiz?.get("days")?.jsonArray?.associate { dayEntry ->
                val obj = dayEntry.jsonObject
                val date = obj["date"]?.jsonPrimitive?.content ?: ""
                date to obj.sumUsageCost("data")
            } ?: emptyMap()

            val dailyList = amountDays.mapNotNull { dayEntry ->
                val obj = dayEntry.jsonObject
                val date = obj["date"]?.jsonPrimitive?.content ?: return@mapNotNull null
                DailyAmount(date, obj.sumUsageTokens("data"), costDaysMap[date] ?: 0.0)
            }.sortedBy { it.date }

            // Today's breakdown
            val todayStr = String.format("%d-%02d-%02d", year, month, now.get(Calendar.DAY_OF_MONTH))
            val todayEntry = amountDays.firstOrNull {
                it.jsonObject["date"]?.jsonPrimitive?.content == todayStr
            }
            val todayCacheHit = todayEntry?.jsonObject?.sumTypeTokens("data", "PROMPT_CACHE_HIT_TOKEN") ?: 0L
            val todayCacheMiss = todayEntry?.jsonObject?.sumTypeTokens("data", "PROMPT_CACHE_MISS_TOKEN") ?: 0L
            val todayPrompt = todayEntry?.jsonObject?.sumTypeTokens("data", "PROMPT_TOKEN") ?: 0L
            val todayResponse = todayEntry?.jsonObject?.sumTypeTokens("data", "RESPONSE_TOKEN") ?: 0L
            val todayTotalTokens = todayCacheHit + todayCacheMiss + todayPrompt + todayResponse
            val todayCostVal = costDaysMap[todayStr] ?: 0.0

            UsageStats(
                totalTokensMonth = totalTokens,
                totalCostMonth = totalCost,
                topModel = topModel,
                dailyAmounts = dailyList,
                fetched = true,
                todayTokens = todayTotalTokens,
                todayCacheHitTokens = todayCacheHit,
                todayCacheMissTokens = todayCacheMiss,
                todayResponseTokens = todayResponse,
                todayCost = todayCostVal
            )
        } catch (e: Exception) {
            UsageStats(fetched = true, error = e.message ?: "Unknown error")
        }
    }

    // Sum token amounts from array field (e.g. "total" or "data") across all models
    private fun kotlinx.serialization.json.JsonObject.sumUsageTokens(field: String): Long {
        return get(field)?.jsonArray?.sumOf { sumModelTokens(it) } ?: 0L
    }

    // Sum cost amounts (Double) from array field
    private fun kotlinx.serialization.json.JsonObject.sumUsageCost(field: String): Double {
        return get(field)?.jsonArray?.sumOf { modelEntry ->
            modelEntry.jsonObject["usage"]?.jsonArray?.sumOf { usageEntry ->
                usageEntry.jsonObject["amount"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            } ?: 0.0
        } ?: 0.0
    }

    // Sum token amounts for a single model entry
    private fun sumModelTokens(modelEntry: JsonElement): Long {
        return modelEntry.jsonObject["usage"]?.jsonArray?.sumOf { usageEntry ->
            usageEntry.jsonObject["amount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        } ?: 0L
    }

    // Sum token amounts for a specific usage type (e.g. "PROMPT_CACHE_HIT_TOKEN") from an array field
    private fun kotlinx.serialization.json.JsonObject.sumTypeTokens(field: String, type: String): Long {
        return get(field)?.jsonArray?.sumOf { modelEntry ->
            modelEntry.jsonObject["usage"]?.jsonArray?.sumOf { usageEntry ->
                val obj = usageEntry.jsonObject
                if (obj["type"]?.jsonPrimitive?.content == type) {
                    obj["amount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                } else 0L
            } ?: 0L
        } ?: 0L
    }

    private fun get(url: String, token: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "Mozilla/5.0")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null
        return response.body?.string()
    }
}
