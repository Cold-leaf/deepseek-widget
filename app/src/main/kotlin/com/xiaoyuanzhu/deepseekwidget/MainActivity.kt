package com.xiaoyuanzhu.deepseekwidget

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.app.Activity
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Declare inputs here so they're accessible from scope.launch below
        var keyInput: EditText? = null
        var cookieInput: EditText? = null

        val scrollView = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(16))
        }

        // Title
        root.addView(textView("DeepSeek 用量监控", 22, true, 0xFF1E1B4B.toInt()))

        root.addView(space(16))

        // API Key section
        root.addView(sectionCard {
            addView(row {
                addView(textView("API Key", 16, true, 0xFF4F46E5.toInt()))
            })
            addView(space(8))

            keyInput = EditText(this@MainActivity).apply {
                hint = "sk-..."
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(0xFFF1F5F9.toInt())
            }
            addView(keyInput)

            addView(space(8))
            addView(primaryButton("保存") {
                val key = keyInput?.text?.toString()?.trim() ?: ""
                scope.launch {
                    WidgetPrefs.saveApiKey(this@MainActivity, key)
                    DeepSeekWidgetWorker.schedule(this@MainActivity)
                    Toast.makeText(this@MainActivity, "API Key已保存", Toast.LENGTH_SHORT).show()
                }
            })
        })

        root.addView(space(16))

        // Dashboard Cookie section (optional, for usage stats)
        root.addView(sectionCard {
            addView(row {
                addView(textView("Dashboard Cookie（可选）", 16, true, 0xFF7C3AED.toInt()))
            })
            addView(space(8))
            addView(textView(
                "浏览器打开 platform.deepseek.com → 登录 → F12 → Application → Cookies → 复制整行，\n" +
                "格式: session_id=xxx; ... 或者直接粘贴完整Cookie字符串。\n" +
                "获取后可看到本月token用量和消费金额。Cookie会过期，需定期刷新。",
                12, false, 0xFF64748B.toInt()
            ))
            addView(space(8))

            cookieInput = EditText(this@MainActivity).apply {
                hint = "session_id=xxx; token=xxx; ..."
                inputType = InputType.TYPE_CLASS_TEXT
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(0xFFF1F5F9.toInt())
                setMinLines(2)
                setMaxLines(4)
            }
            addView(cookieInput)

            addView(space(8))
            addView(primaryButton("保存", 0xFF7C3AED.toInt()) {
                val cookie = cookieInput?.text?.toString()?.trim() ?: ""
                scope.launch {
                    WidgetPrefs.saveDashboardCookie(this@MainActivity, cookie)
                    Toast.makeText(this@MainActivity, "Cookie已保存", Toast.LENGTH_SHORT).show()
                }
            })
        })

        root.addView(space(16))

        // Balance card
        val balanceTitle = textView("当前余额", 18, true, 0xFFFFFFFF.toInt())
        val refreshBtn = Button(this).apply {
            text = "刷新"
            setTextColor(0xFF818CF8.toInt())
            setBackgroundColor(0x00000000)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val loadingBar = ProgressBar(this).apply { visibility = View.GONE }
        val balanceText = textView("", 32, true, 0xFFA5B4FC.toInt())
        val detailText = textView("", 14, false, 0xFF818CF8.toInt())
        val usageText = textView("", 14, false, 0xFFA78BFA.toInt())
        val errorText = textView("", 14, false, 0xFFFF6B6B.toInt())
        val timeText = textView("", 12, false, 0xFF6366F1.toInt())

        val balanceCard = sectionCard(0xFF1E1B4B.toInt()) {
            addView(row {
                addView(balanceTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(refreshBtn)
            })
            addView(loadingBar)
            addView(space(8))
            addView(balanceText)
            addView(detailText)
            addView(usageText)
            addView(errorText)
            addView(space(8))
            addView(timeText)
        }
        root.addView(balanceCard)

        root.addView(space(16))

        // Instructions
        root.addView(sectionCard(0xFFF1F5F9.toInt()) {
            addView(textView("如何使用桌面卡片", 16, true, 0xFF1E293B.toInt()))
            addView(space(8))
            addView(textView(
                "1. 返回桌面，长按空白区域\n" +
                "2. 选择「添加小部件」\n" +
                "3. 搜索或找到「DeepSeek用量卡片」\n" +
                "4. 拖放到桌面即可\n\n" +
                "卡片每30分钟自动刷新，点击卡片可打开此页面。",
                13, false, 0xFF64748B.toInt()
            ))
        })

        scrollView.addView(root)
        setContentView(scrollView)

        // Load saved data
        scope.launch {
            val savedKey = WidgetPrefs.getApiKey(this@MainActivity)
            keyInput?.setText(savedKey ?: "")
            val savedCookie = WidgetPrefs.getDashboardCookie(this@MainActivity)
            cookieInput?.setText(savedCookie ?: "")
            WidgetPrefs.usageFlow(this@MainActivity).collect {
                updateBalanceUI(balanceText, detailText, usageText, errorText, timeText, loadingBar, it)
            }
        }

        // Refresh button
        refreshBtn.setOnClickListener {
            scope.launch {
                val api = WidgetPrefs.getApiKey(this@MainActivity)
                val cookie = WidgetPrefs.getDashboardCookie(this@MainActivity)
                if (api.isNullOrBlank() && cookie.isNullOrBlank()) {
                    Toast.makeText(this@MainActivity, "请先设置API Key或Cookie", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                loadingBar.visibility = View.VISIBLE
                val result = withContext(Dispatchers.IO) {
                    val b = if (!api.isNullOrBlank()) {
                        DeepSeekApi.fetchBalance(api)
                    } else UsageSnapshot(lastUpdated = System.currentTimeMillis())
                    if (!cookie.isNullOrBlank()) {
                        val s = DeepSeekUsageApi.fetchAll(cookie)
                        WidgetPrefs.saveUsageStats(this@MainActivity, s)
                    }
                    b
                }
                WidgetPrefs.saveUsage(this@MainActivity, result)
                DeepSeekWidgetReceiver.updateWidget(this@MainActivity)
                loadingBar.visibility = View.GONE
                updateBalanceUI(balanceText, detailText, usageText, errorText, timeText, loadingBar, result)
            }
        }
    }

    private fun updateBalanceUI(
        balanceText: TextView,
        detailText: TextView,
        usageText: TextView,
        errorText: TextView,
        timeText: TextView,
        loadingBar: ProgressBar,
        usage: UsageSnapshot
    ) {
        loadingBar.visibility = View.GONE

        if (usage.error != null && usage.totalBalance == "0") {
            balanceText.text = "—"
            errorText.text = usage.error
            errorText.visibility = View.VISIBLE
            detailText.visibility = View.GONE
            usageText.visibility = View.GONE
        } else {
            balanceText.text = "${usage.totalBalance} ${usage.currency}"
            errorText.visibility = View.GONE
            if (usage.toppedUpBalance != "0" || usage.grantedBalance != "0") {
                detailText.text = "充值 ${usage.toppedUpBalance} ${usage.currency}  |  赠送 ${usage.grantedBalance} ${usage.currency}"
                detailText.visibility = View.VISIBLE
            } else {
                detailText.visibility = View.GONE
            }
            // Usage stats
            if (usage.monthlyTokens > 0) {
                val costStr = if (usage.monthlyCost > 0) "  |  ¥${"%.2f".format(usage.monthlyCost)}" else ""
                usageText.text = "本月 ${formatTokens(usage.monthlyTokens)} tokens$costStr"
                usageText.visibility = View.VISIBLE
            } else {
                usageText.visibility = View.GONE
            }
        }
        timeText.text = if (usage.lastUpdated > 0) {
            "最后更新: ${SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(usage.lastUpdated))}"
        } else {
            "等待首次加载..."
        }
    }

    private fun formatTokens(count: Long): String {
        return when {
            count >= 1_000_000 -> "${"%.1f".format(count / 1_000_000.0)}M"
            count >= 1_000 -> "${"%.1f".format(count / 1_000.0)}K"
            else -> count.toString()
        }
    }

    private fun space(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(h))
    }

    private fun textView(text: String, size: Int, bold: Boolean, color: Int) = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        if (bold) paintFlags = paintFlags or android.graphics.Paint.FAKE_BOLD_TEXT_FLAG
        setTextColor(color)
    }

    private fun row(block: LinearLayout.() -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        block()
    }

    private fun sectionCard(bgColor: Int = 0xFFF8FAFC.toInt(), block: LinearLayout.() -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bgColor)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        block()
    }

    private fun primaryButton(text: String, color: Int = 0xFF4F46E5.toInt(), onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(color)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.END
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
