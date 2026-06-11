package com.xiaoyuanzhu.deepseekwidget

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
data class BalanceInfo(
    val currency: String,
    @SerialName("total_balance") val totalBalance: String,
    @SerialName("topped_up_balance") val toppedUpBalance: String,
    @SerialName("granted_balance") val grantedBalance: String
)

@Serializable
data class BalanceResponse(
    @SerialName("is_available") val isAvailable: Boolean,
    @SerialName("balance_infos") val balanceInfos: List<BalanceInfo>
)

data class UsageSnapshot(
    val totalBalance: String = "0",
    val grantedBalance: String = "0",
    val toppedUpBalance: String = "0",
    val currency: String = "—",
    val lastUpdated: Long = 0L,
    val error: String? = null,
    val monthlyTokens: Long = 0,
    val monthlyCost: Double = 0.0,
    val todayTokens: Long = 0,
    val todayCacheHitTokens: Long = 0,
    val todayCacheMissTokens: Long = 0,
    val todayResponseTokens: Long = 0,
    val todayCost: Double = 0.0
)

// Shared formatting — both widget and app preview use these

fun formatTokens(count: Long): String = when {
    count >= 1_000_000 -> "${"%.1f".format(count / 1_000_000.0)}M"
    count >= 1_000 -> "${"%.1f".format(count / 1_000.0)}K"
    else -> count.toString()
}

fun UsageSnapshot.balanceDisplay(): String = "余额 $totalBalance $currency"

fun UsageSnapshot.detailDisplay(): String = "充值 $toppedUpBalance  赠送 $grantedBalance"

fun UsageSnapshot.monthlyDisplay(): String {
    val costStr = if (monthlyCost > 0) "  ¥${"%.2f".format(monthlyCost)}" else ""
    return "本月 ${formatTokens(monthlyTokens)} tokens$costStr"
}

fun UsageSnapshot.todayDisplay(): String {
    val parts = mutableListOf<String>()
    if (todayCacheHitTokens > 0) parts.add("命中 ${formatTokens(todayCacheHitTokens)}")
    if (todayCacheMissTokens > 0) parts.add("未命中 ${formatTokens(todayCacheMissTokens)}")
    if (todayResponseTokens > 0) parts.add("输出 ${formatTokens(todayResponseTokens)}")
    val costStr = if (todayCost > 0) "  ¥${"%.2f".format(todayCost)}" else ""
    val detail = parts.joinToString("  ")
    return if (detail.isNotEmpty() || costStr.isNotEmpty()) "今日 $detail$costStr" else "今日 0 tokens"
}

fun UsageSnapshot.timeDisplay(): String =
    if (lastUpdated > 0) "更新于 ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(lastUpdated))}"
    else "等待首次加载..."

fun UsageSnapshot.errorDisplay(): String = "⚠ $error"

object DeepSeekApi {
    private const val BASE_URL = "https://api.deepseek.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun fetchBalance(apiKey: String): UsageSnapshot {
        val now = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url("$BASE_URL/user/balance")
                .header("Authorization", "Bearer $apiKey")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return UsageSnapshot(
                totalBalance = "0",
                grantedBalance = "0",
                toppedUpBalance = "0",
                currency = "—",
                lastUpdated = now,
                error = "Empty response (HTTP ${response.code})"
            )

            if (!response.isSuccessful) {
                return UsageSnapshot(
                    totalBalance = "0",
                    grantedBalance = "0",
                    toppedUpBalance = "0",
                    currency = "—",
                    lastUpdated = now,
                    error = "HTTP ${response.code}: ${body.take(100)}"
                )
            }

            val parsed = json.decodeFromString<BalanceResponse>(body)
            if (!parsed.isAvailable || parsed.balanceInfos.isEmpty()) {
                return UsageSnapshot(
                    totalBalance = "0",
                    grantedBalance = "0",
                    toppedUpBalance = "0",
                    currency = "—",
                    lastUpdated = now,
                    error = "Balance unavailable"
                )
            }

            val info = parsed.balanceInfos.first()
            return UsageSnapshot(
                totalBalance = info.totalBalance,
                grantedBalance = info.grantedBalance,
                toppedUpBalance = info.toppedUpBalance,
                currency = info.currency,
                lastUpdated = now
            )
        } catch (e: Exception) {
            return UsageSnapshot(
                totalBalance = "0",
                grantedBalance = "0",
                toppedUpBalance = "0",
                currency = "—",
                lastUpdated = now,
                error = e.message ?: "Unknown error"
            )
        }
    }
}
