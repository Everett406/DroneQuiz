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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.drone.quiz.MainActivity
import com.drone.quiz.R
import com.drone.quiz.ServiceLocator
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * 每日智能提醒（不再固定 20:00）：
 * - 时刻 = 最近 10 天每天首次刷题时刻的中位数（学习用户习惯，无数据时默认 19:30），
 *   夹在 10:00–21:30 之间；
 * - 当天已经刷过题 → 保持安静，不打扰；
 * - 通知一次后自续：Worker 末尾再排下一天（每天动态重算时刻）。
 */
class ReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val repo = ServiceLocator.repo

        // 今天已刷过 → 不打扰，直接排明天
        val todayAnswered = runCatching { repo.todayAnsweredCount() }.getOrDefault(0)
        if (todayAnswered > 0) {
            scheduleNext(ctx)
            return Result.success()
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            scheduleNext(ctx)
            return Result.success()
        }

        val streak = runCatching { repo.streakDays() }.getOrDefault(0)
        val (title, text) = if (streak > 0) {
            "连击 $streak 天进行中" to "今天还没刷题，别把连击断了"
        } else {
            "今天还没刷题" to "花几分钟，刷几道题吧"
        }

        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_drone)
            .setContentTitle(title)
            .setContentText(text)
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

        scheduleNext(ctx)
        return Result.success()
    }

    /** 通知后自续：排明天的检查（时刻由 schedule 动态重算）。 */
    private suspend fun scheduleNext(ctx: Context) {
        runCatching { ReminderScheduler.schedule(ctx) }
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
                "每日提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    /**
     * 排下一次提醒（智能时刻）：
     * 最近 10 天每天首次刷题时刻的中位数 → 无数据默认 19:30，夹在 10:00–21:30。
     * 若今天的时刻已过，则排明天。OneTime + Worker 自续（每天动态重算）。
     */
    suspend fun schedule(context: Context) {
        val hours = runCatching { ServiceLocator.repo.habitStartHours(10) }.getOrDefault(emptyList())
        val median = if (hours.isEmpty()) 19.5f
        else hours.sorted()[hours.size / 2]
        val smart = median.coerceIn(10f, 21.5f)
        val hour = smart.toInt()
        val minute = ((smart - hour) * 60f).roundToInt().coerceIn(0, 59)

        val now = Calendar.getInstance()
        val target = now.clone() as Calendar
        target.set(Calendar.HOUR_OF_DAY, hour)
        target.set(Calendar.MINUTE, minute)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)
        if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        val delayMs = target.timeInMillis - now.timeInMillis

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "streak_reminder",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("streak_reminder")
    }
}
