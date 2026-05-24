package com.mksoft.phone.core.sip

sealed interface SipEngineState {
    object Uninitialized : SipEngineState
    object Initializing : SipEngineState
    object Ready : SipEngineState
    data class Error(val message: String) : SipEngineState
}

sealed interface RegistrationState {
    object Idle : RegistrationState
    object Registering : RegistrationState
    object Registered : RegistrationState
    data class Failed(val statusCode: Int, val reason: String) : RegistrationState
}

sealed interface SipCallState {
    object Idle : SipCallState
    object Incoming : SipCallState
    object Outgoing : SipCallState
    object Connecting : SipCallState
    object Confirmed : SipCallState
    object Disconnected : SipCallState
}

data class SipLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: Int,
    val threadName: String,
    val message: String
)

data class AccountWrapper(
    val id: String,
    val username: String,
    val domain: String,
    val registrationState: RegistrationState = RegistrationState.Idle,
    val lastStatusCode: Int = 0,
    val lastStatusText: String = ""
)

data class CallWrapper(
    val callId: Int,
    val accountId: String,
    val peerUri: String,
    val callState: SipCallState = SipCallState.Idle,
    val isLocalHold: Boolean = false,
    val isRemoteHold: Boolean = false,
    val isMuted: Boolean = false,
    val isRecording: Boolean = false,
    val isSpeakerphoneOn: Boolean = false,
    val isBluetoothOn: Boolean = false,
    val connectTimestamp: Long? = null,
    val isIncoming: Boolean = false
)

