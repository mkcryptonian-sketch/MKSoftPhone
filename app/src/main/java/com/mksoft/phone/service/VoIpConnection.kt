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
    initialCallId: Int,
    val peerUri: String
) : Connection() {

    var callId: Int = initialCallId
        set(value) {
            val changed = field != value
            field = value
            if (changed && value != -1) {
                Log.d(TAG, "callId updated to $value, triggering audio route sync")
                updateAudioRouteFromEndpoints()
            }
        }

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

    private var isDestroyed = false

    override fun onAnswer() {
        if (isDestroyed) return
        Log.d(TAG, "onAnswer called for callId: $callId")
        try {
            hideIncomingCallUi()
            val sipEngine = SipEngineManager.getInstance(context)
            
            if (callId == -1) {
                Log.d(TAG, "Initializing SIP to handle push-initiated call answer")
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
        if (isDestroyed) return
        isDestroyed = true
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
        if (isDestroyed) return
        isDestroyed = true
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.d(TAG, "onCallAudioStateChanged: route=${state?.route}, supportedRouteMask=${state?.supportedRouteMask}")
            if (state != null) {
                val isSpeaker = (state.route and android.telecom.CallAudioState.ROUTE_SPEAKER) != 0
                val isBluetooth = (state.route and android.telecom.CallAudioState.ROUTE_BLUETOOTH) != 0
                val isBtAvailable = (state.supportedRouteMask and android.telecom.CallAudioState.ROUTE_BLUETOOTH) != 0
                SipEngineManager.getInstance(context).updateCallAudioRoute(callId, isSpeaker, isBluetooth, isBtAvailable)
            }
        }
    }

    private var availableEndpointsList: List<android.telecom.CallEndpoint> = emptyList()
    private var currentCallEndpointObj: android.telecom.CallEndpoint? = null

    override fun onCallEndpointChanged(endpoint: android.telecom.CallEndpoint) {
        super.onCallEndpointChanged(endpoint)
        Log.d(TAG, "onCallEndpointChanged: $endpoint")
        currentCallEndpointObj = endpoint
        updateAudioRouteFromEndpoints()
    }

    override fun onAvailableCallEndpointsChanged(availableEndpoints: MutableList<android.telecom.CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(availableEndpoints)
        Log.d(TAG, "onAvailableCallEndpointsChanged: $availableEndpoints")
        availableEndpointsList = availableEndpoints
        updateAudioRouteFromEndpoints()
    }

    override fun onMuteStateChanged(isMuted: Boolean) {
        super.onMuteStateChanged(isMuted)
        Log.d(TAG, "onMuteStateChanged: $isMuted")
        SipEngineManager.getInstance(context).updateMuteState(callId, isMuted)
    }

    fun forceAudioRouteSync() {
        updateAudioRouteFromEndpoints()
    }

    private fun updateAudioRouteFromEndpoints() {
        if (callId == -1) {
            Log.d(TAG, "updateAudioRouteFromEndpoints: skipping sync because callId is -1")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val currentEndpoint: android.telecom.CallEndpoint? = currentCallEndpointObj ?: try {
                getCurrentCallEndpoint()
            } catch (_: Exception) {
                null
            }
            val available = availableEndpointsList
            val isSpeaker = currentEndpoint?.endpointType == android.telecom.CallEndpoint.TYPE_SPEAKER
            val isBluetooth = currentEndpoint?.endpointType == android.telecom.CallEndpoint.TYPE_BLUETOOTH
            val isBtAvailable = available.any { it.endpointType == android.telecom.CallEndpoint.TYPE_BLUETOOTH }
            SipEngineManager.getInstance(context).updateCallAudioRoute(callId, isSpeaker, isBluetooth, isBtAvailable)
        }
    }

    fun setSpeakerphoneOn(on: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val targetType = if (on) android.telecom.CallEndpoint.TYPE_SPEAKER else android.telecom.CallEndpoint.TYPE_EARPIECE
                val targetEndpoint = availableEndpointsList.find { it.endpointType == targetType }
                    ?: availableEndpointsList.find { it.endpointType == android.telecom.CallEndpoint.TYPE_WIRED_HEADSET }
                    ?: availableEndpointsList.find { it.endpointType == android.telecom.CallEndpoint.TYPE_EARPIECE }
                if (targetEndpoint != null) {
                    requestCallEndpointChange(targetEndpoint, context.mainExecutor, object : android.os.OutcomeReceiver<Void, android.telecom.CallEndpointException> {
                        override fun onResult(result: Void?) {
                            Log.d(TAG, "Successfully changed call endpoint to $targetEndpoint")
                        }
                        override fun onError(error: android.telecom.CallEndpointException) {
                            Log.e(TAG, "Error changing call endpoint: ${error.message}", error)
                        }
                    })
                    Log.d(TAG, "setSpeakerphoneOn: requested=$on, requested endpoint=$targetEndpoint")
                    return
                }
            }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val targetType = if (on) android.telecom.CallEndpoint.TYPE_BLUETOOTH else android.telecom.CallEndpoint.TYPE_EARPIECE
                val targetEndpoint = availableEndpointsList.find { it.endpointType == targetType }
                    ?: availableEndpointsList.find { it.endpointType == android.telecom.CallEndpoint.TYPE_WIRED_HEADSET }
                    ?: availableEndpointsList.find { it.endpointType == android.telecom.CallEndpoint.TYPE_EARPIECE }
                if (targetEndpoint != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted; cannot request Bluetooth call endpoint")
                            return
                        }
                    }
                    requestCallEndpointChange(targetEndpoint, context.mainExecutor, object : android.os.OutcomeReceiver<Void, android.telecom.CallEndpointException> {
                        override fun onResult(result: Void?) {
                            Log.d(TAG, "Successfully changed call endpoint to $targetEndpoint")
                        }
                        override fun onError(error: android.telecom.CallEndpointException) {
                            Log.e(TAG, "Error changing call endpoint: ${error.message}", error)
                        }
                    })
                    Log.d(TAG, "setBluetoothOn: requested=$on, requested endpoint=$targetEndpoint")
                    return
                }
            }
            val route = if (on) {
                android.telecom.CallAudioState.ROUTE_BLUETOOTH
            } else {
                android.telecom.CallAudioState.ROUTE_WIRED_OR_EARPIECE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        Log.w(TAG, "BLUETOOTH_CONNECT permission not granted; cannot request Bluetooth audio route")
                        return
                    }
                }
                setAudioRoute(route)
            }
            Log.d(TAG, "setBluetoothOn: requested=$on, setting route=$route")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting bluetooth route: ${e.message}", e)
        }
    }
}
