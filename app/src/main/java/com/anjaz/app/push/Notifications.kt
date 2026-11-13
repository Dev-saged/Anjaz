package com.anjaz.app.push

import android.Manifest
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

object Notifications {
    const val DEFAULT = "default"
    const val SOS = "sos"

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(DEFAULT, "الإشعارات العامة", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(
            NotificationChannel(SOS, "نداءات الاستغاثة", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                setBypassDnd(true)
            },
        )
    }

    fun show(ctx: Context, id: Int, title: String, body: String, high: Boolean, route: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            ctx, id, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("route", route),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, if (high) SOS else DEFAULT)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(body)
            .setPriority(if (high) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (high) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true).setContentIntent(open).build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }
}
