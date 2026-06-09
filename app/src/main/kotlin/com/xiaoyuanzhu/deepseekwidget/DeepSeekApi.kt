package com.xiaoyuanzhu.deepseekwidget

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
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
    val totalBalance: String,
    val grantedBalance: String,
    val toppedUpBalance: String,
    val currency: String,
    val lastUpdated: Long,
    val error: String? = null,
    val monthlyTokens: Long = 0,
    val monthlyCost: Double = 0.0
)

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
