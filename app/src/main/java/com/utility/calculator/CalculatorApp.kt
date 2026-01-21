package com.utility.calculator

import android.app.Application
import android.content.Context
import android.util.Log
import com.utility.calculator.heartbeat.HeartbeatManager

/**
 * Uygulama sınıfı
 *
 * Uygulama başladığında heartbeat sistemini başlatır.
 */
class CalculatorApp : Application() {

    companion object {
        private const val TAG = "CalculatorApp"
    }

    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "Uygulama başlatılıyor...")

        // Koruma aktifse heartbeat'i başlat
        if (isProtectionEnabled()) {
            HeartbeatManager.start(this)
        }
    }

    private fun isProtectionEnabled(): Boolean {
        return try {
            val prefs = getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
            prefs.getBoolean("protection_enabled", false)
        } catch (e: Exception) {
            false
        }
    }
}
