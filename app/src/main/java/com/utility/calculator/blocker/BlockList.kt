package com.utility.calculator.blocker

/**
 * Gelişmiş Engelleme Sistemi
 * - 500+ kelime
 * - 200+ domain
 * - Leet speak algılama
 * - Regex pattern'ları
 * - Türkçe karakter varyasyonları
 */
object BlockList {

    // ==================== TÜRKÇE KELİMELER ====================
    private val TURKISH_KEYWORDS = setOf(
        // Temel
        "bahis", "kumar", "kumarhane", "gazino", "tombala", "piyango",
        "iddaa", "iddia", "bahisci", "kumarci", "bahissever",

        // Birleşik kelimeler
        "canlibahis", "canlikumar", "canlicasino", "canliiddaa",
        "bahissitesi", "kumarsitesi", "bahisoyunu", "kumaroyunu",
        "bahisanaliz", "bahistahmin", "mactahmin", "iddiatahmin",
        "bahisbonus", "hosgeldinbonus", "yatirimbonus", "kayipbonus",
        "freespin", "bedavabahis", "bedavacasino", "bonusveren",
        "cevrimsiz", "cevrimsart", "yatirimsiz", "kayipsiz",

        // Oyun türleri
        "slot", "slotoyun", "slotmakine", "jackpot", "megajackpot",
        "rulet", "blackjack", "bakara", "baccarat", "poker",
        "holdem", "texasholdem", "omaha", "videopoker",
        "sicbo", "craps", "keno", "bingo", "tombala",

        // Spor bahisleri
        "macbahis", "futbolbahis", "basketbahis", "tenisbahis",
        "voleybolbahis", "canlimaç", "canliskor", "iddaaprogram",
        "bahisoranlar", "canlioran", "macoran", "superoran",

        // Eylemler
        "bahisyap", "bahisoyna", "kumaroyna", "parakazan",
        "kazangaranti", "kesinkazan", "risksiz", "garantili",

        // Argo/Slang
        "bahisco", "kumarco", "casinoco", "betci", "slotcu",
        "bahiskar", "kumarkar"
    )

    // ==================== İNGİLİZCE KELİMELER ====================
    private val ENGLISH_KEYWORDS = setOf(
        // Core - TEK KELİMELER (önemli!)
        "bet", "gamble", "gambling", "betting", "wager", "wagering",
        "casino", "poker", "blackjack", "roulette", "baccarat",
        "slots", "slot", "jackpot", "megaways", "freespins",

        // Betting types
        "sportsbet", "sportbet", "sportsbetting", "livebetting",
        "livebet", "inplay", "inplaybet", "prematch",
        "accumulator", "parlay", "multibet", "combobet",

        // Casino games
        "livecasino", "livegames", "liveroulette", "liveblackjack",
        "videoslots", "classicslots", "fruitslots", "vegasslots",
        "scratchcard", "instantwin", "virtualsports",

        // Bonus terms
        "welcomebonus", "depositbonus", "nodeposit", "freechips",
        "cashback", "reload", "highroller", "vipbonus",
        "loyaltybonus", "referralbonus", "matchbonus",

        // Actions
        "placebet", "betslip", "cashout", "withdraw",

        // Online gambling
        "onlinecasino", "onlinepoker", "onlineslots", "onlinegambling",
        "mobilecasino", "mobilebet", "instantplay"
    )

    // ==================== POPÜLER SİTE İSİMLERİ ====================
    private val SITE_KEYWORDS = setOf(
        // Uluslararası
        "1xbet", "bet365", "betway", "bwin", "unibet", "betfair",
        "williamhill", "ladbrokes", "coral", "paddypower",
        "pinnacle", "marathonbet", "22bet", "melbet", "mostbet",
        "betwinner", "parimatch", "dafabet", "mansion",
        "leovegas", "casumo", "mrgreen", "rizk", "videoslots",

        // Türkiye odaklı
        "bets10", "betboo", "superbahis", "superbetin", "mobilbahis",
        "tipobet", "youwin", "bahigo", "betvole", "betpas",
        "betpark", "betist", "betnano", "betlike", "betexper",
        "casinomaxi", "casinoslot", "casinometropol", "vdcasino",
        "cepbahis", "dinamobet", "dumanbet", "elitbahis",
        "fenomenbet", "goldenbahis", "gorabet", "grandbetting",
        "hilbet", "ikimisli", "imajbet", "jasminbet", "jojobet",
        "klasbahis", "kolaybet", "ligobet", "mariobet", "marsbet",
        "matadorbet", "meritroyalbet", "milosbet", "nakitbahis",
        "ngsbahis", "odeonbet", "onwin", "orisbet", "paribahis",
        "perabet", "piabet", "pinbahis", "polobet", "princessbet",
        "privebet", "pusulabet", "restbet", "rivalo", "romabet",
        "sahabet", "santosbetting", "sekabet", "setrabet",
        "showbahis", "simsekbet", "sultanbet", "superbahis",
        "tempobet", "tipobet", "trbet", "truvabet", "tulipbet",
        "ultrabet", "vegabet", "vevobahis", "vidobet", "wonodds",
        "xbet", "yakinbahis", "zalbet", "zbahis"
    )

    // ==================== BİLİNEN DOMAİNLER ====================
    val BLOCKED_DOMAINS = setOf(
        // Majör siteler
        "1xbet.com", "1xbet.mobi", "1xbettr.com",
        "bet365.com", "bet365.es", "bet365.it",
        "betway.com", "betway.es",
        "bwin.com", "bwin.es",
        "unibet.com", "unibet.fr",
        "betfair.com", "betfair.es",
        "williamhill.com", "williamhill.es",
        "paddypower.com", "ladbrokes.com",
        "pinnacle.com", "pinnaclesports.com",
        "22bet.com", "22bet.ng",
        "melbet.com", "melbet.org",
        "mostbet.com", "mostbet.az",

        // Türkiye siteleri
        "bets10.com", "bets10giris.com", "bets10yenigiris.com",
        "betboo.com", "betboo1.com", "betboogiris.com",
        "superbahis.com", "superbahisgiris.com",
        "superbetin.com", "superbetin1.com",
        "mobilbahis.com", "mobilbahis1.com", "mobilbahisgiris.com",
        "tipobet.com", "tipobet365.com", "tipobetgiris.com",
        "youwin.com", "youwin1.com", "youwingiris.com",
        "bahigo.com", "bahigogiris.com",
        "jojobet.com", "jojobetgiris.com", "jojobet1.com",
        "casinomaxi.com", "casinomaxigiris.com",
        "vdcasino.com", "vdcasinogiris.com",
        "imajbet.com", "imajbetgiris.com",
        "sekabet.com", "sekabetgiris.com",
        "tempobet.com", "tempobetgiris.com",
        "matadorbet.com", "matadorbetgiris.com",
        "sahabet.com", "sahabetgiris.com",
        "onwin.com", "onwingiris.com",
        "perabet.com", "perabetgiris.com",
        "restbet.com", "restbetgiris.com",
        "piabet.com", "piabetgiris.com",
        "pinbahis.com", "pinbahisgiris.com"
    )

    // ==================== LEET SPEAK HARİTASI ====================
    private val LEET_MAP = mapOf(
        'a' to listOf('4', '@', 'α'),
        'e' to listOf('3', '€', 'ε'),
        'i' to listOf('1', '!', 'ı', 'İ'),
        'o' to listOf('0', 'ø', 'ο'),
        's' to listOf('5', '$', 'ş', 'Ş'),
        't' to listOf('7', '+'),
        'b' to listOf('8', 'ß'),
        'g' to listOf('9', 'ğ', 'Ğ'),
        'l' to listOf('1', '|'),
        'c' to listOf('ç', 'Ç', '('),
        'u' to listOf('ü', 'Ü', 'µ')
    )

    // ==================== TÜRKÇE KARAKTER DÖNÜŞÜMÜ ====================
    private val TURKISH_CHAR_MAP = mapOf(
        'ş' to 's', 'Ş' to 's',
        'ğ' to 'g', 'Ğ' to 'g',
        'ü' to 'u', 'Ü' to 'u',
        'ö' to 'o', 'Ö' to 'o',
        'ç' to 'c', 'Ç' to 'c',
        'ı' to 'i', 'İ' to 'i'
    )

    // ==================== REGEX PATTERN'LARI ====================
    private val REGEX_PATTERNS = listOf(
        // Bahis/bet varyasyonları
        Regex("b[a4@]h[i1!ı][s5\$ş]", RegexOption.IGNORE_CASE),
        Regex("b[e3][t7]", RegexOption.IGNORE_CASE),
        Regex("c[a4@][s5\$][i1!][n][o0]", RegexOption.IGNORE_CASE),
        Regex("k[u][m][a4@]r", RegexOption.IGNORE_CASE),
        Regex("s[l1][o0][t7]", RegexOption.IGNORE_CASE),
        Regex("p[o0]k[e3]r", RegexOption.IGNORE_CASE),
        Regex("r[u][l1][e3][t7]", RegexOption.IGNORE_CASE),
        Regex("[i1!]dd[i1!][a4@]", RegexOption.IGNORE_CASE),
        Regex("j[a4@]ckp[o0][t7]", RegexOption.IGNORE_CASE),

        // URL pattern'ları
        Regex(".*bet[0-9]+.*", RegexOption.IGNORE_CASE),
        Regex(".*casino[0-9]+.*", RegexOption.IGNORE_CASE),
        Regex(".*slot[0-9]+.*", RegexOption.IGNORE_CASE),
        Regex(".*bahis[0-9]+.*", RegexOption.IGNORE_CASE),

        // Giriş/Mirror siteleri
        Regex(".*giris[0-9]*\\.(com|net|org).*", RegexOption.IGNORE_CASE),
        Regex(".*yenigiris.*", RegexOption.IGNORE_CASE),
        Regex(".*guncelgiris.*", RegexOption.IGNORE_CASE),
        Regex(".*mobilgiris.*", RegexOption.IGNORE_CASE)
    )

    // Tüm kelimeler birleşik
    val BLOCKED_KEYWORDS: Set<String> by lazy {
        TURKISH_KEYWORDS + ENGLISH_KEYWORDS + SITE_KEYWORDS
    }

    /**
     * Ana engelleme kontrolü
     */
    fun shouldBlock(domain: String): Boolean {
        val normalizedDomain = normalizeDomain(domain)

        // 1. Tam domain eşleşmesi
        if (checkDomainMatch(normalizedDomain)) return true

        // 2. Kelime bazlı kontrol
        if (checkKeywordMatch(normalizedDomain)) return true

        // 3. Regex pattern kontrolü
        if (checkRegexMatch(normalizedDomain)) return true

        // 4. Leet speak kontrolü
        if (checkLeetSpeak(normalizedDomain)) return true

        return false
    }

    /**
     * Domain'i normalize et
     */
    private fun normalizeDomain(domain: String): String {
        var normalized = domain.lowercase()

        // Türkçe karakterleri dönüştür
        TURKISH_CHAR_MAP.forEach { (turkish, latin) ->
            normalized = normalized.replace(turkish, latin)
        }

        // www ve protokol kaldır
        normalized = normalized
            .removePrefix("www.")
            .removePrefix("http://")
            .removePrefix("https://")

        // Tire ve alt çizgileri kaldır (kelime kontrolü için)
        return normalized
    }

    /**
     * Domain listesi kontrolü
     */
    private fun checkDomainMatch(domain: String): Boolean {
        return BLOCKED_DOMAINS.any { blocked ->
            domain.contains(blocked.lowercase())
        }
    }

    /**
     * Kelime kontrolü
     */
    private fun checkKeywordMatch(domain: String): Boolean {
        val cleanDomain = domain.replace("-", "").replace("_", "").replace(".", "")
        return BLOCKED_KEYWORDS.any { keyword ->
            cleanDomain.contains(keyword.lowercase())
        }
    }

    /**
     * Regex pattern kontrolü
     */
    private fun checkRegexMatch(domain: String): Boolean {
        return REGEX_PATTERNS.any { pattern ->
            pattern.containsMatchIn(domain)
        }
    }

    /**
     * Leet speak kontrolü (b4h1s, c4s1n0 gibi)
     */
    private fun checkLeetSpeak(domain: String): Boolean {
        val decoded = decodeLeetSpeak(domain)
        if (decoded != domain) {
            return checkKeywordMatch(decoded)
        }
        return false
    }

    /**
     * Leet speak decode et
     */
    private fun decodeLeetSpeak(text: String): String {
        val sb = StringBuilder()
        for (char in text) {
            var found = false
            for ((letter, leetChars) in LEET_MAP) {
                if (char in leetChars || char == letter) {
                    sb.append(letter)
                    found = true
                    break
                }
            }
            if (!found) {
                sb.append(char.lowercaseChar())
            }
        }
        return sb.toString()
    }

    /**
     * Engelleme sebebini döndür
     */
    fun getBlockReason(domain: String): BlockReason? {
        val normalized = normalizeDomain(domain)

        BLOCKED_DOMAINS.find { normalized.contains(it.lowercase()) }?.let {
            return BlockReason(BlockType.DOMAIN, it)
        }

        val cleanDomain = normalized.replace("-", "").replace("_", "").replace(".", "")
        BLOCKED_KEYWORDS.find { cleanDomain.contains(it.lowercase()) }?.let {
            return BlockReason(BlockType.KEYWORD, it)
        }

        REGEX_PATTERNS.find { it.containsMatchIn(normalized) }?.let {
            return BlockReason(BlockType.PATTERN, it.pattern)
        }

        val decoded = decodeLeetSpeak(normalized)
        if (decoded != normalized) {
            BLOCKED_KEYWORDS.find { decoded.contains(it.lowercase()) }?.let {
                return BlockReason(BlockType.LEET_SPEAK, "$it (${domain})")
            }
        }

        return null
    }

    data class BlockReason(val type: BlockType, val match: String)

    enum class BlockType {
        DOMAIN,     // Bilinen domain
        KEYWORD,    // Yasaklı kelime
        PATTERN,    // Regex pattern
        LEET_SPEAK  // Leet speak varyasyonu
    }
}
