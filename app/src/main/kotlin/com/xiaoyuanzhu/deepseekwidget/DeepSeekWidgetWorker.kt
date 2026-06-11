package com.xiaoyuanzhu.deepseekwidget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class DeepSeekWidgetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        WidgetPrefs.clearError(applicationContext)

        val apiKey = WidgetPrefs.getApiKey(applicationContext)
        val usageToken = WidgetPrefs.getUsageToken(applicationContext)

        if (apiKey.isNullOrBlank() && usageToken.isNullOrBlank()) {
            WidgetPrefs.saveUsage(
                applicationContext,
                UsageSnapshot(error = "未设置API Key或用法Token", lastUpdated = System.currentTimeMillis())
            )
            return Result.failure()
        }

        var balanceResult: UsageSnapshot? = null
        var usageResult: UsageStats? = null
        var tokenError: String? = null

        withContext(Dispatchers.IO) {
            val balanceJob = if (!apiKey.isNullOrBlank()) {
                async { DeepSeekApi.fetchBalance(apiKey) }
            } else null
            val usageJob = if (!usageToken.isNullOrBlank()) {
                async { DeepSeekUsageApi.fetchAll(usageToken) }
            } else null

            balanceResult = balanceJob?.await()
            usageResult = usageJob?.await()
        }

        // Determine token status
        if (usageToken.isNullOrBlank()) {
            tokenError = "未填写用法Token"
        } else if (usageResult != null) {
            if (usageResult.error != null) {
                tokenError = usageResult.error
            } else if (usageResult.totalTokensMonth == 0L && usageResult.fetched) {
                tokenError = "Token 无效或已过期"
            }
        }

        WidgetPrefs.saveTokenError(applicationContext, tokenError)

        val usage = balanceResult ?: UsageSnapshot(lastUpdated = System.currentTimeMillis())
        WidgetPrefs.saveUsage(applicationContext, usage)

        if (usageResult != null) {
            WidgetPrefs.saveUsageStats(applicationContext, usageResult)
        }

        DeepSeekWidgetReceiver.updateWidget(applicationContext)

        return if (usage.error != null) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "deepseek_widget_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DeepSeekWidgetWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
