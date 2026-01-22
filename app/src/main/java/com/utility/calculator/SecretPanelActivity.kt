package com.utility.calculator

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.utility.calculator.admin.DeviceAdminReceiver
import com.utility.calculator.blocker.BlockList
import com.utility.calculator.data.UserRepository
import com.utility.calculator.heartbeat.HeartbeatManager
import com.utility.calculator.heartbeat.SupabaseConfig
import com.utility.calculator.service.*

/**
 * Gizli Kontrol Paneli
 * Erişim: Hesap makinesinde 159753 + = tuşu
 */
class SecretPanelActivity : AppCompatActivity() {

    private lateinit var protectionSwitch: Switch
    private lateinit var statusText: TextView
    private lateinit var blockedCountText: TextView
    private lateinit var vpnStatusText: TextView
    private lateinit var accessibilityStatusText: TextView
    private lateinit var notificationStatusText: TextView
    private lateinit var adminStatusText: TextView
    private lateinit var heartbeatStatusText: TextView

    private lateinit var userRepository: UserRepository

    companion object {
        private const val VPN_REQUEST_CODE = 100
        private const val ADMIN_REQUEST_CODE = 101
        private const val REGISTRATION_REQUEST_CODE = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secret_panel)

        userRepository = UserRepository(this)

        initViews()
        setupListeners()
        updateAllStatus()
    }

    private fun initViews() {
        protectionSwitch = findViewById(R.id.protectionSwitch)
        statusText = findViewById(R.id.statusText)
        blockedCountText = findViewById(R.id.blockedCountText)
        vpnStatusText = findViewById(R.id.vpnStatusText)
        accessibilityStatusText = findViewById(R.id.accessibilityStatusText)
        notificationStatusText = findViewById(R.id.notificationStatusText)
        adminStatusText = findViewById(R.id.adminStatusText)
        heartbeatStatusText = findViewById(R.id.heartbeatStatusText)
    }

    private fun setupListeners() {
        protectionSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                enableAllProtection()
            } else {
                showDisableConfirmation()
            }
        }

        // VPN ayarları
        findViewById<Button>(R.id.btnVpnSettings).setOnClickListener {
            enableVpn()
        }

        // Accessibility ayarları
        findViewById<Button>(R.id.btnAccessibilitySettings).setOnClickListener {
            openAccessibilitySettings()
        }

        // Bildirim ayarları
        findViewById<Button>(R.id.btnNotificationSettings).setOnClickListener {
            openNotificationSettings()
        }

        // Device Admin
        findViewById<Button>(R.id.btnAdminSettings).setOnClickListener {
            requestDeviceAdmin()
        }

        // İstatistikler
        findViewById<Button>(R.id.btnViewStats).setOnClickListener {
            showStatistics()
        }

        // Engelleme listesi
        findViewById<Button>(R.id.btnViewBlockedSites).setOnClickListener {
            showBlockedKeywords()
        }
    }

    private fun updateAllStatus() {
        val isEnabled = isProtectionEnabled()
        protectionSwitch.isChecked = isEnabled

        // Ana durum
        statusText.text = if (isEnabled) "KORUMA AKTİF ✓" else "KORUMA KAPALI ✗"
        statusText.setTextColor(getColor(
            if (isEnabled) android.R.color.holo_green_dark
            else android.R.color.holo_red_dark
        ))

        // Engelleme sayısı
        val blockedCount = getBlockedCount()
        blockedCountText.text = "Toplam engellenen: $blockedCount"

        // VPN durumu
        vpnStatusText.text = if (isVpnActive()) "✓ Aktif" else "✗ Kapalı"
        vpnStatusText.setTextColor(getColor(
            if (isVpnActive()) android.R.color.holo_green_dark
            else android.R.color.holo_red_dark
        ))

        // Accessibility durumu
        accessibilityStatusText.text = if (isAccessibilityEnabled()) "✓ Aktif" else "✗ Kapalı"
        accessibilityStatusText.setTextColor(getColor(
            if (isAccessibilityEnabled()) android.R.color.holo_green_dark
            else android.R.color.holo_red_dark
        ))

        // Bildirim izleme durumu
        notificationStatusText.text = if (isNotificationListenerEnabled()) "✓ Aktif" else "✗ Kapalı"
        notificationStatusText.setTextColor(getColor(
            if (isNotificationListenerEnabled()) android.R.color.holo_green_dark
            else android.R.color.holo_red_dark
        ))

        // Device Admin durumu
        adminStatusText.text = if (isDeviceAdminActive()) "✓ Aktif" else "✗ Kapalı"
        adminStatusText.setTextColor(getColor(
            if (isDeviceAdminActive()) android.R.color.holo_green_dark
            else android.R.color.holo_red_dark
        ))

        // Heartbeat durumu
        val heartbeatStatus = HeartbeatManager.getStatus(this)
        val heartbeatText = if (heartbeatStatus.isConfigured) {
            heartbeatStatus.getStatusText()
        } else {
            "Yapılandırılmamış"
        }
        heartbeatStatusText.text = heartbeatText
        heartbeatStatusText.setTextColor(getColor(
            if (heartbeatStatus.isHealthy()) android.R.color.holo_green_dark
            else if (heartbeatStatus.isConfigured) android.R.color.holo_orange_dark
            else android.R.color.holo_red_dark
        ))
    }

    // ==================== KORUMA YÖNETİMİ ====================

    private fun enableAllProtection() {
        // Önce kullanıcı kayıtlı mı kontrol et
        if (!userRepository.isUserRegistered()) {
            // Kayıtlı değil, kayıt ekranına yönlendir
            protectionSwitch.isChecked = false
            val intent = Intent(this, RegistrationActivity::class.java)
            startActivityForResult(intent, REGISTRATION_REQUEST_CODE)
            return
        }

        // Kayıtlı, korumayı başlat
        enableVpn()
    }

    private fun enableVpn() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE)
        } else {
            startAllServices()
        }
    }

    private fun startAllServices() {
        // VPN servisi
        startForegroundService(Intent(this, BlockerVpnService::class.java))

        // Clipboard izleme
        startService(Intent(this, ClipboardMonitorService::class.java))

        // VPN algılama
        startService(Intent(this, VpnDetectorService::class.java).apply {
            putExtra("our_vpn_active", true)
        })

        // Heartbeat başlat
        HeartbeatManager.start(this)

        setProtectionEnabled(true)
        updateAllStatus()

        // Accessibility ve Notification izinlerini kontrol et
        if (!isAccessibilityEnabled()) {
            showAccessibilityPrompt()
        } else if (!isNotificationListenerEnabled()) {
            showNotificationPrompt()
        } else {
            Toast.makeText(this, "Tüm korumalar aktif!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAccessibilityPrompt() {
        AlertDialog.Builder(this)
            .setTitle("Uygulama İzleme")
            .setMessage("Kumar uygulamalarını engellemek için erişilebilirlik izni gerekli.\n\nAyarlara gidilsin mi?")
            .setPositiveButton("Evet") { _, _ -> openAccessibilitySettings() }
            .setNegativeButton("Sonra") { _, _ ->
                if (!isNotificationListenerEnabled()) {
                    showNotificationPrompt()
                }
            }
            .show()
    }

    private fun showNotificationPrompt() {
        AlertDialog.Builder(this)
            .setTitle("Bildirim Engelleme")
            .setMessage("Kumar bildirimlerini engellemek için bildirim erişimi gerekli.\n\nAyarlara gidilsin mi?")
            .setPositiveButton("Evet") { _, _ -> openNotificationSettings() }
            .setNegativeButton("Sonra", null)
            .show()
    }

    private fun showDisableConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Korumayı Kapat")
            .setMessage("Korumayı kapatmak istediğinizden emin misiniz?\n\nBu, kumar sitelerine ve uygulamalarına erişimi açacaktır.")
            .setPositiveButton("Evet, Kapat") { _, _ ->
                showSecondConfirmation()
            }
            .setNegativeButton("İptal") { _, _ ->
                protectionSwitch.isChecked = true
            }
            .setCancelable(false)
            .show()
    }

    private fun showSecondConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Son Onay")
            .setMessage("24 saat bekleme süresi başlayacak.\n\nKoruma 24 saat sonra kapanacak.\n\nDevam?")
            .setPositiveButton("Evet") { _, _ ->
                // Gerçek uygulamada 24 saat timer
                disableProtection()
            }
            .setNegativeButton("Vazgeç") { _, _ ->
                protectionSwitch.isChecked = true
            }
            .setCancelable(false)
            .show()
    }

    private fun disableProtection() {
        stopService(Intent(this, BlockerVpnService::class.java))
        stopService(Intent(this, ClipboardMonitorService::class.java))
        stopService(Intent(this, VpnDetectorService::class.java))

        // Heartbeat'i DURDURMUYORUZ - uygulama kapatıldığında bile
        // heartbeat devam etmeli ki durumu takip edebilelim
        // HeartbeatManager.stop(this)

        setProtectionEnabled(false)
        updateAllStatus()
        Toast.makeText(this, "Koruma devre dışı", Toast.LENGTH_SHORT).show()
    }

    // ==================== AYARLAR ====================

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "\"Hesap Makinesi\" servisini bulup açın", Toast.LENGTH_LONG).show()
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "\"Hesap Makinesi\" uygulamasını bulup izin verin", Toast.LENGTH_LONG).show()
    }

    private fun requestDeviceAdmin() {
        val componentName = ComponentName(this, DeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Bu izin uygulamanın yanlışlıkla silinmesini önler.")
        }
        startActivityForResult(intent, ADMIN_REQUEST_CODE)
    }

    // ==================== İSTATİSTİKLER ====================

    private fun showStatistics() {
        val prefs = getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
        val blockedCount = prefs.getInt("blocked_count", 0)
        val blockLogs = prefs.getString("block_logs", "") ?: ""
        val installLogs = prefs.getString("install_logs", "") ?: ""
        val vpnLogs = prefs.getString("vpn_logs", "") ?: ""

        val recentBlocks = blockLogs.lines().takeLast(5).joinToString("\n")
        val recentInstalls = installLogs.lines().takeLast(3).joinToString("\n")

        // Heartbeat durumu
        val heartbeatStatus = HeartbeatManager.getStatus(this)
        val heartbeatInfo = if (heartbeatStatus.isConfigured) {
            """
            Cihaz ID: ${heartbeatStatus.deviceId ?: "Bilinmiyor"}
            Son sinyal: ${heartbeatStatus.getStatusText()}
            Durum: ${if (heartbeatStatus.isHealthy()) "Sağlıklı ✓" else "Dikkat ⚠️"}
            """.trimIndent()
        } else {
            "Yapılandırılmamış - SupabaseConfig.kt dosyasını güncelleyin"
        }

        val message = """
            📊 İSTATİSTİKLER

            Toplam engelleme: $blockedCount

            📋 Son engellemeler:
            ${if (recentBlocks.isNotEmpty()) recentBlocks else "Yok"}

            📦 Tespit edilen kumar uygulamaları:
            ${if (recentInstalls.isNotEmpty()) recentInstalls else "Yok"}

            🔒 Harici VPN tespiti:
            ${if (vpnLogs.isNotEmpty()) "${vpnLogs.lines().size} kez" else "Yok"}

            💓 HEARTBEAT
            $heartbeatInfo
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("İstatistikler")
            .setMessage(message)
            .setPositiveButton("Tamam", null)
            .setNeutralButton("Sıfırla") { _, _ ->
                prefs.edit()
                    .putInt("blocked_count", 0)
                    .putString("block_logs", "")
                    .putString("install_logs", "")
                    .putString("vpn_logs", "")
                    .apply()
                updateAllStatus()
                Toast.makeText(this, "İstatistikler sıfırlandı", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showBlockedKeywords() {
        val keywords = BlockList.BLOCKED_KEYWORDS.take(50).sorted()
        val domains = BlockList.BLOCKED_DOMAINS.take(30).sorted()

        val message = """
            🚫 ENGELLENEN KELİMELER (${BlockList.BLOCKED_KEYWORDS.size} adet)

            ${keywords.joinToString(", ")}
            ...ve daha fazlası

            🌐 ENGELLENEN DOMAİNLER (${BlockList.BLOCKED_DOMAINS.size} adet)

            ${domains.joinToString("\n")}
            ...ve daha fazlası
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Engelleme Listesi")
            .setMessage(message)
            .setPositiveButton("Tamam", null)
            .show()
    }

    // ==================== DURUM KONTROL ====================

    private fun isProtectionEnabled(): Boolean {
        val prefs = getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("protection_enabled", false)
    }

    private fun setProtectionEnabled(enabled: Boolean) {
        val prefs = getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("protection_enabled", enabled).apply()
    }

    private fun getBlockedCount(): Int {
        val prefs = getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("blocked_count", 0)
    }

    private fun isVpnActive(): Boolean {
        // Basit kontrol: servis çalışıyor mu
        return isProtectionEnabled()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return listeners?.contains(packageName) == true
    }

    private fun isDeviceAdminActive(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, DeviceAdminReceiver::class.java)
        return dpm.isAdminActive(componentName)
    }

    // ==================== ACTIVITY RESULT ====================

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            VPN_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    startAllServices()
                } else {
                    protectionSwitch.isChecked = false
                    Toast.makeText(this, "VPN izni gerekli", Toast.LENGTH_SHORT).show()
                }
            }
            ADMIN_REQUEST_CODE -> {
                updateAllStatus()
            }
            REGISTRATION_REQUEST_CODE -> {
                if (resultCode == RegistrationActivity.RESULT_REGISTERED) {
                    // Kayıt başarılı, korumayı başlat
                    Toast.makeText(this, "Kayıt tamamlandı!", Toast.LENGTH_SHORT).show()
                    protectionSwitch.isChecked = true
                    enableVpn()
                } else {
                    // Kayıt iptal edildi
                    protectionSwitch.isChecked = false
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateAllStatus()
    }
}
