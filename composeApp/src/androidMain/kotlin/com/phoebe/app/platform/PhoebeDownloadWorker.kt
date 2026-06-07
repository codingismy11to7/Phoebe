package com.phoebe.app.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phoebe.app.MainActivity
import com.phoebe.app.R
import kotlinx.coroutines.CancellationException

class PhoebeDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo("Preparing downloads")

    override suspend fun doWork(): Result {
        runCatching {
            setForeground(foregroundInfo("Downloading queued songs"))
        }.onFailure { error ->
            if (error is CancellationException) throw error
            if (error.isForegroundStartDenied()) {
                PhoebeLog.d("PhoebeDownloadWorker") {
                    "background downloads paused because Android denied foreground-service start: ${error.message}"
                }
                return Result.failure()
            }
            throw error
        }
        return runCatching {
            AndroidDownloadRuntime.catalogRepository().resumeQueuedDownloads()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                if (error is CancellationException) throw error
                PhoebeLog.d("PhoebeDownloadWorker") { "background downloads failed: ${error.message}" }
                Result.retry()
            },
        )
    }

    private fun foregroundInfo(text: String): ForegroundInfo {
        val notification = downloadNotification(text)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                DownloadWorkerNotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(DownloadWorkerNotificationId, notification)
        }
    }

    private fun downloadNotification(text: String): Notification {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    DownloadWorkerChannelId,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val openApp = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(applicationContext, DownloadWorkerChannelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(applicationContext)
        }
        return builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Phoebe downloads")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(applicationContext, android.R.drawable.ic_menu_close_clear_cancel),
                    "Cancel",
                    cancel,
                ).build(),
            )
            .setColor(ContextCompat.getColor(applicationContext, R.color.ic_launcher_background))
            .build()
    }
}

private fun Throwable.isForegroundStartDenied(): Boolean =
    javaClass.name == "android.app.ForegroundServiceStartNotAllowedException" ||
        message.orEmpty().contains("foreground service", ignoreCase = true) ||
        message.orEmpty().contains("mAllowStartForeground", ignoreCase = true)

private const val DownloadWorkerChannelId = "phoebe_download_worker"
private const val DownloadWorkerNotificationId = 2002
