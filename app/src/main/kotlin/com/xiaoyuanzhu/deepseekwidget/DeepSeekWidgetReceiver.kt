package com.xiaoyuanzhu.deepseekwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.compose.ui.unit.sp
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeepSeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DeepSeekWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        DeepSeekWidgetWorker.schedule(context)
    }

    companion object {
        suspend fun updateWidget(context: Context) {
            DeepSeekWidget().updateAll(context)
        }
    }
}

class DeepSeekWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val usage = try {
            WidgetPrefs.usageFlow(context).first()
        } catch (e: Exception) {
            UsageSnapshot(error = e.message, lastUpdated = System.currentTimeMillis())
        }

        provideContent {
            GlanceTheme {
                WidgetContent(context, usage)
            }
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        // Worker keeps running; harmless if no widgets exist
    }
}

@Composable
private fun WidgetContent(context: Context, usage: UsageSnapshot) {
    Column(
        modifier = GlanceModifier
            .background(ColorProvider(R.color.widget_bg))
            .clickable {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
            .fillMaxWidth()
            .padding(R.dimen.glance_padding_16)
    ) {
        Text(
            text = "🧠 DeepSeek",
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ColorProvider(R.color.widget_title)
            ),
            modifier = GlanceModifier.padding(bottom = R.dimen.glance_spacer_4)
        )

        if (usage.error != null && usage.totalBalance == "0") {
            Text(
                text = "⚠ ${usage.error}",
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = ColorProvider(R.color.widget_error)
                )
            )
        } else if (usage.totalBalance != "0") {
            Text(
                text = "余额 ${usage.totalBalance} ${usage.currency}",
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(R.color.widget_text)
                ),
                modifier = GlanceModifier.padding(bottom = R.dimen.glance_spacer_4)
            )
            if (usage.toppedUpBalance != "0" || usage.grantedBalance != "0") {
                Text(
                    text = "充值 ${usage.toppedUpBalance}  赠送 ${usage.grantedBalance}",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = ColorProvider(R.color.widget_detail)
                    ),
                    modifier = GlanceModifier.padding(bottom = R.dimen.glance_spacer_4)
                )
            }
        }

        if (usage.monthlyTokens > 0) {
                Text(
                    text = "本月 ${formatTokens(usage.monthlyTokens)} tokens  ¥${"%.2f".format(usage.monthlyCost)}",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = ColorProvider(R.color.widget_usage)
                    ),
                    modifier = GlanceModifier.padding(bottom = R.dimen.glance_spacer_4)
                )
            }

        if (usage.monthlyTokens > 0) {
                val parts = mutableListOf<String>()
                if (usage.todayCacheHitTokens > 0) parts.add("命中 ${formatTokens(usage.todayCacheHitTokens)}")
                if (usage.todayCacheMissTokens > 0) parts.add("未命中 ${formatTokens(usage.todayCacheMissTokens)}")
                if (usage.todayResponseTokens > 0) parts.add("输出 ${formatTokens(usage.todayResponseTokens)}")
                val costStr = if (usage.todayCost > 0) "  ¥${"%.2f".format(usage.todayCost)}" else ""
                val detail = parts.joinToString("  ")
                Text(
                    text = if (detail.isNotEmpty() || costStr.isNotEmpty()) "今日 $detail$costStr" else "今日 0 tokens",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = ColorProvider(R.color.widget_detail)
                    ),
                    modifier = GlanceModifier.padding(bottom = R.dimen.glance_spacer_4)
                )
            }

        Text(
            text = if (usage.lastUpdated > 0) "更新于 ${formatTime(usage.lastUpdated)}" else "等待首次加载...",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = ColorProvider(R.color.widget_time)
            )
        )
    }
}

private fun formatTokens(count: Long): String {
    return when {
        count >= 1_000_000 -> "${"%.1f".format(count / 1_000_000.0)}M"
        count >= 1_000 -> "${"%.1f".format(count / 1_000.0)}K"
        else -> count.toString()
    }
}

private fun formatTime(epoch: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(epoch))
}
