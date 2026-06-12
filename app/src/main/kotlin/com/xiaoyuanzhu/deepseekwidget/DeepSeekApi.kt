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
    val tokenError: String? = null,
    val monthlyTokens: Long = 0,
    val monthlyCost: Double = 0.0,
    val todayTokens: Long = 0,
    val todayCacheHitTokens: Long = 0,
    val todayCacheMissTokens: Long = 0,
    val todayResponseTokens: Long = 0,
    val todayCost: Double = 0.0,
    val todayModels: List<TodayModelBreakdown> = emptyList()
)

// Shared formatting — both widget and app preview use these

enum class SegmentRole { LABEL, VALUE, ACCENT, MODEL, ERROR }

data class TextSegment(val text: String, val role: SegmentRole)

fun formatTokens(count: Long): String = when {
    count >= 1_000_000 -> "${"%.1f".format(count / 1_000_000.0)}M"
    count >= 1_000 -> "${"%.1f".format(count / 1_000.0)}K"
    else -> count.toString()
}

fun UsageSnapshot.balanceSegments(): List<TextSegment> = listOf(
    TextSegment("余额 ", SegmentRole.LABEL),
    TextSegment(totalBalance, SegmentRole.VALUE),
    TextSegment(" $currency", SegmentRole.ACCENT)
)

fun UsageSnapshot.detailSegments(): List<TextSegment> {
    val segments = mutableListOf<TextSegment>()
    if (toppedUpBalance != "0" && toppedUpBalance != totalBalance) {
        segments.add(TextSegment("充值 ", SegmentRole.LABEL))
        segments.add(TextSegment(toppedUpBalance, SegmentRole.VALUE))
    }
    if (grantedBalance != "0") {
        if (segments.isNotEmpty()) segments.add(TextSegment("  ", SegmentRole.ACCENT))
        segments.add(TextSegment("赠送 ", SegmentRole.LABEL))
        segments.add(TextSegment(grantedBalance, SegmentRole.VALUE))
    }
    return segments
}

fun UsageSnapshot.monthlySegments(): List<TextSegment> {
    val segments = mutableListOf(
        TextSegment("本月 ", SegmentRole.LABEL),
        TextSegment(formatTokens(monthlyTokens), SegmentRole.VALUE),
        TextSegment(" tokens", SegmentRole.ACCENT)
    )
    if (monthlyCost > 0) {
        segments.add(TextSegment("  ", SegmentRole.ACCENT))
        segments.add(TextSegment("¥${"%.2f".format(monthlyCost)}", SegmentRole.ACCENT))
    }
    return segments
}

fun UsageSnapshot.todaySegments(): List<List<TextSegment>> {
    if (todayModels.isEmpty()) {
        val parts = mutableListOf<TextSegment>()
        if (todayCacheHitTokens > 0) {
            parts.add(TextSegment("命中 ", SegmentRole.LABEL))
            parts.add(TextSegment(formatTokens(todayCacheHitTokens), SegmentRole.VALUE))
        }
        if (todayCacheMissTokens > 0) {
            if (parts.isNotEmpty()) parts.add(TextSegment("  ", SegmentRole.ACCENT))
            parts.add(TextSegment("未命中 ", SegmentRole.LABEL))
            parts.add(TextSegment(formatTokens(todayCacheMissTokens), SegmentRole.VALUE))
        }
        if (todayResponseTokens > 0) {
            if (parts.isNotEmpty()) parts.add(TextSegment("  ", SegmentRole.ACCENT))
            parts.add(TextSegment("输出 ", SegmentRole.LABEL))
            parts.add(TextSegment(formatTokens(todayResponseTokens), SegmentRole.VALUE))
        }
        if (todayCost > 0) {
            if (parts.isNotEmpty()) parts.add(TextSegment("  ", SegmentRole.ACCENT))
            parts.add(TextSegment("¥${"%.2f".format(todayCost)}", SegmentRole.ACCENT))
        }
        if (parts.isEmpty()) return emptyList()
        return listOf(listOf(TextSegment("今日 ", SegmentRole.LABEL)) + parts)
    }
    val lines = mutableListOf<List<TextSegment>>()
    lines.add(listOf(TextSegment("今日", SegmentRole.LABEL)))
    for (m in todayModels.filter { it.totalTokens > 0 }) {
        val segs = mutableListOf(TextSegment("  ${m.model}", SegmentRole.MODEL))
        if (m.cacheHitTokens > 0) {
            segs.add(TextSegment("  命中 ", SegmentRole.LABEL))
            segs.add(TextSegment(formatTokens(m.cacheHitTokens), SegmentRole.VALUE))
        }
        if (m.cacheMissTokens > 0) {
            segs.add(TextSegment("  未命中 ", SegmentRole.LABEL))
            segs.add(TextSegment(formatTokens(m.cacheMissTokens), SegmentRole.VALUE))
        }
        if (m.responseTokens > 0) {
            segs.add(TextSegment("  输出 ", SegmentRole.LABEL))
            segs.add(TextSegment(formatTokens(m.responseTokens), SegmentRole.VALUE))
        }
        if (m.cost > 0) {
            segs.add(TextSegment("  ", SegmentRole.ACCENT))
            segs.add(TextSegment("¥${"%.2f".format(m.cost)}", SegmentRole.ACCENT))
        }
        lines.add(segs)
    }
    return lines
}

fun UsageSnapshot.timeSegments(): List<TextSegment> = listOf(
    TextSegment(
        if (lastUpdated > 0) "更新于 ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(lastUpdated))}"
        else "等待首次加载...",
        SegmentRole.LABEL
    )
)

fun UsageSnapshot.errorSegments(): List<TextSegment> = listOf(
    TextSegment("⚠ ${error ?: ""}", SegmentRole.ERROR)
)

fun roleColorRes(role: SegmentRole): Int = when (role) {
    SegmentRole.LABEL -> R.color.widget_label
    SegmentRole.VALUE -> R.color.widget_value
    SegmentRole.ACCENT -> R.color.widget_usage
    SegmentRole.MODEL -> R.color.widget_title
    SegmentRole.ERROR -> R.color.widget_error
}

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
