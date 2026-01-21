package com.utility.calculator.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.utility.calculator.blocker.AppBlockList
import com.utility.calculator.blocker.BlockList

/**
 * Accessibility Service - Uygulama ve tarayıcı izleme
 *
 * Özellikler:
 * - Kumar uygulamalarını tespit ve engelleme
 * - Tarayıcılardaki URL izleme
 * - Şüpheli içerik tespiti
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AppBlocker"

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
    }

    private var lastBlockedApp = ""
    private var lastBlockTime = 0L

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
        Log.i(TAG, "Accessibility Service bağlandı")
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
                    checkBrowserContent(event)
                }
            }
        }
    }

    /**
     * Pencere değişikliği - Uygulama açıldığında kontrol et
     */
    private fun handleWindowChange(packageName: String, event: AccessibilityEvent) {
        // Kumar uygulaması kontrolü
        if (AppBlockList.isBlockedApp(packageName)) {
            blockApp(packageName, "Kumar uygulaması")
            return
        }

        // Paket adında kumar kelimesi kontrolü
        if (BlockList.shouldBlock(packageName)) {
            blockApp(packageName, "Şüpheli uygulama adı")
            return
        }

        // Tarayıcıda URL kontrolü
        if (packageName in BROWSER_PACKAGES) {
            checkBrowserContent(event)
        }
    }

    /**
     * Tarayıcı içeriğini kontrol et - URL bar'daki adresi oku
     */
    private fun checkBrowserContent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return

        try {
            // URL bar'ı bul
            val urlNode = findUrlBar(rootNode)
            urlNode?.let { node ->
                val url = node.text?.toString() ?: return@let

                if (BlockList.shouldBlock(url)) {
                    Log.i(TAG, "Tarayıcıda engellenen URL: $url")
                    goHome()
                    incrementBlockedCount()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tarayıcı kontrolü hatası", e)
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * URL bar'ı bul (farklı tarayıcılar için)
     */
    private fun findUrlBar(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Yaygın URL bar ID'leri
        val urlBarIds = listOf(
            "com.android.chrome:id/url_bar",
            "com.android.chrome:id/search_box_text",
            "org.mozilla.firefox:id/url_bar_title",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.opera.browser:id/url_field",
            "com.brave.browser:id/url_bar",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text"
        )

        for (id in urlBarIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                return nodes[0]
            }
        }

        // Fallback: EditText ara
        return findEditText(root)
    }

    private fun findEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className == "android.widget.EditText" && node.isVisibleToUser) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditText(child)
            if (result != null) return result
            child.recycle()
        }

        return null
    }

    /**
     * Uygulamayı engelle ve ana ekrana dön
     */
    private fun blockApp(packageName: String, reason: String) {
        // Çok sık engelleme spam'ini önle
        val now = System.currentTimeMillis()
        if (packageName == lastBlockedApp && now - lastBlockTime < 2000) {
            return
        }

        lastBlockedApp = packageName
        lastBlockTime = now

        Log.i(TAG, "ENGELLENEN: $packageName - $reason")

        goHome()
        incrementBlockedCount()

        // Log kaydet
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
        val prefs = getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("protection_enabled", false)
    }

    private fun incrementBlockedCount() {
        val prefs = getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
        val current = prefs.getInt("blocked_count", 0)
        prefs.edit().putInt("blocked_count", current + 1).apply()
    }

    private fun saveBlockLog(packageName: String, reason: String) {
        val prefs = getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
        val logs = prefs.getString("block_logs", "") ?: ""
        val timestamp = System.currentTimeMillis()
        val newLog = "$timestamp|$packageName|$reason\n"
        prefs.edit().putString("block_logs", logs + newLog).apply()
    }

    override fun onInterrupt() {
        Log.i(TAG, "Service interrupted")
    }
}
