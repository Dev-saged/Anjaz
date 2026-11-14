package com.anjaz.app.update

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.anjaz.app.MainActivity
import com.anjaz.app.R

/** إشعارات التحديث: متاح، نسبة التنزيل، «تم التحميل اضغط للتثبيت»، وفشل. */
object UpdateNotifications {
    private const val CHANNEL = "update"
    const val PROGRESS_ID = 999_500
    private const val READY_ID = 999_501
    private const val FAILED_ID = 999_502
    private const val AVAILABLE_ID = 999_503

    private fun builder(context: Context): NotificationCompat.Builder {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "تحديثات التطبيق", NotificationManager.IMPORTANCE_DEFAULT))
        }
        return NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
    }

    private fun open(context: Context, install: Boolean): PendingIntent = PendingIntent.getActivity(
        context, if (install) 1 else 0,
        Intent(context, MainActivity::class.java)
            .setAction(if (install) MainActivity.ACTION_INSTALL_UPDATE else MainActivity.ACTION_OPEN_UPDATE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun progress(context: Context, version: String, percent: Int): Notification =
        builder(context)
            .setContentTitle("جارٍ تنزيل إنجاز $version")
            .setContentText("$percent٪")
            .setProgress(100, percent, percent == 0)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(open(context, install = false))
            .build()

    private fun post(context: Context, id: Int, n: Notification) {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    fun available(context: Context, version: String) = post(
        context, AVAILABLE_ID,
        builder(context).setContentTitle("تحديث جديد لإنجاز $version")
            .setContentText("اضغط لعرض المزايا وتنزيل النسخة")
            .setContentIntent(open(context, install = false)).setAutoCancel(true).build(),
    )

    fun ready(context: Context, version: String) = post(
        context, READY_ID,
        builder(context).setContentTitle("تم تحميل إنجاز $version")
            .setContentText("النسخة جاهزة، اضغط للتثبيت")
            .setContentIntent(open(context, install = true)).setAutoCancel(true).build(),
    )

    fun failed(context: Context) = post(
        context, FAILED_ID,
        builder(context).setContentTitle("تعذّر تنزيل التحديث")
            .setContentText("اضغط للمحاولة من جديد")
            .setContentIntent(open(context, install = false)).setAutoCancel(true).build(),
    )

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).apply { cancel(READY_ID); cancel(FAILED_ID); cancel(AVAILABLE_ID) }
    }
}
