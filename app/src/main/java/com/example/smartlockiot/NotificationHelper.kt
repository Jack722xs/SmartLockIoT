package com.example.smartlockiot

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                Constants.CHANNEL_ID, "Eventos de acceso", NotificationManager.IMPORTANCE_HIGH
            )
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val n = NotificationCompat.Builder(ctx, Constants.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(System.currentTimeMillis().toInt(), n)
    }
}
