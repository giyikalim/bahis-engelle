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
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Heartbeat Worker
 *
 * Her 15 dakikada bir Supabase'e heartbeat gönderir.
 * Böylece uygulama çalışıyor mu, durmuş mu takip edilebilir.
 */
class HeartbeatWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "HeartbeatWorker"
        const val WORK_NAME = "heartbeat_work"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Yapılandırma kontrolü
            if (!SupabaseConfig.isConfigured()) {
                Log.w(TAG, "Supabase yapılandırılmamış, heartbeat atlanıyor")
                return@withContext Result.success()
            }

            // Heartbeat verilerini topla
            val heartbeatData = collectHeartbeatData()

            // Supabase'e gönder
            val success = sendHeartbeat(heartbeatData)

            if (success) {
                Log.i(TAG, "Heartbeat başarıyla gönderildi")
                saveLastHeartbeatTime()
                Result.success()
            } else {
                Log.w(TAG, "Heartbeat gönderilemedi, tekrar denenecek")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat hatası: ${e.message}")
            Result.retry()
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

        // Önce kayıtlı ID'yi kontrol et
        var deviceId = prefs.getString("device_id", null)

        if (deviceId == null) {
            // Yeni ID oluştur
            deviceId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    ?: generateRandomId()
            } catch (e: Exception) {
                generateRandomId()
            }

            // Kaydet
            prefs.edit().putString("device_id", deviceId).apply()
        }

        return deviceId
    }

    private fun generateRandomId(): String {
        return "device_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    /**
     * Uygulama versiyonu
     */
    private fun getAppVersion(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    /**
     * VPN aktif mi?
     */
    private fun isVpnActive(): Boolean {
        return try {
            val prefs = context.getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
            prefs.getBoolean("vpn_active", false)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Accessibility Service aktif mi?
     */
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

    /**
     * Pil seviyesi
     */
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

    /**
     * Şarj oluyor mu?
     */
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
            response.close()
            isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "HTTP hatası: ${e.message}")
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
