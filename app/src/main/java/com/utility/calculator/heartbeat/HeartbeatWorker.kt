package com.utility.calculator.heartbeat

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Heartbeat Worker
 *
 * Supabase'e heartbeat gönderir.
 * Gelişmiş retry mekanizması:
 * - Exponential backoff (her denemede bekleme süresi 2x artar)
 * - Maksimum 5 deneme
 * - Başarısız heartbeat'leri yerel olarak saklar ve sonra gönderir
 */
class HeartbeatWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "HeartbeatWorker"
        const val WORK_NAME = "heartbeat_work"

        // Retry ayarları
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val INITIAL_BACKOFF_MS = 1000L // 1 saniye
        private const val MAX_BACKOFF_MS = 60000L // 60 saniye

        // Yerel kuyruk limiti
        private const val MAX_QUEUED_HEARTBEATS = 50
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Yapılandırma kontrolü
            if (!SupabaseConfig.isConfigured()) {
                Log.w(TAG, "Supabase yapılandırılmamış, heartbeat atlanıyor")
                return@withContext Result.success()
            }

            // Önce bekleyen heartbeat'leri gönder
            sendQueuedHeartbeats()

            // Yeni heartbeat verilerini topla
            val heartbeatData = collectHeartbeatData()

            // Retry mekanizması ile gönder
            val success = sendWithRetry(heartbeatData)

            if (success) {
                Log.i(TAG, "Heartbeat başarıyla gönderildi")
                saveLastHeartbeatTime()
                Result.success()
            } else {
                // Başarısız oldu, yerel kuyruğa ekle
                Log.w(TAG, "Heartbeat gönderilemedi, kuyruğa ekleniyor")
                queueHeartbeat(heartbeatData)
                Result.success() // Worker'ı başarılı say, sonraki çalışmada tekrar denenecek
            }
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat hatası: ${e.message}")
            Result.retry()
        }
    }

    /**
     * Exponential backoff ile gönderim
     */
    private suspend fun sendWithRetry(data: JSONObject): Boolean {
        var currentDelay = INITIAL_BACKOFF_MS
        var attempt = 0

        while (attempt < MAX_RETRY_ATTEMPTS) {
            attempt++
            Log.d(TAG, "Gönderim denemesi $attempt/$MAX_RETRY_ATTEMPTS")

            val success = sendHeartbeat(data)
            if (success) {
                if (attempt > 1) {
                    Log.i(TAG, "Heartbeat $attempt. denemede başarılı oldu")
                }
                return true
            }

            // Son deneme değilse bekle
            if (attempt < MAX_RETRY_ATTEMPTS) {
                Log.d(TAG, "Bekleniyor: ${currentDelay}ms")
                delay(currentDelay)

                // Exponential backoff: her seferinde 2 katına çık
                currentDelay = (currentDelay * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }

        Log.e(TAG, "Tüm denemeler başarısız ($MAX_RETRY_ATTEMPTS deneme)")
        return false
    }

    /**
     * Başarısız heartbeat'i yerel kuyruğa ekle
     */
    private fun queueHeartbeat(data: JSONObject) {
        try {
            val prefs = context.getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
            val queueJson = prefs.getString("heartbeat_queue", "[]") ?: "[]"
            val queue = JSONArray(queueJson)

            // Timestamp ekle
            data.put("queued_at", System.currentTimeMillis())

            // Kuyruğa ekle
            queue.put(data)

            // Kuyruk limitini aş
            while (queue.length() > MAX_QUEUED_HEARTBEATS) {
                queue.remove(0) // En eski olanı sil
            }

            prefs.edit().putString("heartbeat_queue", queue.toString()).apply()
            Log.d(TAG, "Heartbeat kuyruğa eklendi. Kuyruk boyutu: ${queue.length()}")
        } catch (e: Exception) {
            Log.e(TAG, "Kuyruğa ekleme hatası: ${e.message}")
        }
    }

    /**
     * Bekleyen heartbeat'leri gönder
     */
    private suspend fun sendQueuedHeartbeats() {
        try {
            val prefs = context.getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
            val queueJson = prefs.getString("heartbeat_queue", "[]") ?: "[]"
            val queue = JSONArray(queueJson)

            if (queue.length() == 0) return

            Log.i(TAG, "${queue.length()} bekleyen heartbeat gönderiliyor...")

            val remainingQueue = JSONArray()
            var successCount = 0

            for (i in 0 until queue.length()) {
                val heartbeat = queue.getJSONObject(i)

                // queued_at alanını kaldır (Supabase'e gönderme)
                heartbeat.remove("queued_at")

                val success = sendHeartbeat(heartbeat)
                if (success) {
                    successCount++
                } else {
                    // Başarısız olanları geri ekle
                    remainingQueue.put(heartbeat)
                }

                // Her gönderimden sonra kısa bir bekleme
                if (i < queue.length() - 1) {
                    delay(500)
                }
            }

            // Kalan kuyruğu kaydet
            prefs.edit().putString("heartbeat_queue", remainingQueue.toString()).apply()

            if (successCount > 0) {
                Log.i(TAG, "$successCount/${queue.length()} bekleyen heartbeat gönderildi")
            }
            if (remainingQueue.length() > 0) {
                Log.w(TAG, "${remainingQueue.length()} heartbeat hala bekliyor")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kuyruk gönderimi hatası: ${e.message}")
        }
    }

    /**
     * Heartbeat verilerini topla
     */
    private fun collectHeartbeatData(): JSONObject {
        val prefs = context.getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)

        return JSONObject().apply {
            put("device_id", getDeviceId())
            put("app_version", getAppVersion())
            put("protection_enabled", prefs.getBoolean("protection_enabled", false))
            put("vpn_active", isVpnActive())
            put("accessibility_active", isAccessibilityActive())
            put("blocked_count", prefs.getInt("blocked_count", 0))
            put("battery_level", getBatteryLevel())
            put("is_charging", isCharging())
            put("android_version", Build.VERSION.SDK_INT)
            put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
        }
    }

    /**
     * Benzersiz cihaz ID'si al
     */
    private fun getDeviceId(): String {
        val prefs = context.getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)

        val savedId = prefs.getString("device_id", null)
        if (savedId != null) {
            return savedId
        }

        val newId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: generateRandomId()
        } catch (e: Exception) {
            generateRandomId()
        }

        prefs.edit().putString("device_id", newId).apply()
        return newId
    }

    private fun generateRandomId(): String {
        return "device_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    private fun getAppVersion(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    private fun isVpnActive(): Boolean {
        return try {
            val prefs = context.getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
            prefs.getBoolean("vpn_active", false)
        } catch (e: Exception) {
            false
        }
    }

    private fun isAccessibilityActive(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            enabledServices.contains(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    private fun getBatteryLevel(): Int {
        return try {
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            (level * 100 / scale)
        } catch (e: Exception) {
            -1
        }
    }

    private fun isCharging(): Boolean {
        return try {
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Supabase'e heartbeat gönder
     */
    private fun sendHeartbeat(data: JSONObject): Boolean {
        val mediaType = "application/json".toMediaType()
        val body = data.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(SupabaseConfig.getApiEndpoint())
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=minimal")
            .post(body)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val isSuccess = response.isSuccessful
            val code = response.code

            if (!isSuccess) {
                Log.w(TAG, "HTTP hatası: $code")
            }

            response.close()
            isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "HTTP isteği başarısız: ${e.message}")
            false
        }
    }

    /**
     * Son heartbeat zamanını kaydet
     */
    private fun saveLastHeartbeatTime() {
        try {
            val prefs = context.getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
            prefs.edit().putLong("last_heartbeat", System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
