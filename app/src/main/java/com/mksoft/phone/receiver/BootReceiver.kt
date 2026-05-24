package com.mksoft.phone.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mksoft.phone.data.DefaultDataRepository
import com.mksoft.phone.service.SipService

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive action: ${intent.action}")
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val repository = DefaultDataRepository.getInstance(context.applicationContext)
            val settings = repository.settings.value
            val accounts = repository.accounts.value
            val hasEnabledAccount = accounts.any { it.isEnabled }
            if (settings.autoStartOnBoot && settings.isLoggedIn && hasEnabledAccount) {
                Log.d(TAG, "Starting SipService on boot completed")
                SipService.start(context)
            } else {
                Log.d(TAG, "Bypassing auto-start on boot: autoStartOnBoot=${settings.autoStartOnBoot}, isLoggedIn=${settings.isLoggedIn}, hasEnabledAccount=$hasEnabledAccount")
            }
        }
    }
}

