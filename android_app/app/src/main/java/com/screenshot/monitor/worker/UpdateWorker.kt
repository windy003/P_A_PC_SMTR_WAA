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
            // 每5分钟更新一次所有 widget 实例
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, ScreenshotWidgetProvider::class.java)
            )

            for (appWidgetId in appWidgetIds) {
                ScreenshotWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
