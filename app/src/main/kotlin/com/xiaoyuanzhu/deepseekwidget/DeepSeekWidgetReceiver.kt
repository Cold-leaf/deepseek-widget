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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.unit.dp
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
            .fillMaxWidth()
            .background(ColorProvider(0xFF1E1B4B.toInt()))
            .cornerRadius(16.dp)
            .padding(16.dp)
            .clickable {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🧠 DeepSeek",
                style = TextStyle(
                    color = ColorProvider(0xFFFFFFFF.toInt()),
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            if (usage.error != null) {
                Text(
                    text = "⚠️",
                    style = TextStyle(color = ColorProvider(0xFFFFD700.toInt()))
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(12.dp))

        if (usage.error != null && usage.totalBalance == "0") {
            Text(
                text = usage.error,
                style = TextStyle(
                    color = ColorProvider(0xFFFF6B6B.toInt()),
                    fontWeight = FontWeight.Medium
                )
            )
        } else {
            Text(
                text = formatBalance(usage.totalBalance, usage.currency),
                style = TextStyle(
                    color = ColorProvider(0xFFA5B4FC.toInt()),
                    fontWeight = FontWeight.Bold
                )
            )
        }

        if (usage.toppedUpBalance != "0" || usage.grantedBalance != "0") {
            Spacer(modifier = GlanceModifier.height(4.dp))
            Row {
                Text(
                    text = "充值 ${usage.toppedUpBalance}",
                    style = TextStyle(
                        color = ColorProvider(0xFF818CF8.toInt()),
                        fontWeight = FontWeight.Normal
                    )
                )
                Spacer(modifier = GlanceModifier.width(12.dp))
                Text(
                    text = "赠送 ${usage.grantedBalance}",
                    style = TextStyle(
                        color = ColorProvider(0xFF818CF8.toInt()),
                        fontWeight = FontWeight.Normal
                    )
                )
            }
        }

        if (usage.monthlyTokens > 0) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            Row {
                Text(
                    text = "本月 ${formatTokens(usage.monthlyTokens)} tokens",
                    style = TextStyle(
                        color = ColorProvider(0xFFA78BFA.toInt()),
                        fontWeight = FontWeight.Medium
                    )
                )
                if (usage.monthlyCost > 0) {
                    Spacer(modifier = GlanceModifier.width(12.dp))
                    Text(
                        text = "¥${"%.2f".format(usage.monthlyCost)}",
                        style = TextStyle(
                            color = ColorProvider(0xFFA78BFA.toInt()),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        Text(
            text = if (usage.lastUpdated > 0) {
                "更新于 ${formatTime(usage.lastUpdated)}"
            } else {
                "等待首次加载..."
            },
            style = TextStyle(
                color = ColorProvider(0xFF6366F1.toInt()),
                fontWeight = FontWeight.Normal
            )
        )
    }
}

private fun formatBalance(balance: String, currency: String): String {
    if (balance == "0" || balance == "0.00") return "—"
    return "余额 $balance $currency"
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
