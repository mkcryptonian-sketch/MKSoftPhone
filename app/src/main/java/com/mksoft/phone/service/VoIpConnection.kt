package com.mksoft.phone.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.util.Log
import com.mksoft.phone.core.sip.SipEngineManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class VoIpConnection(
    private val context: Context,
    var callId: Int, // Changed to var so SipEngineManager can update it later
    val peerUri: String
) : Connection() {

    companion object {
        private const val TAG = "VoIpConnection"
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            connectionProperties = PROPERTY_SELF_MANAGED
        }
        connectionCapabilities = CAPABILITY_SUPPORT_HOLD or CAPABILITY_HOLD
        audioModeIsVoip = true
    }

    override fun onShowIncomingCallUi() {
        Log.d(TAG, "onShowIncomingCallUi called for callId: $callId")
        val serviceIntent = Intent(context, SipService::class.java).apply {
            action = SipService.ACTION_SHOW_INCOMING_CALL_UI
            putExtra(SipService.EXTRA_CALL_ID, callId)
            putExtra(SipService.EXTRA_PEER_URI, peerUri)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun hideIncomingCallUi() {
        val serviceIntent = Intent(context, SipService::class.java).apply {
            action = SipService.ACTION_HIDE_INCOMING_CALL_UI
            putExtra(SipService.EXTRA_CALL_ID, callId)
        }
        context.startService(serviceIntent)
    }

    override fun onAnswer() {
        Log.d(TAG, "onAnswer called for callId: $callId")
        try {
            hideIncomingCallUi()
            val sipEngine = SipEngineManager.getInstance(context)
            
            if (callId == -1) {
                Log.d(TAG, "Initializing PJSIP to handle push-initiated call answer")
                sipEngine.setPendingPushConnection(this)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        sipEngine.ensureThreadRegistered()
                        sipEngine.syncActiveAccountRegistrations()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error syncing registrations: ${e.message}", e)
                    }
                }
            } else {
                sipEngine.answerCall(callId)
            }
            setActive()
        } catch (e: Exception) {
            Log.e(TAG, "Error answering call: ${e.message}", e)
        }
    }

    override fun onReject() {
        Log.d(TAG, "onReject called for callId: $callId")
        try {
            hideIncomingCallUi()
            val sipEngine = SipEngineManager.getInstance(context)
            if (callId == -1) {
                sipEngine.setPendingPushConnection(null)
            } else {
                sipEngine.hangupCall(callId)
                sipEngine.unregisterConnection(callId)
            }
            setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
            destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error rejecting call: ${e.message}", e)
        }
    }

    override fun onDisconnect() {
        Log.d(TAG, "onDisconnect called for callId: $callId")
        try {
            hideIncomingCallUi()
            val sipEngine = SipEngineManager.getInstance(context)
            if (callId == -1) {
                sipEngine.setPendingPushConnection(null)
            } else {
                sipEngine.hangupCall(callId)
                sipEngine.unregisterConnection(callId)
            }
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting call: ${e.message}", e)
        }
    }

    override fun onHold() {
        Log.d(TAG, "onHold called for callId: $callId")
        try {
            SipEngineManager.getInstance(context).setHold(callId, true)
            setOnHold()
        } catch (e: Exception) {
            Log.e(TAG, "Error holding call: ${e.message}", e)
        }
    }

    override fun onUnhold() {
        Log.d(TAG, "onUnhold called for callId: $callId")
        try {
            SipEngineManager.getInstance(context).setHold(callId, false)
            setActive()
        } catch (e: Exception) {
            Log.e(TAG, "Error unholding call: ${e.message}", e)
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onCallAudioStateChanged(state: android.telecom.CallAudioState?) {
        super.onCallAudioStateChanged(state)
        Log.d(TAG, "onCallAudioStateChanged: route=${state?.route}, supportedRouteMask=${state?.supportedRouteMask}")
        if (state != null) {
            val isSpeaker = (state.route and android.telecom.CallAudioState.ROUTE_SPEAKER) != 0
            val isBluetooth = (state.route and android.telecom.CallAudioState.ROUTE_BLUETOOTH) != 0
            SipEngineManager.getInstance(context).updateCallAudioRoute(callId, isSpeaker, isBluetooth)
        }
    }

    fun setSpeakerphoneOn(on: Boolean) {
        try {
            val route = if (on) {
                android.telecom.CallAudioState.ROUTE_SPEAKER
            } else {
                android.telecom.CallAudioState.ROUTE_WIRED_OR_EARPIECE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setAudioRoute(route)
            }
            Log.d(TAG, "setSpeakerphoneOn: requested=$on, setting route=$route")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting audio route: ${e.message}", e)
        }
    }

    fun setBluetoothOn(on: Boolean) {
        try {
            val route = if (on) {
                android.telecom.CallAudioState.ROUTE_BLUETOOTH
            } else {
                android.telecom.CallAudioState.ROUTE_WIRED_OR_EARPIECE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setAudioRoute(route)
            }
            Log.d(TAG, "setBluetoothOn: requested=$on, setting route=$route")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting bluetooth route: ${e.message}", e)
        }
    }
}
