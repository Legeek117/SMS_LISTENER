package com.cryptovip.smslistener

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
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
            if (isOnline()) {
                CoroutineScope(Dispatchers.IO).launch {
                    LogStore.processPending(this@App)
                }
            }
        }, 2, 5, TimeUnit.MINUTES)
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }
}
