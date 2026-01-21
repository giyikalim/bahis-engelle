package com.utility.calculator.heartbeat

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Heartbeat Manager
 *
 * AlarmManager + WorkManager kullanarak periyodik heartbeat'leri planlar.
 * AlarmManager: 10 dakikalık interval için (WorkManager min 15 dk)
 * WorkManager: Güvenilir arka plan işi için
 */
object HeartbeatManager {

    private const val TAG = "HeartbeatManager"
    private const val ALARM_REQUEST_CODE = 9876

    /**
     * Heartbeat'i başlat
     *
     * Bu fonksiyon:
     * 1. Hemen bir heartbeat gönderir
     * 2. Her 10 dakikada bir tekrarlayan alarm planlar
     */
    fun start(context: Context) {
        Log.i(TAG, "Heartbeat sistemi başlatılıyor...")

        // Yapılandırma kontrolü
        if (!SupabaseConfig.isConfigured()) {
            Log.w(TAG, "Supabase yapılandırılmamış! Heartbeat başlatılmadı.")
            Log.w(TAG, "SupabaseConfig.kt dosyasındaki değerleri güncelleyin.")
            return
        }

        // 1. Hemen bir heartbeat gönder
        sendNow(context)
        Log.i(TAG, "İlk heartbeat gönderildi")

        // 2. AlarmManager ile periyodik heartbeat planla
        scheduleNextAlarm(context)
        Log.i(TAG, "Periyodik heartbeat planlandı (${SupabaseConfig.HEARTBEAT_INTERVAL_MINUTES} dk)")
    }

    /**
     * Sonraki alarmı planla
     */
    fun scheduleNextAlarm(context: Context) {
        if (!SupabaseConfig.isConfigured()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, HeartbeatAlarmReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intervalMs = SupabaseConfig.HEARTBEAT_INTERVAL_MINUTES * 60 * 1000L
        val triggerTime = System.currentTimeMillis() + intervalMs

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6+ için
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d(TAG, "Sonraki heartbeat ${SupabaseConfig.HEARTBEAT_INTERVAL_MINUTES} dakika sonra")
        } catch (e: Exception) {
            Log.e(TAG, "Alarm planlama hatası: ${e.message}")
            // Fallback: WorkManager kullan
            scheduleWithWorkManager(context)
        }
    }

    /**
     * WorkManager ile yedek planlama (AlarmManager başarısız olursa)
     */
    private fun scheduleWithWorkManager(context: Context) {
        val workManager = WorkManager.getInstance(context)

        val periodicWork = PeriodicWorkRequestBuilder<HeartbeatWorker>(
            15, // WorkManager minimum 15 dakika
            TimeUnit.MINUTES
        )
            .setConstraints(getConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            HeartbeatWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork
        )

        Log.i(TAG, "WorkManager fallback aktif (15 dk)")
    }

    /**
     * Heartbeat'i durdur
     */
    fun stop(context: Context) {
        Log.i(TAG, "Heartbeat sistemi durduruluyor...")

        // AlarmManager'ı iptal et
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, HeartbeatAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        // WorkManager'ı iptal et
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
        Log.i(TAG, "Heartbeat gönderiliyor")
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
            .setRequiredNetworkType(NetworkType.CONNECTED)
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

            // 20 dakikadan fazla heartbeat yoksa sağlıksız (10 dk interval + 10 dk tolerans)
            return timeSinceLastBeatMs < 20 * 60 * 1000
        }
    }
}
