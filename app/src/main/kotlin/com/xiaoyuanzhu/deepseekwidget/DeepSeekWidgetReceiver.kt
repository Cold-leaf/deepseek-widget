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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
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
    val lines = buildList {
        add("🧠 DeepSeek")
        if (usage.error != null && usage.totalBalance == "0") {
            add("⚠ ${usage.error}")
        } else {
            add("余额 ${usage.totalBalance} ${usage.currency}")
        }
        if (usage.toppedUpBalance != "0" || usage.grantedBalance != "0") {
            add("充值 ${usage.toppedUpBalance}  赠送 ${usage.grantedBalance}")
        }
        if (usage.monthlyTokens > 0) {
            add("本月 ${formatTokens(usage.monthlyTokens)} tokens  ¥${"%.2f".format(usage.monthlyCost)}")
        }
        add(if (usage.lastUpdated > 0) "更新于 ${formatTime(usage.lastUpdated)}" else "等待首次加载...")
    }

    Text(
        text = lines.joinToString("\n"),
        style = TextStyle(
            fontSize = TextUnit(14f, TextUnitType.SP),
            fontWeight = FontWeight.Normal
        ),
        modifier = GlanceModifier
            .clickable {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
            .fillMaxWidth()
            .padding(R.dimen.glance_padding_16)
    )
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
