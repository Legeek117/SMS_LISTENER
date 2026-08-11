package com.cryptovip.smslistener

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var btnRequestPerms: Button
    private lateinit var btnStartSvc: Button
    private lateinit var etApiUrl: EditText
    private lateinit var etApiKey: EditText
    private lateinit var etMerchantPhone: EditText
    private lateinit var tvLogs: TextView
    private lateinit var tvStatusPerms: TextView
    private lateinit var tvStatusService: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnRequestPerms  = findViewById(R.id.btn_request_perms)
        btnStartSvc      = findViewById(R.id.btn_start_service)
        etApiUrl         = findViewById(R.id.et_api_url)
        etApiKey         = findViewById(R.id.et_api_key)
        etMerchantPhone  = findViewById(R.id.et_merchant_phone)
        tvLogs           = findViewById(R.id.tv_logs)
        tvStatusPerms    = findViewById(R.id.tv_status_perms)
        tvStatusService  = findViewById(R.id.tv_status_service)

        // Charger les préférences sauvegardées (si déjà configuré)
        val prefs    = getSharedPreferences("vip_prefs", MODE_PRIVATE)
        val savedUrl = prefs.getString("api_url", NetworkHelper.API_URL)!!
        val savedKey = prefs.getString("api_key", NetworkHelper.SECRET_KEY)!!
        etApiUrl.setText(savedUrl)
        etApiKey.setText(savedKey)
        etMerchantPhone.setText(prefs.getString("merchant_phone", ""))

        // Injecter dans NetworkHelper immédiatement
        NetworkHelper.API_URL    = savedUrl
        NetworkHelper.SECRET_KEY = savedKey

        btnRequestPerms.setOnClickListener { requestAllPermissions() }
        btnStartSvc.setOnClickListener    { onStartServiceClick() }

        refreshLogs()
        updateStatusUI()
    }

    override fun onResume() {
        super.onResume()
        updateStatusUI()
        refreshLogs()
    }

    // ════════════════════════════════════════════════════════
    // GESTION DES PERMISSIONS
    // ════════════════════════════════════════════════════════
    private fun requestAllPermissions() {
        val list = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED)
            list.add(Manifest.permission.RECEIVE_SMS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED)
            list.add(Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED)
            list.add(Manifest.permission.POST_NOTIFICATIONS)

        if (list.isNotEmpty())
            ActivityCompat.requestPermissions(this, list.toTypedArray(), 999)

        // Demander l'exclusion batterie (popup système)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }
        Toast.makeText(this, "✅ Autorisations demandées — relancer l'app ensuite", Toast.LENGTH_LONG).show()
        updateStatusUI()
    }

    // ════════════════════════════════════════════════════════
    // DÉMARRER LE SERVICE (BOUTON VERT)
    // ════════════════════════════════════════════════════════
    private fun onStartServiceClick() {
        val url = etApiUrl.text.toString().trim()
        val key = etApiKey.text.toString().trim()

        if (url.length < 10 || !url.startsWith("http")) {
            Toast.makeText(this, "⚠️ URL serveur invalide (https://...)", Toast.LENGTH_LONG).show()
            return
        }
        if (key.length < 6) {
            Toast.makeText(this, "⚠️ Clé secrète trop courte", Toast.LENGTH_LONG).show()
            return
        }

        // Sauvegarder URL + KEY dans SharedPrefs
        getSharedPreferences("vip_prefs", MODE_PRIVATE).edit().apply {
            putString("api_url",         url)
            putString("api_key",         key)
            putString("merchant_phone",  etMerchantPhone.text.toString().trim())
            apply()
        }
        NetworkHelper.API_URL    = url
        NetworkHelper.SECRET_KEY = key

        // Lancer le service foreground
        ListenerService.start(this)

        tvStatusService.text = "✅ SERVICE ACTIF — Les SMS arrivent maintenant, notification permanente visible dans le tiroir."
        tvStatusService.setTextColor(0xFF10B981.toInt())
        Toast.makeText(
            this,
            "🚀 Service démarré ! Vous pouvez fermer l'app, elle continue en arrière-plan.",
            Toast.LENGTH_LONG
        ).show()
    }

    // ════════════════════════════════════════════════════════
    // LOGS
    // ════════════════════════════════════════════════════════
    private fun refreshLogs() {
        lifecycleScope.launch {
            val logs = withContext(Dispatchers.IO) { LogStore.getLast50(this@MainActivity) }
            tvLogs.text = if (logs.isEmpty())
                "(vide — patientez qu'un SMS de paiement arrive sur ce téléphone)"
            else
                logs.joinToString("\n\n")
        }
    }

    // ════════════════════════════════════════════════════════
    // UI STATUT PERMISSIONS
    // ════════════════════════════════════════════════════════
    private fun updateStatusUI() {
        val hasSms = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val hasNotif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val pm    = getSystemService(POWER_SERVICE) as PowerManager
        val batOk = pm.isIgnoringBatteryOptimizations(packageName)

        tvStatusPerms.text = buildString {
            append("📨 SMS  : ").append(if (hasSms)   "✅" else "❌ À AUTORISER").append("\n")
            append("🔔 Notif: ").append(if (hasNotif) "✅" else "❌ Android 13+ obligatoire").append("\n")
            append("🔋 Bat. : ").append(
                if (batOk) "✅" else "⚠️ Désactiver l'optimisation (sinon app tuée après 10min)"
            )
        }
    }
}
