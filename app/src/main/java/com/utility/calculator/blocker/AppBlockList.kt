package com.utility.calculator.blocker

/**
 * Kumar/Bahis Uygulamaları Engelleme Listesi
 *
 * Bu liste bilinen kumar uygulamalarının paket adlarını içerir.
 * Ayrıca paket adlarındaki şüpheli kelimeleri de kontrol eder.
 */
object AppBlockList {

    // Bilinen kumar uygulama paket adları
    private val BLOCKED_PACKAGES = setOf(
        // 1xBet
        "com.x1bet.mobile", "com.xbet.app", "com.oneXbet",

        // Bet365
        "com.bet365", "com.bet365.app",

        // Betway
        "com.betway.app", "com.betway.mobile",

        // Bwin
        "com.bwin.androidclient", "com.bwin.mobile",

        // Unibet
        "com.unibet", "com.unibet.casino", "com.unibet.poker",

        // William Hill
        "com.williamhill.sports", "com.williamhill.casino",

        // Paddy Power
        "com.paddypower.sportsbook", "com.paddypower.casino",

        // PokerStars
        "com.pokerstars.eu", "com.pokerstars.mobile",

        // 888
        "com.casino888", "com.poker888", "com.slots888",

        // Türkiye odaklı (Play Store'da olmasa bile APK)
        "com.bets10", "com.bets10.app",
        "com.superbahis", "com.superbetin",
        "com.mobilbahis", "com.mobilbahis.app",
        "com.tipobet", "com.tipobet365",
        "com.jojobet", "com.jojobet.app",
        "com.casinomaxi", "com.casinoslot",
        "com.matadorbet", "com.sahabet",
        "com.sekabet", "com.imajbet",
        "com.perabet", "com.piabet",
        "com.onwin", "com.restbet",

        // Genel casino/slot uygulamaları
        "com.huuuge.casino", "com.huuuge.slots",
        "com.playtika.slotomania", "com.playtika.caesarscasino",
        "com.productmadness.hotslotsplus", "com.igt.slots",
        "com.bigfishgames.jackpotmagicslotsgooglefree",
        "com.aristocrat.lightning.link", "com.sg.interactive",
        "com.dragonplay.slotcity", "com.pharaohslegacy.slots",

        // Poker uygulamaları
        "com.zynga.poker", "com.ea.game.wsop_row",
        "com.me2zen.texasholdem", "com.kama.texasholdempoker"
    )

    // Paket adında geçerse engelle
    private val BLOCKED_PACKAGE_KEYWORDS = setOf(
        "bet", "bahis", "casino", "poker", "slot", "gambling",
        "kumar", "iddaa", "wager", "blackjack", "roulette",
        "baccarat", "jackpot", "sportsbook", "bookmaker"
    )

    // Beyaz liste - Bu paketler engellenmez
    private val WHITELIST_PACKAGES = setOf(
        "com.google.android.youtube", // YouTube
        "com.spotify.music", // Spotify
        "com.netflix.mediaclient", // Netflix
        "com.betblocker", // BetBlocker (rakip ama zararsız)
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.alphabet", // Alphabet
        "com.elizabeth.hairbet" // Saç bakım uygulaması (bet içeriyor ama kumar değil)
    )

    /**
     * Paket adının engellenip engellenmeyeceğini kontrol et
     */
    fun isBlockedApp(packageName: String): Boolean {
        val lowerPackage = packageName.lowercase()

        // Beyaz listede mi?
        if (lowerPackage in WHITELIST_PACKAGES) {
            return false
        }

        // Tam eşleşme
        if (lowerPackage in BLOCKED_PACKAGES) {
            return true
        }

        // Kelime bazlı kontrol
        if (BLOCKED_PACKAGE_KEYWORDS.any { keyword ->
            // Paket adının parçalarında kelime geçiyor mu?
            lowerPackage.split(".").any { part ->
                part.contains(keyword)
            }
        }) {
            return true
        }

        return false
    }

    /**
     * Uygulamanın neden engellendiğini döndür
     */
    fun getBlockReason(packageName: String): String? {
        val lowerPackage = packageName.lowercase()

        if (lowerPackage in BLOCKED_PACKAGES) {
            return "Bilinen kumar uygulaması"
        }

        BLOCKED_PACKAGE_KEYWORDS.find { keyword ->
            lowerPackage.split(".").any { it.contains(keyword) }
        }?.let {
            return "Şüpheli kelime: $it"
        }

        return null
    }

    /**
     * Yüklü uygulamalar arasında kumar uygulaması ara
     */
    fun findGamblingApps(installedPackages: List<String>): List<String> {
        return installedPackages.filter { isBlockedApp(it) }
    }
}
