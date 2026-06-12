package com.xiaoyuanzhu.deepseekwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Row
import androidx.glance.layout.size
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
            .cornerRadius(R.dimen.glance_radius_16)
            .clickable {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
            .fillMaxWidth()
            .padding(R.dimen.glance_padding_16)
    ) {
        Row(
            modifier = GlanceModifier.padding(bottom = R.dimen.glance_spacer_4)
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_deepseek_whale),
                contentDescription = "DeepSeek",
                modifier = GlanceModifier.size(R.dimen.glance_icon_20)
            )
            Text(
                text = "DeepSeek",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(R.color.widget_title)
                ),
                modifier = GlanceModifier.padding(start = R.dimen.glance_spacer_6)
            )
        }

        if (usage.monthlyTokens > 0) {
            // Token is working — show only usage info
            SegmentedRow(
                segments = usage.monthlySegments(),
                fontSize = 13,
                bottomPadding = R.dimen.glance_spacer_4
            )
            val todayLines = usage.todaySegments()
            if (todayLines.isNotEmpty()) {
                for ((idx, line) in todayLines.withIndex()) {
                    SegmentedRow(
                        segments = line,
                        fontSize = 10,
                        bottomPadding = if (idx == todayLines.lastIndex) R.dimen.glance_spacer_4 else null
                    )
                }
            }
        } else if (usage.error != null && usage.totalBalance == "0") {
            SegmentedRow(
                segments = usage.errorSegments(),
                fontSize = 13
            )
        } else if (usage.totalBalance != "0") {
            // Balance available, token not working
            SegmentedRow(
                segments = usage.balanceSegments(),
                fontSize = 28,
                fontWeight = FontWeight.Bold,
                bottomPadding = R.dimen.glance_spacer_4
            )
            val detail = usage.detailSegments()
            if (detail.isNotEmpty()) {
                SegmentedRow(
                    segments = detail,
                    fontSize = 13,
                    bottomPadding = R.dimen.glance_spacer_4
                )
            }
            if (usage.tokenError != null) {
                SegmentedRow(
                    segments = listOf(TextSegment("⚠ ${usage.tokenError}", SegmentRole.ERROR)),
                    fontSize = 13
                )
            }
        }

        SegmentedRow(
            segments = usage.timeSegments(),
            fontSize = 11
        )
    }
}

@Composable
private fun SegmentedRow(
    segments: List<TextSegment>,
    fontSize: Int,
    fontWeight: FontWeight = FontWeight.Normal,
    bottomPadding: Int? = null
) {
    if (segments.isEmpty()) return
    val modifier = if (bottomPadding != null) {
        GlanceModifier.padding(bottom = bottomPadding)
    } else GlanceModifier
    Row(modifier = modifier) {
        segments.forEach { seg ->
            Text(
                text = seg.text,
                style = TextStyle(
                    fontSize = fontSize.sp,
                    fontWeight = fontWeight,
                    color = ColorProvider(roleColorRes(seg.role))
                )
            )
        }
    }
}
