package com.screenshot.monitor.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
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

    companion object {
        private const val TAG = "ScreenshotWidget"
        private const val ACTION_UPDATE_WIDGET = "com.screenshot.monitor.UPDATE_WIDGET"

        // 字号不再由代码计算：布局里两行文字都启用了 autoSizeTextType="uniform"，
        // 系统会把文字自动缩放到刚好填满各自按权重分配到的高度框，保证完整显示不溢出。

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            Log.d(TAG, "updateAppWidget called for widget ID: $appWidgetId, Android SDK: ${android.os.Build.VERSION.SDK_INT}")

            val appContext = context.applicationContext
            val sharedPref = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val pcIp = sharedPref.getString("pc_ip", "") ?: ""

            // 读取用户设置的主机名字，留空则默认显示 "PC"
            val hostName = (sharedPref.getString("host_name", "") ?: "").ifBlank { "PC" }

            if (pcIp.isEmpty()) {
                Log.w(TAG, "PC IP is empty, showing no config widget")
                showNoConfigWidget(appContext, appWidgetManager, appWidgetId)
                return
            }

            // 获取当前时间
            val currentTime = Calendar.getInstance()
            val month = currentTime.get(Calendar.MONTH) + 1
            val day = currentTime.get(Calendar.DAY_OF_MONTH)
            val hour = currentTime.get(Calendar.HOUR_OF_DAY)
            val minute = currentTime.get(Calendar.MINUTE)

            // 使用 HTML 格式实现多颜色显示
            // 日期时间单行显示：日期和时间用空格分隔
            val dateTimeDisplay = String.format(
                Locale.getDefault(),
                "<font color='#00FF00'>%d-%d</font> <font color='#00FF00'>%d:</font><font color='#FF0000'>%02d</font>",
                month, day, hour, minute
            )

            // 上次缓存的条目数（用于立即显示；字号由布局的 autosize 自动处理）
            val cachedCount = sharedPref.getInt("cached_count", -1)

            // 立即更新时间显示（使用上次缓存的条目数）
            val views = RemoteViews(context.packageName, R.layout.widget_screenshot)

            // 设置点击事件
            val intent = android.content.Intent(context, com.screenshot.monitor.MainActivity::class.java)
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            views.setViewVisibility(R.id.widget_time_display_layout, View.VISIBLE)

            // 第一行：主机名
            views.setTextViewText(R.id.widget_host_text, hostName)

            // 第三行：日期时间
            views.setTextViewText(
                R.id.widget_date_text,
                android.text.Html.fromHtml(dateTimeDisplay, android.text.Html.FROM_HTML_MODE_LEGACY)
            )

            // 第二行：个数（使用上次缓存的条目数，数字显示为红色）
            views.setViewVisibility(R.id.widget_update_time, View.VISIBLE)
            if (cachedCount >= 0) {
                val countText = String.format(
                    Locale.getDefault(),
                    "<b><font color='#FF0000'>%d</font> 个</b>",
                    cachedCount
                )
                views.setTextViewText(
                    R.id.widget_update_time,
                    android.text.Html.fromHtml(countText, android.text.Html.FROM_HTML_MODE_LEGACY)
                )
            } else {
                views.setTextViewText(R.id.widget_update_time, "检查中...")
            }

            // 立即更新widget显示时间
            appWidgetManager.updateAppWidget(appWidgetId, views)

            // 在协程中异步执行网络请求
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d(TAG, "Starting async API request to: $pcIp")
                    val apiService = ApiService(appContext)
                    val response = apiService.getStatus(pcIp)
                    Log.d(TAG, "API response received: ${response?.status ?: "null"}")

                    // 更新 widget UI（只更新条目数部分）
                    withContext(Dispatchers.Main) {
                        // 使用 partiallyUpdateAppWidget 只更新条目数，不重新展开布局（避免覆盖已显示的日期时间）
                        val partialViews = RemoteViews(context.packageName, R.layout.widget_screenshot)

                        if (response != null) {
                            // 缓存条目数
                            sharedPref.edit().putInt("cached_count", response.totalCount).apply()

                            val countText = String.format(
                                Locale.getDefault(),
                                "<font color='#FF0000'>%d</font> 个",
                                response.totalCount
                            )
                            partialViews.setViewVisibility(R.id.widget_update_time, View.VISIBLE)
                            partialViews.setTextViewText(
                                R.id.widget_update_time,
                                android.text.Html.fromHtml(countText, android.text.Html.FROM_HTML_MODE_LEGACY)
                            )
                        } else {
                            partialViews.setViewVisibility(R.id.widget_update_time, View.VISIBLE)
                            partialViews.setTextViewText(R.id.widget_update_time, "连接失败")
                        }

                        // partiallyUpdateAppWidget：只应用差量，不重新展开布局，日期时间不受影响
                        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, partialViews)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching server data: ${e.message}", e)
                    // 网络请求失败，但时间已经在主线程更新了，不影响时间显示
                    withContext(Dispatchers.Main) {
                        val errorViews = RemoteViews(context.packageName, R.layout.widget_screenshot)
                        errorViews.setViewVisibility(R.id.widget_update_time, View.VISIBLE)
                        errorViews.setTextViewText(R.id.widget_update_time, "网络错误")
                        // 同样使用 partiallyUpdateAppWidget，避免覆盖日期时间
                        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, errorViews)
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

            views.setViewVisibility(R.id.widget_time_display_layout, View.VISIBLE)
            views.setViewVisibility(R.id.widget_update_time, View.VISIBLE)
            views.setTextViewText(R.id.widget_host_text, "未配置")   // 第一行
            views.setTextViewText(R.id.widget_update_time, "请打开应用") // 第二行
            views.setTextViewText(R.id.widget_date_text, "设置 IP")    // 第三行
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * 启动定时更新任务 - 每分钟更新一次
         */
        fun startAutoUpdate(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                val intent = android.content.Intent(context, ScreenshotWidgetProvider::class.java).apply {
                    action = ACTION_UPDATE_WIDGET
                }
                val pendingIntent = android.app.PendingIntent.getBroadcast(
                    context,
                    0,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )

                // 计算下一分钟整点的时间
                val calendar = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.MINUTE, 1)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }

                // 检查并使用精确闹钟
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    // Android 12+ 检查是否可以设置精确闹钟
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            android.app.AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                        Log.d(TAG, "使用 setExactAndAllowWhileIdle，下次更新时间: ${calendar.time}")
                    } else {
                        Log.w(TAG, "没有精确闹钟权限，使用 setAndAllowWhileIdle")
                        alarmManager.setAndAllowWhileIdle(
                            android.app.AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    }
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    // Android 6.0+
                    alarmManager.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "使用 setExactAndAllowWhileIdle，下次更新时间: ${calendar.time}")
                } else {
                    // Android 6.0 以下
                    alarmManager.setExact(
                        android.app.AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "使用 setExact，下次更新时间: ${calendar.time}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动定时更新失败: ${e.message}")
                e.printStackTrace()
            }
        }

        /**
         * 停止定时更新任务
         */
        fun stopAutoUpdate(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                val intent = android.content.Intent(context, ScreenshotWidgetProvider::class.java).apply {
                    action = ACTION_UPDATE_WIDGET
                }
                val pendingIntent = android.app.PendingIntent.getBroadcast(
                    context,
                    0,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )

                alarmManager.cancel(pendingIntent)
                Log.d(TAG, "停止定时更新任务")
            } catch (e: Exception) {
                Log.e(TAG, "停止定时更新失败: ${e.message}")
                e.printStackTrace()
            }
        }

        /**
         * 更新所有 widgets
         */
        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, ScreenshotWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

                Log.d(TAG, "更新所有小部件，数量: ${appWidgetIds.size}")

                // 直接调用更新方法
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                    Log.d(TAG, "已更新widget ID: $appWidgetId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "更新小部件失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * 当用户调整小部件尺寸时触发，重新根据新高度更新字体大小
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val newHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        Log.d(TAG, "小部件尺寸变化，新高度: ${newHeight}dp，重新更新 widget ID: $appWidgetId")
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: android.content.Intent) {
        super.onReceive(context, intent)

        // 处理定时更新广播
        if (intent.action == ACTION_UPDATE_WIDGET) {
            Log.d(TAG, "收到定时更新广播")
            // 更新所有小部件
            updateAllWidgets(context)
            // 重新设置下一次的定时任务（因为精确闹钟只触发一次）
            startAutoUpdate(context)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate 被调用，widget数量: ${appWidgetIds.size}")
        // 更新所有的widget实例
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        // 启动定时更新
        startAutoUpdate(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "第一个小部件被添加，启动定时更新")
        // 第一个小部件被添加到桌面时，启动定时更新
        startAutoUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "最后一个小部件被移除，停止定时更新")
        // 最后一个小部件从桌面移除时，停止定时更新
        stopAutoUpdate(context)
    }
}
