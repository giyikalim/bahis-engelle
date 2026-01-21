package com.utility.calculator.heartbeat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Heartbeat Alarm Receiver
 *
 * AlarmManager tarafından tetiklenir ve heartbeat gönderir.
 * Bu sayede 10 dakikalık interval mümkün olur (WorkManager min 15 dk).
 */
class HeartbeatAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HeartbeatAlarm"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "Alarm tetiklendi, heartbeat gönderiliyor...")

        // WorkManager ile heartbeat gönder
        val workRequest = OneTimeWorkRequestBuilder<HeartbeatWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)

        // Sonraki alarmı planla
        HeartbeatManager.scheduleNextAlarm(context)
    }
}
