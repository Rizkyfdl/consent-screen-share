package com.example.target

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootResumeReceiver : BroadcastReceiver() {

    companion object {
        const val PREFS_NAME = "target_screen_share_preferences"
        const val PREF_RESUME_AFTER_REBOOT = "resume_after_reboot"
        private const val CHANNEL_ID = "screen_share_resume"
        private const val NOTIFICATION_ID = 4002
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val enabled = context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .getBoolean(PREF_RESUME_AFTER_REBOOT, false)

        if (!enabled) return

        createChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            4002,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = android.app.Notification.Builder(
            context,
            CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Lanjutkan berbagi layar?")
            .setContentText(
                "Buka aplikasi untuk menyetujui sesi MediaProjection baru"
            )
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Screen sharing resume",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
    }
}