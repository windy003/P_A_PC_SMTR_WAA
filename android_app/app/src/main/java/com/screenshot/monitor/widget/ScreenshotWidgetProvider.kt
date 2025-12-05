package com.screenshot.monitor.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import android.view.View
import com.screenshot.monitor.R
import com.screenshot.monitor.api.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ScreenshotWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 对每个 widget 实例进行更新
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            // 在协程中执行网络请求
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 获取保存的 PC IP
                    val sharedPref = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    val pcIp = sharedPref.getString("pc_ip", "") ?: ""

                    if (pcIp.isEmpty()) {
                        // 如果没有设置 IP，显示提示
                        withContext(Dispatchers.Main) {
                            showNoConfigWidget(context, appWidgetManager, appWidgetId)
                        }
                        return@launch
                    }

                    // 请求 API
                    val apiService = ApiService(context)
                    val response = apiService.getStatus(pcIp)

                    // 获取当前时间 - 分两行显示
                    val currentTime = Calendar.getInstance()
                    val dateFormat = SimpleDateFormat("M-d", Locale.getDefault())
                    val timeFormat = SimpleDateFormat("H:mm", Locale.getDefault())
                    val timeString = "${dateFormat.format(currentTime.time)}\n${timeFormat.format(currentTime.time)}"

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

                        if (response != null && response.status == "has") {
                            // 状态为"有"：显示图片
                            views.setViewVisibility(R.id.widget_status_image, View.VISIBLE)
                            views.setViewVisibility(R.id.widget_time_display_layout, View.GONE)

                            // 加载 has.png 图片
                            try {
                                val assetManager = context.assets
                                val inputStream = assetManager.open("has.png")
                                val bitmap = BitmapFactory.decodeStream(inputStream)
                                views.setImageViewBitmap(R.id.widget_status_image, bitmap)
                                inputStream.close()
                            } catch (e: Exception) {
                                // 如果加载失败，使用默认图标
                                views.setImageViewResource(R.id.widget_status_image, R.drawable.ic_launcher)
                            }

                            views.setTextViewText(R.id.widget_update_time, "最后检查: $timeString")
                        } else {
                                                    // 状态为"无"：显示时间
                                                    views.setViewVisibility(R.id.widget_status_image, View.GONE)
                                                    views.setViewVisibility(R.id.widget_time_display_layout, View.VISIBLE) // Use new layout ID
                            
                                                    // Format individual time components
                                                    val datePart = SimpleDateFormat("M-d", Locale.getDefault()).format(currentTime.time)
                                                    val hourPart = SimpleDateFormat("H", Locale.getDefault()).format(currentTime.time)
                                                    val minutePart = SimpleDateFormat(":mm", Locale.getDefault()).format(currentTime.time) // Include colon with minutes
                            
                                                    views.setTextViewText(R.id.widget_date_text, datePart)
                                                    views.setTextViewText(R.id.widget_hour_text, hourPart)
                                                    views.setTextViewText(R.id.widget_minute_text, minutePart)
                                                    views.setTextViewText(R.id.widget_update_time, "上次检查时间")                        }

                        // 保存最后检查时间
                        sharedPref.edit().putString("last_check_time", timeString).apply()

                        // 更新 widget
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    // 出错时显示错误信息
                    withContext(Dispatchers.Main) {
                        showErrorWidget(context, appWidgetManager, appWidgetId, e.message)
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
            views.setTextViewText(R.id.widget_date_text, "连接失败") // Use date text for main message
            views.setTextViewText(R.id.widget_hour_text, "") // Clear hour
            views.setTextViewText(R.id.widget_minute_text, "") // Clear minute
            views.setTextViewText(R.id.widget_update_time, errorMessage ?: "请检查网络")
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
