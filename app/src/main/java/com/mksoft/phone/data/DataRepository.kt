package com.mksoft.phone.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mksoft.phone.core.sip.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File

@Serializable
data class CallHistoryEntry(
    val id: String,
    val number: String,
    val timestamp: Long,
    val duration: Long, // in seconds
    val isIncoming: Boolean,
    val wasAnswered: Boolean,
    val isThirdParty: Boolean = false
)

@Serializable
data class SipContact(
    val id: String,
    val displayName: String,
    val sipAddress: String
)

@Serializable
data class VoIpSettings(
    val isLoggedIn: Boolean = false,
    val autoStartOnBoot: Boolean = true,
    val stunServer: String = "stun.l.google.com:19302",
    val registrationExpiry: Int = 300, // seconds
    val logLevel: Int = 3, // PJSIP log level (0 to 6)
    /**
     * Outbound SIP proxy URI applied to every account.
     * Defaults to the CDO Softphone proxy (hidden fallback).
     */
    val outboundProxy: String = "",
    /**
     * The SIP domain that is hosted directly on this device's proxy server.
     */
    val localDomain: String = "",
    val aecEnabled: Boolean = true,
    val agcEnabled: Boolean = true,
    val wakeLockEnabled: Boolean = true,
    val backgroundKeepAliveEnabled: Boolean = true,
    
    // New Advanced Settings
    val transportProtocol: String = "TLS", // TLS, TCP, UDP
    val turnServer: String = "",
    val iceEnabled: Boolean = false,
    val keepAliveInterval: Int = 30, // seconds
    val rportEnabled: Boolean = true,
    val ipv6Preference: String = "Dual-stack", // "Force IPv4", "Force IPv6", "Dual-stack"
    val dtmfMethod: String = "RFC 2833", // "RFC 2833", "SIP INFO", "In-band"
    val echoCancellationType: String = "Hardware", // "Hardware", "Software"
    val nativeCallIntegrationEnabled: Boolean = true,
    val pushNotificationsEnabled: Boolean = true,
    val dndSyncEnabled: Boolean = false,
    val globalCodecs: List<SipCodecConfig> = emptyList(),

    // Enterprise & Media Settings
    val attendedTransferEnabled: Boolean = true,
    val blfEnabled: Boolean = false,
    val videoEnabled: Boolean = false,
    val limeEnabled: Boolean = false,
    val proximitySensorEnabled: Boolean = true,
    val zrtpSasDisplayEnabled: Boolean = true,
    val sipMessagingEnabled: Boolean = true,
    val postQuantumEnabled: Boolean = false,

    // ── Opus Quality Tuning ──────────────────────────────────────
    // These are exposed in the Audio settings panel so the user can tune them.
    // Default values are chosen for the best voice quality on mobile networks.
    /** Opus packetization time in ms. 20ms = standard; lower = less latency; higher = less CPU. */
    val opusPtime: Int = 20,
    /** Opus target bitrate in kbps. 0 = auto (recommended; lets the encoder adapt per-frame). */
    val opusBitrate: Int = 0,
    /** Enable Opus in-band FEC. Strongly recommended for mobile/lossy networks. */
    val opusFecEnabled: Boolean = true,
    /** Enable Opus DTX. Saves bandwidth during silence and hold periods. */
    val opusDtxEnabled: Boolean = true
)

@Serializable
data class SipCodecConfig(
    val id: String, // e.g. "opus/48000/2", "G729/8000/1"
    val enabled: Boolean,
    val priority: Int
)

@Serializable
data class SipAccountConfig(
    val id: String,
    val username: String,
    val domain: String,
    val secret: String,
    val authUsername: String = "",
    val outboundProxy: String = "",
    val isPushEnabled: Boolean = false,
    val usePushProxy: Boolean = false, // Requirement: Support 3rd party push routing via Flexisip
    val useSbc: Boolean = false, // Use SBC for local domain
    val transport: String = "UDP", // "UDP", "TCP", "TLS"
    val port: Int? = null,

    // ── Per-Account Encryption ───────────────────────────────────────────
    // ALL defaults are OFF so a freshly-added account works with providers that
    // do not support any encryption. Enable only what the specific provider requires.
    /** SRTP media encryption. 0=disabled, 1=optional (offer+accept), 2=mandatory (refuse unencrypted). */
    val srtpMode: Int = 0,
    /** ZRTP key exchange for media. Negotiated via Diffie-Hellman at call setup. */
    val zrtpEnabled: Boolean = false,
    /** LIME End-to-End Encryption for calls and messages. Requires LIME server on the provider side. */
    val limeEnabled: Boolean = false,
    /** Show ZRTP Short Authentication String during calls for user to verify. */
    val zrtpSasDisplayEnabled: Boolean = true,

    val numberRewriting: String = "",

    // ── Legacy per-codec priority fields (kept for migration only) ───────────
    // These are superseded by the [codecs] list. Do not add new fields here.
    val opusPriority: Int = 128,
    val pcmuPriority: Int = 128,
    val pcmaPriority: Int = 128,
    val g722Priority: Int = 128,
    val gsmPriority: Int = 128,
    val g729Priority: Int = 128,
    val speexPriority: Int = 128,
    val amrPriority: Int = 128,
    val opusEnabled: Boolean = true,
    val pcmuEnabled: Boolean = true,
    val pcmaEnabled: Boolean = true,
    val g722Enabled: Boolean = true,
    val gsmEnabled: Boolean = false,
    val g729Enabled: Boolean = true,
    val speexEnabled: Boolean = false,
    val amrEnabled: Boolean = false,
    // ── End legacy fields ────────────────────────────────────────────────

    val fcmToken: String = "",
    val packageName: String = "",
    val isEnabled: Boolean = true,
    /**
     * Per-account UUID used for the +sip.instance Contact-URI parameter (RFC 5626).
     * Assigned once at account creation time and stored permanently so the same
     * instance ID survives app restarts. An empty value means "not yet assigned";
     * the SIP engine will generate a fresh UUID and persist it before registering.
     */
    val sipInstanceId: String = "",
    /** Per-account codec list (ordered by priority, index 0 = highest). Overrides [globalCodecs]. */
    val codecs: List<SipCodecConfig> = emptyList()
) {
    /**
     * Returns the effective ordered codec list for this account.
     *
     * Priority cascade:
     * 1. Per-account [codecs] list (set in Account Details screen)
     * 2. [globalCodecs] from [VoIpSettings.globalCodecs] (set in Audio settings)
     * 3. Legacy individual enable/priority fields (for accounts created before the codec list existed)
     */
    fun getNormalizedCodecs(globalCodecs: List<SipCodecConfig> = emptyList()): List<SipCodecConfig> {
        // 1. Per-account override takes highest precedence
        if (codecs.isNotEmpty()) return codecs
        // 2. Global codec list is the shared baseline
        if (globalCodecs.isNotEmpty()) return globalCodecs
        // 3. Legacy field migration: build list from the old individual enable/priority fields
        return listOf(
            SipCodecConfig("opus/48000/2",   opusEnabled,  if (opusPriority  == 128) 200 else opusPriority),
            SipCodecConfig("G722/16000/1",   g722Enabled,  if (g722Priority  == 128) 190 else g722Priority),
            SipCodecConfig("G729/8000/1",    g729Enabled,  if (g729Priority  == 128) 180 else g729Priority),
            SipCodecConfig("PCMU/8000/1",    pcmuEnabled,  if (pcmuPriority  == 128) 170 else pcmuPriority),
            SipCodecConfig("PCMA/8000/1",    pcmaEnabled,  if (pcmaPriority  == 128) 160 else pcmaPriority),
            SipCodecConfig("AMR-WB/16000/1", false,        150),
            SipCodecConfig("AMR/8000/1",     amrEnabled,   if (amrPriority   == 128) 140 else amrPriority),
            SipCodecConfig("speex/16000/1",  speexEnabled, if (speexPriority == 128) 130 else speexPriority),
            SipCodecConfig("speex/32000/1",  false,        120),
            SipCodecConfig("speex/8000/1",   false,        110),
            SipCodecConfig("iLBC/8000/1",    false,        100),
            SipCodecConfig("GSM/8000/1",     gsmEnabled,   if (gsmPriority   == 128) 90  else gsmPriority),
            SipCodecConfig("G7221/16000/1",  false,        80),
            SipCodecConfig("G7221/32000/1",  false,        70),
            SipCodecConfig("L16/16000/1",    false,        60),
            SipCodecConfig("L16/8000/1",     false,        50)
        ).sortedByDescending { it.priority }
    }
}

interface DataRepository {
    val callHistory: Flow<List<CallHistoryEntry>>
    val contacts: Flow<List<SipContact>>
    val settings: Flow<VoIpSettings>
    val accounts: Flow<List<SipAccountConfig>>
    val primaryAccountId: Flow<String?>
    val messages: Flow<List<SipChatMessage>>
    
    fun addCallHistory(entry: CallHistoryEntry)
    fun clearCallHistory()
    fun addContact(contact: SipContact)
    fun removeContact(contactId: String)
    fun updateSettings(settings: VoIpSettings)
    fun getRecordingsList(context: Context): List<File>
    fun addSipAccount(account: SipAccountConfig)
    fun removeSipAccount(accountId: String)
    fun setPrimaryAccountId(accountId: String?)
    fun saveFcmToken(token: String)
    fun getFcmToken(): String?
    fun addChatMessage(message: SipChatMessage)
    fun markMessagesAsRead(peerUri: String)
    fun clearChatHistory(peerUri: String)
}

class DefaultDataRepository private constructor(private val context: Context) : DataRepository {
    
    companion object {
        @Volatile
        private var instance: DefaultDataRepository? = null

        fun getInstance(context: Context): DefaultDataRepository {
            return instance ?: synchronized(this) {
                instance ?: DefaultDataRepository(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences("voip_app_prefs", Context.MODE_PRIVATE)
    private val securePrefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_voip_app_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("secure_voip_app_prefs_fallback", Context.MODE_PRIVATE)
    }
    private val json = Json { ignoreUnknownKeys = true }
    
    private val dbHelper = CallHistoryDbHelper(context)
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _callHistory = MutableStateFlow<List<CallHistoryEntry>>(emptyList())
    override val callHistory = _callHistory.asStateFlow()
    
    private val _contacts = MutableStateFlow<List<SipContact>>(emptyList())
    override val contacts = _contacts.asStateFlow()
    
    private val _settings = MutableStateFlow<VoIpSettings>(VoIpSettings())
    override val settings = _settings.asStateFlow()
    
    private val _accounts = MutableStateFlow<List<SipAccountConfig>>(emptyList())
    override val accounts = _accounts.asStateFlow()

    private val _messages = MutableStateFlow<List<SipChatMessage>>(emptyList())
    override val messages = _messages.asStateFlow()

    private val _primaryAccountId = MutableStateFlow<String?>(null)
    override val primaryAccountId = _primaryAccountId.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "sip_accounts" -> loadAccounts()
            "primary_account_id" -> loadPrimaryAccountId()
            "settings" -> loadSettings()
            "contacts" -> loadContacts()
            "chat_messages" -> loadMessages()
        }
    }
    
    init {
        migrateSipCredentialsIfNeeded()
        loadCallHistory()
        loadContacts()
        loadSettings()
        loadAccounts()
        loadPrimaryAccountId()
        loadMessages()
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        securePrefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun migrateSipCredentialsIfNeeded() {
        if (prefs.contains("sip_accounts")) {
            val legacyAccounts = prefs.getString("sip_accounts", null)
            if (legacyAccounts != null) {
                securePrefs.edit().putString("sip_accounts", legacyAccounts).commit()
            }
            prefs.edit().remove("sip_accounts").commit()
        }
        if (prefs.contains("primary_account_id")) {
            val legacyPrimary = prefs.getString("primary_account_id", null)
            if (legacyPrimary != null) {
                securePrefs.edit().putString("primary_account_id", legacyPrimary).commit()
            }
            prefs.edit().remove("primary_account_id").commit()
        }
    }
    
    private fun loadCallHistory() {
        repositoryScope.launch {
            try {
                val calls = dbHelper.getAllCalls()
                _callHistory.value = calls
            } catch (e: Exception) {
                _callHistory.value = emptyList()
            }
        }
    }
    
    override fun addCallHistory(entry: CallHistoryEntry) {
        repositoryScope.launch {
            dbHelper.insertCall(entry)
            loadCallHistory()
        }
    }
    
    override fun clearCallHistory() {
        repositoryScope.launch {
            dbHelper.clearAllCalls()
            loadCallHistory()
        }
    }
    
    private fun loadContacts() {
        val jsonStr = prefs.getString("contacts", null)
        if (jsonStr != null) {
            try {
                _contacts.value = json.decodeFromString<List<SipContact>>(jsonStr)
            } catch (e: Exception) {
                _contacts.value = defaultContacts()
            }
        } else {
            _contacts.value = defaultContacts()
            saveContacts(_contacts.value)
        }
    }
    
    private fun defaultContacts(): List<SipContact> {
        return emptyList()
    }
    
    private fun saveContacts(list: List<SipContact>) {
        _contacts.value = list
        prefs.edit().putString("contacts", json.encodeToString(list)).apply()
    }
    
    override fun addContact(contact: SipContact) {
        val current = _contacts.value.toMutableList()
        current.removeAll { it.id == contact.id || it.sipAddress == contact.sipAddress }
        current.add(contact)
        saveContacts(current)
    }
    
    override fun removeContact(contactId: String) {
        val current = _contacts.value.toMutableList()
        current.removeAll { it.id == contactId }
        saveContacts(current)
    }
    
    private fun loadSettings() {
        val jsonStr = prefs.getString("settings", null)
        if (jsonStr != null) {
            try {
                _settings.value = json.decodeFromString<VoIpSettings>(jsonStr)
            } catch (e: Exception) {
                _settings.value = VoIpSettings()
            }
        }
    }
    
    override fun updateSettings(settings: VoIpSettings) {
        _settings.value = settings
        prefs.edit().putString("settings", json.encodeToString(settings)).apply()
    }
    
    override fun getRecordingsList(context: Context): List<File> {
        val dir = File(context.filesDir, "recordings")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir.listFiles()?.filter { it.isFile && (it.name.endsWith(".wav") || it.name.endsWith(".mp3")) }?.toList() ?: emptyList()
    }
    
    private fun loadAccounts() {
        val jsonStr = securePrefs.getString("sip_accounts", null)
        if (jsonStr != null) {
            try {
                _accounts.value = json.decodeFromString<List<SipAccountConfig>>(jsonStr)
            } catch (e: Exception) {
                _accounts.value = emptyList()
            }
        } else {
            _accounts.value = emptyList()
        }
    }

    private fun loadPrimaryAccountId() {
        _primaryAccountId.value = securePrefs.getString("primary_account_id", null)
    }
    
    private fun saveAccounts(list: List<SipAccountConfig>) {
        _accounts.value = list
        securePrefs.edit().putString("sip_accounts", json.encodeToString(list)).commit()
    }
    
    private fun loadMessages() {
        val jsonStr = prefs.getString("chat_messages", null)
        if (jsonStr != null) {
            try {
                _messages.value = json.decodeFromString<List<SipChatMessage>>(jsonStr)
            } catch (e: Exception) {
                _messages.value = emptyList()
            }
        }
    }

    private fun saveMessages(list: List<SipChatMessage>) {
        _messages.value = list
        prefs.edit().putString("chat_messages", json.encodeToString(list)).apply()
    }

    override fun addChatMessage(message: SipChatMessage) {
        val current = _messages.value.toMutableList()
        current.add(message)
        saveMessages(current)
    }

    override fun markMessagesAsRead(peerUri: String) {
        val current = _messages.value.map {
            if (it.peerUri == peerUri && !it.isRead) it.copy(isRead = true) else it
        }
        saveMessages(current)
    }

    override fun clearChatHistory(peerUri: String) {
        val current = _messages.value.filter { it.peerUri != peerUri }
        saveMessages(current)
    }

    override fun addSipAccount(account: SipAccountConfig) {
        val current = _accounts.value.toMutableList()
        current.removeAll { it.id == account.id }
        current.add(account)
        saveAccounts(current)
    }
    
    override fun removeSipAccount(accountId: String) {
        val current = _accounts.value.toMutableList()
        current.removeAll { it.id == accountId }
        saveAccounts(current)
    }

    override fun setPrimaryAccountId(accountId: String?) {
        _primaryAccountId.value = accountId
        if (accountId == null) {
            securePrefs.edit().remove("primary_account_id").commit()
        } else {
            securePrefs.edit().putString("primary_account_id", accountId).commit()
        }
    }

    override fun saveFcmToken(token: String) {
        securePrefs.edit().putString("fcm_token", token).apply()
    }

    override fun getFcmToken(): String? {
        // Fallback to legacy plain prefs, migrate, and clean up if present
        if (prefs.contains("fcm_token")) {
            val token = prefs.getString("fcm_token", null)
            if (token != null) {
                securePrefs.edit().putString("fcm_token", token).commit()
            }
            prefs.edit().remove("fcm_token").commit()
            return token
        }
        return securePrefs.getString("fcm_token", null)
    }
}


