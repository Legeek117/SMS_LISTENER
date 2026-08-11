package com.cryptovip.smslistener

import android.app.Application
import android.net.ConnectivityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // Toutes les 5 minutes : vérifie si le réseau est disponible.
        // Si oui, retente tous les SMS en file d'attente (stockés hors ligne).
        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay({
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val isOnline = runCatching {
                cm.activeNetworkInfo?.isConnected == true
            }.getOrDefault(false)
            if (isOnline) {
                CoroutineScope(Dispatchers.IO).launch {
                    LogStore.processPending(this@App)
                }
            }
        }, 2, 5, TimeUnit.MINUTES)
    }
}
