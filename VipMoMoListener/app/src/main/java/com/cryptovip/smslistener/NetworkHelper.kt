package com.cryptovip.smslistener

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    // Écrasées par MainActivity avec les valeurs des SharedPreferences
    var API_URL    = "https://cryptovip.159-223-162-195.nip.io/api/payment/sms-webhook"
    var SECRET_KEY = "VipCrypto_2026_MoMo_Listener_Secret_XyZ9!"

    // =========================================================================
    // 1. ENVOYER UN PAIEMENT AU SERVEUR (appelé depuis SmsReceiver)
    // =========================================================================
    fun sendPaymentToServer(
        context: Context,
        transactionId: String,
        amount: Int,
        senderPhone: String,
        senderName: String,
        date: String,
        rawSms: String
    ) {
        val payloadJson = JSONObject()
            .put("transactionId", transactionId)
            .put("amount",        amount)
            .put("senderPhone",   senderPhone)
            .put("senderName",    senderName)
            .put("date",          date)
            .put("rawSms",        rawSms)
            .toString()

        val request = Request.Builder()
            .url(API_URL)
            .post(payloadJson.toRequestBody(JSON_MEDIA))
            .addHeader("Content-Type",   "application/json")
            .addHeader("X-Listener-Key", SECRET_KEY)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {

            override fun onFailure(call: okhttp3.Call, e: IOException) {
                // Pas de réseau / timeout → stocker en file locale pour retry
                Log.e("NetworkHelper", "❌ Pas de réseau pour $transactionId : ${e.message}")
                CoroutineScope(Dispatchers.IO).launch {
                    LogStore.enqueueRetry(context, payloadJson)
                    LogStore.log(
                        context, transactionId, amount, senderPhone,
                        "PENDING", "Hors ligne → file d'attente : ${e.message}"
                    )
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    val body    = resp.body?.string().orEmpty()
                    val summary = "HTTP ${resp.code} | ${body.take(120)}"
                    CoroutineScope(Dispatchers.IO).launch {
                        when {
                            resp.isSuccessful -> {
                                Log.i("NetworkHelper", "✅ $transactionId — $summary")
                                LogStore.log(
                                    context, transactionId, amount, senderPhone,
                                    "SUCCESS", summary
                                )
                            }
                            resp.code == 401 -> {
                                // Clé invalide : on ne retente PAS (problème de config)
                                Log.e("NetworkHelper", "🔐 CLÉ INVALIDE 401 — $transactionId")
                                LogStore.log(
                                    context, transactionId, amount, senderPhone,
                                    "FAILED", "401 - Clé X-Listener-Key invalide côté serveur"
                                )
                            }
                            else -> {
                                Log.w("NetworkHelper", "⚠️ $transactionId erreur — $summary")
                                LogStore.log(
                                    context, transactionId, amount, senderPhone,
                                    "FAILED", summary
                                )
                                LogStore.enqueueRetry(context, payloadJson)
                            }
                        }
                    }
                }
            }
        })
    }

    // =========================================================================
    // 2. RETRY — renvoyer un JSON stocké dans la file Room
    //    (appelé depuis App.kt toutes les 5 minutes)
    // =========================================================================
    suspend fun retrySendRaw(context: Context, json: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(API_URL)
                    .post(json.toRequestBody(JSON_MEDIA))
                    .addHeader("Content-Type",   "application/json")
                    .addHeader("X-Listener-Key", SECRET_KEY)
                    .build()
                client.newCall(request).execute().use { it.isSuccessful }
            } catch (e: IOException) {
                false
            }
        }
}
