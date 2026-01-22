-- =====================================================
-- BAHİS ENGEL - SUPABASE SCHEMA
-- =====================================================

-- 1. KULLANICILAR TABLOSU
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    email TEXT UNIQUE NOT NULL,
    phone TEXT,
    password_hash TEXT, -- Dashboard girişi için (sonra eklenecek)
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    is_active BOOLEAN DEFAULT true,
    notes TEXT
);

-- 2. CİHAZLAR TABLOSU
CREATE TABLE IF NOT EXISTS devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    device_id TEXT UNIQUE NOT NULL,
    device_name TEXT, -- Kullanıcının verdiği isim (örn: "Ali'nin Telefonu")
    device_model TEXT,
    android_version INTEGER,
    app_version TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ DEFAULT NOW(),
    is_active BOOLEAN DEFAULT true
);

-- 3. HEARTBEATS TABLOSU (güncellendi - device foreign key)
-- Mevcut tabloyu silmeden önce yedek al!
-- DROP TABLE IF EXISTS heartbeats;

CREATE TABLE IF NOT EXISTS heartbeats (
    id BIGSERIAL PRIMARY KEY,
    device_id TEXT NOT NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    timestamp TIMESTAMPTZ DEFAULT NOW(),
    protection_enabled BOOLEAN,
    vpn_active BOOLEAN,
    accessibility_active BOOLEAN,
    blocked_count INTEGER,
    battery_level INTEGER,
    is_charging BOOLEAN,
    android_version INTEGER,
    device_model TEXT
);

-- 4. ALERT AYARLARI TABLOSU
CREATE TABLE IF NOT EXISTS alert_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL DEFAULT 'Varsayılan',
    is_active BOOLEAN DEFAULT true,

    -- Alert eşikleri (dakika)
    email_threshold_minutes INTEGER DEFAULT 20,
    sms_threshold_minutes INTEGER DEFAULT 30,
    call_threshold_minutes INTEGER DEFAULT 60,

    -- Alıcılar (JSON array)
    email_recipients JSONB DEFAULT '[]'::jsonb,
    sms_recipients JSONB DEFAULT '[]'::jsonb,
    call_recipients JSONB DEFAULT '[]'::jsonb,

    -- Tekrar gönderim engeli (dakika)
    cooldown_minutes INTEGER DEFAULT 60,

    -- Hangi durumlarda alert gönderilsin
    alert_on_no_heartbeat BOOLEAN DEFAULT true,
    alert_on_protection_disabled BOOLEAN DEFAULT true,
    alert_on_app_uninstall BOOLEAN DEFAULT true,

    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. ALERT GEÇMİŞİ TABLOSU
CREATE TABLE IF NOT EXISTS alert_logs (
    id BIGSERIAL PRIMARY KEY,
    device_id TEXT,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    alert_config_id UUID REFERENCES alert_configs(id) ON DELETE SET NULL,

    alert_type TEXT NOT NULL CHECK (alert_type IN ('email', 'sms', 'call')),
    alert_reason TEXT NOT NULL CHECK (alert_reason IN ('no_heartbeat', 'protection_disabled', 'app_uninstalled')),

    recipient TEXT NOT NULL,
    status TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'sent', 'failed')),
    error_message TEXT,

    minutes_inactive INTEGER,

    created_at TIMESTAMPTZ DEFAULT NOW(),
    sent_at TIMESTAMPTZ
);

-- =====================================================
-- INDEXES
-- =====================================================

CREATE INDEX IF NOT EXISTS idx_heartbeats_device_time ON heartbeats(device_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_heartbeats_user ON heartbeats(user_id);
CREATE INDEX IF NOT EXISTS idx_devices_user ON devices(user_id);
CREATE INDEX IF NOT EXISTS idx_devices_device_id ON devices(device_id);
CREATE INDEX IF NOT EXISTS idx_alert_logs_device ON alert_logs(device_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_alert_logs_status ON alert_logs(status);
CREATE INDEX IF NOT EXISTS idx_alert_logs_user ON alert_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- =====================================================
-- ROW LEVEL SECURITY (RLS)
-- =====================================================

-- Users tablosu
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow anonymous insert on users" ON users FOR INSERT TO anon WITH CHECK (true);
CREATE POLICY "Allow anonymous select on users" ON users FOR SELECT TO anon USING (true);
CREATE POLICY "Allow anonymous update on users" ON users FOR UPDATE TO anon USING (true);

-- Devices tablosu
ALTER TABLE devices ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow anonymous insert on devices" ON devices FOR INSERT TO anon WITH CHECK (true);
CREATE POLICY "Allow anonymous select on devices" ON devices FOR SELECT TO anon USING (true);
CREATE POLICY "Allow anonymous update on devices" ON devices FOR UPDATE TO anon USING (true);

-- Heartbeats tablosu
ALTER TABLE heartbeats ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow anonymous insert on heartbeats" ON heartbeats FOR INSERT TO anon WITH CHECK (true);
CREATE POLICY "Allow anonymous select on heartbeats" ON heartbeats FOR SELECT TO anon USING (true);

-- Alert configs tablosu
ALTER TABLE alert_configs ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow anonymous all on alert_configs" ON alert_configs FOR ALL TO anon USING (true) WITH CHECK (true);

-- Alert logs tablosu
ALTER TABLE alert_logs ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow anonymous insert on alert_logs" ON alert_logs FOR INSERT TO anon WITH CHECK (true);
CREATE POLICY "Allow anonymous select on alert_logs" ON alert_logs FOR SELECT TO anon USING (true);
CREATE POLICY "Allow anonymous update on alert_logs" ON alert_logs FOR UPDATE TO anon USING (true);

-- =====================================================
-- VARSAYILAN ALERT AYARI
-- =====================================================

INSERT INTO alert_configs (name, is_active, email_threshold_minutes, sms_threshold_minutes, call_threshold_minutes)
VALUES ('Varsayılan', true, 20, 30, 60)
ON CONFLICT DO NOTHING;

-- =====================================================
-- YARDIMCI VIEW'LAR
-- =====================================================

-- Kullanıcı ve son heartbeat bilgisi
CREATE OR REPLACE VIEW user_device_status AS
SELECT
    u.id as user_id,
    u.first_name,
    u.last_name,
    u.email,
    d.device_id,
    d.device_name,
    d.device_model,
    d.last_seen_at,
    h.timestamp as last_heartbeat,
    h.protection_enabled,
    h.vpn_active,
    h.battery_level,
    EXTRACT(EPOCH FROM (NOW() - h.timestamp)) / 60 as minutes_since_heartbeat
FROM users u
LEFT JOIN devices d ON d.user_id = u.id
LEFT JOIN LATERAL (
    SELECT * FROM heartbeats
    WHERE heartbeats.device_id = d.device_id
    ORDER BY timestamp DESC
    LIMIT 1
) h ON true
WHERE u.is_active = true;

-- =====================================================
-- TAMAMLANDI
-- =====================================================
