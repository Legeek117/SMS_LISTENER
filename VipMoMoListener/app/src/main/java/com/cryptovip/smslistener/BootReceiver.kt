package com.cryptovip.smslistener

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED
            || action == Intent.ACTION_LOCKED_BOOT_COMPLETED
            || action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.i("BootReceiver", "⚠️ Boot / Mise à jour détectée → relance ListenerService")
            val i = Intent(context, ListenerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}
