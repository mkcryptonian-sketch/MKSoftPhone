package com.mksoft.phone.core.sip

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import com.mksoft.phone.data.DefaultDataRepository
import com.google.firebase.messaging.FirebaseMessaging
import com.mksoft.phone.data.SipAccountConfig
import com.mksoft.phone.service.VoIpConnection
import com.mksoft.phone.service.VoIpConnectionService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import org.pjsip.pjsua2.*
import java.io.File

class SipEngineManager private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "SipEngineManager"
        private const val MAX_CALLS = 16
        
        // Loader for native libraries
        init {
            try {
                System.loadLibrary("pjsua2")
                Log.d(TAG, "Successfully loaded native pjsua2 library")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native pjsua2 library: ${e.message}")
            }
        }

        @Volatile
        private var instance: SipEngineManager? = null

        fun getInstance(context: Context): SipEngineManager {
            return instance ?: synchronized(this) {
                instance ?: SipEngineManager(context.applicationContext).also { instance = it }
            }
        }
    }



    fun ensureThreadRegistered() {
        val threadId = Thread.currentThread().id
        if (registeredThreads.contains(threadId)) {
            return
        }
        
        try {
            val ep = endpoint
            // Only attempt registration if the engine is fully Ready.
            // Calling native libRegisterThread during early libCreate/libInit 
            // can trigger "assertion mutex failed" if the internal native state is incomplete.
            if (ep != null && _engineState.value is SipEngineState.Ready) {
                val threadName = Thread.currentThread().name
                ep.libRegisterThread(threadName)
                registeredThreads.add(threadId)
                Log.d(TAG, "Registered thread with PJSIP: $threadName (ID: $threadId)")
            }
        } catch (e: Exception) {
            // PJSIP not ready or thread already registered
        }
    }

    private var sipScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private var endpoint: Endpoint? = null
    private var logWriter: LogWriter? = null
    
    private val _engineState = MutableStateFlow<SipEngineState>(SipEngineState.Uninitialized)
    val engineState: StateFlow<SipEngineState> = _engineState.asStateFlow()
    
    private val _activeAccounts = MutableStateFlow<Map<String, AccountWrapper>>(emptyMap())
    val activeAccounts: StateFlow<Map<String, AccountWrapper>> = _activeAccounts.asStateFlow()
    
    private val _activeCalls = MutableStateFlow<Map<Int, CallWrapper>>(emptyMap())
    val activeCalls: StateFlow<Map<Int, CallWrapper>> = _activeCalls.asStateFlow()
    
    private val _sipLogs = MutableSharedFlow<SipLogEntry>(replay = 100)
    val sipLogs: SharedFlow<SipLogEntry> = _sipLogs.asSharedFlow()

    private val accountMap = java.util.concurrent.ConcurrentHashMap<String, AccountWrapper>()
    private val callMap = java.util.concurrent.ConcurrentHashMap<Int, CallWrapper>()
    
    private val pjsipAccounts = java.util.concurrent.ConcurrentHashMap<String, MyAccount>()
    private val pjsipCalls = java.util.concurrent.ConcurrentHashMap<Int, MyCall>()

    private val connections = java.util.concurrent.ConcurrentHashMap<Int, VoIpConnection>()
    private val activeRecorders = java.util.concurrent.ConcurrentHashMap<Int, AudioMediaRecorder>()
    private val registeredThreads = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    private val sipMutex = kotlinx.coroutines.sync.Mutex()

    private var pendingPushConnection: VoIpConnection? = null
    private var pendingPushTimeoutJob: Job? = null

    fun setPendingPushConnection(connection: VoIpConnection?) {
        pendingPushTimeoutJob?.cancel()
        pendingPushConnection = connection
        
        if (connection != null) {
            // Auto-cleanup if no INVITE arrives within 15 seconds of push wakeup
            pendingPushTimeoutJob = sipScope.launch {
                delay(15_000)
                if (pendingPushConnection == connection) {
                    Log.w(TAG, "Push connection timeout: No INVITE arrived within 15s. Cleaning up.")
                    pendingPushConnection = null
                    connection.setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.MISSED, "Timed out waiting for INVITE"))
                    connection.destroy()
                }
            }
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

    private fun configureAudioDriverAndDsp(epConfig: EpConfig) {
        try {
            val prefs = context.getSharedPreferences("voip_app_prefs", Context.MODE_PRIVATE)
            val aecEnabled = prefs.getBoolean("aec_enabled", true)
            val agcEnabled = prefs.getBoolean("agc_enabled", true)
            val audioDriver = prefs.getString("audio_driver", "AAudio") ?: "AAudio"

            if (aecEnabled) {
                epConfig.medConfig.ecOptions = 1
                epConfig.medConfig.ecTailLen = 200
            } else {
                epConfig.medConfig.ecOptions = 0
                epConfig.medConfig.ecTailLen = 0
            }
            
            Log.d(TAG, "Configured PJSIP MediaConfig: AEC=$aecEnabled, AGC=$agcEnabled, Driver=$audioDriver")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring audio/DSP: ${e.message}")
        }
    }

    private fun applyAudioDriverPreference() {
        try {
            val prefs = context.getSharedPreferences("voip_app_prefs", Context.MODE_PRIVATE)
            val audioDriver = prefs.getString("audio_driver", "AAudio") ?: "AAudio"
            
            val adm = Endpoint.instance().audDevManager()
            val devCount = adm.getDevCount()
            var preferredDevIndex = -1
            
            for (i in 0 until devCount.toInt()) {
                val devInfo = adm.getDevInfo(i)
                val devName = devInfo.getName()
                val devDriver = devInfo.getDriver()
                Log.d(TAG, "Audio device [$i]: name=$devName, driver=$devDriver")
                if (devDriver.contains(audioDriver, ignoreCase = true)) {
                    preferredDevIndex = i
                    devInfo.delete()
                    break
                }
                devInfo.delete()
            }
            
            if (preferredDevIndex != -1) {
                adm.setPlaybackDev(preferredDevIndex)
                adm.setCaptureDev(preferredDevIndex)
                Log.d(TAG, "Set active audio driver to $audioDriver (device $preferredDevIndex)")
            } else {
                Log.w(TAG, "No audio device matched driver preference: $audioDriver")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying audio driver preference: ${e.message}")
        }
    }

    /**
     * Re-registers all saved accounts that are not currently registered.
     * Safe to call at any time when engine is Ready (e.g., from onResume).
     */
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
                    if (!acc.isEnabled) return@forEach
                    val existing = accountMap[acc.id]
                    val isRegistered = existing?.registrationState is RegistrationState.Registered
                    if (!isRegistered) {
                        Log.d(TAG, "reRegisterAll: re-registering ${acc.id}")
                        // Remove stale native account if any, then re-add.
                        // Use removeAccountNative to NOT delete from repository.
                        removeAccountNative(acc.id)
                        addAccountNative(acc)
                    } else {
                        Log.d(TAG, "reRegisterAll: ${acc.id} already registered, skipping")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "reRegisterAll error: ${e.message}", e)
            }
        }
    }

    fun initialize() {
        synchronized(this) {
            if (!sipScope.isActive) {
                sipScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            }
        }
        sipScope.launch {
            sipMutex.withLock {
                if (_engineState.value !is SipEngineState.Uninitialized) {
                    // If already Ready, just ensure accounts are registered
                    if (_engineState.value is SipEngineState.Ready) {
                        Log.d(TAG, "SIP Engine already Ready — ensuring accounts are registered")
                        sipScope.launch { reRegisterAll() }
                    } else {
                        Log.d(TAG, "SIP Engine already initialized or initializing")
                    }
                    return@withLock
                }

                _engineState.value = SipEngineState.Initializing
                try {
                    Log.d(TAG, "Initialization: Creating Endpoint...")
                    val newEndpoint = Endpoint()
                    Log.d(TAG, "Initialization: libCreate...")
                    newEndpoint.libCreate()
                    
                    // Assign to global field only AFTER libCreate is finished.
                    // This prevents other threads from seeing a half-initialized endpoint.
                    endpoint = newEndpoint
                    
                    // Now register this initialization thread
                    val threadId = Thread.currentThread().id
                    // No need to call libRegisterThread on the thread that created/initialized the native endpoint
                    registeredThreads.add(threadId)
                    Log.d(TAG, "Initialization: Registered init thread ID: $threadId")

                    // Endpoint configuration
                    val epConfig = EpConfig()
                    try {
                        Log.d(TAG, "Initialization: Configuring Media...")
                        configureAudioDriverAndDsp(epConfig)
                        
                        // Logging configuration
                        val settings = DefaultDataRepository.getInstance(context).settings.value
                        epConfig.logConfig.level = settings.logLevel.toLong()
                        
                        logWriter = object : LogWriter() {
                            override fun write(entry: LogEntry) {
                                // Only emit logs to the UI Flow if it's at least an Info level or higher
                                // to prevent saturating the main thread with trace/debug spam
                                if (entry.level <= 3) { // 3 = Info
                                    val logEntry = SipLogEntry(
                                        level = entry.level,
                                        threadName = entry.threadName ?: "PJSIP Thread",
                                        message = entry.msg ?: ""
                                    )
                                    _sipLogs.tryEmit(logEntry)
                                }
                                
                                // Only log to Logcat if it's a critical error or if level is high
                                if (entry.level <= settings.logLevel) {
                                    Log.d("PJSIP_NATIVE", "[${entry.level}] ${entry.msg}")
                                }
                            }
                        }
                        epConfig.logConfig.writer = logWriter
                        epConfig.logConfig.decor = epConfig.logConfig.decor and 
                                (pj_log_decoration.PJ_LOG_HAS_CR or pj_log_decoration.PJ_LOG_HAS_NEWLINE).toLong().inv()

                        // UA Configuration
                        epConfig.uaConfig.maxCalls = MAX_CALLS.toLong()
                        epConfig.uaConfig.userAgent = "VoIPApp Android SIP Client"

                        // STUN Server configuration
                        try {
                            val repository = DefaultDataRepository.getInstance(context)
                            val stunServer = repository.settings.value.stunServer
                            if (!stunServer.isNullOrBlank()) {
                                val stunServers = epConfig.uaConfig.stunServer
                                stunServers.clear()
                                stunServers.add(stunServer)
                                Log.d(TAG, "Configured STUN server: $stunServer")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to configure STUN server: ${e.message}")
                        }

                        Log.d(TAG, "Initialization: libInit...")
                        newEndpoint.libInit(epConfig)
                    } finally {
                        epConfig.delete()
                    }
                    
                    // Transport configuration
                    Log.d(TAG, "Initialization: Creating Transports...")
                    // UDP Transport configuration
                    try {
                        val udpConfig = TransportConfig()
                        try {
                            udpConfig.port = 0 // Let system choose port
                            newEndpoint.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_UDP, udpConfig)
                            Log.d(TAG, "Created UDP transport")
                        } finally {
                            udpConfig.delete()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to create UDP transport: ${e.message}")
                    }

                    // TCP Transport configuration
                    try {
                        val tcpConfig = TransportConfig()
                        try {
                            tcpConfig.port = 0 // Let system choose port
                            newEndpoint.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TCP, tcpConfig)
                            Log.d(TAG, "Created TCP transport")
                        } finally {
                            tcpConfig.delete()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to create TCP transport: ${e.message}")
                    }

                    // TLS Transport configuration
                    try {
                        val tlsConfig = TransportConfig()
                        try {
                            tlsConfig.port = 0 // Let system choose port
                            // Requirement 1: Trust All Certificates & Disable Hostname Verification.
                            // This is mandatory because the gateway (ast.cdoapp.online) handles
                            // registrations for other domains whose certs won't match.
                            tlsConfig.tlsConfig.verifyServer = false
                            tlsConfig.tlsConfig.verifyClient = false
                            tlsConfig.tlsConfig.requireClientCert = false
                            newEndpoint.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TLS, tlsConfig)
                            Log.d(TAG, "Created TLS transport (Trust-All: verifyServer=false, verifyClient=false)")
                        } finally {
                            tlsConfig.delete()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to create TLS transport: ${e.message}")
                    }
                    
                    // Register Telecom Phone Account
                    VoIpConnectionService.registerPhoneAccount(context)

                    Log.d(TAG, "Initialization: libStart...")
                    // Start PJSUA2 endpoint
                    newEndpoint.libStart()
                    Log.d(TAG, "Initialization: applying preferences...")
                    applyAudioDriverPreference()
                    
                    _engineState.value = SipEngineState.Ready
                    Log.d(TAG, "SIP Engine initialized successfully")
                    
                    // Fetch FCM Token if not present/refresh it on startup
                    try {
                        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val token = task.result
                                if (!token.isNullOrEmpty()) {
                                    saveFcmToken(token)
                                }
                            } else {
                                Log.e(TAG, "Failed to fetch FCM token on startup: ${task.exception?.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to initialize Firebase Messaging on startup: ${e.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing SIP Engine: ${e.message}", e)
                    _engineState.value = SipEngineState.Error(e.message ?: "Unknown initialization error")
                }
            }
            
            // Automatically load and register saved accounts on startup (outside lock block)
            if (_engineState.value is SipEngineState.Ready) {
                loadAndRegisterSavedAccounts()
            }
        }
    }

    fun shutdown() {
        sipScope.launch {
            sipMutex.withLock {
                try {
                    ensureThreadRegistered()
                    // Stop active recordings
                    activeRecorders.keys.forEach { callId ->
                        try {
                            stopRecording(callId)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error stopping recording on shutdown: ${e.message}")
                        }
                    }
                    activeRecorders.clear()

                    // Hang up all calls
                    pjsipCalls.values.forEach { call ->
                        try {
                            val param = CallOpParam()
                            try {
                                param.statusCode = pjsip_status_code.PJSIP_SC_DECLINE
                                call.hangup(param)
                            } finally {
                                param.delete()
                                call.delete()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error hanging up call during shutdown: ${e.message}")
                        }
                    }
                    pjsipCalls.clear()
                    callMap.clear()
                    _activeCalls.value = emptyMap()

                    // Delete accounts
                    pjsipAccounts.values.forEach { acc ->
                        try {
                            acc.delete()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error deleting account during shutdown: ${e.message}")
                        }
                    }
                    pjsipAccounts.clear()
                    accountMap.clear()
                    _activeAccounts.value = emptyMap()

                    // Destroy Endpoint
                    endpoint?.libDestroy()
                    endpoint?.delete()
                    endpoint = null
                    logWriter?.delete()
                    logWriter = null
                    registeredThreads.clear()
                    
                    _engineState.value = SipEngineState.Uninitialized
                    Log.d(TAG, "SIP Engine shutdown successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error shutting down SIP Engine: ${e.message}", e)
                } finally {
                    sipScope.cancel()
                }
            }
        }
    }

    private fun updateCodecPriorities(config: SipAccountConfig) {
        try {
            ensureThreadRegistered()
            val ep = Endpoint.instance()
            val codecs = ep.codecEnum2()

            // 1 & 2: Disable Extra Codecs, strict list to prevent Asterisk PJMEDIA_SDP_EMISSINGRTPMAP
            for (i in 0 until codecs.size) {
                val codecInfo = codecs.get(i)
                val codecId = codecInfo.getCodecId()
                
                // Set priority to 0 (disabled) for all codecs except our allowed 4
                if (!codecId.contains("opus") && !codecId.contains("PCMU") && !codecId.contains("PCMA") && !codecId.contains("G722")) {
                    ep.codecSetPriority(codecId, 0.toShort())
                }
                codecInfo.delete()
            }
            codecs.delete()

            // ONLY send the following 4 codecs with explicit priorities
            ep.codecSetPriority("opus/48000/2", config.opusPriority.toShort())
            ep.codecSetPriority("PCMU/8000/1", config.pcmuPriority.toShort())
            ep.codecSetPriority("PCMA/8000/1", config.pcmaPriority.toShort())
            ep.codecSetPriority("G722/16000/1", config.g722Priority.toShort())
            Log.d(TAG, "Applied codec priorities for ${config.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying codec priorities: ${e.message}")
        }
    }

    private fun configureAccountConfig(
        accConfig: AccountConfig,
        auth: AuthCredInfo,
        config: SipAccountConfig,
        token: String,
        appId: String
    ) {
        val baseAccountId = "${config.username}@${config.domain}"
        accConfig.idUri = "sip:$baseAccountId"
        
        val transportSuffix = when (config.transport.uppercase()) {
            "TCP" -> ";transport=tcp"
            "TLS" -> ";transport=tls"
            else -> ";transport=udp"
        }
        
        accConfig.regConfig.apply {
            registrarUri = "sip:${config.domain}$transportSuffix"
            registerOnAdd = true
            
            val usePush = config.isPushEnabled && token.isNotEmpty()
            var customContactParams = ""
            if (usePush) {
                timeoutSec = 86400L // Long duration (24h) for push-enabled accounts
                
                val safeToken = token
                    .replace("\"", "")   // strip literal double-quotes
                    .trim()              // remove any accidental whitespace
                    .replace(":", "%3A") // URL-encode colons
                customContactParams += ";pn-provider=fcm;pn-param=$appId;pn-prid=$safeToken"
                
                if (config.sipInstanceId.isNotEmpty()) {
                    customContactParams += ";+sip.instance=\"<urn:uuid:${config.sipInstanceId}>\""
                }
            } else {
                val repository = DefaultDataRepository.getInstance(context)
                timeoutSec = repository.settings.value.registrationExpiry.toLong()
            }
            
            contactParams = customContactParams
            contactUriParams = ""
            Log.d(TAG, "Configured Contact header params: $contactParams")
        }
        
        accConfig.sipConfig.authCreds.add(auth)

        if (config.outboundProxy.isNotEmpty()) {
            var proxyUri = config.outboundProxy
            // Only prepend sip: if no scheme is present. Never touch sips: URIs.
            if (!proxyUri.startsWith("sip:", ignoreCase = true) && !proxyUri.startsWith("sips:", ignoreCase = true)) {
                proxyUri = "sip:$proxyUri"
            }
            // For third-party accounts the proxy is sips:…;transport=tls.
            // Only append the account transport suffix when the proxy has no transport
            // parameter AND the proxy scheme is plain sip: (not sips:).
            if (transportSuffix.isNotEmpty()
                && !proxyUri.contains("transport=", ignoreCase = true)
                && !proxyUri.startsWith("sips:", ignoreCase = true)) {
                proxyUri = "$proxyUri$transportSuffix"
            }
            accConfig.sipConfig.proxies.add(proxyUri)
        }

        try {
            accConfig.mediaConfig.srtpUse = when (config.srtpMode) {
                1 -> pjmedia_srtp_use.PJMEDIA_SRTP_OPTIONAL
                2 -> pjmedia_srtp_use.PJMEDIA_SRTP_MANDATORY
                else -> pjmedia_srtp_use.PJMEDIA_SRTP_DISABLED
            }
            accConfig.mediaConfig.srtpSecureSignaling = if (config.transport.uppercase() == "TLS") 1 else 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure SRTP: ${e.message}")
        }
    }

    private suspend fun addAccountNativeLocked(config: SipAccountConfig): String {
        val repository = DefaultDataRepository.getInstance(context)
        val settings = repository.settings.value

        val defaultLocalDomain = "ast.cdoapp.online"
        val configuredLocal = settings.localDomain.trim()
        
        val isLocalAccount = if (configuredLocal.isNotBlank()) {
            config.domain.contains(configuredLocal, ignoreCase = true)
        } else {
            config.domain.contains(defaultLocalDomain, ignoreCase = true)
        }

        val customProxy = settings.outboundProxy.trim()
        val resolvedProxy = if (customProxy.isNotBlank()) customProxy else "sips:ast.cdoapp.online:5066;transport=tls"

        val resolvedConfig = config.copy(
            outboundProxy = resolvedProxy,
            transport = when {
                configuredLocal.isNotBlank() -> {
                    if (isLocalAccount) "TLS" else "UDP"
                }
                isLocalAccount -> "TLS"
                else -> config.transport
            }
        )
        val accountId = resolvedConfig.id
        if (pjsipAccounts.containsKey(accountId)) {
            Log.d(TAG, "Account already exists natively: $accountId")
            return accountId
        }

        // Wait for the PJSIP endpoint to be fully started before calling pjsua_acc_add.
        val currentState = _engineState.value
        if (currentState !is SipEngineState.Ready) {
            Log.d(TAG, "Engine not ready ($currentState), waiting up to 30s for Ready state...")
            val ready = withTimeoutOrNull(30_000L) {
                _engineState.first { it is SipEngineState.Ready }
            }
            if (ready == null) {
                val errMsg = "SIP Engine did not become Ready within timeout. Cannot add account."
                Log.e(TAG, errMsg)
                accountMap[accountId] = AccountWrapper(
                    id = accountId,
                    username = resolvedConfig.username,
                    domain = resolvedConfig.domain,
                    registrationState = RegistrationState.Failed(0, errMsg)
                )
                _activeAccounts.value = accountMap.toMap()
                return accountId
            }
        }
        
        // Ensure thread is registered now that we are Ready
        ensureThreadRegistered()

        try {
            val authUser = if (resolvedConfig.authUsername.isNotEmpty()) resolvedConfig.authUsername else resolvedConfig.username
            val accConfig = AccountConfig()
            val auth = AuthCredInfo("digest", "*", authUser, 0, resolvedConfig.secret)
            try {
                val fcmToken = resolvedConfig.fcmToken
                val appId = resolvedConfig.packageName

                configureAccountConfig(accConfig, auth, resolvedConfig, fcmToken, appId)

                val myAccount = MyAccount(accountId)
                myAccount.create(accConfig)

                pjsipAccounts[accountId] = myAccount
                
                updateCodecPriorities(resolvedConfig)
            } finally {
                accConfig.delete()
                auth.delete()
            }
            
            val wrapper = AccountWrapper(
                id = accountId,
                username = resolvedConfig.username,
                domain = resolvedConfig.domain,
                registrationState = RegistrationState.Registering
            )
            accountMap[accountId] = wrapper
            _activeAccounts.value = accountMap.toMap()
            
            Log.d(TAG, "Added account natively: $accountId")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding account natively $accountId: ${e.message}", e)
            accountMap[accountId] = AccountWrapper(
                id = accountId,
                username = resolvedConfig.username,
                domain = resolvedConfig.domain,
                registrationState = RegistrationState.Failed(0, e.message ?: "Failed to create account")
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

        val defaultLocalDomain = "ast.cdoapp.online"
        val configuredLocal = settings.localDomain.trim()
        
        val isLocalAccount = if (configuredLocal.isNotBlank()) {
            config.domain.contains(configuredLocal, ignoreCase = true)
        } else {
            config.domain.contains(defaultLocalDomain, ignoreCase = true)
        }

        // The pn-param for FCM push is always the running app's package name.
        val appPackageName    = context.packageName

        // +sip.instance must be unique per account (RFC 5626).
        // Generate a random UUID the very first time, then reuse it forever.
        val sipInstanceId = if (config.sipInstanceId.isBlank()) {
            java.util.UUID.randomUUID().toString().also {
                Log.d(TAG, "Generated new +sip.instance UUID for ${config.id}: $it")
            }
        } else {
            config.sipInstanceId
        }

        val customProxy = settings.outboundProxy.trim()
        val resolvedProxy = if (customProxy.isNotBlank()) customProxy else "sips:ast.cdoapp.online:5066;transport=tls"

        val resolvedConfig = config.copy(
            outboundProxy = resolvedProxy,
            transport = when {
                configuredLocal.isNotBlank() -> {
                    if (isLocalAccount) "TLS" else "UDP"
                }
                isLocalAccount -> "TLS"
                else -> config.transport
            },
            isPushEnabled = true,
            fcmToken = fcmToken,
            packageName = appPackageName,
            sipInstanceId = sipInstanceId
        )

        Log.d(TAG, "addAccount: id=${resolvedConfig.id} proxy='${resolvedConfig.outboundProxy}' transport=${resolvedConfig.transport} push=${resolvedConfig.isPushEnabled} sipInstance=${resolvedConfig.sipInstanceId} pkg=${resolvedConfig.packageName}")

        // Ensure FCM token and package name are always populated (defensive).
        val updatedConfig = resolvedConfig.copy(
            fcmToken = if (resolvedConfig.fcmToken.isEmpty()) fcmToken else resolvedConfig.fcmToken,
            packageName = resolvedConfig.packageName.ifEmpty { appPackageName }
        )

        val id = updatedConfig.id
        
        // 1. Save to repository FIRST to ensure it doesn't "vanish" if native call crashes
        try {
            val repository = DefaultDataRepository.getInstance(context)
            val savedConfig = config.copy(
                sipInstanceId = sipInstanceId,
                fcmToken = if (config.fcmToken.isEmpty()) fcmToken else config.fcmToken,
                packageName = config.packageName.ifEmpty { appPackageName }
            )
            repository.addSipAccount(savedConfig)
            Log.d(TAG, "Saved account to repository: $id (without fallback proxy)")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving account to repository: ${e.message}")
        }

        // 2. Add natively or remove natively based on isEnabled
        sipMutex.withLock {
            if (updatedConfig.isEnabled) {
                if (pjsipAccounts.containsKey(id)) {
                    Log.d(TAG, "Account already exists natively, removing to apply new configuration: $id")
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
            ensureThreadRegistered()
            val myAccount = pjsipAccounts.remove(accountId)
            if (myAccount != null) {
                try {
                    try {
                        myAccount.setRegistration(false)
                        // Give some time for PJSIP to send the unregister REGISTER request
                        delay(500)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting registration false for $accountId: ${e.message}")
                    }
                    myAccount.delete()
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
        
        // Always clean repository
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
                    Log.d(TAG, "loadAndRegisterSavedAccountsLocked: Auto-registering saved account: ${acc.id}")
                    // Requirement 2: Sanitize any stale FCM token that may have been
                    // persisted with literal double-quotes before the fix was applied.
                    // Also refresh with the latest token in case it rotated.
                    val sanitizedToken = (if (currentFcmToken.isNotEmpty()) currentFcmToken else acc.fcmToken)
                        .replace("\"", "")   // strip literal double-quotes
                        .trim()
                    
                    // +sip.instance migration: accounts saved before the per-account UUID
                    // fix will have sipInstanceId = "". Assign a stable UUID now and persist
                    // it so every subsequent cold-start uses the same value.
                    val instanceId = if (acc.sipInstanceId.isBlank()) {
                        java.util.UUID.randomUUID().toString().also {
                            Log.d(TAG, "loadAndRegisterSavedAccountsLocked: assigned new +sip.instance UUID for ${acc.id}: $it")
                        }
                    } else {
                        acc.sipInstanceId
                    }
                    
                    val needsPersist = sanitizedToken != acc.fcmToken || instanceId != acc.sipInstanceId
                    val sanitizedAcc = if (needsPersist) {
                        acc.copy(fcmToken = sanitizedToken, sipInstanceId = instanceId).also {
                            // Persist so the corrected values survive the next cold-start
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

            try {
                val callId = withContext(Dispatchers.Default) {
                    ensureThreadRegistered()

                    // Extract SIP URI from potentially formatted string (e.g. from history)
                    val cleanedDest = if (destUri.contains("<") && destUri.contains(">")) {
                        destUri.substringAfter("<").substringBefore(">")
                    } else {
                        destUri
                    }

                    // AUTO-HOLD existing active calls before starting a new one
                    pjsipCalls.values.forEach { existingCall ->
                        try {
                            val callInfo = existingCall.info
                            try {
                                if (callInfo.state == pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED) {
                                    val holdParam = CallOpParam(true)
                                    try {
                                        existingCall.setHold(holdParam)
                                        val currentWrapper = callMap[existingCall.id]
                                        if (currentWrapper != null) {
                                            callMap[existingCall.id] = currentWrapper.copy(isLocalHold = true)
                                        }
                                    } finally {
                                        holdParam.delete()
                                    }
                                }
                            } finally {
                                callInfo.delete()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to auto-hold call ${existingCall.id}: ${e.message}")
                        }
                    }
                    _activeCalls.value = callMap.toMap()

                    val myAccount = pjsipAccounts[accountId] ?: throw IllegalArgumentException("Account not found: $accountId")
                    val call = MyCall(myAccount)
                    val param = CallOpParam(true)
                    
                    // Get the transport from the account configuration to match registration
                    val repository = DefaultDataRepository.getInstance(context)
                    val accountConfig = repository.accounts.value.find { it.id == accountId }
                    
                    val transportSuffix = when (accountConfig?.transport?.uppercase()) {
                        "TCP" -> ";transport=tcp"
                        "TLS" -> ";transport=tls"
                        else -> "" // Default
                    }

                    // Build proper SIP URI. If it's just a number/extension, append the account domain.
                    val finalUri = when {
                        cleanedDest.startsWith("sip:") -> {
                            if (transportSuffix.isNotEmpty() && !cleanedDest.contains("transport=")) {
                                "$cleanedDest$transportSuffix"
                            } else {
                                cleanedDest
                            }
                        }
                        cleanedDest.contains("@") -> "sip:$cleanedDest$transportSuffix"
                        else -> {
                            val accountDomain = accountConfig?.domain?.trim() ?: accountId.substringAfter("@", "")
                            if (accountDomain.isNotEmpty()) {
                                "sip:$cleanedDest@$accountDomain$transportSuffix"
                            } else {
                                "sip:$cleanedDest$transportSuffix"
                            }
                        }
                    }
                    
                    Log.d(TAG, "Calling: $finalUri")
                    try {
                        call.makeCall(finalUri, param)
                    } finally {
                        param.delete()
                    }
                    
                    val callId = call.id
                    pjsipCalls[callId] = call
                    
                    val wrapper = CallWrapper(
                        callId = callId,
                        accountId = accountId,
                        peerUri = cleanedDest,
                        callState = SipCallState.Outgoing,
                        isIncoming = false
                    )
                    callMap[callId] = wrapper
                    _activeCalls.value = callMap.toMap()
                    Log.d(TAG, "Outgoing call initiated natively. Call ID: $callId")
                    callId
                }
                onCallId(callId)
            } catch (e: Exception) {
                Log.e(TAG, "Error making native call to $destUri: ${e.message}", e)
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
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException placing call via TelecomManager: ${e.message}, falling back to native.")
            sipScope.launch {
                makeCallNativeAsync(accountId, destUri) { _ -> }
            }
            return 0
        } catch (e: Exception) {
            Log.e(TAG, "Error placing call via TelecomManager: ${e.message}, falling back to native.")
            sipScope.launch {
                makeCallNativeAsync(accountId, destUri) { _ -> }
            }
            return 0
        }
    }

    fun answerCall(callId: Int) {
        ensureThreadRegistered()
        val call = pjsipCalls[callId] ?: return
        val param = CallOpParam(true)
        try {
            param.statusCode = pjsip_status_code.PJSIP_SC_OK
            call.answer(param)
            Log.d(TAG, "Answered call ID: $callId")
        } catch (e: Exception) {
            Log.e(TAG, "Error answering call $callId: ${e.message}", e)
        } finally {
            param.delete()
        }
    }

    fun hangupCall(callId: Int) {
        ensureThreadRegistered()
        val call = pjsipCalls[callId]
        if (call != null) {
            val param = CallOpParam(true)
            try {
                param.statusCode = pjsip_status_code.PJSIP_SC_DECLINE
                call.hangup(param)
                Log.d(TAG, "Hanging up call ID: $callId")
            } catch (e: Exception) {
                Log.e(TAG, "Error hanging up call $callId: ${e.message}", e)
            } finally {
                param.delete()
            }
        }
    }

    fun setHold(callId: Int, hold: Boolean) {
        ensureThreadRegistered()
        val call = pjsipCalls[callId] ?: return
        val param = CallOpParam(true)
        try {
            if (hold) {
                call.setHold(param)
            } else {
                call.reinvite(param)
            }
            val current = callMap[callId]
            if (current != null) {
                callMap[callId] = current.copy(isLocalHold = hold)
                _activeCalls.value = callMap.toMap()
            }
            Log.d(TAG, "Hold status updated for call $callId to $hold")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting hold for call $callId: ${e.message}", e)
        } finally {
            param.delete()
        }
    }

    fun setMute(callId: Int, mute: Boolean) {
        ensureThreadRegistered()
        val call = pjsipCalls[callId] ?: return
        try {
            val callInfo = call.info
            val mediaList = callInfo.media
            for (i in 0 until mediaList.size) {
                val mediaInfo = mediaList.get(i)
                if (mediaInfo.type == pjmedia_type.PJMEDIA_TYPE_AUDIO && 
                    mediaInfo.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE) {
                    val audioMedia = call.getAudioMedia(i)
                    val am = Endpoint.instance().audDevManager()
                    val capMedia = am.captureDevMedia
                    if (mute) {
                        // Disconnect mic from call audio
                        capMedia.stopTransmit(audioMedia)
                    } else {
                        // Reconnect mic to call audio
                        capMedia.startTransmit(audioMedia)
                    }
                    capMedia.delete()
                    audioMedia.delete()
                }
                mediaInfo.delete()
            }
            mediaList.delete()
            callInfo.delete()
            val current = callMap[callId]
            if (current != null) {
                callMap[callId] = current.copy(isMuted = mute)
                _activeCalls.value = callMap.toMap()
            }
            Log.d(TAG, "Mute status updated for call $callId to $mute")
        } catch (e: Exception) {
            Log.e(TAG, "Error muting call $callId: ${e.message}", e)
        }
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
        val connection = getConnection(callId)
        if (connection != null) {
            connection.setSpeakerphoneOn(on)
        } else {
            Log.w(TAG, "toggleSpeakerphone: connection not found for callId $callId")
        }
    }

    fun toggleBluetooth(callId: Int, on: Boolean) {
        val connection = getConnection(callId)
        if (connection != null) {
            connection.setBluetoothOn(on)
        } else {
            Log.w(TAG, "toggleBluetooth: connection not found for callId $callId")
        }
    }

    fun startRecording(callId: Int) {
        ensureThreadRegistered()
        val call = pjsipCalls[callId] ?: return
        if (activeRecorders.containsKey(callId)) {
            Log.d(TAG, "Recording is already active for call $callId")
            return
        }
        try {
            val callInfo = call.info
            var audioMedia: AudioMedia? = null
            try {
                val mediaList = callInfo.media
                try {
                    for (i in 0 until mediaList.size) {
                        val mediaInfo = mediaList.get(i)
                        try {
                            if (mediaInfo.type == pjmedia_type.PJMEDIA_TYPE_AUDIO && 
                                mediaInfo.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE) {
                                audioMedia = call.getAudioMedia(i)
                                break
                            }
                        } finally {
                            mediaInfo.delete()
                        }
                    }
                } finally {
                    mediaList.delete()
                }
            } finally {
                callInfo.delete()
            }
            
            if (audioMedia == null) {
                Log.e(TAG, "No active audio media to record for call $callId")
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
                
                val recorder = AudioMediaRecorder()
                recorder.createRecorder(file.absolutePath)
                
                // Connect Call Audio (incoming stream from peer) to Recorder
                audioMedia.startTransmit(recorder)
                
                // Connect Mic Audio (outgoing stream from user) to Recorder
                val am = Endpoint.instance().audDevManager()
                val capMedia = am.captureDevMedia
                try {
                    capMedia.startTransmit(recorder)
                } finally {
                    capMedia.delete()
                }
                
                activeRecorders[callId] = recorder
                
                val current = callMap[callId]
                if (current != null) {
                    callMap[callId] = current.copy(isRecording = true)
                    _activeCalls.value = callMap.toMap()
                }
                Log.d(TAG, "Started call recording for call $callId, output file: ${file.absolutePath}")
            } finally {
                audioMedia.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting call recording for call $callId: ${e.message}", e)
        }
    }

    fun stopRecording(callId: Int) {
        ensureThreadRegistered()
        val call = pjsipCalls[callId]
        val recorder = activeRecorders.remove(callId) ?: return
        try {
            if (call != null) {
                val callInfo = call.info
                try {
                    var audioMedia: AudioMedia? = null
                    val mediaList = callInfo.media
                    try {
                        for (i in 0 until mediaList.size) {
                            val mediaInfo = mediaList.get(i)
                            try {
                                if (mediaInfo.type == pjmedia_type.PJMEDIA_TYPE_AUDIO && 
                                    mediaInfo.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE) {
                                    audioMedia = call.getAudioMedia(i)
                                    break
                                }
                            } finally {
                                mediaInfo.delete()
                            }
                        }
                    } finally {
                        mediaList.delete()
                    }
                    
                    if (audioMedia != null) {
                        try {
                            audioMedia.stopTransmit(recorder)
                        } finally {
                            audioMedia.delete()
                        }
                    }
                } finally {
                    callInfo.delete()
                }
            }
            
            val am = Endpoint.instance().audDevManager()
            val capMedia = am.captureDevMedia
            try {
                capMedia.stopTransmit(recorder)
            } finally {
                capMedia.delete()
            }
            
            recorder.delete()
            
            val current = callMap[callId]
            if (current != null) {
                callMap[callId] = current.copy(isRecording = false)
                _activeCalls.value = callMap.toMap()
            }
            Log.d(TAG, "Stopped call recording for call $callId")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping call recording for call $callId: ${e.message}", e)
        }
    }

    fun sendDtmf(callId: Int, digit: String) {
        ensureThreadRegistered()
        val call = pjsipCalls[callId] ?: return
        try {
            call.dialDtmf(digit)
            Log.d(TAG, "Sent DTMF '$digit' for call $callId")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending DTMF for call $callId: ${e.message}", e)
        }
    }

    fun conferenceCalls() {
        ensureThreadRegistered()
        val activePjsipCalls = pjsipCalls.values.toList()
        if (activePjsipCalls.size < 2) return

        try {
            for (i in activePjsipCalls.indices) {
                val callA = activePjsipCalls[i]
                val infoA = callA.info
                val mediaA = infoA.media
                
                // Get audio media for Call A
                var audioMediaA: AudioMedia? = null
                for (mi in 0 until mediaA.size) {
                    val miInfo = mediaA.get(mi)
                    if (miInfo.type == pjmedia_type.PJMEDIA_TYPE_AUDIO &&
                        miInfo.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE) {
                        audioMediaA = callA.getAudioMedia(mi)
                        miInfo.delete()
                        break
                    }
                    miInfo.delete()
                }
                mediaA.delete()
                infoA.delete()
                
                if (audioMediaA == null) continue

                try {
                    for (j in activePjsipCalls.indices) {
                        if (i == j) continue
                        val callB = activePjsipCalls[j]
                        try {
                            val infoB = callB.info
                            val mediaB = infoB.media
                            try {
                                // Get audio media for Call B
                                var audioMediaB: AudioMedia? = null
                                for (mi in 0 until mediaB.size) {
                                    val miInfo = mediaB.get(mi)
                                    try {
                                        if (miInfo.type == pjmedia_type.PJMEDIA_TYPE_AUDIO &&
                                            miInfo.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE) {
                                            audioMediaB = callB.getAudioMedia(mi)
                                            break
                                        }
                                    } finally {
                                        miInfo.delete()
                                    }
                                }
                                if (audioMediaB != null) {
                                    try {
                                        // Bridge Call A to Call B
                                        audioMediaA.startTransmit(audioMediaB)
                                        Log.d(TAG, "Bridged audio from call ${callA.id} to ${callB.id}")
                                    } finally {
                                        audioMediaB.delete()
                                    }
                                }
                            } finally {
                                mediaB.delete()
                                infoB.delete()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error bridging call ${callA.id} to ${callB.id}: ${e.message}")
                        }
                    }
                } finally {
                    audioMediaA.delete()
                }
            }
            Log.d(TAG, "Conference established between ${activePjsipCalls.size} calls")
        } catch (e: Exception) {
            Log.e(TAG, "Error establishing conference: ${e.message}", e)
        }
    }

    // Inner class for Account callbacks
    private inner class MyAccount(val accId: String) : Account() {
        @Suppress("deprecation")
        override fun finalize() {
            try {
                val ep = endpoint
                if (ep != null && _engineState.value is SipEngineState.Ready) {
                    val threadId = Thread.currentThread().id
                    if (!registeredThreads.contains(threadId)) {
                        ep.libRegisterThread(Thread.currentThread().name)
                        registeredThreads.add(threadId)
                        Log.d(TAG, "Registered GC finalizer thread with PJSIP: ${Thread.currentThread().name} (ID: $threadId)")
                    }
                }
            } catch (e: Throwable) {
                // Ignore
            }
            try {
                delete()
            } catch (e: Throwable) {
                // Ignore
            }
        }

        override fun onRegState(prm: OnRegStateParam) {
            try {
                val accInfo = info
                try {
                    val isRegActive = accInfo.regIsActive
                    val lastCode = accInfo.regStatus
                    val lastReason = accInfo.regStatusText ?: ""

                    val state = if (isRegActive) {
                        RegistrationState.Registered
                    } else {
                        if (lastCode.toInt() >= 300) {
                            RegistrationState.Failed(lastCode.toInt(), lastReason)
                        } else {
                            RegistrationState.Registering
                        }
                    }

                    val current = accountMap[accId]
                    if (current != null) {
                        accountMap[accId] = current.copy(
                            registrationState = state,
                            lastStatusCode = lastCode.toInt(),
                            lastStatusText = lastReason
                        )
                        _activeAccounts.value = accountMap.toMap()
                    }
                    Log.d(TAG, "Account reg state changed: $accId -> $state (code=$lastCode)")
                } finally {
                    accInfo.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onRegState for $accId: ${e.message}")
            }
        }

        override fun onIncomingCall(prm: OnIncomingCallParam) {
            try {
                Log.d(TAG, "Incoming call event: callId ${prm.callId} for account $accId")
                val call = MyCall(this, prm.callId)
                val callId = prm.callId
                pjsipCalls[callId] = call
                
                // Immediately send 180 Ringing to tell the caller we are alerting the user
                val ringingParam = CallOpParam()
                try {
                    ringingParam.statusCode = pjsip_status_code.PJSIP_SC_RINGING
                    call.answer(ringingParam)
                } finally {
                    ringingParam.delete()
                }
                
                val callInfo = call.info
                val peerUri: String
                try {
                    peerUri = callInfo.remoteUri ?: "Unknown"
                } finally {
                    callInfo.delete()
                }

                val wrapper = CallWrapper(
                    callId = callId,
                    accountId = accId,
                    peerUri = peerUri,
                    callState = SipCallState.Incoming,
                    isIncoming = true
                )
                callMap[callId] = wrapper
                _activeCalls.value = callMap.toMap()

                val pushConnection = pendingPushConnection ?: connections[-1]
                if (pushConnection != null) {
                    Log.d(TAG, "Linking incoming call $callId to existing push connection (pending=${pendingPushConnection != null})")
                    pushConnection.callId = callId
                    registerConnection(callId, pushConnection)
                    
                    if (pendingPushConnection != null) {
                        pendingPushConnection = null
                        // The user already tapped "Answer" in the Telecom UI, so answer the PJSIP call immediately
                        answerCall(callId)
                    } else {
                        // Push connection existed but wasn't answered yet, remove the -1 placeholder
                        unregisterConnection(-1)
                    }
                } else {
                    Log.d(TAG, "Incoming call registered in map: ID $callId from $peerUri. Notifying TelecomManager.")
                    
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
                        Log.d(TAG, "Successfully notified TelecomManager of new incoming call $callId")
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException notifying TelecomManager: ${e.message}. App UI should still show.")
                    } catch (e: Exception) {
                        Log.e(TAG, "General error notifying TelecomManager: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Critical error in onIncomingCall: ${e.message}", e)
            }
        }
    }

    // Inner class for Call callbacks
    private inner class MyCall(val acc: Account, callId: Int = -1) : Call(acc, callId) {
        @Suppress("deprecation")
        override fun finalize() {
            try {
                val ep = endpoint
                if (ep != null && _engineState.value is SipEngineState.Ready) {
                    val threadId = Thread.currentThread().id
                    if (!registeredThreads.contains(threadId)) {
                        ep.libRegisterThread(Thread.currentThread().name)
                        registeredThreads.add(threadId)
                        Log.d(TAG, "Registered GC finalizer thread with PJSIP: ${Thread.currentThread().name} (ID: $threadId)")
                    }
                }
            } catch (e: Throwable) {
                // Ignore
            }
            try {
                delete()
            } catch (e: Throwable) {
                // Ignore
            }
        }

        override fun onCallState(prm: OnCallStateParam) {
            try {
                val callInfo = info
                val callId = id
                val state = callInfo.state
                
                Log.d(TAG, "Call state event. ID: $callId, State: $state")

                val isIncoming = try { callInfo.role == pjsip_role_e.PJSIP_ROLE_UAS } catch (e: Exception) { false }

                val sipCallState = when (state) {
                    pjsip_inv_state.PJSIP_INV_STATE_INCOMING -> SipCallState.Incoming
                    pjsip_inv_state.PJSIP_INV_STATE_CALLING -> SipCallState.Outgoing
                    pjsip_inv_state.PJSIP_INV_STATE_EARLY,
                    pjsip_inv_state.PJSIP_INV_STATE_CONNECTING -> {
                        if (isIncoming) SipCallState.Incoming else SipCallState.Connecting
                    }
                    pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED -> SipCallState.Confirmed
                    pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED -> SipCallState.Disconnected
                    else -> SipCallState.Idle
                }

                var current = callMap[callId]
                if (current == null) {
                    val peerUri = try { callInfo.remoteUri ?: "Unknown" } catch (e: Exception) { "Unknown" }
                    val accountId = (acc as? MyAccount)?.accId ?: ""
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
                        connectTimestamp = updatedConnectTimestamp
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
                        connectTimestamp = updatedConnectTimestamp
                    )
                    callMap[callId] = current
                }
                _activeCalls.value = callMap.toMap()

                try {
                    val connection = connections[callId]
                    if (connection != null) {
                        when (state) {
                            pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED -> {
                                connection.setActive()
                            }
                            pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED -> {
                                val code = callInfo.getLastStatusCode()
                                val reason = callInfo.getLastReason() ?: "Disconnected"
                                val cause = when (code.toInt()) {
                                    486 -> android.telecom.DisconnectCause.BUSY
                                    487 -> android.telecom.DisconnectCause.CANCELED
                                    403 -> android.telecom.DisconnectCause.RESTRICTED
                                    else -> android.telecom.DisconnectCause.REMOTE
                                }
                                try {
                                    connection.setDisconnected(android.telecom.DisconnectCause(cause, reason))
                                    connection.destroy()
                                } catch (e: Exception) {
                                    Log.w(TAG, "Telecom Connection already destroyed or failed to update: ${e.message}")
                                }
                                unregisterConnection(callId)
                            }
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating Telecom Connection state: ${e.message}", e)
                }

                // Delete callInfo after usage
                callInfo.delete()

                if (state == pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED) {
                    if (activeRecorders.containsKey(callId)) {
                        stopRecording(callId)
                    }
                    
                    val hideIntent = android.content.Intent(context, com.mksoft.phone.service.SipService::class.java).apply {
                        action = com.mksoft.phone.service.SipService.ACTION_HIDE_INCOMING_CALL_UI
                        putExtra(com.mksoft.phone.service.SipService.EXTRA_CALL_ID, callId)
                    }
                    sipScope.launch {
                        try {
                            context.startService(hideIntent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start SipService to hide incoming call UI: ${e.message}")
                        }
                    }

                    // Cleanup call
                    pjsipCalls.remove(callId)
                    callMap.remove(callId)
                    _activeCalls.value = callMap.toMap()
                    
                    // Defer native C++ object deletion to safely escape the JNI callback stack
                    val callRef = this
                    sipScope.launch(Dispatchers.Default) {
                        delay(1000)
                        try {
                            ensureThreadRegistered()
                            callRef.delete()
                            Log.d(TAG, "Safely deallocated disconnected call native peer: ID $callId")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error deallocating native call peer: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onCallState: ${e.message}")
            }
        }

        override fun onCallMediaState(prm: OnCallMediaStateParam) {
            try {
                val callInfo = info
                try {
                    val callId = id
                    Log.d(TAG, "Call media state changed. ID: $callId")
                    
                    val mediaList = callInfo.media
                    try {
                        for (i in 0 until mediaList.size) {
                            val mediaInfo = mediaList.get(i)
                            try {
                                if (mediaInfo.type == pjmedia_type.PJMEDIA_TYPE_AUDIO && 
                                    (mediaInfo.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE ||
                                     mediaInfo.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_REMOTE_HOLD)) {
                                    
                                    val audioMedia = getAudioMedia(i)
                                    try {
                                        val am = Endpoint.instance().audDevManager()
                                        
                                        // Bridge Call Audio with Device Audio
                                        val capMedia = am.captureDevMedia
                                        try {
                                            val playMedia = am.playbackDevMedia
                                            try {
                                                capMedia.startTransmit(audioMedia)
                                                audioMedia.startTransmit(playMedia)
                                                Log.d(TAG, "Bridged call audio media for Call ID: $callId")
                                            } finally {
                                                playMedia.delete()
                                            }
                                        } finally {
                                            capMedia.delete()
                                        }
                                    } finally {
                                        audioMedia.delete()
                                    }
                                }
                            } finally {
                                mediaInfo.delete()
                            }
                        }
                    } finally {
                        mediaList.delete()
                    }
                } finally {
                    callInfo.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onCallMediaState: ${e.message}")
            }
        }
    }

    fun sendKeepAliveOptions() {
        sipScope.launch {
            pjsipAccounts.forEach { (accountId, account) ->
                try {
                    account.setRegistration(true)
                    Log.d(TAG, "Refreshed registration for account keep-alive: $accountId")
                } catch (e: Exception) {
                    Log.e(TAG, "Error refreshing registration keep-alive for account $accountId: ${e.message}")
                }
            }
        }
    }

    fun getFcmToken(): String? {
        val repository = DefaultDataRepository.getInstance(context)
        return repository.getFcmToken()
    }

    fun saveFcmToken(token: String) {
        val repository = DefaultDataRepository.getInstance(context)
        val oldToken = repository.getFcmToken()
        if (oldToken == token) return
        
        repository.saveFcmToken(token)
        Log.d(TAG, "Saved new FCM token: $token")
        
        // If we have active accounts with push enabled, modify their configuration to register with the new token
        sipScope.launch {
            try {
                ensureThreadRegistered()
                val accounts = repository.accounts.value
                accounts.forEach { config ->
                    if (config.isPushEnabled) {
                        val appId = context.packageName // always use the running app's package name
                        val updatedConfig = config.copy(
                            fcmToken = token,
                            packageName = appId
                        )
                        // Save the updated configuration to repository
                        try {
                            repository.addSipAccount(updatedConfig)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating repository account in saveFcmToken: ${e.message}")
                        }

                        val myAccount = pjsipAccounts[config.id]
                        if (myAccount != null) {
                            val accConfig = AccountConfig()
                            val authUser = if (config.authUsername.isNotEmpty()) config.authUsername else config.username
                            val auth = AuthCredInfo("digest", "*", authUser, 0, config.secret)
                            try {
                                Log.d(TAG, "Re-registering account ${config.id} with new FCM token")
                                configureAccountConfig(accConfig, auth, updatedConfig, token, appId)
                                myAccount.modify(accConfig)
                                // Force fresh REGISTER with new token
                                myAccount.setRegistration(true)
                                Log.d(TAG, "Successfully modified native account and forced REGISTER with new FCM token")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error updating contact parameters for ${config.id}: ${e.message}")
                            } finally {
                                accConfig.delete()
                                auth.delete()
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
        ensureThreadRegistered()
        
        // If engine is Initializing, wait for it to become Ready
        if (_engineState.value is SipEngineState.Initializing) {
            Log.d(TAG, "syncActiveAccountRegistrations: Engine is initializing, waiting for Ready state...")
            withTimeoutOrNull(30_000L) {
                _engineState.first { it is SipEngineState.Ready }
            }
            // Once the engine transitions to Ready, the initialize() thread has already triggered loadAndRegisterSavedAccountsLocked().
            // Returning early avoids concurrent duplicate registration attempts.
            Log.d(TAG, "syncActiveAccountRegistrations: Engine finished initializing, skipping duplicate registration")
            return
        }

        // If engine is not ready, initialize it (which will load and register accounts)
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
                    val nativeAccount = pjsipAccounts[accountId]
                    if (nativeAccount != null) {
                        val accInfo = nativeAccount.info
                        try {
                            // If we just added the account, it might still have a REGISTER
                            // transaction in flight (regStatus == 0 = pending, or 200 = success).
                            // Calling setRegistration(true) into a live transaction triggers PJSIP_EBUSY.
                            // Skip refresh in any of these states:
                            //   - regStatus == 0  → transaction in flight, not yet answered
                            //   - regStatus == 200 → already successfully registered
                            val regStatus = accInfo.regStatus.toInt()
                            if (regStatus == 0 || regStatus == 200) {
                                Log.d(TAG, "Registration in-flight or already active for $accountId (status=$regStatus), skipping refresh")
                                return@forEach
                            }
                            
                            Log.d(TAG, "Forcing account contact refresh: $accountId")
                            nativeAccount.setRegistration(true)
                        } finally {
                            accInfo.delete()
                        }
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


