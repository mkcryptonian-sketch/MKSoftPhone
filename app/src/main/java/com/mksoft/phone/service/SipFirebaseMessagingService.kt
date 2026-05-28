package com.mksoft.phone.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.mksoft.phone.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mksoft.phone.core.sip.SipEngineManager
import com.mksoft.phone.core.sip.SipEngineState
import android.app.PendingIntent
import com.mksoft.phone.data.DataRepository
import com.mksoft.phone.data.DefaultDataRepository
import kotlinx.coroutines.*

class SipFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "SipFcmService"
        private const val WAKE_LOCK_TIMEOUT_MS = 15000L // 15 seconds max execution window
    }

    private lateinit var sipEngineManager: SipEngineManager
    private lateinit var dataRepository: DataRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        sipEngineManager = SipEngineManager.getInstance(applicationContext)
        dataRepository = DefaultDataRepository.getInstance(applicationContext)
    }

    /**
     * Triggered downstream when Flexisip hits late-forking mode on an incoming INVITE
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "FCM Payloads Received. From: ${remoteMessage.from}, Data: ${remoteMessage.data}")

        // More robust check for VoIP wakeup payloads from Flexisip or other gateways
        val data = remoteMessage.data
        val isPushWakePayload = data.containsKey("pn-callid") || 
                                data["type"] == "incoming_call" ||
                                data.containsKey("call-id") ||
                                data.containsKey("from") ||
                                data.containsKey("pn-provider") // Handle Flexisip 2.4+ payloads

        if (isPushWakePayload) {
            val peer = data["from"] ?: data["pn-callid"] ?: "Unknown Caller"
            handleIncomingCallWakeup(applicationContext, peer)
        } else {
            Log.w(TAG, "Received FCM message that doesn't look like a call wakeup. Ignoring.")
        }
    }

    /**
     * Wakes the SIP engine so it can receive the incoming SIP INVITE.
     * 
     * We do NOT call addNewIncomingCall here. The actual TelecomManager notification
     * and full-screen UI are triggered by SipEngineManager.onCallStateChanged when the
     * real SIP INVITE is received. Calling addNewIncomingCall prematurely (before the
     * INVITE arrives) caused a phantom connection with callId=-1 that auto-answered calls.
     */
    private fun handleIncomingCallWakeup(context: Context, peerUri: String) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MKSoftPhone::FCMWakeLock"
        )

        // Secure a temporary execution window to beat Android Doze/App Standby loops
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
        Log.d(TAG, "Acquired partial CPU WakeLock for push wakeup from: $peerUri")

        serviceScope.launch {
            try {
                // Start/wake SipService so Linphone engine is running and can receive the INVITE
                Log.d(TAG, "Starting SipService to wake Linphone engine for incoming call from: $peerUri")
                SipService.start(context)

                // Wait briefly for engine to become ready then re-register all accounts
                // This ensures our SIP registration is active when the INVITE arrives
                withTimeoutOrNull(10_000L) {
                    while (sipEngineManager.engineState.value !is SipEngineState.Ready) {
                        delay(200)
                    }
                }

                if (sipEngineManager.engineState.value is SipEngineState.Ready) {
                    Log.d(TAG, "Engine ready. Syncing account registrations to receive incoming INVITE...")
                    sipEngineManager.syncActiveAccountRegistrations()
                    Log.d(TAG, "Account registration sync complete. Waiting for SIP INVITE...")
                    // The actual incoming call UI will be triggered by SipEngineManager.onCallStateChanged
                    // when the SIP INVITE arrives from the server after our re-registration.
                } else {
                    Log.w(TAG, "Engine did not become ready in time. Showing fallback notification.")
                    showFallbackIncomingCallNotification(context, peerUri)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error in push wakeup handler", e)
                showFallbackIncomingCallNotification(context, peerUri)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                Log.d(TAG, "Released execution CPU WakeLock cleanly")
            }
        }
    }

    private fun showFallbackIncomingCallNotification(context: Context, peerUri: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "VoIpCallChannel"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "VoIP Calls",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Incoming VoIP call notifications"
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Target MainActivity for tapping the notification
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cleanPeerUri = if (peerUri.startsWith("sip:")) peerUri.substring(4) else peerUri

            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setContentTitle("Incoming VoIP Call")
                .setContentText(cleanPeerUri)
                .setSmallIcon(android.R.drawable.stat_sys_phone_call)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                
            notificationManager.notify(9999, builder.build())
            Log.d(TAG, "Fallback notification posted successfully")
        } catch (ex: Exception) {
            Log.e(TAG, "Error posting fallback notification", ex)
        }
    }

    /**
     * Downstream listener mapping whenever Firebase rotates device cloud tokens
     */
    override fun onNewToken(token: String) {
        Log.i(TAG, "FCM Cloud Registration Token Refreshed: $token")
        
        serviceScope.launch {
            // Commit token directly to disk layout repository
            dataRepository.saveFcmToken(token)
            
            // Re-trigger dynamic upstream registration bindings if endpoint is already running
            if (sipEngineManager.engineState.value is SipEngineState.Ready) {
                sipEngineManager.ensureThreadRegistered()
                sipEngineManager.saveFcmToken(token)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

