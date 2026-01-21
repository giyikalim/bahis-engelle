package com.utility.calculator.heartbeat

/**
 * Supabase API Yapılandırması
 *
 * Bu değerleri kendi Supabase projenizden alın:
 * 1. https://supabase.com adresine gidin
 * 2. Proje oluşturun veya mevcut projeyi seçin
 * 3. Settings → API bölümünden URL ve anon key'i kopyalayın
 *
 * Supabase'de şu tabloyu oluşturun:
 *
 * CREATE TABLE heartbeats (
 *     id BIGSERIAL PRIMARY KEY,
 *     device_id TEXT NOT NULL,
 *     timestamp TIMESTAMPTZ DEFAULT NOW(),
 *     app_version TEXT,
 *     protection_enabled BOOLEAN,
 *     vpn_active BOOLEAN,
 *     accessibility_active BOOLEAN,
 *     blocked_count INTEGER,
 *     battery_level INTEGER,
 *     is_charging BOOLEAN
 * );
 *
 * -- Performans için index
 * CREATE INDEX idx_heartbeats_device_time ON heartbeats(device_id, timestamp DESC);
 *
 * -- RLS (Row Level Security) - Opsiyonel
 * ALTER TABLE heartbeats ENABLE ROW LEVEL SECURITY;
 * CREATE POLICY "Allow insert" ON heartbeats FOR INSERT WITH CHECK (true);
 * CREATE POLICY "Allow select own" ON heartbeats FOR SELECT USING (true);
 */
object SupabaseConfig {

    // ⚠️ BU DEĞERLERİ KENDİ SUPABASE PROJENİZDEN ALIN
    // Supabase Dashboard → Settings → API

    const val SUPABASE_URL = "https://ztfufhecfcriyryaulmv.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inp0ZnVmaGVjZmNyaXlyeWF1bG12Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjkwMTQ2MzEsImV4cCI6MjA4NDU5MDYzMX0.5s9sMTF8bzyvGXVIZI3U2R24gXEC0WTojoN7g2yoOL0"

    // Heartbeat tablosu adı
    const val TABLE_NAME = "heartbeats"

    // Heartbeat aralığı (dakika)
    const val HEARTBEAT_INTERVAL_MINUTES = 10

    /**
     * API endpoint'i oluştur
     */
    fun getApiEndpoint(): String {
        return "$SUPABASE_URL/rest/v1/$TABLE_NAME"
    }

    /**
     * Yapılandırma geçerli mi kontrol et
     */
    fun isConfigured(): Boolean {
        return SUPABASE_URL != "https://YOUR_PROJECT_ID.supabase.co" &&
                SUPABASE_ANON_KEY != "YOUR_ANON_KEY_HERE" &&
                SUPABASE_URL.contains("supabase.co")
    }
}
