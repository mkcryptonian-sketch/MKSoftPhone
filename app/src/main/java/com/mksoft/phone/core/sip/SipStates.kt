package com.mksoft.phone.core.sip

import kotlinx.serialization.Serializable

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
    val isBluetoothAvailable: Boolean = false,
    val connectTimestamp: Long? = null,
    val isIncoming: Boolean = false,
    val isThirdParty: Boolean = false,
    val wasAnswered: Boolean = false
)

enum class SecurityLevel {
    NONE,       // Plain RTP, no encryption
    SRTP,       // SRTP with SDES key exchange
    DTLS_SRTP,  // SRTP with DTLS-SRTP key exchange (more secure)
    ZRTP        // ZRTP-negotiated media key (highest security)
}

data class CallStats(
    val callId: Int,
    // Security
    val securityLevel: SecurityLevel = SecurityLevel.NONE,
    val securityProto: String = "RTP/AVP",
    val localRtpAddress: String = "",
    val remoteRtpAddress: String = "",
    // Codec
    val codecName: String = "Unknown",
    val clockRate: Long = 0,
    // TX stats
    val txPackets: Long = 0,
    val txBytes: Long = 0,
    val txLoss: Long = 0,
    val txJitterMs: Int = 0,   // mean jitter µs -> ms
    // RX stats
    val rxPackets: Long = 0,
    val rxBytes: Long = 0,
    val rxLoss: Long = 0,
    val rxJitterMs: Int = 0,
    val rxDiscard: Long = 0,
    // Round-trip time
    val rttMs: Int = 0,        // mean RTT µs -> ms
    // Jitter buffer
    val jbAvgDelayMs: Long = 0,
    val jbMaxDelayMs: Long = 0,
    val jbCurrentSize: Long = 0,
    // Quality score 0-100 (estimated)
    val qualityScore: Int = 100,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SipChatMessage(
    val id: String,
    val peerUri: String,
    val content: String,
    val timestamp: Long,
    val isIncoming: Boolean,
    val isRead: Boolean = false,
    val status: MessageStatus = MessageStatus.Delivered
)

enum class MessageStatus {
    Sending,
    Delivered,
    Failed
}
