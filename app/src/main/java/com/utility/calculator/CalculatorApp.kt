package com.utility.calculator

import android.app.Application
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

        // Uygulama her açıldığında heartbeat'i başlat
        // Bu hem ilk kurulumda hem de sonraki açılışlarda çalışır
        HeartbeatManager.start(this)
    }
}
