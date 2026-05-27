package com.mksoft.phone.ui.main

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mksoft.phone.core.sip.*
import com.mksoft.phone.data.*
import com.mksoft.phone.service.SipService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
    
    private val context: Context get() = getApplication()
    private val sipEngineManager = SipEngineManager.getInstance(context)
    private val repository = DefaultDataRepository.getInstance(context)
    
    // Engine State Flow
    val engineState: StateFlow<SipEngineState> = sipEngineManager.engineState
    
    // Sip Accounts Flow
    val activeAccounts: StateFlow<Map<String, AccountWrapper>> = sipEngineManager.activeAccounts
    
    // Active Calls Flow
    val activeCalls: StateFlow<Map<Int, CallWrapper>> = sipEngineManager.activeCalls
    val isConferenceActive: StateFlow<Boolean> = sipEngineManager.isConferenceActive

    // Live Call Stats Flow
    val callStats: StateFlow<Map<Int, CallStats>> = sipEngineManager.callStats
    
    // SIP Native Logs Flow (latest 100 entries)
    private val _logsList = MutableStateFlow<List<SipLogEntry>>(emptyList())
    val sipLogs: StateFlow<List<SipLogEntry>> = _logsList.asStateFlow()
    
    // Call History Flow from Repository
    val callHistory: StateFlow<List<CallHistoryEntry>> = repository.callHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    // Saved Contacts Flow
    val contacts: StateFlow<List<SipContact>> = repository.contacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    // Settings Flow
    val settings: StateFlow<VoIpSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VoIpSettings())
        
    // Service status
    val serviceRunning: StateFlow<Boolean> = SipService.isRunning
        
    // Audio Recordings list Flow
    private val _recordings = MutableStateFlow<List<File>>(emptyList())
    val recordings: StateFlow<List<File>> = _recordings.asStateFlow()

    // Saved Accounts Flow from Repository
    val savedAccounts: StateFlow<List<SipAccountConfig>> = repository.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    // Primary Account ID Flow from Repository
    val primaryAccountId: StateFlow<String?> = repository.primaryAccountId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Messaging Flow
    val messages: StateFlow<List<SipChatMessage>> = repository.messages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Socket transport probing state
    enum class ProbeStatus {
        Untested,
        Probing,
        Found,
        NotFound
    }

    data class ProbeResult(
        val tls: ProbeStatus = ProbeStatus.Untested,
        val tcp: ProbeStatus = ProbeStatus.Untested,
        val udp: ProbeStatus = ProbeStatus.Untested,
        val iax: ProbeStatus = ProbeStatus.Untested
    )

    data class TransferSession(
        val originalCallId: Int,
        val targetCallId: Int? = null
    )

    private val _probeState = MutableStateFlow(ProbeResult())
    val probeState: StateFlow<ProbeResult> = _probeState.asStateFlow()

    private val _transferSession = MutableStateFlow<TransferSession?>(null)
    val transferSession: StateFlow<TransferSession?> = _transferSession.asStateFlow()

    init {
        // Collect native logs and update our state
        viewModelScope.launch {
            sipEngineManager.sipLogs.collect { entry ->
                val current = _logsList.value.toMutableList()
                current.add(entry)
                if (current.size > 200) {
                    current.removeAt(0)
                }
                _logsList.value = current
            }
        }

        // Collect incoming messages
        viewModelScope.launch {
            sipEngineManager.incomingMessages.collect { message ->
                repository.addChatMessage(message)
                Log.d("MainScreenViewModel", "Persisted incoming message: ${message.content}")
            }
        }
        
        // Listen to active calls to automatically save calls to history when they end
        viewModelScope.launch {
            var lastActiveCallIds = emptySet<Int>()
            var activeCallDetails = mutableMapOf<Int, CallWrapper>()
            
            sipEngineManager.activeCalls.collect { currentCalls ->
                // Handle Transfer Session logic: identify the target call
                val session = _transferSession.value
                if (session != null && session.targetCallId == null) {
                    val newCallId = currentCalls.keys.find { it != session.originalCallId && !lastActiveCallIds.contains(it) }
                    if (newCallId != null) {
                        _transferSession.value = session.copy(targetCallId = newCallId)
                        Log.d("MainScreenViewModel", "Linked target call $newCallId to transfer session for ${session.originalCallId}")
                    }
                }

                // If a transfer session exists and one of the calls ends, clear the session
                if (session != null) {
                    if (!currentCalls.containsKey(session.originalCallId)) {
                        // Original caller hung up, just clear session
                        _transferSession.value = null
                    } else if (session.targetCallId != null && !currentCalls.containsKey(session.targetCallId)) {
                        // Transfer target hung up or rejected before completion.
                        // Automatically unhold the original caller.
                        Log.d("MainScreenViewModel", "Transfer target hung up. Resuming original call.")
                        sipEngineManager.setHold(session.originalCallId, false)
                        _transferSession.value = null
                    }
                }

                // Look for calls that were in the map, but are no longer there (disconnected)
                for ((callId, wrapper) in currentCalls) {
                    activeCallDetails[callId] = wrapper
                }
                
                val disconnectedIds = lastActiveCallIds - currentCalls.keys
                for (id in disconnectedIds) {
                    val detail = activeCallDetails[id]
                    if (detail != null) {
                        // Create history entry
                        val wasAnswered = detail.connectTimestamp != null
                        val duration = if (wasAnswered) {
                            (System.currentTimeMillis() - detail.connectTimestamp!!) / 1000
                        } else {
                            0L
                        }
                        
                        repository.addCallHistory(
                            CallHistoryEntry(
                                id = UUID.randomUUID().toString(),
                                number = detail.peerUri,
                                timestamp = System.currentTimeMillis(),
                                duration = duration,
                                isIncoming = detail.isIncoming,
                                wasAnswered = wasAnswered,
                                isThirdParty = detail.isThirdParty
                            )
                        )
                    }
                    activeCallDetails.remove(id)
                    refreshRecordings()
                }
                lastActiveCallIds = currentCalls.keys
            }
        }
        
        refreshRecordings()
    }
    
    fun refreshRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repository.getRecordingsList(context)
            _recordings.value = list
        }
    }
    
    fun initializeEngine() {
        sipEngineManager.initialize()
    }
    
    /** Called on every app foreground (onResume) to ensure registration is active. */
    fun ensureRegistered() {
        startSipService()          // no-op if already running
        sipEngineManager.initialize() // re-registers if Ready, initializes if Uninitialized
    }
    

    fun shutdownEngine() {
        sipEngineManager.shutdown()
    }
    
    // Service management
    fun startSipService() {
        try {
            SipService.start(context)
            Log.d("MainScreenViewModel", "SipService start called")
        } catch (e: Exception) {
            Log.e("MainScreenViewModel", "Failed to start SipService: ${e.message}")
        }
    }
    
    fun stopSipService() {
        try {
            SipService.stop(context)
            Log.d("MainScreenViewModel", "SipService stop called")
        } catch (e: Exception) {
            Log.e("MainScreenViewModel", "Failed to stop SipService: ${e.message}")
        }
    }
    
    fun addSipAccount(config: SipAccountConfig) {
        viewModelScope.launch {
            // Ensure service is running and engine is initialized before adding account
            startSipService()
            initializeEngine()
            
            // Just call addAccount. It will update the repository and handle native refresh if needed.
            sipEngineManager.addAccount(config)

            // If primary account is not set, set this as primary
            if (repository.primaryAccountId.value == null) {
                repository.setPrimaryAccountId(config.id)
            }
            
            // Re-enable all other accounts if logging in
            val allSaved = repository.accounts.value
            for (acc in allSaved) {
                if (!acc.isEnabled && acc.id != config.id) {
                    sipEngineManager.addAccount(acc.copy(isEnabled = true))
                }
            }
            
            val currentSettings = repository.settings.value
            if (!currentSettings.isLoggedIn) {
                repository.updateSettings(currentSettings.copy(isLoggedIn = true))
            }
        }
    }

    fun addSipAccount(username: String, domain: String, password: String, transport: String, useSbc: Boolean = false, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            // Ensure service is running and engine is initialized first
            withContext(Dispatchers.Main) {
                startSipService()
                initializeEngine()
            }
            
            withContext(Dispatchers.Main) {
                val id = "sip:$username@$domain"
                val config = SipAccountConfig(
                    id = id,
                    username = username,
                    domain = domain,
                    secret = password,
                    transport = transport,
                    isPushEnabled = true, // Default to true for background reliability
                    useSbc = useSbc
                )
                addSipAccount(config)
                onComplete()
            }
        }
    }
    
    fun setPrimaryAccount(accountId: String?) {
        repository.setPrimaryAccountId(accountId)
    }

    fun probeTransports(domain: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _probeState.value = ProbeResult(
                tls = ProbeStatus.Probing,
                tcp = ProbeStatus.Untested,
                udp = ProbeStatus.Untested,
                iax = ProbeStatus.Untested
            )
            
            val tlsFound = probeSocket(domain, 5061, isTcp = true)
            _probeState.value = _probeState.value.copy(
                tls = if (tlsFound) ProbeStatus.Found else ProbeStatus.NotFound,
                tcp = ProbeStatus.Probing
            )
            
            val tcpFound = probeSocket(domain, 5060, isTcp = true)
            _probeState.value = _probeState.value.copy(
                tcp = if (tcpFound) ProbeStatus.Found else ProbeStatus.NotFound,
                udp = ProbeStatus.Probing
            )
            
            val udpFound = probeSocket(domain, 5060, isTcp = false)
            _probeState.value = _probeState.value.copy(
                udp = if (udpFound) ProbeStatus.Found else ProbeStatus.NotFound,
                iax = ProbeStatus.Probing
            )
            
            val iaxFound = probeSocket(domain, 4569, isTcp = false)
            _probeState.value = _probeState.value.copy(
                iax = if (iaxFound) ProbeStatus.Found else ProbeStatus.NotFound
            )
        }
    }
    
    private fun probeSocket(host: String, port: Int, isTcp: Boolean): Boolean {
        return try {
            if (isTcp) {
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(host, port), 2000)
                    true
                }
            } else {
                java.net.DatagramSocket().use { socket ->
                    socket.soTimeout = 1000
                    val address = java.net.InetAddress.getByName(host)
                    socket.connect(address, port)
                    val dummyData = ByteArray(4)
                    val packet = java.net.DatagramPacket(dummyData, dummyData.size)
                    socket.send(packet)
                    try {
                        socket.receive(packet)
                        true
                    } catch (e: java.net.PortUnreachableException) {
                        false
                    } catch (e: java.io.IOException) {
                        true // assume found on generic timeout since UDP is connectionless
                    }
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    fun removeSipAccount(accountId: String) {
        viewModelScope.launch {
            sipEngineManager.removeAccount(accountId)
            // If primary account was deleted, select another one if available
            if (repository.primaryAccountId.value == accountId) {
                val remaining = repository.accounts.value
                val nextPrimary = remaining.firstOrNull { it.id != accountId }?.id
                repository.setPrimaryAccountId(nextPrimary)
            }
        }
    }

    fun logout(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val accounts = repository.accounts.value.toList()
                for (account in accounts) {
                    // Disable all accounts but keep them in repository
                    val disabledAcc = account.copy(isEnabled = false)
                    sipEngineManager.addAccount(disabledAcc)
                }
                val currentSettings = repository.settings.value
                repository.updateSettings(currentSettings.copy(isLoggedIn = false))
                stopSipService()
            } catch (e: Exception) {
                Log.e("MainScreenViewModel", "Logout error: ${e.message}")
            } finally {
                onComplete()
            }
        }
    }
    
    // Calls management
    fun makeSipCall(accountId: String, destUri: String) {
        viewModelScope.launch {
            try {
                sipEngineManager.makeCall(accountId, destUri)
            } catch (e: Exception) {
                Log.e("MainScreenViewModel", "Failed to make call: ${e.message}")
            }
        }
    }
    
    fun answerActiveCall(callId: Int) {
        viewModelScope.launch {
            val connection = sipEngineManager.getConnection(callId)
            if (connection != null) {
                connection.onAnswer()
            } else {
                Log.w("MainScreenViewModel", "Telecom connection not found for answer. Falling back to native.")
                sipEngineManager.answerCall(callId)
            }
        }
    }
    
    fun hangupActiveCall(callId: Int) {
        viewModelScope.launch {
            val connection = sipEngineManager.getConnection(callId)
            if (connection != null) {
                // If it's an incoming call that hasn't been confirmed yet, reject it. Otherwise, disconnect it.
                val call = sipEngineManager.activeCalls.value[callId]
                if (call != null && call.callState == com.mksoft.phone.core.sip.SipCallState.Incoming) {
                    connection.onReject()
                } else {
                    connection.onDisconnect()
                }
            } else {
                Log.w("MainScreenViewModel", "Telecom connection not found for hangup. Falling back to native.")
                sipEngineManager.hangupCall(callId)
            }
        }
    }
    
    fun toggleHold(callId: Int, hold: Boolean) {
        viewModelScope.launch {
            sipEngineManager.setHold(callId, hold)
        }
    }
    
    fun toggleMute(callId: Int, mute: Boolean) {
        viewModelScope.launch {
            sipEngineManager.setMute(callId, mute)
        }
    }

    fun toggleSpeakerphone(callId: Int, on: Boolean) {
        viewModelScope.launch {
            sipEngineManager.toggleSpeakerphone(callId, on)
        }
    }

    fun toggleBluetooth(callId: Int, on: Boolean) {
        viewModelScope.launch {
            sipEngineManager.toggleBluetooth(callId, on)
        }
    }

    fun toggleRecording(callId: Int, record: Boolean) {
        viewModelScope.launch {
            if (record) {
                sipEngineManager.startRecording(callId)
            } else {
                sipEngineManager.stopRecording(callId)
                refreshRecordings()
            }
        }
    }

    fun sendDtmf(callId: Int, digit: String) {
        viewModelScope.launch {
            sipEngineManager.sendDtmf(callId, digit)
        }
    }

    fun mergeCalls() {
        viewModelScope.launch {
            sipEngineManager.conferenceCalls()
        }
    }

    fun transferCall(callId: Int, destination: String) {
        viewModelScope.launch {
            val callWrapper = sipEngineManager.activeCalls.value[callId] ?: return@launch
            val accountId = callWrapper.accountId
            
            // 1. Put current call on hold
            sipEngineManager.setHold(callId, true)
            
            // 2. Start a new transfer session
            _transferSession.value = TransferSession(originalCallId = callId)
            
            // 3. Initiate the call to the other party
            makeSipCall(accountId, destination)
        }
    }

    fun completeTransfer() {
        val session = _transferSession.value ?: return
        val targetId = session.targetCallId ?: return
        
        viewModelScope.launch {
            sipEngineManager.attendedTransfer(session.originalCallId, targetId)
            _transferSession.value = null
        }
    }

    fun cancelTransfer() {
        val session = _transferSession.value ?: return
        viewModelScope.launch {
            // Hang up the second call if it exists
            session.targetCallId?.let { hangupActiveCall(it) }
            // Resume the original call
            sipEngineManager.setHold(session.originalCallId, false)
            _transferSession.value = null
        }
    }

    fun deleteRecording(file: File) {
        viewModelScope.launch {
            try {
                if (file.exists()) {
                    file.delete()
                }
                refreshRecordings()
            } catch (e: Exception) {
                android.util.Log.e("MainScreenViewModel", "Failed to delete file: ${e.message}")
            }
        }
    }

    // Messaging management
    fun sendSipMessage(peerUri: String, content: String) {
        val outMessage = SipChatMessage(
            id = UUID.randomUUID().toString(),
            peerUri = peerUri,
            content = content,
            timestamp = System.currentTimeMillis(),
            isIncoming = false
        )
        repository.addChatMessage(outMessage)
        sipEngineManager.sendMessage("", peerUri, content)
    }

    fun markAsRead(peerUri: String) {
        repository.markMessagesAsRead(peerUri)
    }

    fun clearChat(peerUri: String) {
        repository.clearChatHistory(peerUri)
    }
    
    // Contacts management
    fun addContact(displayName: String, sipAddress: String) {
        repository.addContact(
            SipContact(
                id = UUID.randomUUID().toString(),
                displayName = displayName,
                sipAddress = sipAddress
            )
        )
    }
    
    fun deleteContact(contactId: String) {
        repository.removeContact(contactId)
    }
    
    // Settings management
    fun updateSettings(
        autoStart: Boolean,
        stunServer: String,
        expiry: Int,
        logLevel: Int,
        outboundProxy: String = repository.settings.value.outboundProxy,
        localDomain: String = repository.settings.value.localDomain,
        aecEnabled: Boolean = repository.settings.value.aecEnabled,
        agcEnabled: Boolean = repository.settings.value.agcEnabled,
        wakeLockEnabled: Boolean = repository.settings.value.wakeLockEnabled,
        backgroundKeepAliveEnabled: Boolean = repository.settings.value.backgroundKeepAliveEnabled,
        transportProtocol: String = repository.settings.value.transportProtocol,
        turnServer: String = repository.settings.value.turnServer,
        iceEnabled: Boolean = repository.settings.value.iceEnabled,
        keepAliveInterval: Int = repository.settings.value.keepAliveInterval,
        rportEnabled: Boolean = repository.settings.value.rportEnabled,
        ipv6Preference: String = repository.settings.value.ipv6Preference,
        dtmfMethod: String = repository.settings.value.dtmfMethod,
        echoCancellationType: String = repository.settings.value.echoCancellationType,
        nativeCallIntegrationEnabled: Boolean = repository.settings.value.nativeCallIntegrationEnabled,
        pushNotificationsEnabled: Boolean = repository.settings.value.pushNotificationsEnabled,
        dndSyncEnabled: Boolean = repository.settings.value.dndSyncEnabled,
        attendedTransferEnabled: Boolean = repository.settings.value.attendedTransferEnabled,
        blfEnabled: Boolean = repository.settings.value.blfEnabled,
        videoEnabled: Boolean = repository.settings.value.videoEnabled,
        limeEnabled: Boolean = repository.settings.value.limeEnabled,
        proximitySensorEnabled: Boolean = repository.settings.value.proximitySensorEnabled,
        zrtpSasDisplayEnabled: Boolean = repository.settings.value.zrtpSasDisplayEnabled,
        sipMessagingEnabled: Boolean = repository.settings.value.sipMessagingEnabled,
        postQuantumEnabled: Boolean = repository.settings.value.postQuantumEnabled
    ) {
        val newSettings = repository.settings.value.copy(
            autoStartOnBoot = autoStart,
            stunServer = stunServer,
            registrationExpiry = expiry,
            logLevel = logLevel,
            outboundProxy = outboundProxy,
            localDomain = localDomain,
            aecEnabled = aecEnabled,
            agcEnabled = agcEnabled,
            wakeLockEnabled = wakeLockEnabled,
            backgroundKeepAliveEnabled = backgroundKeepAliveEnabled,
            transportProtocol = transportProtocol,
            turnServer = turnServer,
            iceEnabled = iceEnabled,
            keepAliveInterval = keepAliveInterval,
            rportEnabled = rportEnabled,
            ipv6Preference = ipv6Preference,
            dtmfMethod = dtmfMethod,
            echoCancellationType = echoCancellationType,
            nativeCallIntegrationEnabled = nativeCallIntegrationEnabled,
            pushNotificationsEnabled = pushNotificationsEnabled,
            dndSyncEnabled = dndSyncEnabled,
            attendedTransferEnabled = attendedTransferEnabled,
            blfEnabled = blfEnabled,
            videoEnabled = videoEnabled,
            limeEnabled = limeEnabled,
            proximitySensorEnabled = proximitySensorEnabled,
            zrtpSasDisplayEnabled = zrtpSasDisplayEnabled,
            sipMessagingEnabled = sipMessagingEnabled,
            postQuantumEnabled = postQuantumEnabled
        )
        repository.updateSettings(newSettings)
        
        // Push changes to the live engine
        sipEngineManager.applyGlobalSettings(newSettings)
    }
    
    fun clearHistory() {
        repository.clearCallHistory()
    }

    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainScreenViewModel(application) as T
            }
        }
    }
}


