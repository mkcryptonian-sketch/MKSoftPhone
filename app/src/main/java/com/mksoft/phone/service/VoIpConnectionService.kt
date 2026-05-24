package com.mksoft.phone.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.mksoft.phone.R
import com.mksoft.phone.core.sip.SipEngineManager

class VoIpConnectionService : ConnectionService() {

    companion object {
        private const val TAG = "VoIpConnectionService"
        private const val ACCOUNT_ID = "MKSoftphoneAccount"

        fun registerPhoneAccount(context: Context) {
            try {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                val componentName = ComponentName(context, VoIpConnectionService::class.java)
                val phoneAccountHandle = PhoneAccountHandle(componentName, ACCOUNT_ID)

                val phoneAccount = PhoneAccount.builder(phoneAccountHandle, context.getString(R.string.app_name))
                    .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                    .setIcon(android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.stat_sys_phone_call))
                    .build()

                telecomManager.registerPhoneAccount(phoneAccount)
                Log.d(TAG, "Phone account registered with TelecomManager successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error registering phone account: ${e.message}", e)
            }
        }

        fun getPhoneAccountHandle(context: Context): PhoneAccountHandle {
            val componentName = ComponentName(context, VoIpConnectionService::class.java)
            return PhoneAccountHandle(componentName, ACCOUNT_ID)
        }
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.d(TAG, "onCreateIncomingConnection request: $request")
        val extras = request?.extras ?: Bundle()
        val nestedExtras = extras.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS)
        val callId = nestedExtras?.getInt("callId", -1) ?: extras.getInt("callId", -1)
        val peerUri = nestedExtras?.getString("peerUri") ?: extras.getString("peerUri") ?: "Unknown"

        val cleanPeerUri = if (peerUri.startsWith("sip:")) peerUri.substring(4) else peerUri

        val connection = VoIpConnection(applicationContext, callId, cleanPeerUri)
        val addressUri = if (peerUri.startsWith("sip:")) Uri.parse(peerUri) else Uri.parse("sip:$peerUri")
        connection.setAddress(addressUri, TelecomManager.PRESENTATION_ALLOWED)
        connection.setCallerDisplayName(cleanPeerUri, TelecomManager.PRESENTATION_ALLOWED)
        connection.setRinging()

        SipEngineManager.getInstance(applicationContext).registerConnection(callId, connection)
        return connection
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.d(TAG, "onCreateOutgoingConnection request: $request")
        val extras = request?.extras ?: Bundle()
        val nestedExtras = extras.getBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS)
        val accountId = nestedExtras?.getString("accountId") ?: extras.getString("accountId") ?: ""
        val destUri = nestedExtras?.getString("destUri") ?: extras.getString("destUri") ?: ""

        val connection = VoIpConnection(applicationContext, -1, destUri)
        connection.setAddress(Uri.parse("sip:$destUri"), TelecomManager.PRESENTATION_ALLOWED)
        connection.setCallerDisplayName(destUri, TelecomManager.PRESENTATION_ALLOWED)
        connection.setInitializing()

        val sipEngine = SipEngineManager.getInstance(applicationContext)
        sipEngine.makeCallNativeAsync(accountId, destUri) { callId ->
            if (callId == -1) {
                connection.setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.ERROR, "Failed to initiate call"))
                connection.destroy()
            } else {
                connection.callId = callId
                connection.setDialing()
                sipEngine.registerConnection(callId, connection)
                
                // Open MainActivity so that they are looking at our custom call screen
                try {
                    val launchIntent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    if (launchIntent != null) {
                        applicationContext.startActivity(launchIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to bring MainActivity to foreground: ${e.message}")
                }
            }
        }

        return connection
    }
}
