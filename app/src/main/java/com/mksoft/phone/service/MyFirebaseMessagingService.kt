package com.mksoft.phone.service

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mksoft.phone.core.sip.SipEngineManager

class MyFirebaseMessagingService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "MyFirebaseMsgService"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token: $token")
        
        // Save FCM token and update any registered push accounts
        val sipEngineManager = SipEngineManager.getInstance(applicationContext)
        sipEngineManager.saveFcmToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        // When a push is received, it means there is an incoming call/message.
        // We start the foreground SipService to wake up the SIP engine,
        // which will automatically register and receive the incoming invite.
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
        }
        
        Log.d(TAG, "Waking up SipService on incoming push notification")
        SipService.start(applicationContext)

        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
        }
    }
}
