package com.utility.calculator.service

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.utility.calculator.blocker.BlockList

/**
 * Clipboard İzleme Servisi
 *
 * SADECE kumar sitesi URL'lerini tespit eder ve temizler.
 * Normal metinleri ETKİLEMEZ.
 */
class ClipboardMonitorService : Service() {

    companion object {
        private const val TAG = "ClipboardMonitor"

        // URL olduğunu gösteren kalıplar
        private val URL_INDICATORS = listOf(
            "http://", "https://", "www."
        )

        // Domain uzantıları
        private val DOMAIN_EXTENSIONS = listOf(
            ".com", ".net", ".org", ".xyz", ".io", ".bet",
            ".casino", ".poker", ".info", ".site", ".online"
        )
    }

    private lateinit var clipboardManager: ClipboardManager
    private var lastClipHash = 0

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        checkClipboard()
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        Log.i(TAG, "Clipboard izleme başlatıldı")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    /**
     * Panoyu kontrol et - SADECE URL'leri kontrol eder
     */
    private fun checkClipboard() {
        if (!isProtectionEnabled()) return

        try {
            val clip = clipboardManager.primaryClip ?: return
            if (clip.itemCount == 0) return

            val item = clip.getItemAt(0)
            val text = item.text?.toString() ?: return

            // Hash kontrolü - aynı içerik tekrar kontrol edilmesin
            val currentHash = text.hashCode()
            if (currentHash == lastClipHash) return
            lastClipHash = currentHash

            // SADECE URL içeren metinleri kontrol et
            if (!looksLikeUrl(text)) {
                return // Normal metin, dokunma
            }

            // URL'yi çıkar ve kontrol et
            val url = extractUrl(text)
            if (url != null && BlockList.shouldBlock(url)) {
                Log.i(TAG, "Kumar URL'si tespit edildi: $url")

                // Panoyu temizle
                clearClipboard()

                // Kullanıcıyı bilgilendir
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        this,
                        "Kumar sitesi linki engellendi",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                incrementBlockedCount()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard kontrolü hatası: ${e.message}")
        }
    }

    /**
     * Metin URL gibi görünüyor mu?
     */
    private fun looksLikeUrl(text: String): Boolean {
        val lowerText = text.lowercase().trim()

        // URL başlangıcı kontrolü
        if (URL_INDICATORS.any { lowerText.contains(it) }) {
            return true
        }

        // Domain uzantısı kontrolü (www olmadan da olabilir)
        if (DOMAIN_EXTENSIONS.any { lowerText.contains(it) }) {
            // Boşluk yoksa muhtemelen URL
            if (!text.contains(" ") || text.length < 100) {
                return true
            }
        }

        return false
    }

    /**
     * Metinden URL'yi çıkar
     */
    private fun extractUrl(text: String): String? {
        // URL regex
        val urlRegex = Regex(
            "(https?://)?([\\w-]+\\.)+[\\w-]+(/[\\w-./?%&=]*)?"
        )

        val match = urlRegex.find(text)
        return match?.value
    }

    /**
     * Panoyu temizle
     */
    private fun clearClipboard() {
        try {
            val emptyClip = ClipData.newPlainText("", "")
            clipboardManager.setPrimaryClip(emptyClip)
            lastClipHash = "".hashCode()
        } catch (e: Exception) {
            Log.e(TAG, "Pano temizleme hatası: ${e.message}")
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

    private fun incrementBlockedCount() {
        try {
            val prefs = getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
            val current = prefs.getInt("blocked_count", 0)
            prefs.edit().putInt("blocked_count", current + 1).apply()
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        } catch (e: Exception) {
            // Ignore
        }
        super.onDestroy()
    }
}
