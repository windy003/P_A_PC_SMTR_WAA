package com.screenshot.monitor.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.screenshot.monitor.widget.ScreenshotWidgetProvider

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val ACTION_UPDATE_WIDGET = "com.screenshot.monitor.UPDATE_WIDGET"
        private const val UPDATE_INTERVAL_MS = 60 * 1000L // 1分钟

        fun scheduleAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 计算下一分钟的触发时间
            val triggerTime = SystemClock.elapsedRealtime() + UPDATE_INTERVAL_MS

            // 使用 setExact 或 setExactAndAllowWhileIdle 确保精确执行
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6.0+ 使用 setExactAndAllowWhileIdle 以在 Doze 模式下也能执行
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                // Android 6.0 以下使用 setExact
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }

            Log.d(TAG, "Exact alarm scheduled in ${UPDATE_INTERVAL_MS}ms")
        }

        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Alarm cancelled")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_UPDATE_WIDGET) {
            Log.d(TAG, "Alarm triggered, updating widgets")

            // 更新所有 widget 实例
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, ScreenshotWidgetProvider::class.java)
            )

            for (appWidgetId in appWidgetIds) {
                ScreenshotWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId)
            }

            // 重新调度下一次alarm（因为setExact只执行一次）
            scheduleAlarm(context)
        }
    }
}
