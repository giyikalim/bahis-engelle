package com.utility.calculator.heartbeat

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Heartbeat Manager
 *
 * WorkManager kullanarak periyodik heartbeat'leri planlar ve yönetir.
 */
object HeartbeatManager {

    private const val TAG = "HeartbeatManager"

    /**
     * Heartbeat'i başlat
     *
     * Bu fonksiyon:
     * 1. Hemen bir heartbeat gönderir
     * 2. Her 15 dakikada bir tekrarlayan heartbeat planlar
     */
    fun start(context: Context) {
        Log.i(TAG, "Heartbeat sistemi başlatılıyor...")

        // Yapılandırma kontrolü
        if (!SupabaseConfig.isConfigured()) {
            Log.w(TAG, "Supabase yapılandırılmamış! Heartbeat başlatılmadı.")
            Log.w(TAG, "SupabaseConfig.kt dosyasındaki değerleri güncelleyin.")
            return
        }

        val workManager = WorkManager.getInstance(context)

        // 1. Hemen bir heartbeat gönder (one-time)
        val immediateWork = OneTimeWorkRequestBuilder<HeartbeatWorker>()
            .setConstraints(getConstraints())
            .build()

        workManager.enqueue(immediateWork)
        Log.i(TAG, "İlk heartbeat planlandı")

        // 2. Periyodik heartbeat planla (her 15 dakika)
        val periodicWork = PeriodicWorkRequestBuilder<HeartbeatWorker>(
            SupabaseConfig.HEARTBEAT_INTERVAL_MINUTES.toLong(),
            TimeUnit.MINUTES
        )
            .setConstraints(getConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        // Mevcut işi değiştir (duplicate önle)
        workManager.enqueueUniquePeriodicWork(
            HeartbeatWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork
        )

        Log.i(TAG, "Periyodik heartbeat planlandı (${SupabaseConfig.HEARTBEAT_INTERVAL_MINUTES} dk)")
    }

    /**
     * Heartbeat'i durdur
     */
    fun stop(context: Context) {
        Log.i(TAG, "Heartbeat sistemi durduruluyor...")

        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(HeartbeatWorker.WORK_NAME)

        Log.i(TAG, "Heartbeat durduruldu")
    }

    /**
     * Hemen heartbeat gönder (manuel tetikleme)
     */
    fun sendNow(context: Context) {
        if (!SupabaseConfig.isConfigured()) {
            Log.w(TAG, "Supabase yapılandırılmamış!")
            return
        }

        val workManager = WorkManager.getInstance(context)

        val immediateWork = OneTimeWorkRequestBuilder<HeartbeatWorker>()
            .setConstraints(getConstraints())
            .build()

        workManager.enqueue(immediateWork)
        Log.i(TAG, "Manuel heartbeat gönderiliyor")
    }

    /**
     * Heartbeat durumunu kontrol et
     */
    fun getStatus(context: Context): HeartbeatStatus {
        val prefs = context.getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
        val lastHeartbeat = prefs.getLong("last_heartbeat", 0)
        val deviceId = prefs.getString("device_id", null)

        val timeSinceLastBeat = if (lastHeartbeat > 0) {
            System.currentTimeMillis() - lastHeartbeat
        } else {
            -1
        }

        return HeartbeatStatus(
            isConfigured = SupabaseConfig.isConfigured(),
            lastHeartbeatTime = lastHeartbeat,
            timeSinceLastBeatMs = timeSinceLastBeat,
            deviceId = deviceId
        )
    }

    /**
     * WorkManager kısıtlamaları
     */
    private fun getConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)  // İnternet bağlantısı gerekli
            .build()
    }

    /**
     * Heartbeat durum bilgisi
     */
    data class HeartbeatStatus(
        val isConfigured: Boolean,
        val lastHeartbeatTime: Long,
        val timeSinceLastBeatMs: Long,
        val deviceId: String?
    ) {
        fun getStatusText(): String {
            if (!isConfigured) {
                return "Yapılandırılmamış"
            }

            if (lastHeartbeatTime == 0L) {
                return "Henüz gönderilmedi"
            }

            val minutes = timeSinceLastBeatMs / 60000
            return when {
                minutes < 1 -> "Az önce gönderildi"
                minutes < 60 -> "$minutes dakika önce"
                else -> "${minutes / 60} saat önce"
            }
        }

        fun isHealthy(): Boolean {
            if (!isConfigured) return false
            if (timeSinceLastBeatMs < 0) return false

            // 30 dakikadan fazla heartbeat yoksa sağlıksız
            return timeSinceLastBeatMs < 30 * 60 * 1000
        }
    }
}
