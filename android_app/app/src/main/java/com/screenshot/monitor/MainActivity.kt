package com.screenshot.monitor

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.screenshot.monitor.api.ApiService
import com.screenshot.monitor.widget.ScreenshotWidgetProvider
import com.screenshot.monitor.worker.UpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var editPcIp: EditText
    private lateinit var btnSave: Button
    private lateinit var textStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editPcIp = findViewById(R.id.edit_pc_ip)
        btnSave = findViewById(R.id.btn_save)
        textStatus = findViewById(R.id.text_status)

        // 加载保存的 IP
        val sharedPref = getSharedPreferences("settings", MODE_PRIVATE)
        val savedIp = sharedPref.getString("pc_ip", "")
        editPcIp.setText(savedIp)

        // 如果已有 IP 配置，自动获取状态
        if (!savedIp.isNullOrEmpty()) {
            fetchAndDisplayStatus(savedIp)
        } else {
            textStatus.text = "请输入 PC IP 地址并保存"
        }

        btnSave.setOnClickListener {
            val pcIp = editPcIp.text.toString().trim()
            if (pcIp.isNotEmpty()) {
                // 保存 IP
                sharedPref.edit().putString("pc_ip", pcIp).apply()

                // 调度定时任务
                schedulePeriodicUpdate()

                // 立即更新 widget
                updateWidget()

                // 获取并显示状态
                fetchAndDisplayStatus(pcIp)
            } else {
                textStatus.text = "请输入 PC IP 地址"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次返回应用时，都使用已保存的 IP 刷新状态
        val sharedPref = getSharedPreferences("settings", MODE_PRIVATE)
        val savedIp = sharedPref.getString("pc_ip", "")
        if (!savedIp.isNullOrEmpty()) {
            fetchAndDisplayStatus(savedIp)
        }
    }

    private fun schedulePeriodicUpdate() {
        // 计算到下一个 10 分的延迟时间
        val calendar = Calendar.getInstance()
        val currentMinute = calendar.get(Calendar.MINUTE)

        // 计算初始延迟
        val initialDelayMinutes = if (currentMinute < 10) {
            10 - currentMinute
        } else {
            60 - currentMinute + 10
        }

        // 创建每小时执行一次的定期任务
        val updateRequest = PeriodicWorkRequestBuilder<UpdateWorker>(
            1, TimeUnit.HOURS,  // 每小时执行一次
            15, TimeUnit.MINUTES  // 灵活窗口期 15 分钟
        )
            .setInitialDelay(initialDelayMinutes.toLong(), TimeUnit.MINUTES)
            .build()

        // 使用 REPLACE 策略，如果已存在则替换
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "screenshot_update",
            ExistingPeriodicWorkPolicy.UPDATE,
            updateRequest
        )
    }

    private fun updateWidget() {
        val intent = android.content.Intent(this, ScreenshotWidgetProvider::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE

        val ids = AppWidgetManager.getInstance(application)
            .getAppWidgetIds(ComponentName(application, ScreenshotWidgetProvider::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)

        sendBroadcast(intent)
    }

    private fun fetchAndDisplayStatus(pcIp: String) {
        // 显示加载中
        textStatus.text = "正在连接服务器...\n服务器: $pcIp"

        // 在后台线程执行网络请求
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiService = ApiService(this@MainActivity)
                val response = apiService.getStatus(pcIp)

                // 获取当前时间
                val currentTime = Calendar.getInstance()
                val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val timeString = timeFormat.format(currentTime.time)

                // 在主线程更新 UI
                withContext(Dispatchers.Main) {
                    if (response != null) {
                        val statusText = when (response.status) {
                            "has" -> "✅ 有已整理的截图文件夹"
                            "none" -> "⭕ 无已整理的截图文件夹"
                            else -> "未知状态: ${response.status}"
                        }

                        textStatus.text = """
                            |设置已保存！
                            |
                            |服务器: $pcIp
                            |状态: $statusText
                            |PC有: ${response.totalCount} 个文件
                            |服务器消息: ${response.message}
                            |服务器时间: ${response.timestamp}
                            |
                            |本地时间: $timeString
                            |Widget 将在每小时 10 分自动检查状态
                        """.trimMargin()

                        // 成功获取状态后，立即更新 Widget
                        updateWidget()
                    } else {
                        textStatus.text = """
                            |❌ 连接失败
                            |
                            |服务器: $pcIp
                            |错误: 无法连接到服务器
                            |
                            |请检查：
                            |1. PC 服务器是否运行 (python web_server.py)
                            |2. PC 和手机是否在同一 WiFi
                            |3. PC 防火墙是否允许端口 5001
                            |4. IP 地址是否正确
                        """.trimMargin()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    textStatus.text = """
                        |❌ 连接异常
                        |
                        |服务器: $pcIp
                        |错误: ${e.message ?: "未知错误"}
                        |
                        |请检查网络连接和服务器状态
                    """.trimMargin()
                }
            }
        }
    }
}
