package com.cryptovip.smslistener

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class ListenerService : Service() {

    companion object {
        private const val CHANNEL_ID = "vip_momo_listener_channel"
        private const val NOTIF_ID   = 1001

        fun start(context: Context) {
            val intent = Intent(context, ListenerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    // START_STICKY = si le système tue le service, il le relance automatiquement
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── Notification PERMANENTE (impossible à swiper) ───────────────────────
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("👑 VIP MoMo Listener ACTIF")
            .setContentText("SMS Mobile Money capturés 24h/24. Ne pas supprimer.")
            .setSmallIcon(R.drawable.ic_notif)
            .setPriority(NotificationCompat.PRIORITY_MIN)   // pas de son, pas de vibration
            .setOngoing(true)                                // impossible à swiper
            .setShowWhen(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID,
                "Service VIP MoMo Listener",
                NotificationManager.IMPORTANCE_MIN          // silencieux
            ).apply {
                description  = "Notification obligatoire pour que l'app tourne en permanence."
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
    }
}
