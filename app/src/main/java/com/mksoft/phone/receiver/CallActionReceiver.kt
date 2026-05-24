package com.mksoft.phone.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mksoft.phone.core.sip.SipEngineManager

class CallActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ANSWER = "com.mksoft.phone.ACTION_ANSWER"
        const val ACTION_DECLINE = "com.mksoft.phone.ACTION_DECLINE"
        const val EXTRA_CALL_ID = "com.mksoft.phone.EXTRA_CALL_ID"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val callId = intent.getIntExtra(EXTRA_CALL_ID, -1)
        Log.d("CallActionReceiver", "onReceive action=$action, callId=$callId")
        if (callId == -1) return

        val sipEngine = SipEngineManager.getInstance(context.applicationContext)
        when (action) {
            ACTION_ANSWER -> {
                val connection = sipEngine.getConnection(callId)
                if (connection != null) {
                    connection.onAnswer()
                } else {
                    Log.w("CallActionReceiver", "Telecom connection not found. Falling back to native answer.")
                    sipEngine.answerCall(callId)
                }
                
                // Open MainActivity so that they are looking at the call screen
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                }
            }
            ACTION_DECLINE -> {
                val connection = sipEngine.getConnection(callId)
                if (connection != null) {
                    connection.onReject()
                } else {
                    Log.w("CallActionReceiver", "Telecom connection not found. Falling back to native decline.")
                    sipEngine.hangupCall(callId)
                }
            }
        }

        // Cancel the incoming call notification
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(callId)
        } catch (e: Exception) {
            Log.e("CallActionReceiver", "Failed to cancel notification: ${e.message}")
        }
    }
}
