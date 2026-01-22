package com.utility.calculator.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.utility.calculator.heartbeat.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Kullanıcı ve Cihaz Yönetimi
 */
class UserRepository(private val context: Context) {

    companion object {
        private const val TAG = "UserRepository"
        private const val PREFS_NAME = "calc_prefs"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Kullanıcı kayıtlı mı?
     */
    fun isUserRegistered(): Boolean {
        return prefs.getString("user_id", null) != null
    }

    /**
     * Kayıtlı kullanıcı ID'si
     */
    fun getUserId(): String? {
        return prefs.getString("user_id", null)
    }

    /**
     * Cihaz ID'si
     */
    fun getDeviceId(): String {
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            deviceId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    ?: "device_${System.currentTimeMillis()}"
            } catch (e: Exception) {
                "device_${System.currentTimeMillis()}"
            }
            prefs.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }

    /**
     * Yeni kullanıcı ve cihaz kaydı
     */
    suspend fun registerUser(
        firstName: String,
        lastName: String,
        email: String,
        phone: String? = null,
        deviceName: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Önce kullanıcıyı oluştur
            val userId = createUser(firstName, lastName, email, phone)
                ?: return@withContext Result.failure(Exception("Kullanıcı oluşturulamadı"))

            // 2. Cihazı kaydet
            val deviceRegistered = registerDevice(userId, deviceName)
            if (!deviceRegistered) {
                return@withContext Result.failure(Exception("Cihaz kaydedilemedi"))
            }

            // 3. Lokal kaydet
            prefs.edit()
                .putString("user_id", userId)
                .putString("user_first_name", firstName)
                .putString("user_last_name", lastName)
                .putString("user_email", email)
                .putBoolean("is_registered", true)
                .apply()

            Log.i(TAG, "Kullanıcı başarıyla kaydedildi: $userId")
            Result.success(userId)

        } catch (e: Exception) {
            Log.e(TAG, "Kayıt hatası: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Kullanıcı oluştur
     */
    private fun createUser(
        firstName: String,
        lastName: String,
        email: String,
        phone: String?
    ): String? {
        val userData = JSONObject().apply {
            put("first_name", firstName)
            put("last_name", lastName)
            put("email", email)
            if (!phone.isNullOrBlank()) {
                put("phone", phone)
            }
        }

        val mediaType = "application/json".toMediaType()
        val body = userData.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/users")
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .post(body)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val jsonArray = JSONArray(responseBody)
                if (jsonArray.length() > 0) {
                    val user = jsonArray.getJSONObject(0)
                    user.getString("id")
                } else {
                    null
                }
            } else {
                Log.e(TAG, "User create error: ${response.code} - $responseBody")

                // Email zaten varsa, mevcut kullanıcıyı bul
                if (response.code == 409 || responseBody?.contains("duplicate") == true) {
                    Log.i(TAG, "Email zaten kayıtlı, mevcut kullanıcı aranıyor...")
                    findUserByEmail(email)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "User create exception: ${e.message}")
            null
        }
    }

    /**
     * Email ile kullanıcı bul
     */
    private fun findUserByEmail(email: String): String? {
        val request = Request.Builder()
            .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/users?email=eq.$email&select=id")
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val jsonArray = JSONArray(responseBody)
                if (jsonArray.length() > 0) {
                    jsonArray.getJSONObject(0).getString("id")
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Find user error: ${e.message}")
            null
        }
    }

    /**
     * Cihaz kaydet
     */
    private fun registerDevice(userId: String, deviceName: String?): Boolean {
        val deviceId = getDeviceId()

        val deviceData = JSONObject().apply {
            put("user_id", userId)
            put("device_id", deviceId)
            put("device_name", deviceName ?: "${Build.MANUFACTURER} ${Build.MODEL}")
            put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("android_version", Build.VERSION.SDK_INT)
            put("app_version", getAppVersion())
        }

        val mediaType = "application/json".toMediaType()
        val body = deviceData.toString().toRequestBody(mediaType)

        // Upsert kullan (varsa güncelle, yoksa oluştur)
        val request = Request.Builder()
            .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/devices")
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates")
            .post(body)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val isSuccess = response.isSuccessful || response.code == 409

            if (!isSuccess) {
                Log.e(TAG, "Device register error: ${response.code} - ${response.body?.string()}")
            }

            response.close()
            isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Device register exception: ${e.message}")
            false
        }
    }

    /**
     * Cihaz son görülme zamanını güncelle
     */
    suspend fun updateDeviceLastSeen() = withContext(Dispatchers.IO) {
        val deviceId = getDeviceId()

        val updateData = JSONObject().apply {
            put("last_seen_at", "now()")
        }

        val mediaType = "application/json".toMediaType()
        val body = updateData.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/devices?device_id=eq.$deviceId")
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            .addHeader("Content-Type", "application/json")
            .patch(body)
            .build()

        try {
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.e(TAG, "Update last seen error: ${e.message}")
        }
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
     * Kayıtlı kullanıcı bilgileri
     */
    fun getUserInfo(): UserInfo? {
        if (!isUserRegistered()) return null

        return UserInfo(
            id = prefs.getString("user_id", "") ?: "",
            firstName = prefs.getString("user_first_name", "") ?: "",
            lastName = prefs.getString("user_last_name", "") ?: "",
            email = prefs.getString("user_email", "") ?: ""
        )
    }

    data class UserInfo(
        val id: String,
        val firstName: String,
        val lastName: String,
        val email: String
    ) {
        val fullName: String get() = "$firstName $lastName"
    }
}
