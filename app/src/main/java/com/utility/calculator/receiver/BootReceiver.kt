package com.utility.calculator.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.utility.calculator.service.BlockerVpnService

/**
 * Boot Receiver - Telefon açıldığında servisi otomatik başlatır
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            // Koruma aktif mi kontrol et
            val prefs = context.getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("protection_enabled", false)

            if (isEnabled) {
                // VPN izni var mı kontrol et
                val vpnIntent = VpnService.prepare(context)
                if (vpnIntent == null) {
                    // İzin var, servisi başlat
                    val serviceIntent = Intent(context, BlockerVpnService::class.java)
                    context.startForegroundService(serviceIntent)
                }
            }
        }
    }
}
