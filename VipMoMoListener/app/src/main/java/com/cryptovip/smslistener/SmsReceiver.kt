package com.cryptovip.smslistener

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // CRITIQUE Android 12+ : goAsync() signale à l'OS qu'on a du travail async, ne pas tuer
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Sécurité : relancer le service s'il est mort
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        context.startForegroundService(Intent(context, ListenerService::class.java))
                    else
                        context.startService(Intent(context, ListenerService::class.java))
                } catch (_: Throwable) {}

                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    ?: return@launch

                for (sms in messages) {
                    val sender = sms.originatingAddress ?: ""
                    val body   = sms.messageBody       ?: ""
                    Log.d("SmsReceiver", "Reçu de [$sender]: ${body.take(90)}")

                    // Filtrer : ne garder que les SMS Mobile Money
                    val isMoneySms = sender.contains("131")
                            || sender.contains("MTN",    ignoreCase = true)
                            || sender.contains("MOOV",   ignoreCase = true)
                            || sender.contains("ORANGE", ignoreCase = true)
                            || sender.contains("WAVE",   ignoreCase = true)
                            || body.contains("Transfert")
                            || body.contains("MoMo")
                            || body.contains("fait un dépôt")
                            || body.contains("vous avez recu",  ignoreCase = true)
                            || body.contains("reçu",            ignoreCase = true)

                    if (!isMoneySms) continue
                    parseAndSend(context, body, sender)
                }
            } finally {
                pending.finish()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parse les infos du SMS par REGEX puis envoie au serveur
    // ─────────────────────────────────────────────────────────────────────────
    private suspend fun parseAndSend(context: Context, sms: String, rawSender: String) {
        try {
            // Exemple SMS MTN Bénin :
            // "Transfert 5125F de ADEDJOUMAN GILBERT (2290196223463) 2026-08-10 19:14:35 Ref: Solde:5200F ID:12639965385"

            val amountRegex = Regex(
                """(?:Transfert|Dépot|Dépôt|Paiement|Reçu|Vous avez reçu|recu|reçu)\s*([0-9 ]+)\s*[FfCc]""",
                RegexOption.IGNORE_CASE
            )
            val nameRegex   = Regex("""de (.+?) \(""")
            val numberRegex = Regex("""\(([0-9]{8,15})\)""")
            val dateRegex   = Regex("""([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2})""")
            val idRegex     = Regex(
                """(?:ID|Ref|Référence|transaction|trans)[ :#]+([A-Za-z0-9]{5,})""",
                RegexOption.IGNORE_CASE
            )

            val amount  = amountRegex.find(sms)
                ?.groupValues?.get(1)?.replace(" ", "")?.toIntOrNull()
            val name    = nameRegex.find(sms)?.groupValues?.get(1)?.trim() ?: ""
            val number  = numberRegex.find(sms)?.groupValues?.get(1)
                ?: rawSender.replace("+", "")
            val date    = dateRegex.find(sms)?.groupValues?.get(1) ?: ""
            val transId = idRegex.find(sms)?.groupValues?.get(1)
                ?: "TX-${sms.hashCode().toUInt()}"

            if (amount == null || amount <= 0) {
                LogStore.log(
                    context, transId, 0, number, "FAILED",
                    "Regex montant KO — vérifier format SMS"
                )
                return
            }

            NetworkHelper.sendPaymentToServer(
                context       = context,
                transactionId = transId,
                amount        = amount,
                senderPhone   = number,
                senderName    = name,
                date          = date,
                rawSms        = sms
            )
        } catch (e: Throwable) {
            Log.e("SmsReceiver", "Erreur parser : ${e.message}", e)
            LogStore.log(
                context, "ERR-${sms.hashCode().toUInt()}", 0, rawSender,
                "FAILED", "Exception: ${e.message}"
            )
        }
    }
}
