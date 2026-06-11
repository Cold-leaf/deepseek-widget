package com.xiaoyuanzhu.deepseekwidget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "deepseek_widget")

object WidgetPrefs {
    private val KEY_API_KEY = stringPreferencesKey("api_key")
    private val KEY_USAGE_TOKEN = stringPreferencesKey("usage_token")

    private val KEY_TOTAL_BALANCE = stringPreferencesKey("total_balance")
    private val KEY_GRANTED_BALANCE = stringPreferencesKey("granted_balance")
    private val KEY_TOPPED_UP_BALANCE = stringPreferencesKey("topped_up_balance")
    private val KEY_CURRENCY = stringPreferencesKey("currency")
    private val KEY_LAST_UPDATED = longPreferencesKey("last_updated")
    private val KEY_ERROR = stringPreferencesKey("error")

    private val KEY_USAGE_TOKENS = longPreferencesKey("usage_tokens_month")
    private val KEY_USAGE_COST = stringPreferencesKey("usage_cost_month")
    private val KEY_USAGE_DAYS = stringPreferencesKey("usage_days") // JSON
    private val KEY_TODAY_TOKENS = longPreferencesKey("today_tokens")
    private val KEY_TODAY_CACHE_HIT = longPreferencesKey("today_cache_hit")
    private val KEY_TODAY_CACHE_MISS = longPreferencesKey("today_cache_miss")
    private val KEY_TODAY_RESPONSE = longPreferencesKey("today_response")
    private val KEY_TODAY_COST = stringPreferencesKey("today_cost")

    private val json = Json

    // ---- API Key ----

    suspend fun saveApiKey(context: Context, apiKey: String) {
        context.dataStore.edit { it[KEY_API_KEY] = apiKey }
    }

    suspend fun getApiKey(context: Context): String? {
        return context.dataStore.data.first()[KEY_API_KEY]
    }

    // ---- Usage token (Bearer) ----

    suspend fun saveUsageToken(context: Context, token: String) {
        context.dataStore.edit { it[KEY_USAGE_TOKEN] = token }
    }

    suspend fun getUsageToken(context: Context): String? {
        return context.dataStore.data.first()[KEY_USAGE_TOKEN]
    }

    // ---- Balance (from API key) ----

    suspend fun saveUsage(context: Context, usage: UsageSnapshot) {
        context.dataStore.edit {
            it[KEY_TOTAL_BALANCE] = usage.totalBalance
            it[KEY_GRANTED_BALANCE] = usage.grantedBalance
            it[KEY_TOPPED_UP_BALANCE] = usage.toppedUpBalance
            it[KEY_CURRENCY] = usage.currency
            it[KEY_LAST_UPDATED] = usage.lastUpdated
            if (usage.error != null) it[KEY_ERROR] = usage.error
        }
    }

    // ---- Clear error ----

    suspend fun clearError(context: Context) {
        context.dataStore.edit { it.remove(KEY_ERROR) }
    }

    // ---- Usage stats (from dashboard cookie) ----

    suspend fun saveUsageStats(context: Context, stats: UsageStats) {
        context.dataStore.edit {
            it[KEY_USAGE_TOKENS] = stats.totalTokensMonth
            it[KEY_USAGE_COST] = stats.totalCostMonth.toString()
            if (stats.dailyAmounts.isNotEmpty()) {
                it[KEY_USAGE_DAYS] = json.encodeToString(stats.dailyAmounts)
            }
            it[KEY_TODAY_TOKENS] = stats.todayTokens
            it[KEY_TODAY_CACHE_HIT] = stats.todayCacheHitTokens
            it[KEY_TODAY_CACHE_MISS] = stats.todayCacheMissTokens
            it[KEY_TODAY_RESPONSE] = stats.todayResponseTokens
            it[KEY_TODAY_COST] = stats.todayCost.toString()
            if (stats.error != null) it[KEY_ERROR] = stats.error
        }
    }

    // ---- Combined flow for widget / UI ----

    fun usageFlow(context: Context): Flow<UsageSnapshot> {
        return context.dataStore.data.map { prefs ->
            UsageSnapshot(
                totalBalance = prefs[KEY_TOTAL_BALANCE] ?: "0",
                grantedBalance = prefs[KEY_GRANTED_BALANCE] ?: "0",
                toppedUpBalance = prefs[KEY_TOPPED_UP_BALANCE] ?: "0",
                currency = prefs[KEY_CURRENCY] ?: "—",
                lastUpdated = prefs[KEY_LAST_UPDATED] ?: 0L,
                error = prefs[KEY_ERROR],
                monthlyTokens = prefs[KEY_USAGE_TOKENS] ?: 0L,
                monthlyCost = prefs[KEY_USAGE_COST]?.toDoubleOrNull() ?: 0.0,
                todayTokens = prefs[KEY_TODAY_TOKENS] ?: 0L,
                todayCacheHitTokens = prefs[KEY_TODAY_CACHE_HIT] ?: 0L,
                todayCacheMissTokens = prefs[KEY_TODAY_CACHE_MISS] ?: 0L,
                todayResponseTokens = prefs[KEY_TODAY_RESPONSE] ?: 0L,
                todayCost = prefs[KEY_TODAY_COST]?.toDoubleOrNull() ?: 0.0
            )
        }
    }
}
