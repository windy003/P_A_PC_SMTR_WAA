package com.screenshot.monitor.worker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.screenshot.monitor.widget.ScreenshotWidgetProvider
import java.util.Calendar

class UpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        return try {
            // 检查当前是否是每小时的 10 分
            val calendar = Calendar.getInstance()
            val currentMinute = calendar.get(Calendar.MINUTE)

            // 允许 10 分前后 5 分钟的误差范围（5-15 分之间都执行）
            if (currentMinute in 5..15) {
                // 更新所有 widget 实例
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, ScreenshotWidgetProvider::class.java)
                )

                for (appWidgetId in appWidgetIds) {
                    ScreenshotWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId)
                }

                Result.success()
            } else {
                // 不在指定时间范围内，跳过本次更新
                Result.success()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
