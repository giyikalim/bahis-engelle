package com.utility.calculator.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.utility.calculator.blocker.AppBlockList
import com.utility.calculator.blocker.BlockList

/**
 * Gelişmiş Accessibility Service
 *
 * 1. Kumar uygulamalarını engeller
 * 2. Tarayıcıdaki URL'leri kontrol eder
 * 3. SAYFA İÇERİĞİNİ ANALİZ EDER - Kumar içeriği tespit edilirse engeller
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AppBlocker"
        private const val CONTENT_CHECK_DELAY = 2000L // Sayfa yüklenmesi için 2 saniye bekle

        // İzlenecek tarayıcı paketleri
        private val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.brave.browser",
            "com.microsoft.emmx",
            "com.UCMobile.intl",
            "com.sec.android.app.sbrowser",
            "com.huawei.browser",
            "com.mi.globalbrowser",
            "com.yandex.browser",
            "com.duckduckgo.mobile.android"
        )

        // Kumar içeriği tespit kelimeleri (sayfa içinde aranacak)
        private val GAMBLING_CONTENT_KEYWORDS = setOf(
            // Türkçe
            "bahis yap", "bahis oyna", "canlı bahis", "spor bahisleri",
            "casino oyunları", "canlı casino", "slot oyunları", "rulet oyna",
            "poker oyna", "blackjack oyna", "yatırım bonusu", "hoşgeldin bonusu",
            "deneme bonusu", "çevrim şartı", "kayıp bonusu", "freespin",
            "jackpot", "şans oyunları", "kumar", "iddaa kupon",
            "canlı maç izle bahis", "bahis oranları", "kupon yap",
            "para yatır", "para çek", "minimum yatırım", "maksimum kazanç",

            // İngilizce
            "place your bet", "betting odds", "live betting", "sports betting",
            "casino games", "live casino", "slot machines", "play roulette",
            "play poker", "play blackjack", "welcome bonus", "deposit bonus",
            "free spins", "wagering requirements", "cashback bonus",
            "gambling", "place bet", "bet now", "join now bonus",
            "win big", "jackpot winner", "lucky spin"
        )

        // Kesin kumar göstergeleri (bunlardan 1 tanesi bile varsa kumar sitesi)
        private val DEFINITE_GAMBLING_INDICATORS = setOf(
            "bahis kuponu", "kupon yap", "iddaa kuponu",
            "canlı bahis yap", "bahis oranları",
            "casino kayıt", "slot oyna", "rulet oyna",
            "hoşgeldin bonusu al", "deneme bonusu al",
            "bet now", "place your bet", "betting slip",
            "deposit and play", "gambling license"
        )

        // SİSTEM UYGULAMALARI - ASLA ENGELLEME (Whitelist)
        private val SYSTEM_APPS_WHITELIST = setOf(
            // Temel sistem
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.launcher",
            "com.android.launcher3",

            // Telefon & Rehber
            "com.android.contacts",
            "com.android.dialer",
            "com.android.phone",
            "com.android.server.telecom",
            "com.google.android.contacts",
            "com.google.android.dialer",
            "com.samsung.android.contacts",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",

            // Mesajlaşma
            "com.android.mms",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",

            // Kamera & Galeri
            "com.android.camera",
            "com.android.camera2",
            "com.google.android.GoogleCamera",
            "com.samsung.android.camera",
            "com.android.gallery3d",
            "com.google.android.apps.photos",
            "com.samsung.android.gallery",

            // Temel Google uygulamaları
            "com.google.android.gm",           // Gmail
            "com.google.android.apps.maps",    // Maps
            "com.google.android.youtube",      // YouTube
            "com.google.android.calendar",     // Calendar
            "com.google.android.deskclock",    // Clock
            "com.google.android.apps.docs",    // Drive
            "com.google.android.keep",         // Keep

            // Sosyal medya (popüler, kumar değil)
            "com.whatsapp",
            "org.telegram.messenger",
            "com.instagram.android",
            "com.twitter.android",
            "com.facebook.katana",
            "com.facebook.orca",               // Messenger
            "com.linkedin.android",
            "com.snapchat.android",
            "com.zhiliaoapp.musically",        // TikTok

            // Müzik & Medya
            "com.spotify.music",
            "com.google.android.music",
            "com.apple.android.music",
            "com.amazon.mp3",

            // Diğer önemli uygulamalar
            "com.android.vending",             // Play Store
            "com.android.documentsui",         // Files
            "com.android.calculator2",         // Calculator
            "com.android.calendar",
            "com.android.deskclock",
            "com.android.email",

            // Bizim uygulama
            "com.utility.calculator"
        )
    }

    private var lastBlockedApp = ""
    private var lastBlockTime = 0L
    private var lastCheckedUrl = ""
    private val handler = Handler(Looper.getMainLooper())
    private var contentCheckRunnable: Runnable? = null

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
        Log.i(TAG, "Accessibility Service bağlandı - İçerik analizi aktif")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!isProtectionEnabled()) return

        val packageName = event.packageName?.toString() ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowChange(packageName, event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (packageName in BROWSER_PACKAGES) {
                    // Sayfa içeriği değişti, analiz planla
                    scheduleContentAnalysis(packageName)
                }
            }
        }
    }

    /**
     * Pencere değişikliği - Uygulama açıldığında kontrol et
     */
    private fun handleWindowChange(packageName: String, event: AccessibilityEvent) {
        // 1. ÖNCELİKLE: Sistem uygulamaları whitelist kontrolü
        if (isWhitelistedApp(packageName)) {
            return // Güvenli uygulama, engelleme
        }

        // 2. Kumar uygulaması kontrolü
        if (AppBlockList.isBlockedApp(packageName)) {
            blockApp(packageName, "Kumar uygulaması")
            return
        }

        // 3. Paket adında kumar kelimesi kontrolü
        if (BlockList.shouldBlock(packageName)) {
            blockApp(packageName, "Şüpheli uygulama adı")
            return
        }

        // Tarayıcıda URL ve içerik kontrolü
        if (packageName in BROWSER_PACKAGES) {
            checkBrowserUrlAndContent()
        }
    }

    /**
     * İçerik analizini planla (debounce)
     */
    private fun scheduleContentAnalysis(packageName: String) {
        contentCheckRunnable?.let { handler.removeCallbacks(it) }

        contentCheckRunnable = Runnable {
            if (packageName in BROWSER_PACKAGES) {
                checkBrowserUrlAndContent()
            }
        }

        handler.postDelayed(contentCheckRunnable!!, CONTENT_CHECK_DELAY)
    }

    /**
     * Tarayıcı URL ve içerik kontrolü
     */
    private fun checkBrowserUrlAndContent() {
        val rootNode = rootInActiveWindow ?: return

        try {
            // 1. URL kontrolü
            val url = findUrlInBrowser(rootNode)
            if (url != null && url != lastCheckedUrl) {
                lastCheckedUrl = url

                if (BlockList.shouldBlock(url)) {
                    Log.i(TAG, "URL ENGELLENDİ: $url")
                    blockAndGoHome("Kumar sitesi URL'si")
                    return
                }
            }

            // 2. SAYFA İÇERİĞİ ANALİZİ
            val pageContent = extractPageContent(rootNode)
            if (pageContent.isNotEmpty()) {
                val gamblingScore = analyzeContentForGambling(pageContent)

                if (gamblingScore >= 100) {
                    Log.i(TAG, "İÇERİK ANALİZİ - KUMAR TESPİT EDİLDİ (skor: $gamblingScore)")
                    Log.i(TAG, "URL: $url")
                    blockAndGoHome("Kumar içeriği tespit edildi")
                    return
                } else if (gamblingScore >= 50) {
                    Log.w(TAG, "İÇERİK ANALİZİ - ŞÜPHELİ (skor: $gamblingScore) - $url")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "İçerik analizi hatası: ${e.message}")
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * Tarayıcıdan URL'yi bul
     */
    private fun findUrlInBrowser(root: AccessibilityNodeInfo): String? {
        // URL bar ID'leri
        val urlBarIds = listOf(
            "com.android.chrome:id/url_bar",
            "com.android.chrome:id/search_box_text",
            "org.mozilla.firefox:id/url_bar_title",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.opera.browser:id/url_field",
            "com.brave.browser:id/url_bar",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.mi.globalbrowser:id/url_bar"
        )

        for (id in urlBarIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                val text = nodes[0].text?.toString()
                nodes.forEach { it.recycle() }
                if (!text.isNullOrEmpty()) return text
            }
        }

        return null
    }

    /**
     * Sayfadaki tüm metin içeriğini çıkar
     */
    private fun extractPageContent(node: AccessibilityNodeInfo, depth: Int = 0): String {
        if (depth > 15) return "" // Çok derin aramayı önle

        val content = StringBuilder()

        // Bu node'un metnini al
        node.text?.let { text ->
            if (text.length in 3..500) { // Çok kısa veya çok uzun metinleri atla
                content.append(text).append(" ")
            }
        }

        node.contentDescription?.let { desc ->
            if (desc.length in 3..200) {
                content.append(desc).append(" ")
            }
        }

        // Alt node'ları tara
        for (i in 0 until node.childCount) {
            try {
                val child = node.getChild(i) ?: continue
                content.append(extractPageContent(child, depth + 1))
                child.recycle()
            } catch (e: Exception) {
                // Ignore
            }
        }

        return content.toString()
    }

    /**
     * İçeriği kumar açısından analiz et
     * Skor: 0-100+ (100 ve üzeri = kesin kumar)
     */
    private fun analyzeContentForGambling(content: String): Int {
        val lowerContent = content.lowercase()
        var score = 0

        // 1. Kesin göstergeler (tek başına yeterli)
        for (indicator in DEFINITE_GAMBLING_INDICATORS) {
            if (lowerContent.contains(indicator)) {
                Log.d(TAG, "Kesin kumar göstergesi: $indicator")
                return 100 // Kesin kumar
            }
        }

        // 2. Kumar anahtar kelimeleri sayısı
        var keywordCount = 0
        for (keyword in GAMBLING_CONTENT_KEYWORDS) {
            if (lowerContent.contains(keyword)) {
                keywordCount++
                score += 15
            }
        }

        // 3. Bonus: Çok fazla kumar kelimesi varsa
        if (keywordCount >= 5) {
            score += 30
        }

        // 4. Para/ödeme kelimeleri + kumar kombinasyonu
        val moneyKeywords = listOf("yatır", "çek", "bonus", "kazan", "ödül", "deposit", "withdraw", "win")
        val hasMoneyKeywords = moneyKeywords.any { lowerContent.contains(it) }

        if (hasMoneyKeywords && keywordCount >= 2) {
            score += 25
        }

        // 5. Sayısal oranlar (1.5, 2.3 gibi bahis oranları)
        val oddsPattern = Regex("\\b\\d+[.,]\\d{1,2}\\b")
        val oddsCount = oddsPattern.findAll(lowerContent).count()
        if (oddsCount >= 5) {
            score += 20 // Çok fazla oran var, muhtemelen bahis sitesi
        }

        return score
    }

    /**
     * Engelle ve ana ekrana dön
     */
    private fun blockAndGoHome(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < 3000) return // 3 saniye spam koruması

        lastBlockTime = now

        Log.i(TAG, "🚫 SAYFA ENGELLENDİ: $reason")

        // Ana ekrana dön
        goHome()

        // Kullanıcıyı bilgilendir
        handler.post {
            Toast.makeText(
                this,
                "Kumar sitesi engellendi",
                Toast.LENGTH_SHORT
            ).show()
        }

        incrementBlockedCount()
        saveBlockLog(lastCheckedUrl, reason)
    }

    /**
     * Uygulamayı engelle
     */
    private fun blockApp(packageName: String, reason: String) {
        val now = System.currentTimeMillis()
        if (packageName == lastBlockedApp && now - lastBlockTime < 2000) return

        lastBlockedApp = packageName
        lastBlockTime = now

        Log.i(TAG, "🚫 UYGULAMA ENGELLENDİ: $packageName - $reason")

        goHome()
        incrementBlockedCount()
        saveBlockLog(packageName, reason)
    }

    /**
     * Ana ekrana dön
     */
    private fun goHome() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } else {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
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

    /**
     * Whitelist kontrolü - Sistem ve güvenli uygulamalar
     */
    private fun isWhitelistedApp(packageName: String): Boolean {
        // Tam eşleşme
        if (packageName in SYSTEM_APPS_WHITELIST) {
            return true
        }

        // Sistem uygulaması prefix kontrolü
        if (packageName.startsWith("com.android.") ||
            packageName.startsWith("com.google.android.") ||
            packageName.startsWith("com.samsung.android.") ||
            packageName.startsWith("com.huawei.") ||
            packageName.startsWith("com.xiaomi.") ||
            packageName.startsWith("com.miui.") ||
            packageName.startsWith("com.oppo.") ||
            packageName.startsWith("com.vivo.") ||
            packageName.startsWith("com.oneplus.")) {

            // Ama kumar kelimesi içermiyorsa
            val lowerPkg = packageName.lowercase()
            val gamblingKeywords = listOf("bet", "casino", "poker", "slot", "gambl", "bahis", "kumar")
            if (gamblingKeywords.none { lowerPkg.contains(it) }) {
                return true
            }
        }

        return false
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

    private fun saveBlockLog(target: String, reason: String) {
        try {
            val prefs = getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
            val logs = prefs.getString("block_logs", "") ?: ""
            val timestamp = System.currentTimeMillis()
            val newLog = "$timestamp|$target|$reason\n"

            // Son 50 logu tut
            val logLines = (logs + newLog).lines().takeLast(50).joinToString("\n")
            prefs.edit().putString("block_logs", logLines).apply()
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "Service interrupted")
        contentCheckRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        contentCheckRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }
}
