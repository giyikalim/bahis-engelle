package com.utility.calculator.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.net.NetworkInterface

/**
 * Harici VPN Algılama Servisi
 *
 * Kullanıcının harici VPN kullanarak korumayı atlatmaya çalışıp
 * çalışmadığını tespit eder.
 */
class VpnDetectorService : Service() {

    companion object {
        private const val TAG = "VpnDetector"
        private const val CHECK_INTERVAL = 30000L // 30 saniye

        // Bilinen VPN arayüz isimleri
        private val VPN_INTERFACE_NAMES = listOf(
            "tun", "tap", "ppp", "pptp", "l2tp", "ipsec", "vpn"
        )

        // Bizim VPN arayüzümüz (hariç tutulacak)
        private const val OUR_VPN_INTERFACE = "tun0"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isOurVpnActive = false

    private val connectivityCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            checkForExternalVpn()
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                checkForExternalVpn()
            }
        }
    }

    private val periodicCheck = object : Runnable {
        override fun run() {
            if (isProtectionEnabled()) {
                checkForExternalVpn()
            }
            handler.postDelayed(this, CHECK_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerNetworkCallback()
        handler.post(periodicCheck)
        Log.i(TAG, "VPN algılama servisi başlatıldı")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isOurVpnActive = intent?.getBooleanExtra("our_vpn_active", false) ?: false
        return START_STICKY
    }

    private fun registerNetworkCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .build()

            try {
                cm.registerNetworkCallback(request, connectivityCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Network callback kaydı hatası", e)
            }
        }
    }

    /**
     * Harici VPN kontrolü
     */
    private fun checkForExternalVpn() {
        if (!isProtectionEnabled()) return

        try {
            // Yöntem 1: Network interface kontrolü
            val hasExternalVpn = checkNetworkInterfaces()

            // Yöntem 2: Connectivity Manager kontrolü
            val hasVpnTransport = checkConnectivityManager()

            if (hasExternalVpn || hasVpnTransport) {
                Log.w(TAG, "⚠️ HARİCİ VPN TESPİT EDİLDİ!")
                onExternalVpnDetected()
            }
        } catch (e: Exception) {
            Log.e(TAG, "VPN kontrolü hatası", e)
        }
    }

    /**
     * Network interface'leri kontrol et
     */
    private fun checkNetworkInterfaces(): Boolean {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                val name = ni.name.lowercase()

                // Bizim VPN'imizi atla
                if (name == OUR_VPN_INTERFACE && isOurVpnActive) {
                    continue
                }

                // VPN arayüzü mü kontrol et
                if (VPN_INTERFACE_NAMES.any { name.startsWith(it) }) {
                    if (ni.isUp && !ni.isLoopback) {
                        Log.d(TAG, "VPN arayüzü bulundu: $name")
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Interface kontrolü hatası", e)
        }
        return false
    }

    /**
     * Connectivity Manager ile VPN kontrolü
     */
    private fun checkConnectivityManager(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false

            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                // Bizim VPN'imiz mi kontrol et
                if (!isOurVpnActive) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Harici VPN tespit edildiğinde
     */
    private fun onExternalVpnDetected() {
        // Kullanıcıyı uyar
        handler.post {
            Toast.makeText(
                this,
                "⚠️ Harici VPN tespit edildi!\nKoruma devre dışı kalabilir.",
                Toast.LENGTH_LONG
            ).show()
        }

        // Log kaydet
        saveVpnLog()

        // Opsiyonel: Uygulamayı kilitle veya uyarı göster
        // lockApp()
    }

    private fun saveVpnLog() {
        val prefs = getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
        val logs = prefs.getString("vpn_logs", "") ?: ""
        val timestamp = System.currentTimeMillis()
        val newLog = "$timestamp|EXTERNAL_VPN_DETECTED\n"
        prefs.edit().putString("vpn_logs", logs + newLog).apply()
    }

    private fun isProtectionEnabled(): Boolean {
        val prefs = getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("protection_enabled", false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(periodicCheck)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(connectivityCallback)
            } catch (e: Exception) {
                // Ignore
            }
        }
        super.onDestroy()
    }
}
