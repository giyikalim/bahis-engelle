package com.utility.calculator.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.utility.calculator.blocker.AppBlockList

/**
 * Paket Yükleme İzleyici
 *
 * Yeni uygulama yüklendiğinde kontrol eder.
 * Kumar uygulaması tespit edilirse kullanıcıyı uyarır.
 */
class PackageInstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PackageInstall"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!isProtectionEnabled(context)) return

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                handlePackageInstalled(context, intent)
            }
            Intent.ACTION_PACKAGE_REPLACED -> {
                handlePackageInstalled(context, intent)
            }
        }
    }

    private fun handlePackageInstalled(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return

        Log.i(TAG, "Yeni uygulama yüklendi: $packageName")

        if (AppBlockList.isBlockedApp(packageName)) {
            Log.w(TAG, "KUMAR UYGULAMASI TESPİT EDİLDİ: $packageName")

            // Kullanıcıyı uyar
            Toast.makeText(
                context,
                "⚠️ Kumar uygulaması tespit edildi!\nKaldırmanız önerilir.",
                Toast.LENGTH_LONG
            ).show()

            // Engelleme sayısını artır
            incrementBlockedCount(context)

            // Log kaydet
            saveInstallLog(context, packageName)

            // Uygulamayı kaldırma ekranını aç (opsiyonel)
            // promptUninstall(context, packageName)
        }
    }

    /**
     * Uygulamayı kaldırma ekranını aç
     */
    private fun promptUninstall(context: Context, packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Kaldırma ekranı açılamadı", e)
        }
    }

    private fun isProtectionEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("protection_enabled", false)
    }

    private fun incrementBlockedCount(context: Context) {
        val prefs = context.getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
        val current = prefs.getInt("blocked_count", 0)
        prefs.edit().putInt("blocked_count", current + 1).apply()
    }

    private fun saveInstallLog(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
        val logs = prefs.getString("install_logs", "") ?: ""
        val timestamp = System.currentTimeMillis()
        val newLog = "$timestamp|$packageName|INSTALLED\n"
        prefs.edit().putString("install_logs", logs + newLog).apply()
    }
}
