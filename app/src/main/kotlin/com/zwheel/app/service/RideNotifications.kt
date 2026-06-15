package com.zwheel.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.zwheel.app.MainActivity
import com.zwheel.app.R

internal const val RIDE_CHANNEL_ID = "zwheel_ride"
internal const val RIDE_NOTIFICATION_ID = 1

internal class RideNotifications(private val context: Context) {

    fun createChannel() {
        val channel = NotificationChannel(
            RIDE_CHANNEL_ID,
            "Ride",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Active ride status"
        }
        nm().createNotificationChannel(channel)
    }

    fun notify(content: String) {
        nm().notify(RIDE_NOTIFICATION_ID, build(content))
    }

    fun build(content: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnectIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, RideForegroundService::class.java).apply { action = "DISCONNECT" },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, RIDE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ZWheel")
            .setContentText(content)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Disconnect", disconnectIntent)
            .build()
    }

    private fun nm() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
