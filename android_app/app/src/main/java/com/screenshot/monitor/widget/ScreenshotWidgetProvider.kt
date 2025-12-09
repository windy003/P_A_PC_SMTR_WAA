package com.screenshot.monitor.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import android.view.View
import android.util.Log
import com.screenshot.monitor.R
import com.screenshot.monitor.api.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.Spanned

class ScreenshotWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets, Android SDK: ${android.os.Build.VERSION.SDK_INT}")
        // 对每个 widget 实例进行更新
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        private const val TAG = "ScreenshotWidget"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            Log.d(TAG, "updateAppWidget called for widget ID: $appWidgetId, Android SDK: ${android.os.Build.VERSION.SDK_INT}")

            // 在协程中执行网络请求
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 使用 applicationContext 确保在所有 Android 版本上都能正确访问 SharedPreferences
                    val appContext = context.applicationContext
                    Log.d(TAG, "Using context: ${appContext.javaClass.simpleName}")

                    // 获取保存的 PC IP
                    val sharedPref = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    val pcIp = sharedPref.getString("pc_ip", "") ?: ""

                    Log.d(TAG, "Retrieved PC IP from SharedPreferences: '$pcIp'")

                    if (pcIp.isEmpty()) {
                        // 如果没有设置 IP，显示提示
                        Log.w(TAG, "PC IP is empty, showing no config widget")
                        withContext(Dispatchers.Main) {
                            showNoConfigWidget(appContext, appWidgetManager, appWidgetId)
                        }
                        return@launch
                    }

                    // 请求 API
                    Log.d(TAG, "Starting API request to: $pcIp")
                    val apiService = ApiService(appContext)
                    val response = apiService.getStatus(pcIp)
                    Log.d(TAG, "API response received: ${response?.status ?: "null"}")

                    // 获取当前时间
                    val currentTime = Calendar.getInstance()
                    val dateFormat = SimpleDateFormat("M-d", Locale.getDefault())
                    val timeFormat = SimpleDateFormat("H:mm", Locale.getDefault())
                    val timeString = "${dateFormat.format(currentTime.time)}\n${timeFormat.format(currentTime.time)}"
                    // 两行时间格式，用于显示检查时间
                    val twoLineTime = "${dateFormat.format(currentTime.time)}\n${timeFormat.format(currentTime.time)}"

                    // 获取设备配置
                    val deviceConfig = DeviceConfig.getDeviceConfig()
                    Log.d(TAG, "设备配置: ${DeviceConfig.getDeviceInfo()}")

                    // 更新 widget UI
                    withContext(Dispatchers.Main) {
                        val views = RemoteViews(context.packageName, R.layout.widget_screenshot)

                        // 设置点击事件 - 打开主界面
                        val intent = android.content.Intent(context, com.screenshot.monitor.MainActivity::class.java)
                        val pendingIntent = android.app.PendingIntent.getActivity(
                            context,
                            0,
                            intent,
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                        if (response != null) {
                            // 成功获取到响应：显示 "PC X个 + 日期 + 时间"
                            views.setViewVisibility(R.id.widget_status_image, View.GONE)
                            views.setViewVisibility(R.id.widget_time_display_layout, View.VISIBLE)

                            // 显示 "PC" + 数量 + "个"
                            views.setViewVisibility(R.id.widget_pc_text, View.VISIBLE)
                            views.setTextViewText(R.id.widget_pc_text, "PC")
                            views.setTextViewText(R.id.widget_date_text, "${response.totalCount}")
                            views.setTextViewText(R.id.widget_hour_text, "个")

                            // 设置文字颜色: PC和"个"为绿色，数量为红色
                            views.setTextColor(R.id.widget_pc_text, 0xFF00AA00.toInt())      // 绿色
                            views.setTextColor(R.id.widget_date_text, 0xFFFF0000.toInt())    // 红色
                            views.setTextColor(R.id.widget_hour_text, 0xFF00AA00.toInt())    // 绿色

                            // 应用设备配置的字体大小
                            views.setTextViewTextSize(R.id.widget_pc_text, android.util.TypedValue.COMPLEX_UNIT_SP, deviceConfig.pcTextSize)
                            views.setTextViewTextSize(R.id.widget_date_text, android.util.TypedValue.COMPLEX_UNIT_SP, deviceConfig.dateTextSize)
                            views.setTextViewTextSize(R.id.widget_hour_text, android.util.TypedValue.COMPLEX_UNIT_SP, deviceConfig.hourTextSize)

                            // 隐藏分钟文本（不需要显示）
                            views.setViewVisibility(R.id.widget_minute_text, View.GONE)

                            // 显示检查时间（两行格式：日期换行时间），小时部分为红色
                            views.setViewVisibility(R.id.widget_update_time, View.VISIBLE)

                            // 创建 SpannableString，将小时部分设置为红色
                            val spannableTime = SpannableString(twoLineTime)
                            val newlineIndex = twoLineTime.indexOf('\n')
                            val colonIndex = twoLineTime.indexOf(':', newlineIndex)
                            if (newlineIndex != -1 && colonIndex != -1) {
                                // 小时部分是从换行符后到冒号前
                                spannableTime.setSpan(
                                    ForegroundColorSpan(0xFFFF0000.toInt()),
                                    newlineIndex + 1,
                                    colonIndex,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                            views.setTextViewText(R.id.widget_update_time, spannableTime)
                            views.setTextViewTextSize(R.id.widget_update_time, android.util.TypedValue.COMPLEX_UNIT_SP, deviceConfig.updateTimeSize)
                        } else {
                            // 无法获取响应（连接失败等）：显示当前时间
                            views.setViewVisibility(R.id.widget_status_image, View.GONE)
                            views.setViewVisibility(R.id.widget_time_display_layout, View.VISIBLE)

                            // Format individual time components
                            val datePart = SimpleDateFormat("M-d", Locale.getDefault()).format(currentTime.time)
                            val hourPart = SimpleDateFormat("H", Locale.getDefault()).format(currentTime.time)
                            val minutePart = SimpleDateFormat(":mm", Locale.getDefault()).format(currentTime.time)

                            views.setViewVisibility(R.id.widget_pc_text, View.VISIBLE)
                            views.setTextViewText(R.id.widget_pc_text, "PC")
                            views.setTextViewText(R.id.widget_date_text, datePart)
                            views.setTextViewText(R.id.widget_hour_text, hourPart)
                            views.setViewVisibility(R.id.widget_minute_text, View.VISIBLE)
                            views.setTextViewText(R.id.widget_minute_text, minutePart)
                            views.setViewVisibility(R.id.widget_update_time, View.VISIBLE)
                            views.setTextViewText(R.id.widget_update_time, "上次检查时间")

                            // 应用设备配置的字体大小
                            views.setTextViewTextSize(R.id.widget_pc_text, android.util.TypedValue.COMPLEX_UNIT_SP, deviceConfig.pcTextSize)
                            views.setTextViewTextSize(R.id.widget_date_text, android.util.TypedValue.COMPLEX_UNIT_SP, deviceConfig.dateTextSize)
                            views.setTextViewTextSize(R.id.widget_hour_text, android.util.TypedValue.COMPLEX_UNIT_SP, deviceConfig.hourTextSize)
                            views.setTextViewTextSize(R.id.widget_minute_text, android.util.TypedValue.COMPLEX_UNIT_SP, deviceConfig.minuteTextSize)
                            views.setTextViewTextSize(R.id.widget_update_time, android.util.TypedValue.COMPLEX_UNIT_SP, deviceConfig.updateTimeSize)
                        }

                        // 保存最后检查时间
                        sharedPref.edit().putString("last_check_time", timeString).apply()

                        // 更新 widget
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error updating widget on Android ${android.os.Build.VERSION.SDK_INT}: ${e.message}", e)
                    e.printStackTrace()
                    // 出错时显示错误信息
                    withContext(Dispatchers.Main) {
                        val appContext = context.applicationContext
                        showErrorWidget(appContext, appWidgetManager, appWidgetId, e.message)
                    }
                }
            }
        }

        private fun showNoConfigWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_screenshot)

            // 设置点击事件 - 打开主界面
            val intent = android.content.Intent(context, com.screenshot.monitor.MainActivity::class.java)
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            views.setViewVisibility(R.id.widget_status_image, View.GONE)
            views.setViewVisibility(R.id.widget_time_display_layout, View.VISIBLE)
            views.setViewVisibility(R.id.widget_pc_text, View.GONE)
            views.setTextViewText(R.id.widget_date_text, "未配置") // Use date text for main message
            views.setTextViewText(R.id.widget_hour_text, "") // Clear hour
            views.setTextViewText(R.id.widget_minute_text, "") // Clear minute
            views.setTextViewText(R.id.widget_update_time, "请打开应用设置 IP")
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun showErrorWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            errorMessage: String?
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_screenshot)

            // 设置点击事件 - 打开主界面
            val intent = android.content.Intent(context, com.screenshot.monitor.MainActivity::class.java)
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            views.setViewVisibility(R.id.widget_status_image, View.GONE)
            views.setViewVisibility(R.id.widget_time_display_layout, View.VISIBLE)
            views.setViewVisibility(R.id.widget_pc_text, View.GONE)
            views.setTextViewText(R.id.widget_date_text, "连接失败") // Use date text for main message
            views.setTextViewText(R.id.widget_hour_text, "") // Clear hour
            views.setTextViewText(R.id.widget_minute_text, "") // Clear minute
            views.setTextViewText(R.id.widget_update_time, errorMessage ?: "请检查网络")
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
