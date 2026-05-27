package com.mksoft.phone.core.sip

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import com.mksoft.phone.core.audit.AuditManager
import com.mksoft.phone.data.DefaultDataRepository
import com.mksoft.phone.data.SipAccountConfig
import com.mksoft.phone.data.SipCodecConfig
import com.mksoft.phone.data.VoIpSettings
import com.mksoft.phone.service.SipService
import com.mksoft.phone.service.VoIpConnection
import com.mksoft.phone.service.VoIpConnectionService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.linphone.core.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class SipEngineManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SipEngineManager"

        @Volatile
        private var instance: SipEngineManager? = null

        fun getInstance(context: Context): SipEngineManager {
            return instance ?: synchronized(this) {
                instance ?: SipEngineManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val factory = Factory.instance()
    private lateinit var core: Core
    private val sipScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Flow states exposed to the UI viewmodels
    private val _engineState = MutableStateFlow<SipEngineState>(SipEngineState.Uninitialized)
    val engineState: StateFlow<SipEngineState> = _engineState.asStateFlow()

    private val accountMap = ConcurrentHashMap<String, AccountWrapper>()
    private val _activeAccounts = MutableStateFlow<Map<String, AccountWrapper>>(emptyMap())
    val activeAccounts: StateFlow<Map<String, AccountWrapper>> = _activeAccounts.asStateFlow()

    private val callMap = ConcurrentHashMap<Int, CallWrapper>()
    private val _activeCalls = MutableStateFlow<Map<Int, CallWrapper>>(emptyMap())
    val activeCalls: StateFlow<Map<Int, CallWrapper>> = _activeCalls.asStateFlow()

    private val _isConferenceActive = MutableStateFlow(false)
    val isConferenceActive: StateFlow<Boolean> = _isConferenceActive.asStateFlow()

    private val _callStats = MutableStateFlow<Map<Int, CallStats>>(emptyMap())
    val callStats: StateFlow<Map<Int, CallStats>> = _callStats.asStateFlow()

    private val _sipLogs = MutableSharedFlow<SipLogEntry>(replay = 100)
    val sipLogs: SharedFlow<SipLogEntry> = _sipLogs.asSharedFlow()

    private val _incomingMessages = MutableSharedFlow<SipChatMessage>(extraBufferCapacity = 10)
    val incomingMessages: SharedFlow<SipChatMessage> = _incomingMessages.asSharedFlow()

    // Native tracking maps
    private val connections = ConcurrentHashMap<Int, VoIpConnection>()
    private val linphoneCalls = ConcurrentHashMap<Int, Call>()
    private val linphoneAccounts = ConcurrentHashMap<String, Account>()
    private val activeRecorders = ConcurrentHashMap<Int, String>() // callId to File path
    private val locallyDeclinedCallIds = ConcurrentHashMap.newKeySet<Int>()
    private val answeredCallIds = ConcurrentHashMap.newKeySet<Int>()
    private val sipMutex = Mutex()
    private var pendingPushConnection: VoIpConnection? = null

    init {
        // Poll Call stats periodically.
        // MUST run on Dispatchers.Main – Linphone's CoreImpl holds the native lock on the main
        // thread during core.iterate(); accessing Call/CallParams from a background thread
        // causes a deadlock / ANR (the two threads wait on each other's locks).
        sipScope.launch(Dispatchers.Main) {
            while (true) {
                try {
                    delay(1000)
                    val activeCallIds = _activeCalls.value.keys
                    if (activeCallIds.isNotEmpty()) {
                        val updatedStats = _callStats.value.toMutableMap()

                        // Clean up inactive stats
                        updatedStats.keys.retainAll(activeCallIds)

                        for (callId in activeCallIds) {
                            val call = linphoneCalls[callId]
                            if (call != null) {
                                try {
                                    val currentParams = call.currentParams
                                    val usedCodec = currentParams?.usedAudioPayloadType
                                    val codecName = usedCodec?.mimeType ?: "Unknown"
                                    val clockRate = usedCodec?.clockRate?.toLong() ?: 0L

                                    val audioStats = call.getStats(org.linphone.core.StreamType.Audio)
                                    val rxLoss = audioStats?.receiverLossRate?.toLong() ?: 0L
                                    val txLoss = audioStats?.senderLossRate?.toLong() ?: 0L
                                    val rtt = ((audioStats?.roundTripDelay ?: 0f) * 1000f).toInt()
                                    val jbSize = audioStats?.jitterBufferSizeMs?.toLong() ?: 0L

                                    val activeEnc = currentParams?.mediaEncryption
                                    val securityLevel = when (activeEnc) {
                                        org.linphone.core.MediaEncryption.SRTP -> SecurityLevel.SRTP
                                        org.linphone.core.MediaEncryption.ZRTP -> SecurityLevel.ZRTP
                                        org.linphone.core.MediaEncryption.DTLS -> SecurityLevel.DTLS_SRTP
                                        else -> SecurityLevel.NONE
                                    }
                                    val securityProto = when (activeEnc) {
                                        org.linphone.core.MediaEncryption.SRTP -> "RTP/SAVP"
                                        org.linphone.core.MediaEncryption.ZRTP -> "RTP/AVP (ZRTP)"
                                        org.linphone.core.MediaEncryption.DTLS -> "RTP/SAVPF (DTLS)"
                                        else -> "RTP/AVP"
                                    }
                                    val callAccount = findAccountForCall(call)

                                    val stats = CallStats(
                                        callId = callId,
                                        codecName = codecName,
                                        clockRate = clockRate,
                                        securityLevel = securityLevel,
                                        securityProto = securityProto,
                                        txPackets = 0L,
                                        txBytes = 0L,
                                        txLoss = txLoss,
                                        txJitterMs = 0,
                                        rxPackets = 0L,
                                        rxBytes = 0L,
                                        rxLoss = rxLoss,
                                        rxJitterMs = 0,
                                        rxDiscard = 0L,
                                        rttMs = rtt,
                                        jbAvgDelayMs = 0L,
                                        jbMaxDelayMs = 0L,
                                        jbCurrentSize = jbSize,
                                        localRtpAddress = callAccount?.params?.identityAddress?.asString() ?: "",
                                        remoteRtpAddress = call.remoteAddress?.asString() ?: ""
                                    )
                                    updatedStats[callId] = stats
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error fetching stream stats for call $callId: ${e.message}")
                                }
                            }
                        }
                        _callStats.value = updatedStats
                    } else if (_callStats.value.isNotEmpty()) {
                        _callStats.value = emptyMap()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in stats polling loop: ${e.message}")
                }
            }
        }
    }

    private fun findAccountForCall(call: Call): Account? {
        val localAddr = call.callLog?.localAddress ?: return null
        val localUsername = localAddr.username ?: ""
        val localDomain = localAddr.domain ?: ""
        val accountList = core.accountList
        if (accountList != null) {
            for (account in accountList) {
                val identity = account.params?.identityAddress ?: continue
                if (identity.username == localUsername && identity.domain == localDomain) {
                    return account
                }
            }
        }
        return null
    }

    private val coreListener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: org.linphone.core.RegistrationState,
            message: String
        ) {
            try {
                val params = account.params
                val identity = params?.identityAddress ?: return
                val accId = "sip:${identity.username}@${identity.domain}"

                val uiState = when (state) {
                    org.linphone.core.RegistrationState.Ok -> com.mksoft.phone.core.sip.RegistrationState.Registered
                    org.linphone.core.RegistrationState.Progress -> com.mksoft.phone.core.sip.RegistrationState.Registering
                    org.linphone.core.RegistrationState.Failed -> {
                        val errorInfo = account.errorInfo
                        val code = errorInfo?.protocolCode ?: 0
                        val phrase = errorInfo?.phrase ?: message
                        com.mksoft.phone.core.sip.RegistrationState.Failed(code, phrase)
                    }
                    else -> com.mksoft.phone.core.sip.RegistrationState.Idle
                }

                val current = accountMap[accId]
                if (current != null) {
                    accountMap[accId] = current.copy(
                        registrationState = uiState,
                        lastStatusCode = account.errorInfo?.protocolCode ?: 0,
                        lastStatusText = account.errorInfo?.phrase ?: message
                    )
                    _activeAccounts.value = accountMap.toMap()
                }
                Log.d(TAG, "Account reg state changed: $accId -> $uiState")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling reg state change callback", e)
            }
        }

        override fun onMessageReceived(core: Core, chatRoom: ChatRoom, message: ChatMessage) {
            try {
                val text = message.utf8Text ?: ""
                val sender = message.fromAddress?.asString() ?: "Unknown"
                val sipMessage = SipChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    peerUri = sender,
                    content = text,
                    timestamp = System.currentTimeMillis(),
                    isIncoming = true
                )
                _incomingMessages.tryEmit(sipMessage)
                Log.d(TAG, "New message from $sender: $text")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming message", e)
            }
        }

        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            try {
                val callId = call.hashCode()
                Log.d(TAG, "Call state event. ID: $callId, State: $state")

                // No extra cleanup needed for outgoing calls as we no longer use temporary accounts/params.

                val isIncoming = call.dir == Call.Dir.Incoming
                
                val sipCallState = when (state) {
                    Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> SipCallState.Incoming
                    Call.State.OutgoingInit, Call.State.OutgoingProgress, Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia -> SipCallState.Outgoing
                    Call.State.Connected -> SipCallState.Connecting
                    Call.State.StreamsRunning -> SipCallState.Confirmed
                    Call.State.End, Call.State.Released, Call.State.Error -> SipCallState.Disconnected
                    else -> SipCallState.Idle
                }

                // Ensure the call is tracked natively throughout its lifecycle
                if (state != Call.State.Released) {
                    linphoneCalls[callId] = call
                }

                val account = findAccountForCall(call)
                val accountId = if (account != null) {
                    val identity = account.params?.identityAddress
                    if (identity != null) "sip:${identity.username}@${identity.domain}" else ""
                } else {
                    ""
                }
                val remoteAddr = call.remoteAddress
                val peerUri = if (remoteAddr != null) "sip:${remoteAddr.username}@${remoteAddr.domain}" else "Unknown"

                var current = callMap[callId]
                
                // Track "Answered" state permanently for this call instance
                if (state == Call.State.Connected || state == Call.State.StreamsRunning) {
                    answeredCallIds.add(callId)
                }
                
                val isActuallyAnswered = answeredCallIds.contains(callId)

                if (current == null) {
                    val updatedConnectTimestamp = if (sipCallState == SipCallState.Confirmed) {
                        System.currentTimeMillis()
                    } else {
                        null
                    }
                    current = CallWrapper(
                        callId = callId,
                        accountId = accountId,
                        peerUri = peerUri,
                        callState = sipCallState,
                        isIncoming = isIncoming,
                        connectTimestamp = updatedConnectTimestamp,
                        wasAnswered = isActuallyAnswered
                    )
                    callMap[callId] = current
                } else {
                    val updatedConnectTimestamp = if (sipCallState == SipCallState.Confirmed && current.connectTimestamp == null) {
                        System.currentTimeMillis()
                    } else {
                        current.connectTimestamp
                    }
                    current = current.copy(
                        callState = sipCallState,
                        connectTimestamp = updatedConnectTimestamp,
                        wasAnswered = isActuallyAnswered
                    )
                    callMap[callId] = current
                }
                _activeCalls.value = callMap.toMap()

                // Telecom integration connection updates
                val connection = connections[callId]
                if (connection != null) {
                    when (state) {
                        Call.State.Connected, Call.State.StreamsRunning -> {
                            connection.setActive()
                        }
                        Call.State.End, Call.State.Released, Call.State.Error -> {
                            val errorInfo = call.errorInfo
                            val code = errorInfo?.protocolCode ?: 0
                            val reason = errorInfo?.phrase ?: "Disconnected"
                            val cause = when (code) {
                                486 -> android.telecom.DisconnectCause.BUSY
                                487 -> android.telecom.DisconnectCause.CANCELED
                                403 -> android.telecom.DisconnectCause.RESTRICTED
                                else -> android.telecom.DisconnectCause.REMOTE
                            }
                            Log.d(TAG, "Notifying Telecom of disconnection for $callId. Code: $code, Reason: $reason")
                            try {
                                connection.setDisconnected(android.telecom.DisconnectCause(cause, reason))
                                connection.destroy()
                            } catch (e: Exception) {
                                Log.w(TAG, "Telecom Connection already destroyed: ${e.message}")
                            }
                            unregisterConnection(callId)
                        }
                        else -> {}
                    }
                }

                // Handle incoming call ringing and matching
                if (state == Call.State.IncomingReceived || state == Call.State.IncomingEarlyMedia) {
                    val repository = DefaultDataRepository.getInstance(context)
                    val settings = repository.settings.value

                    // DND Sync: Reject with 486 Busy if DND is active
                    if (settings.dndSyncEnabled) {
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        val filter = notificationManager.currentInterruptionFilter
                        if (filter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                            Log.d(TAG, "DND active (filter=$filter). Rejecting call $callId with 486 Busy.")
                            call.terminateWithErrorInfo(factory.createErrorInfo().apply {
                                setProtocolCode(486)
                                phrase = "Busy Here (DND)"
                            })
                            return
                        }
                    }
                    
                    // Check if there is a pending push connection from FCM wakeup (callId == -1)
                    val pushConnection = connections[-1]
                    if (pushConnection != null) {
                        // Link the pending push connection to the real incoming call
                        Log.d(TAG, "Linking incoming SIP call $callId to pending push connection")
                        pushConnection.callId = callId
                        registerConnection(callId, pushConnection)
                        unregisterConnection(-1)
                        pendingPushConnection = null
                        // DO NOT auto-answer – fall through to show the UI below
                    }

                    // Always notify TelecomManager so the system shows the incoming call UI
                    if (settings.nativeCallIntegrationEnabled) {
                        Log.d(TAG, "Incoming call registered: ID $callId from $peerUri. Notifying TelecomManager.")
                        try {
                            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                            val phoneAccountHandle = VoIpConnectionService.getPhoneAccountHandle(context)
                            val extras = Bundle().apply {
                                putInt("callId", callId)
                                putString("peerUri", peerUri)
                            }
                            val incomingCallExtras = Bundle().apply {
                                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
                                putBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, extras)
                            }
                            telecomManager.addNewIncomingCall(phoneAccountHandle, incomingCallExtras)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error adding incoming call to TelecomManager: ${e.message}", e)
                        }
                    }

                    // Also trigger SipService to wake screen, start ringtone, and show full-screen notification
                    val showUiIntent = android.content.Intent(context, SipService::class.java).apply {
                        action = SipService.ACTION_SHOW_INCOMING_CALL_UI
                        putExtra(SipService.EXTRA_CALL_ID, callId)
                        putExtra(SipService.EXTRA_PEER_URI, peerUri)
                    }
                    sipScope.launch {
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                context.startForegroundService(showUiIntent)
                            } else {
                                context.startService(showUiIntent)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to trigger SHOW_INCOMING_CALL_UI: ${e.message}")
                        }
                    }
                }

                if (state == Call.State.End || state == Call.State.Released || state == Call.State.Error) {
                    if (activeRecorders.containsKey(callId)) {
                        stopRecording(callId)
                    }

                    val hideIntent = android.content.Intent(context, SipService::class.java).apply {
                        action = SipService.ACTION_HIDE_INCOMING_CALL_UI
                        putExtra(SipService.EXTRA_CALL_ID, callId)
                    }
                    sipScope.launch {
                        try {
                            context.startService(hideIntent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start SipService: ${e.message}")
                        }
                    }

                    // Remove from UI map immediately on End/Error to ensure UI reflects disconnection
                    val removedCall = callMap.remove(callId)
                    if (removedCall != null) {
                        Log.d(TAG, "Removed call $callId from callMap. wasAnswered=${removedCall.wasAnswered}")
                    }
                    _activeCalls.value = callMap.toMap()

                    if (state == Call.State.Released) {
                        linphoneCalls.remove(callId)
                        answeredCallIds.remove(callId)
                    }

                    val wasLocallyDeclined = locallyDeclinedCallIds.remove(callId)
                    val wasAnswered = answeredCallIds.contains(callId) || (removedCall?.wasAnswered == true)
                    
                    Log.d(TAG, "Termination check for $callId: state=$state, isIncoming=${removedCall?.isIncoming}, wasAnswered=$wasAnswered, locallyDeclined=$wasLocallyDeclined")

                    if (removedCall != null && removedCall.isIncoming && !wasAnswered) {
                        if (!wasLocallyDeclined && state != Call.State.Error) {
                            Log.d(TAG, "Triggering missed call notification for ${removedCall.peerUri}")
                            val missedIntent = android.content.Intent(context, SipService::class.java).apply {
                                action = SipService.ACTION_SHOW_MISSED_CALL
                                putExtra(SipService.EXTRA_PEER_URI, removedCall.peerUri)
                            }
                            sipScope.launch {
                                try {
                                    context.startService(missedIntent)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to start SipService: ${e.message}")
                                }
                            }
                        }
                    }

                    if (linphoneCalls.size < 2) {
                        _isConferenceActive.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onCallStateChanged callback: ${e.message}", e)
            }
        }
    }

    fun ensureThreadRegistered() {
        // No-op for Linphone
    }

    fun setPendingPushConnection(connection: VoIpConnection?) {
        pendingPushConnection = connection
        if (connection != null) {
            connections[-1] = connection
        } else {
            connections.remove(-1)
        }
    }

    fun registerConnection(callId: Int, connection: VoIpConnection) {
        connections[callId] = connection
        Log.d(TAG, "Registered Telecom connection for callId: $callId")
    }

    fun unregisterConnection(callId: Int) {
        connections.remove(callId)
        Log.d(TAG, "Unregistered Telecom connection for callId: $callId")
    }

    fun getConnection(callId: Int): VoIpConnection? {
        return connections[callId]
    }

    fun reRegisterAll() {
        if (_engineState.value !is SipEngineState.Ready) {
            Log.d(TAG, "reRegisterAll: engine not ready, calling initialize() instead")
            initialize()
            return
        }
        sipScope.launch {
            try {
                val repository = DefaultDataRepository.getInstance(context)
                val savedAccounts = repository.accounts.value
                Log.d(TAG, "reRegisterAll: checking ${savedAccounts.size} account(s) for registration")
                savedAccounts.forEach { acc ->
                    val existing = accountMap[acc.id]
                    val isRegistered = existing?.registrationState is com.mksoft.phone.core.sip.RegistrationState.Registered
                    if (!isRegistered) {
                        Log.d(TAG, "reRegisterAll: re-registering ${acc.id}")
                        val nativeAccount = linphoneAccounts[acc.id]
                        nativeAccount?.refreshRegister()
                    } else {
                        Log.d(TAG, "reRegisterAll: ${acc.id} already registered, skipping")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "reRegisterAll error: ${e.message}", e)
            }
        }
    }

    fun updateMediaSettings() {
        // Linphone handles DSP filters, AEC, and AGC internally.
    }

    fun applyGlobalSettings(settings: VoIpSettings) {
        sipScope.launch(Dispatchers.Main) {
            try {
                if (!::core.isInitialized) return@launch

                // --- Latency Optimization: Audio Settings ---
                core.config?.setInt("sound", "playback_buffer_size", 40) 
                core.config?.setInt("sound", "record_buffer_size", 40)
                
                // --- Audio & Media ---
                core.isEchoCancellationEnabled = settings.aecEnabled
                
                // RTP Timeout settings to detect disconnected peers (Stuck Call Fix)
                core.config?.setInt("rtp", "rtp_timeout", 10) 
                core.config?.setInt("rtp", "nortp_timeout", 5) // 5s of no RTP -> disconnect

                // Session Timers (RFC 4028) - Force refresh to detect dead sessions
                core.config?.setBool("sip", "use_session_timers", true)
                core.config?.setInt("sip", "session_expires", 60) 
                core.config?.setInt("sip", "session_refresher_value", 0) 
                
                // NAT & VPN Robustness
                core.config?.setBool("sip", "relaxed_ack_validation", true)
                core.config?.setBool("sip", "fixed_contact_with_any_port", true)
                core.config?.setBool("sip", "contact_has_any_port", true)
                
                core.config?.setBool("rtp", "jitter_buffer_enabled", true)
                core.config?.setInt("rtp", "jitter_buffer_min_size", 40)   
                core.config?.setInt("rtp", "jitter_buffer_max_size", 200)

                // Network & NAT
                val natPolicy = core.natPolicy ?: core.createNatPolicy()
                natPolicy.stunServer = settings.stunServer
                natPolicy.isStunEnabled = settings.stunServer.isNotBlank()
                natPolicy.isIceEnabled = settings.iceEnabled
                // Standard behavior for modern SIP proxies: always use rport
                core.config?.setBool("sip", "fixed_contact_with_any_port", true)
                core.isNetworkReachable = true
                
                if (settings.turnServer.isNotBlank()) {
                    natPolicy.stunServer = settings.turnServer
                    // In some SDK versions, it's setTurnEnabled(true)
                    try {
                        // Attempt common method names via property access or direct call
                        // natPolicy.isTurnEnabled = true 
                    } catch (e: Exception) {}
                } else {
                    // natPolicy.isTurnEnabled = false
                }
                core.natPolicy = natPolicy

                core.isIpv6Enabled = when (settings.ipv6Preference) {
                    "Force IPv6" -> true
                    "Force IPv4" -> false
                    else -> true
                }
                core.config?.setInt("sip", "keepalive_period", settings.keepAliveInterval * 1000)
                core.isKeepAliveEnabled = settings.backgroundKeepAliveEnabled

                // Audio & Media
                core.isEchoCancellationEnabled = settings.aecEnabled
                // Note: Linphone doesn't have a direct "Hardware/Software" AEC toggle in this way,
                // it usually uses the OS hardware AEC if available when software AEC is disabled,
                // or supplements it. We map "Software" to enabling Linphone's AEC.
                
                when (settings.dtmfMethod) {
                    "RFC 2833" -> {
                        core.useRfc2833ForDtmf = true
                        core.useInfoForDtmf = false
                    }
                    "SIP INFO" -> {
                        core.useRfc2833ForDtmf = false
                        core.useInfoForDtmf = true
                    }
                    "In-band" -> {
                        core.useRfc2833ForDtmf = false
                        core.useInfoForDtmf = false
                    }
                }

                // Security
                try {
                    core.setLimeX3DhEnabled(settings.limeEnabled)
                } catch (e: Exception) {
                    Log.w(TAG, "LIME not supported in this SDK build")
                }
                core.mediaEncryption = if (settings.limeEnabled) org.linphone.core.MediaEncryption.ZRTP else org.linphone.core.MediaEncryption.None

                // Post-Quantum Encryption (PQE)
                if (settings.postQuantumEnabled) {
                    try {
                        // Using enum values if available, otherwise fallback to strings if supported by a different overload
                        // For 5.3.77, it usually expects ZrtpKeyAgreement enum array.
                        // We will try to set it via config if direct property is tricky.
                        core.config?.setString("sip", "zrtp_key_agreement_suites", "KYB1,X255,X448")
                        Log.d(TAG, "PQE (Kyber) enabled via config suites")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to set PQE suites: ${e.message}")
                    }
                }

                // Hardware
                // Proximity is handled in SipService.kt

                Log.d(TAG, "Global settings applied successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error applying global settings: ${e.message}")
            }
        }
    }

    fun initialize() {
        if (_engineState.value is SipEngineState.Ready || _engineState.value is SipEngineState.Initializing) {
            Log.d(TAG, "SIP Engine already initialized or initializing")
            return
        }

        _engineState.value = SipEngineState.Initializing
        sipScope.launch(Dispatchers.Main) {
            try {
                // Initialize Logging
                val loggingService = factory.loggingService
                loggingService.addListener(object : org.linphone.core.LoggingServiceListener {
                    override fun onLogMessageWritten(
                        logService: org.linphone.core.LoggingService,
                        domain: String,
                        level: org.linphone.core.LogLevel,
                        message: String
                    ) {
                        val uiLevel = when (level) {
                            org.linphone.core.LogLevel.Debug -> 4
                            org.linphone.core.LogLevel.Warning -> 2
                            org.linphone.core.LogLevel.Error -> 1
                            else -> 3
                        }
                        val logEntry = SipLogEntry(
                            level = uiLevel,
                            threadName = Thread.currentThread().name,
                            message = "[$domain] $message"
                        )
                        _sipLogs.tryEmit(logEntry)
                    }
                })
                
                factory.setDebugMode(true, "LinphoneEngine")
                
                core = factory.createCore(null, null, context)

                val repository = DefaultDataRepository.getInstance(context)
                val settings = repository.settings.value
                
                // Configure global NAT Policy for STUN / ICE / TURN
                val natPolicy = core.createNatPolicy()
                if (natPolicy != null) {
                    natPolicy.stunServer = settings.stunServer
                    natPolicy.isStunEnabled = settings.stunServer.isNotBlank()
                    natPolicy.isIceEnabled = settings.iceEnabled
                    if (settings.turnServer.isNotBlank()) {
                        natPolicy.stunServer = settings.turnServer // Linphone uses stunServer field for TURN too
                        // natPolicy.enableTurn(true) // Try different variants if this fails
                    }
                    core.natPolicy = natPolicy
                }

                // Apply IPv6 Preference
                core.isIpv6Enabled = when (settings.ipv6Preference) {
                    "Force IPv6" -> true
                    "Force IPv4" -> false
                    else -> true // Dual-stack
                }

                // Apply Keep-Alive Interval (Linphone uses ms via config)
                core.config?.setInt("sip", "keepalive_period", settings.keepAliveInterval * 1000)
                core.isKeepAliveEnabled = true

                // Proximity Sensor - Omit if not available in SDK
                // core.isProximitySensorEnabled = settings.proximitySensorEnabled

                // DTMF and Encryption
                core.playDtmf('0', 0) // Initialize DTMF
                // if (settings.limeEnabled) {
                //    core.isLimeX3DhEnabled = true
                // }
                
                core.addListener(coreListener)
                core.start()
                
                core.clearAccounts()
                core.clearAllAuthInfo()
                
                _engineState.value = SipEngineState.Ready
                
                // Apply global settings immediately after engine is ready
                applyGlobalSettings(repository.settings.value)
                
                loadAndRegisterSavedAccounts()
                VoIpConnectionService.registerPhoneAccount(context)
                
                Log.d(TAG, "Linphone SIP Engine successfully initialized and started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Linphone Engine: ${e.message}", e)
                _engineState.value = SipEngineState.Error(e.message ?: "Unknown init error")
            }
        }
    }

    fun shutdown() {
        sipScope.launch(Dispatchers.Main) {
            try {
                if (::core.isInitialized) {
                    core.stop()
                    core.removeListener(coreListener)
                }
                _engineState.value = SipEngineState.Uninitialized
                linphoneCalls.clear()
                linphoneAccounts.clear()
                accountMap.clear()
                callMap.clear()
                _activeAccounts.value = emptyMap()
                _activeCalls.value = emptyMap()
                Log.d(TAG, "Linphone SIP Engine successfully shut down")
            } catch (e: Exception) {
                Log.e(TAG, "Error shutting down Linphone core: ${e.message}", e)
            }
        }
    }

    private fun updateCodecPriorities() {
        try {
            val audioCodecs = core.audioPayloadTypes
            val repository = DefaultDataRepository.getInstance(context)
            val allAccounts = repository.accounts.value
            
            // Priority list for low latency:
            // 1. Opus (if configured for low latency/high bit rate)
            // 2. G722 (excellent quality, low overhead)
            // 3. PCMU/PCMA (Standard, zero processing delay)
            val latencyPriority = listOf("opus", "g722", "pcmu", "pcma")

            // Enable all codecs that are enabled in ANY active account
            val enabledMimes = mutableSetOf<String>()
            allAccounts.filter { it.isEnabled }.forEach { config ->
                config.getNormalizedCodecs().filter { it.enabled }.forEach {
                    enabledMimes.add(it.id.substringBefore("/").lowercase())
                }
            }

            if (enabledMimes.isEmpty()) {
                enabledMimes.addAll(listOf("opus", "g722", "pcmu", "pcma"))
            }

            audioCodecs.forEach { payloadType ->
                val mime = payloadType.mimeType.lowercase()
                val isEnabled = enabledMimes.contains(mime)
                payloadType.enable(isEnabled)
                
                if (isEnabled) {
                    // Adjust priority based on our latency list
                    val index = latencyPriority.indexOf(mime)
                    if (index != -1) {
                        // Linphone higher number = higher priority
                        payloadType.number = 100 - index 
                    }
                }
            }
            Log.d(TAG, "Codec priorities updated globally for all active accounts with latency optimization")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating codec priorities: ${e.message}", e)
        }
    }

    private fun resolveRoutingParameters(config: SipAccountConfig, settings: VoIpSettings): Triple<String, String, Int?> {
        val defaultLocalDomain = "ast.cdoapp.online"
        val configuredLocal = settings.localDomain.trim()

        val isLocalAccount = if (configuredLocal.isNotBlank()) {
            config.domain.contains(configuredLocal, ignoreCase = true)
        } else {
            config.domain.contains(defaultLocalDomain, ignoreCase = true)
        }

        val customProxy = settings.outboundProxy.trim()

        return when {
            // Logic 1: Direct Asterisk (Bypass Flexisip)
            isLocalAccount && !config.useSbc -> {
                Triple("", config.transport, config.port)
            }
            // Logic 2: Local account via Flexisip SBC
            isLocalAccount && config.useSbc -> {
                Triple("sip:ast.cdoapp.online:5066;transport=tls", "TLS", 5066)
            }
            // Logic 3: Non-local account via Flexisip Proxy
            else -> {
                val proxy = customProxy.ifBlank { "sips:ast.cdoapp.online:5066;transport=tls" }
                Triple(proxy, "TLS", 5066)
            }
        }
    }

    private suspend fun addAccountNativeLocked(config: SipAccountConfig): String {
        val repository = DefaultDataRepository.getInstance(context)
        val settings = repository.settings.value

        val routing = resolveRoutingParameters(config, settings)
        val resolvedConfig = config.copy(
            outboundProxy = routing.first,
            transport = routing.second,
            port = routing.third
        )
        val accountId = resolvedConfig.id
        if (linphoneAccounts.containsKey(accountId)) {
            Log.d(TAG, "Account already exists natively: $accountId")
            return accountId
        }

        if (_engineState.value !is SipEngineState.Ready) {
            Log.d(TAG, "Engine not ready, waiting...")
            val ready = withTimeoutOrNull(30_000L) {
                _engineState.first { it is SipEngineState.Ready }
            }
            if (ready == null) {
                val errMsg = "SIP Engine did not become Ready. Cannot add account."
                Log.e(TAG, errMsg)
                accountMap[accountId] = AccountWrapper(
                    id = accountId,
                    username = resolvedConfig.username,
                    domain = resolvedConfig.domain,
                    registrationState = com.mksoft.phone.core.sip.RegistrationState.Failed(0, errMsg)
                )
                _activeAccounts.value = accountMap.toMap()
                return accountId
            }
        }

        try {
            val accountParams = core.createAccountParams()
            val identity = factory.createAddress("sip:${resolvedConfig.username}@${resolvedConfig.domain}")
            accountParams.identityAddress = identity
            
            val transportSuffix = when (resolvedConfig.transport.uppercase()) {
                "TCP" -> ";transport=tcp"
                "TLS" -> ";transport=tls"
                else -> ";transport=udp"
            }

            if (resolvedConfig.outboundProxy.isNotEmpty()) {
                val proxyAddress = factory.createAddress(resolvedConfig.outboundProxy)
                // When using SBC, we point the server address directly to the proxy to avoid "482 Loop Detected"
                accountParams.serverAddress = proxyAddress
                accountParams.isOutboundProxyEnabled = true
            } else {
                val scheme = if (resolvedConfig.transport.uppercase() == "TLS") "sips" else "sip"
                val portSuffix = if (resolvedConfig.port != null) ":${resolvedConfig.port}" else ""
                val server = factory.createAddress("$scheme:${resolvedConfig.domain}$portSuffix$transportSuffix")
                accountParams.serverAddress = server
                accountParams.isOutboundProxyEnabled = false
            }

            // Authentication Setup
            val authUser = if (resolvedConfig.authUsername.isNotEmpty()) resolvedConfig.authUsername else resolvedConfig.username
            
            // Remove existing auth info for this account to prevent duplicates
            core.authInfoList.find { it.username == authUser && it.domain == resolvedConfig.domain }?.let {
                core.removeAuthInfo(it)
            }
            
            val authInfo = factory.createAuthInfo(authUser, null, resolvedConfig.secret, null, null, resolvedConfig.domain)
            core.addAuthInfo(authInfo)

            // Push Notifications Configuration
            val fcmToken = resolvedConfig.fcmToken
            val appId = resolvedConfig.packageName
            
            if (resolvedConfig.isPushEnabled && fcmToken.isNotEmpty()) {
                val pushConfig = accountParams.pushNotificationConfig ?: core.pushNotificationConfig
                if (pushConfig != null) {
                    pushConfig.provider = "fcm"
                    pushConfig.param = appId
                    pushConfig.prid = fcmToken
                    pushConfig.remoteToken = fcmToken
                    accountParams.pushNotificationConfig = pushConfig
                }
                accountParams.pushNotificationAllowed = true
                accountParams.expires = 86400
            } else {
                accountParams.expires = repository.settings.value.registrationExpiry
            }

            // Media Encryption is handled globally in applyGlobalSettings 
            // or per-call in makeCallNativeAsync. We don't set it here to avoid clobbering other accounts.


            // Select Transport
            accountParams.transport = when (resolvedConfig.transport.uppercase()) {
                "TCP" -> TransportType.Tcp
                "TLS" -> TransportType.Tls
                else -> TransportType.Udp
            }

            accountParams.isRegisterEnabled = true

            val account = core.createAccount(accountParams)
            core.addAccount(account)
            
            linphoneAccounts[accountId] = account
            updateCodecPriorities()

            val wrapper = AccountWrapper(
                id = accountId,
                username = resolvedConfig.username,
                domain = resolvedConfig.domain,
                registrationState = com.mksoft.phone.core.sip.RegistrationState.Registering
            )
            accountMap[accountId] = wrapper
            _activeAccounts.value = accountMap.toMap()

            Log.d(TAG, "Added Linphone account natively: $accountId")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding Linphone account: ${e.message}", e)
            accountMap[accountId] = AccountWrapper(
                id = accountId,
                username = resolvedConfig.username,
                domain = resolvedConfig.domain,
                registrationState = com.mksoft.phone.core.sip.RegistrationState.Failed(0, e.message ?: "Failed to create account")
            )
            _activeAccounts.value = accountMap.toMap()
        }

        return accountId
    }

    private suspend fun addAccountNative(config: SipAccountConfig): String {
        return sipMutex.withLock {
            addAccountNativeLocked(config)
        }
    }

    suspend fun addAccount(config: SipAccountConfig): String {
        val fcmToken = getFcmToken() ?: ""
        val repository = DefaultDataRepository.getInstance(context)
        val settings = repository.settings.value

        val routing = resolveRoutingParameters(config, settings)
        val appPackageName = context.packageName

        val sipInstanceId = if (config.sipInstanceId.isBlank()) {
            java.util.UUID.randomUUID().toString()
        } else {
            config.sipInstanceId
        }

        val resolvedConfig = config.copy(
            outboundProxy = routing.first,
            transport = routing.second,
            port = routing.third,
            isPushEnabled = true,
            fcmToken = fcmToken,
            packageName = appPackageName,
            sipInstanceId = sipInstanceId
        )

        Log.d(TAG, "addAccount: id=${resolvedConfig.id} proxy='${resolvedConfig.outboundProxy}' transport=${resolvedConfig.transport} push=${resolvedConfig.isPushEnabled} sipInstance=${resolvedConfig.sipInstanceId} pkg=${resolvedConfig.packageName}")

        val updatedConfig = resolvedConfig.copy(
            fcmToken = if (resolvedConfig.fcmToken.isEmpty()) fcmToken else resolvedConfig.fcmToken,
            packageName = resolvedConfig.packageName.ifEmpty { appPackageName }
        )

        val id = updatedConfig.id
        
        try {
            val repository = DefaultDataRepository.getInstance(context)
            val savedConfig = config.copy(
                sipInstanceId = sipInstanceId,
                fcmToken = if (config.fcmToken.isEmpty()) fcmToken else config.fcmToken,
                packageName = config.packageName.ifEmpty { appPackageName }
            )
            repository.addSipAccount(savedConfig)
            Log.d(TAG, "Saved account to repository: $id")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving account to repository: ${e.message}")
        }

        sipMutex.withLock {
            if (updatedConfig.isEnabled) {
                if (linphoneAccounts.containsKey(id)) {
                    Log.d(TAG, "Account already exists natively, removing: $id")
                    removeAccountNativeLocked(id)
                }
                addAccountNativeLocked(updatedConfig)
            } else {
                Log.d(TAG, "Account is disabled, removing natively: $id")
                removeAccountNativeLocked(id)
            }
        }
        
        return id
    }

    suspend fun addAccount(username: String, domain: String, password: String): String {
        val id = "sip:$username@$domain"
        val config = SipAccountConfig(
            id = id,
            username = username,
            domain = domain,
            secret = password
        )
        return addAccount(config)
    }

    private suspend fun removeAccountNativeLocked(accountId: String) {
        val currentState = _engineState.value
        val ready = if (currentState is SipEngineState.Ready) {
            true
        } else if (currentState is SipEngineState.Initializing) {
            Log.d(TAG, "Engine is initializing during removeAccountNative, waiting for Ready state...")
            withTimeoutOrNull(10_000L) {
                _engineState.first { it is SipEngineState.Ready }
            } != null
        } else {
            false
        }

        if (ready) {
            val account = linphoneAccounts.remove(accountId)
            if (account != null) {
                try {
                    val identity = account.params?.identityAddress
                    if (identity != null) {
                        core.authInfoList.find { it.username == identity.username && it.domain == identity.domain }?.let {
                            core.removeAuthInfo(it)
                        }
                    }

                    val params = account.params?.clone()
                    if (params != null) {
                        params.isRegisterEnabled = false
                        account.params = params
                    }
                    delay(500)
                    core.removeAccount(account)
                    accountMap.remove(accountId)
                    _activeAccounts.value = accountMap.toMap()
                    Log.d(TAG, "Removed account natively: $accountId")
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting account natively $accountId: ${e.message}", e)
                }
            }
        }
    }

    private suspend fun removeAccountNative(accountId: String) {
        sipMutex.withLock {
            removeAccountNativeLocked(accountId)
        }
    }

    suspend fun removeAccount(accountId: String) {
        sipMutex.withLock {
            removeAccountNativeLocked(accountId)
        }
        withContext(Dispatchers.IO) {
            try {
                val repository = DefaultDataRepository.getInstance(context)
                repository.removeSipAccount(accountId)
                Log.d(TAG, "Removed account from repository: $accountId")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing account from repository: ${e.message}")
            }
        }
    }

    private suspend fun loadAndRegisterSavedAccountsLocked() {
        try {
            val repository = DefaultDataRepository.getInstance(context)
            val savedAccounts = repository.accounts.value
            val currentFcmToken = getFcmToken() ?: ""
            Log.d(TAG, "loadAndRegisterSavedAccountsLocked: Found ${savedAccounts.size} saved accounts")
            savedAccounts.forEach { acc ->
                if (acc.isEnabled) {
                    Log.d(TAG, "loadAndRegisterSavedAccountsLocked: Auto-registering: ${acc.id}")
                    val sanitizedToken = (if (currentFcmToken.isNotEmpty()) currentFcmToken else acc.fcmToken)
                        .replace("\"", "")
                        .trim()
                    
                    val instanceId = if (acc.sipInstanceId.isBlank()) {
                        java.util.UUID.randomUUID().toString()
                    } else {
                        acc.sipInstanceId
                    }
                    
                    val needsPersist = sanitizedToken != acc.fcmToken || instanceId != acc.sipInstanceId
                    val sanitizedAcc = if (needsPersist) {
                        acc.copy(fcmToken = sanitizedToken, sipInstanceId = instanceId).also {
                            try { repository.addSipAccount(it) } catch (_: Exception) {}
                        }
                    } else acc
                    addAccountNativeLocked(sanitizedAcc)
                } else {
                    Log.d(TAG, "loadAndRegisterSavedAccountsLocked: Bypassing deactivated account: ${acc.id}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved accounts: ${e.message}", e)
        }
    }

    private fun loadAndRegisterSavedAccounts() {
        sipScope.launch {
            sipMutex.withLock {
                loadAndRegisterSavedAccountsLocked()
            }
        }
    }

    fun makeCallNativeAsync(accountId: String, destUri: String, onCallId: (Int) -> Unit) {
        sipScope.launch {
            if (_engineState.value !is SipEngineState.Ready) {
                Log.w(TAG, "makeCallNativeAsync: Engine not ready, waiting...")
                withTimeoutOrNull(10_000L) {
                    _engineState.first { it is SipEngineState.Ready }
                }
            }

            if (_engineState.value !is SipEngineState.Ready) {
                Log.e(TAG, "makeCallNativeAsync: Engine not ready in time. Failing call.")
                onCallId(-1)
                return@launch
            }

            var account: Account? = null
            try {
                // Auto-hold active calls
                core.calls.forEach { call ->
                    if (call.state == Call.State.StreamsRunning) {
                        call.pause()
                    }
                }

                account = linphoneAccounts[accountId] ?: throw IllegalArgumentException("Account not found: $accountId")
                val cleanedDest = if (destUri.contains("<") && destUri.contains(">")) {
                    destUri.substringAfter("<").substringBefore(">")
                } else {
                    destUri
                }

                val finalUri = when {
                    cleanedDest.startsWith("sip:") -> cleanedDest
                    cleanedDest.contains("@") -> "sip:$cleanedDest"
                    else -> {
                        val identity = account.params?.identityAddress
                        val domain = identity?.domain ?: accountId.substringAfter("@", "")
                        "sip:$cleanedDest@$domain"
                    }
                }

                val destAddress = factory.createAddress(finalUri) ?: throw IllegalArgumentException("Invalid target: $finalUri")
                val callParams = core.createCallParams(null)!!
                val repository = DefaultDataRepository.getInstance(context)
                val settings = repository.settings.value
                val resolvedConfig = repository.accounts.value.find { it.id == accountId }
                
                val defaultLocalDomain = "ast.cdoapp.online"
                val configuredLocal = settings.localDomain.trim()
                val isLocalAccount = if (configuredLocal.isNotBlank()) {
                    resolvedConfig?.domain?.contains(configuredLocal, ignoreCase = true) == true
                } else {
                    resolvedConfig?.domain?.contains(defaultLocalDomain, ignoreCase = true) == true
                }
                
                val isThirdParty = resolvedConfig != null && !isLocalAccount
                
                // Audit the outgoing call
                AuditManager.getInstance(context).logOutgoingCall(accountId, cleanedDest, isThirdParty)

                // All calls must use the registered account's configuration (including its proxy/registrar)
                // for routing, ensuring outgoing calls follow the same path as registration and incoming calls.
                callParams.account = account

                val mediaEnc = if (resolvedConfig != null) {
                    when {
                        resolvedConfig.zrtpEnabled -> org.linphone.core.MediaEncryption.ZRTP
                        resolvedConfig.srtpMode > 0 -> org.linphone.core.MediaEncryption.SRTP
                        else -> org.linphone.core.MediaEncryption.None
                    }
                } else {
                    org.linphone.core.MediaEncryption.None
                }
                callParams.setMediaEncryption(mediaEnc)
                
                val call = core.inviteAddressWithParams(destAddress, callParams)

                if (call != null) {
                    val callId = call.hashCode()
                    linphoneCalls[callId] = call
                    
                    val wrapper = CallWrapper(
                        callId = callId,
                        accountId = accountId,
                        peerUri = cleanedDest,
                        callState = SipCallState.Outgoing,
                        isIncoming = false,
                        isThirdParty = isThirdParty
                    )
                    callMap[callId] = wrapper
                    _activeCalls.value = callMap.toMap()
                    Log.d(TAG, "Outgoing call initiated natively. Call ID: $callId")
                    onCallId(callId)
                } else {
                    onCallId(-1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error making call: ${e.message}", e)
                onCallId(-1)
            }
        }
    }

    fun makeCall(accountId: String, destUri: String): Int {
        Log.d(TAG, "makeCall (Telecom request) for account: $accountId, dest: $destUri")
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val phoneAccountHandle = VoIpConnectionService.getPhoneAccountHandle(context)
            val extras = Bundle().apply {
                putString("accountId", accountId)
                putString("destUri", destUri)
            }
            val address = Uri.fromParts("sip", destUri, null)
            val outgoingRequestExtras = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
                putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extras)
            }
            telecomManager.placeCall(address, outgoingRequestExtras)
            return 0
        } catch (e: Exception) {
            Log.e(TAG, "Error placing call via TelecomManager, falling back to native: ${e.message}")
            sipScope.launch {
                makeCallNativeAsync(accountId, destUri) { _ -> }
            }
            return 0
        }
    }

    fun answerCall(callId: Int) {
        val call = linphoneCalls[callId]
        if (call != null) {
            answeredCallIds.add(callId)
            val current = callMap[callId]
            if (current != null) {
                callMap[callId] = current.copy(wasAnswered = true)
            }
            call.accept()
            Log.d(TAG, "Answered call ID: $callId")
        } else {
            Log.e(TAG, "answerCall: Call ID $callId not found")
        }
    }

    fun hangupCall(callId: Int) {
        Log.d(TAG, "hangupCall request for ID: $callId")
        val call = linphoneCalls[callId]
        if (call != null) {
            locallyDeclinedCallIds.add(callId)
            call.terminate()
            Log.d(TAG, "Hanging up call ID: $callId. Added to locallyDeclinedCallIds.")
        } else {
            Log.e(TAG, "hangupCall: Call ID $callId not found in linphoneCalls map")
            // Still add to declined list just in case of a race condition
            locallyDeclinedCallIds.add(callId)
            
            // Try to find it in the core's own call list as a fallback
            core.calls.find { it.hashCode() == callId }?.let {
                Log.d(TAG, "Found call in core.calls fallback. Terminating.")
                it.terminate()
            }
        }
    }

    fun setHold(callId: Int, hold: Boolean) {
        val call = linphoneCalls[callId] ?: return
        if (hold) {
            call.pause()
        } else {
            call.resume()
        }
        val current = callMap[callId]
        if (current != null) {
            callMap[callId] = current.copy(isLocalHold = hold)
            _activeCalls.value = callMap.toMap()
        }
        val connection = connections[callId]
        if (connection != null) {
            if (hold) connection.setOnHold() else connection.setActive()
        }
        Log.d(TAG, "Hold status updated for call $callId to $hold")
    }

    fun setMute(callId: Int, mute: Boolean) {
        core.isMicEnabled = !mute
        val current = callMap[callId]
        if (current != null) {
            callMap[callId] = current.copy(isMuted = mute)
            _activeCalls.value = callMap.toMap()
        }
        Log.d(TAG, "Mute status updated for call $callId to $mute")
    }

    fun updateCallAudioRoute(callId: Int, isSpeakerphoneOn: Boolean, isBluetoothOn: Boolean) {
        val current = callMap[callId]
        if (current != null) {
            callMap[callId] = current.copy(isSpeakerphoneOn = isSpeakerphoneOn, isBluetoothOn = isBluetoothOn)
            _activeCalls.value = callMap.toMap()
            Log.d(TAG, "updateCallAudioRoute: callId $callId, isSpeakerphoneOn = $isSpeakerphoneOn, isBluetoothOn = $isBluetoothOn")
        }
    }

    fun toggleSpeakerphone(callId: Int, on: Boolean) {
        // 1. Update Telecom route
        val connection = getConnection(callId)
        if (connection != null) {
            connection.setSpeakerphoneOn(on)
        }

        // 2. Update Linphone native audio device (Redundancy for better device support)
        try {
            val call = linphoneCalls[callId] ?: return
            val deviceType = if (on) AudioDevice.Type.Speaker else AudioDevice.Type.Earpiece
            val device = core.audioDevices.find { it.type == deviceType }
            if (device != null) {
                call.outputAudioDevice = device
                Log.d(TAG, "Linphone output audio device set to: ${device.deviceName} ($deviceType)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting Linphone audio device: ${e.message}")
        }
    }

    fun toggleBluetooth(callId: Int, on: Boolean) {
        // 1. Update Telecom route
        val connection = getConnection(callId)
        if (connection != null) {
            connection.setBluetoothOn(on)
        }

        // 2. Update Linphone native audio device
        try {
            val call = linphoneCalls[callId] ?: return
            val deviceType = if (on) AudioDevice.Type.Bluetooth else AudioDevice.Type.Earpiece
            val device = core.audioDevices.find { it.type == deviceType }
            if (device != null) {
                call.outputAudioDevice = device
                Log.d(TAG, "Linphone output audio device set to: ${device.deviceName} ($deviceType)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting Linphone bluetooth device: ${e.message}")
        }
    }

    fun startRecording(callId: Int) {
        val call = linphoneCalls[callId] ?: return
        if (activeRecorders.containsKey(callId)) {
            Log.d(TAG, "Recording is already active for call $callId")
            return
        }
        try {
            val dir = File(context.filesDir, "recordings")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            
            val peerStr = callMap[callId]?.peerUri ?: "unknown"
            val cleanPeerUri = peerStr.replace(Regex("[^a-zA-Z0-9]"), "_")
            val file = File(dir, "rec_${cleanPeerUri}_${System.currentTimeMillis()}.wav")
            
            val params = call.params?.copy() ?: core.createCallParams(call)!!
            params.recordFile = file.absolutePath
            call.params = params
            call.startRecording()
            
            activeRecorders[callId] = file.absolutePath
            
            val current = callMap[callId]
            if (current != null) {
                callMap[callId] = current.copy(isRecording = true)
                _activeCalls.value = callMap.toMap()
            }
            Log.d(TAG, "Started call recording for call $callId, output file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting call recording for call $callId: ${e.message}", e)
        }
    }

    fun stopRecording(callId: Int) {
        val call = linphoneCalls[callId]
        val filePath = activeRecorders.remove(callId)
        if (call != null && filePath != null) {
            try {
                call.stopRecording()
                val current = callMap[callId]
                if (current != null) {
                    callMap[callId] = current.copy(isRecording = false)
                    _activeCalls.value = callMap.toMap()
                }
                Log.d(TAG, "Stopped call recording for call $callId")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping call recording: ${e.message}")
            }
        }
    }

    fun sendDtmf(callId: Int, digit: String) {
        val call = linphoneCalls[callId] ?: return
        if (digit.isNotEmpty()) {
            call.sendDtmf(digit[0])
            Log.d(TAG, "Sent DTMF digit: $digit")
        }
    }

    fun sendMessage(accountId: String, peerUri: String, content: String) {
        sipScope.launch(Dispatchers.Main) {
            try {
                if (!::core.isInitialized) return@launch
                
                val addr = factory.createAddress(peerUri) ?: return@launch
                
                // For Linphone 5.x, getChatRoom(Address) or createChatRoom(subject, participants)
                val chatRoom = core.getChatRoom(addr) ?: core.createChatRoom("", arrayOf(addr))

                if (chatRoom == null) {
                    Log.e(TAG, "Failed to find or create chat room for $peerUri")
                    return@launch
                }
                
                val message = chatRoom.createEmptyMessage()
                message.addUtf8TextContent(content)
                message.send()
                
                Log.d(TAG, "Message sent to $peerUri: $content")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message to $peerUri: ${e.message}")
            }
        }
    }

    fun attendedTransfer(callId: Int, targetCallId: Int) {
        sipScope.launch(Dispatchers.Main) {
            val call = linphoneCalls[callId] ?: return@launch
            val targetCall = linphoneCalls[targetCallId] ?: return@launch
            try {
                // Check if call state allows transfer
                if (call.state == Call.State.Updating || call.state == Call.State.Pausing) {
                    Log.w(TAG, "Call $callId is in a transient state (${call.state}), retrying transfer in 500ms")
                    delay(500)
                    attendedTransfer(callId, targetCallId)
                    return@launch
                }
                call.transferToAnother(targetCall)
                Log.d(TAG, "Attended transfer of call $callId to $targetCallId initiated")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to perform attended transfer: ${e.message}")
            }
        }
    }

    fun subscribeToPresence(sipUri: String) {
        try {
            val address = factory.createAddress(sipUri) ?: return
            val friend = core.createFriend()
            friend.address = address
            friend.edit()
            friend.isSubscribesEnabled = true
            friend.done()
            core.defaultFriendList?.addFriend(friend)
            Log.d(TAG, "Subscribed to presence for $sipUri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to presence: ${e.message}")
        }
    }

    fun bridgeAllActiveCalls() {
        // No-op for Linphone: conference audio routing is managed natively.
    }

    fun conferenceCalls() {
        try {
            _isConferenceActive.value = true
            var conference = core.conference
            if (conference == null) {
                val params = core.createConferenceParams(null)
                conference = core.createConferenceWithParams(params)
            }
            if (conference != null) {
                linphoneCalls.values.forEach { call ->
                    if (call.state == Call.State.Paused) {
                        call.resume()
                    }
                    conference.addParticipant(call)
                }
            }
            Log.d(TAG, "Merged all active calls into a conference")
        } catch (e: Exception) {
            Log.e(TAG, "Error merging calls into conference: ${e.message}", e)
        }
    }

    fun transferCall(callId: Int, destination: String) {
        val call = linphoneCalls[callId] ?: return
        try {
            call.transfer(destination)
            Log.d(TAG, "Transferred call $callId to $destination")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to transfer call: ${e.message}", e)
        }
    }

    fun sendKeepAliveOptions() {
        // No-op for Linphone
    }

    fun getFcmToken(): String? {
        val repository = DefaultDataRepository.getInstance(context)
        return repository.getFcmToken()
    }

    fun saveFcmToken(token: String) {
        val repository = DefaultDataRepository.getInstance(context)
        repository.saveFcmToken(token)
        
        sipScope.launch {
            try {
                if (_engineState.value is SipEngineState.Ready) {
                    linphoneAccounts.values.forEach { account ->
                        val params = account.params?.clone()
                        if (params != null && params.pushNotificationAllowed) {
                            val pushConfig = params.pushNotificationConfig
                            if (pushConfig != null) {
                                pushConfig.prid = token
                                pushConfig.remoteToken = token
                                params.pushNotificationConfig = pushConfig
                                account.params = params
                                Log.d(TAG, "Updated remote FCM token for account: ${account.params?.identityAddress?.asString()}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in saveFcmToken: ${e.message}")
            }
        }
    }

    suspend fun syncActiveAccountRegistrations() {
        if (_engineState.value is SipEngineState.Initializing) {
            Log.d(TAG, "syncActiveAccountRegistrations: Engine is initializing, waiting for Ready state...")
            withTimeoutOrNull(30_000L) {
                _engineState.first { it is SipEngineState.Ready }
            }
            Log.d(TAG, "syncActiveAccountRegistrations: Engine finished initializing, skipping duplicate registration")
            return
        }

        if (_engineState.value !is SipEngineState.Ready) {
            Log.d(TAG, "syncActiveAccountRegistrations: Engine not ready, initializing...")
            initialize()
            return
        }

        sipMutex.withLock {
            val activeMap = activeAccounts.value
            if (activeMap.isEmpty()) {
                Log.w(TAG, "Wakeup triggered but no active client memory accounts matched. Re-pulling disk data cache.")
                loadAndRegisterSavedAccountsLocked()
                return
            }
            activeMap.keys.forEach { accountId ->
                try {
                    val account = linphoneAccounts[accountId]
                    if (account != null) {
                        val state = account.state
                        if (state == org.linphone.core.RegistrationState.Progress || state == org.linphone.core.RegistrationState.Ok) {
                            Log.d(TAG, "Registration in-flight or already active for $accountId (status=$state), skipping refresh")
                            return@forEach
                        }
                        
                        Log.d(TAG, "Forcing account contact refresh: $accountId")
                        account.refreshRegister()
                    } else {
                        Log.w(TAG, "No native account found for $accountId during syncActiveAccountRegistrations. Attempting to re-add.")
                        val repository = DefaultDataRepository.getInstance(context)
                        val config = repository.accounts.value.find { it.id == accountId }
                        if (config != null && config.isEnabled) {
                            addAccountNativeLocked(config)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to force registration update on: $accountId", e)
                }
            }
        }
    }
}
