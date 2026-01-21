package com.utility.calculator.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.utility.calculator.MainActivity
import com.utility.calculator.R
import com.utility.calculator.blocker.BlockList
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Güvenli DNS Filtreleme VPN Servisi
 *
 * SADECE DNS trafiğini filtreler, diğer tüm trafik normal şekilde geçer.
 * Bu sayede telefonun normal fonksiyonları etkilenmez.
 */
class BlockerVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = AtomicBoolean(false)
    private var vpnThread: Thread? = null
    private var executor: ExecutorService? = null

    // Aktif bağlantıları takip et
    private val activeConnections = ConcurrentHashMap<Int, DatagramSocket>()
    private var transactionId = 0

    companion object {
        private const val TAG = "BlockerVPN"
        private const val CHANNEL_ID = "vpn_channel"
        private const val NOTIFICATION_ID = 1

        // VPN yapılandırması
        private const val VPN_ADDRESS = "10.255.255.1"
        private const val VPN_ROUTE = "10.255.255.0"
        private const val VPN_DNS = "10.255.255.2"

        // Gerçek DNS sunucuları (engellenmemiş sorgular için)
        private val UPSTREAM_DNS_SERVERS = listOf(
            "8.8.8.8",      // Google DNS
            "1.1.1.1",      // Cloudflare DNS
            "208.67.222.222" // OpenDNS
        )

        private const val DNS_PORT = 53
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        executor = Executors.newCachedThreadPool()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning.get()) {
            return START_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())

        if (startVpn()) {
            Log.i(TAG, "VPN başarıyla başlatıldı")
        } else {
            Log.e(TAG, "VPN başlatılamadı")
            stopSelf()
        }

        return START_STICKY
    }

    private fun startVpn(): Boolean {
        return try {
            // VPN arayüzünü oluştur - SADECE DNS trafiğini yakala
            val builder = Builder()
                .setSession("Calculator")
                .addAddress(VPN_ADDRESS, 24)
                .addDnsServer(VPN_DNS)
                // SADECE DNS sunucumuza giden trafiği yakala
                // Diğer tüm trafik normal şekilde geçer
                .addRoute(VPN_DNS, 32)
                .setMtu(1500)
                .setBlocking(false)

            // Önemli uygulamaları VPN'den hariç tut
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    // Sistem uygulamalarını hariç tut
                    builder.addDisallowedApplication(packageName)
                    builder.addDisallowedApplication("com.android.vending") // Play Store
                    builder.addDisallowedApplication("com.google.android.gms") // Google Play Services
                } catch (e: Exception) {
                    Log.w(TAG, "Bazı uygulamalar hariç tutulamadı: ${e.message}")
                }
            }

            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                Log.e(TAG, "VPN interface oluşturulamadı")
                return false
            }

            isRunning.set(true)

            // DNS proxy thread'ini başlat
            vpnThread = Thread(DnsProxyRunnable(), "VPN-DNS-Proxy")
            vpnThread?.start()

            true
        } catch (e: Exception) {
            Log.e(TAG, "VPN başlatma hatası", e)
            false
        }
    }

    /**
     * DNS Proxy - DNS isteklerini filtreler
     */
    private inner class DnsProxyRunnable : Runnable {
        override fun run() {
            val vpnFd = vpnInterface?.fileDescriptor ?: return
            val inputStream = FileInputStream(vpnFd)
            val outputStream = FileOutputStream(vpnFd)
            val buffer = ByteArray(32767)

            Log.i(TAG, "DNS Proxy başlatıldı")

            while (isRunning.get()) {
                try {
                    val length = inputStream.read(buffer)
                    if (length > 0) {
                        val packet = buffer.copyOf(length)
                        handlePacket(packet, outputStream)
                    }
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        Log.e(TAG, "Paket okuma hatası: ${e.message}")
                        Thread.sleep(100)
                    }
                }
            }

            Log.i(TAG, "DNS Proxy durduruldu")
        }
    }

    /**
     * Gelen paketi işle
     */
    private fun handlePacket(packet: ByteArray, outputStream: FileOutputStream) {
        if (packet.size < 28) return // IP (20) + UDP (8) minimum

        try {
            // IP header kontrolü
            val ipVersion = (packet[0].toInt() and 0xF0) shr 4
            if (ipVersion != 4) return // Sadece IPv4

            val protocol = packet[9].toInt() and 0xFF
            if (protocol != 17) return // Sadece UDP (DNS)

            val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4

            // UDP portları kontrol et
            val destPort = ((packet[ipHeaderLength + 2].toInt() and 0xFF) shl 8) or
                    (packet[ipHeaderLength + 3].toInt() and 0xFF)

            if (destPort != DNS_PORT) return // Sadece DNS (port 53)

            // DNS query'yi çıkar
            val dnsStart = ipHeaderLength + 8
            val dnsData = packet.copyOfRange(dnsStart, packet.size)

            // Domain adını çıkar
            val domain = extractDomainFromDnsQuery(dnsData)

            if (domain != null) {
                if (BlockList.shouldBlock(domain)) {
                    // ENGELLE - Boş yanıt gönder
                    Log.i(TAG, "🚫 ENGELLENDİ: $domain")
                    val blockedResponse = createBlockedDnsResponse(packet, dnsData)
                    if (blockedResponse != null) {
                        synchronized(outputStream) {
                            outputStream.write(blockedResponse)
                        }
                    }
                    incrementBlockedCount()
                } else {
                    // İZİN VER - Gerçek DNS'e ilet
                    forwardDnsQuery(packet, dnsData, outputStream)
                }
            } else {
                // Domain çıkarılamadı, izin ver
                forwardDnsQuery(packet, dnsData, outputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Paket işleme hatası: ${e.message}")
        }
    }

    /**
     * DNS query'den domain adını çıkar
     */
    private fun extractDomainFromDnsQuery(dnsData: ByteArray): String? {
        if (dnsData.size < 12) return null

        try {
            val sb = StringBuilder()
            var pos = 12 // DNS header sonrası

            while (pos < dnsData.size) {
                val labelLength = dnsData[pos].toInt() and 0xFF
                if (labelLength == 0) break

                if (sb.isNotEmpty()) sb.append(".")

                for (i in 1..labelLength) {
                    if (pos + i < dnsData.size) {
                        sb.append(dnsData[pos + i].toInt().toChar())
                    }
                }
                pos += labelLength + 1
            }

            return if (sb.isNotEmpty()) sb.toString().lowercase() else null
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Engellenen domain için NXDOMAIN yanıtı oluştur
     */
    private fun createBlockedDnsResponse(originalPacket: ByteArray, dnsQuery: ByteArray): ByteArray? {
        try {
            val ipHeaderLength = (originalPacket[0].toInt() and 0x0F) * 4

            // DNS yanıtı oluştur (NXDOMAIN - domain bulunamadı)
            val dnsResponse = dnsQuery.copyOf()

            // DNS header flags: QR=1 (response), RCODE=3 (NXDOMAIN)
            dnsResponse[2] = 0x81.toByte() // QR=1, RD=1
            dnsResponse[3] = 0x83.toByte() // RA=1, RCODE=3 (NXDOMAIN)

            // Answer count = 0
            dnsResponse[6] = 0
            dnsResponse[7] = 0

            // IP yanıt paketi oluştur
            val responsePacket = ByteArray(ipHeaderLength + 8 + dnsResponse.size)

            // IP header'ı kopyala ve değiştir
            System.arraycopy(originalPacket, 0, responsePacket, 0, ipHeaderLength)

            // Kaynak ve hedef IP'leri değiştir
            System.arraycopy(originalPacket, 16, responsePacket, 12, 4) // Dest -> Src
            System.arraycopy(originalPacket, 12, responsePacket, 16, 4) // Src -> Dest

            // IP total length güncelle
            val totalLength = responsePacket.size
            responsePacket[2] = ((totalLength shr 8) and 0xFF).toByte()
            responsePacket[3] = (totalLength and 0xFF).toByte()

            // UDP header
            val udpStart = ipHeaderLength
            // Port swap
            responsePacket[udpStart] = originalPacket[udpStart + 2]
            responsePacket[udpStart + 1] = originalPacket[udpStart + 3]
            responsePacket[udpStart + 2] = originalPacket[udpStart]
            responsePacket[udpStart + 3] = originalPacket[udpStart + 1]

            // UDP length
            val udpLength = 8 + dnsResponse.size
            responsePacket[udpStart + 4] = ((udpLength shr 8) and 0xFF).toByte()
            responsePacket[udpStart + 5] = (udpLength and 0xFF).toByte()

            // UDP checksum = 0 (optional for IPv4)
            responsePacket[udpStart + 6] = 0
            responsePacket[udpStart + 7] = 0

            // DNS yanıtı
            System.arraycopy(dnsResponse, 0, responsePacket, udpStart + 8, dnsResponse.size)

            // IP checksum hesapla
            calculateIpChecksum(responsePacket, ipHeaderLength)

            return responsePacket
        } catch (e: Exception) {
            Log.e(TAG, "DNS yanıt oluşturma hatası: ${e.message}")
            return null
        }
    }

    /**
     * IP checksum hesapla
     */
    private fun calculateIpChecksum(packet: ByteArray, headerLength: Int) {
        // Checksum alanını sıfırla
        packet[10] = 0
        packet[11] = 0

        var sum = 0
        for (i in 0 until headerLength step 2) {
            val word = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            sum += word
        }

        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        val checksum = sum.inv() and 0xFFFF
        packet[10] = ((checksum shr 8) and 0xFF).toByte()
        packet[11] = (checksum and 0xFF).toByte()
    }

    /**
     * DNS sorgusunu gerçek DNS sunucusuna ilet
     */
    private fun forwardDnsQuery(
        originalPacket: ByteArray,
        dnsQuery: ByteArray,
        outputStream: FileOutputStream
    ) {
        executor?.execute {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.soTimeout = 5000 // 5 saniye timeout
                protect(socket) // VPN'den hariç tut

                // DNS sunucusuna gönder
                val dnsServer = InetAddress.getByName(UPSTREAM_DNS_SERVERS[0])
                val requestPacket = DatagramPacket(dnsQuery, dnsQuery.size, dnsServer, DNS_PORT)
                socket.send(requestPacket)

                // Yanıtı al
                val responseBuffer = ByteArray(512)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)

                // Yanıtı VPN'e yaz
                val dnsResponse = responseBuffer.copyOf(responsePacket.length)
                val vpnResponse = createDnsResponsePacket(originalPacket, dnsResponse)

                if (vpnResponse != null) {
                    synchronized(outputStream) {
                        outputStream.write(vpnResponse)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "DNS forward hatası: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    /**
     * DNS yanıtını IP paketine dönüştür
     */
    private fun createDnsResponsePacket(originalPacket: ByteArray, dnsResponse: ByteArray): ByteArray? {
        return try {
            val ipHeaderLength = (originalPacket[0].toInt() and 0x0F) * 4
            val responsePacket = ByteArray(ipHeaderLength + 8 + dnsResponse.size)

            // IP header kopyala
            System.arraycopy(originalPacket, 0, responsePacket, 0, ipHeaderLength)

            // IP swap
            System.arraycopy(originalPacket, 16, responsePacket, 12, 4)
            System.arraycopy(originalPacket, 12, responsePacket, 16, 4)

            // Total length
            val totalLength = responsePacket.size
            responsePacket[2] = ((totalLength shr 8) and 0xFF).toByte()
            responsePacket[3] = (totalLength and 0xFF).toByte()

            // UDP header
            val udpStart = ipHeaderLength
            responsePacket[udpStart] = originalPacket[udpStart + 2]
            responsePacket[udpStart + 1] = originalPacket[udpStart + 3]
            responsePacket[udpStart + 2] = originalPacket[udpStart]
            responsePacket[udpStart + 3] = originalPacket[udpStart + 1]

            val udpLength = 8 + dnsResponse.size
            responsePacket[udpStart + 4] = ((udpLength shr 8) and 0xFF).toByte()
            responsePacket[udpStart + 5] = (udpLength and 0xFF).toByte()
            responsePacket[udpStart + 6] = 0
            responsePacket[udpStart + 7] = 0

            // DNS yanıtı
            System.arraycopy(dnsResponse, 0, responsePacket, udpStart + 8, dnsResponse.size)

            // IP checksum
            calculateIpChecksum(responsePacket, ipHeaderLength)

            responsePacket
        } catch (e: Exception) {
            null
        }
    }

    private fun incrementBlockedCount() {
        val prefs = getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
        val current = prefs.getInt("blocked_count", 0)
        prefs.edit().putInt("blocked_count", current + 1).apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sistem Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Arka plan servisi"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Hesap Makinesi")
                .setContentText("Çalışıyor")
                .setSmallIcon(R.drawable.ic_calculator)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Hesap Makinesi")
                .setContentText("Çalışıyor")
                .setSmallIcon(R.drawable.ic_calculator)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }

    override fun onDestroy() {
        isRunning.set(false)
        vpnThread?.interrupt()
        executor?.shutdownNow()
        activeConnections.values.forEach { it.close() }
        activeConnections.clear()
        vpnInterface?.close()
        super.onDestroy()
    }

    override fun onRevoke() {
        onDestroy()
        super.onRevoke()
    }
}
