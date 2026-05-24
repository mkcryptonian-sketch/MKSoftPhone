package com.mksoft.phone.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.mksoft.phone.core.sip.SipEngineManager
import com.mksoft.phone.service.SipService

class KeepAliveReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "KeepAliveReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Keep-alive alarm triggered")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoIPApp::KeepAliveWakeLock")

        try {
            wakeLock.acquire(10000L /*10 seconds max*/)
            val sipEngine = SipEngineManager.getInstance(context)
            sipEngine.sendKeepAliveOptions()

            // Schedule the next keep alive alarm via the service
            val serviceIntent = Intent(context, SipService::class.java).apply {
                action = SipService.ACTION_SCHEDULE_ALARM
            }
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling keep-alive alarm: ${e.message}", e)
        } finally {
            try {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing wake lock: ${e.message}")
            }
        }
    }
}
