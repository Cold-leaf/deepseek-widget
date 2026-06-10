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
        val dashboardCookie = WidgetPrefs.getDashboardCookie(applicationContext)

        if (apiKey.isNullOrBlank() && dashboardCookie.isNullOrBlank()) {
            WidgetPrefs.saveUsage(
                applicationContext,
                UsageSnapshot(error = "未设置API Key或Cookie", lastUpdated = System.currentTimeMillis())
            )
            return Result.failure()
        }

        var balanceResult: UsageSnapshot? = null
        var usageResult: UsageStats? = null

        withContext(Dispatchers.IO) {
            val balanceJob = if (!apiKey.isNullOrBlank()) {
                async { DeepSeekApi.fetchBalance(apiKey) }
            } else null
            val usageJob = if (!dashboardCookie.isNullOrBlank()) {
                async { DeepSeekUsageApi.fetchAll(dashboardCookie) }
            } else null

            balanceResult = balanceJob?.await()
            usageResult = usageJob?.await()
        }

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
                30, TimeUnit.MINUTES
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
