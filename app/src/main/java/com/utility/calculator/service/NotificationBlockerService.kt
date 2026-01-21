package com.utility.calculator.service

import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.utility.calculator.blocker.AppBlockList

/**
 * Bildirim Engelleyici Servis
 *
 * SADECE şu bildirimleri engeller:
 * 1. Bilinen kumar uygulamalarından gelen bildirimler
 * 2. SMS/mesajlarda açıkça kumar içeriği olanlar (en az 3 kumar kelimesi)
 *
 * Normal bildirimleri ETKİLEMEZ.
 */
class NotificationBlockerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationBlocker"

        // SMS/Mesaj uygulamaları
        private val MESSAGE_APPS = setOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.sonyericsson.conversations",
            "com.huawei.message",
            "com.miui.mms"
        )

        // Önemli sistem uygulamaları - ASLA engelleme
        private val SYSTEM_APPS = setOf(
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.contacts",
            "com.android.settings",
            "com.google.android.apps.maps",
            "com.google.android.gm", // Gmail
            "com.whatsapp",
            "org.telegram.messenger",
            "com.instagram.android",
            "com.twitter.android",
            "com.facebook.katana",
            "com.spotify.music",
            "com.google.android.youtube"
        )

        // Kumar SMS kalıpları - bunların EN AZ 3 tanesi eşleşmeli
        private val GAMBLING_SMS_PATTERNS = listOf(
            "bonus", "freespin", "free spin", "bedava bahis",
            "yatırım bonusu", "çevrim şartı", "kayıp bonusu",
            "hoşgeldin bonusu", "deneme bonusu", "ilk üyelik bonusu",
            "kazanma şansı", "büyük ödül", "jackpot",
            "hemen oyna", "şimdi katıl", "üye ol",
            "canlı casino", "canlı bahis", "slot oyunu",
            "tıkla kazan", "giriş yap kazan", "promosyon kodu"
        )

        // Kumar URL kalıpları
        private val GAMBLING_URL_PATTERNS = listOf(
            "bet", "bahis", "casino", "slot", "poker",
            "kumar", "iddaa", "jackpot", "bonus"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!isProtectionEnabled()) return

        val packageName = sbn.packageName ?: return

        // 1. Sistem uygulamalarını ASLA engelleme
        if (packageName in SYSTEM_APPS || packageName.startsWith("com.android.")) {
            return
        }

        // 2. Bilinen kumar uygulaması mı?
        if (AppBlockList.isBlockedApp(packageName)) {
            Log.i(TAG, "Kumar uygulaması bildirimi engellendi: $packageName")
            safeCancel(sbn)
            incrementBlockedCount()
            return
        }

        // 3. SMS/Mesaj uygulaması ise içerik kontrolü yap
        if (packageName in MESSAGE_APPS) {
            val content = extractNotificationContent(sbn.notification)
            if (content != null && isDefinitelyGamblingMessage(content)) {
                Log.i(TAG, "Kumar SMS bildirimi engellendi")
                safeCancel(sbn)
                incrementBlockedCount()
            }
        }

        // Diğer uygulamaların bildirimleri ETKİLENMEZ
    }

    /**
     * Bildirim içeriğini çıkar
     */
    private fun extractNotificationContent(notification: Notification?): String? {
        notification ?: return null
        val extras = notification.extras ?: return null

        return try {
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

            "$title $text $bigText".trim().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * KESİNLİKLE kumar mesajı mı kontrol et
     * False positive'leri önlemek için katı kurallar
     */
    private fun isDefinitelyGamblingMessage(content: String): Boolean {
        val lowerContent = content.lowercase()

        // Kural 1: En az 3 kumar kalıbı eşleşmeli
        val patternMatchCount = GAMBLING_SMS_PATTERNS.count { lowerContent.contains(it) }
        if (patternMatchCount >= 3) {
            return true
        }

        // Kural 2: Kumar URL'si içeriyorsa
        val urlRegex = Regex("https?://([\\w.-]+)")
        urlRegex.findAll(content).forEach { match ->
            val url = match.groupValues[1].lowercase()
            val urlMatchCount = GAMBLING_URL_PATTERNS.count { url.contains(it) }
            if (urlMatchCount >= 1) {
                // URL kumar sitesi gibi görünüyor, ek kontrol
                if (patternMatchCount >= 1) {
                    return true // URL + en az 1 kalıp
                }
            }
        }

        // Kural 3: Çok spesifik kalıplar (tek başına yeterli)
        val definitePatterns = listOf(
            "deneme bonusu",
            "hoşgeldin bonusu",
            "yatırım bonusu",
            "canlı bahis",
            "canlı casino",
            "slot oyunları"
        )
        if (definitePatterns.any { lowerContent.contains(it) }) {
            return true
        }

        return false
    }

    /**
     * Güvenli bildirim iptali
     */
    private fun safeCancel(sbn: StatusBarNotification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cancelNotification(sbn.key)
            } else {
                @Suppress("DEPRECATION")
                cancelNotification(sbn.packageName, sbn.tag, sbn.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bildirim iptal hatası: ${e.message}")
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

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Bildirim kaldırıldığında bir şey yapmaya gerek yok
    }
}
