package com.screenshot.monitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.screenshot.monitor.worker.UpdateWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 开机后重新调度定时任务
            schedulePeriodicUpdate(context)
        }
    }

    private fun schedulePeriodicUpdate(context: Context) {
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
            1, TimeUnit.HOURS,
            15, TimeUnit.MINUTES
        )
            .setInitialDelay(initialDelayMinutes.toLong(), TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "screenshot_update",
            ExistingPeriodicWorkPolicy.UPDATE,
            updateRequest
        )
    }
}
