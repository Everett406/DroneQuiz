package com.drone.quiz.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.drone.quiz.MainActivity
import com.drone.quiz.R
import com.drone.quiz.ServiceLocator
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }
        val streak = runCatching { ServiceLocator.repo.streakDays() }.getOrDefault(0)
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_drone)
            .setContentTitle("该刷题啦")
            .setContentText("已连续打卡 $streak 天，今天继续保持吧！")
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    ctx, 0,
                    Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        runCatching { manager.notify(1001, notification) }
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "streak_reminder"
    }
}

object ReminderScheduler {

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                ReminderWorker.CHANNEL_ID,
                "每日打卡提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    fun schedule(context: Context) {
        // 每天 20:00 触发（首次计算延迟）
        val now = Calendar.getInstance()
        val target = now.clone() as Calendar
        target.set(Calendar.HOUR_OF_DAY, 20)
        target.set(Calendar.MINUTE, 0)
        target.set(Calendar.SECOND, 0)
        if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        val delayMs = target.timeInMillis - now.timeInMillis

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "streak_reminder",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("streak_reminder")
    }
}
