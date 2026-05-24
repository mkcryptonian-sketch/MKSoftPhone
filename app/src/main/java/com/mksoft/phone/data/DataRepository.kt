package com.mksoft.phone.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
    val wasAnswered: Boolean
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
    val localDomain: String = ""
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
    val transport: String = "UDP", // "UDP", "TCP", "TLS"
    val srtpMode: Int = 0, // 0 = disabled, 1 = optional, 2 = mandatory
    val zrtpEnabled: Boolean = false,
    val numberRewriting: String = "",
    val opusPriority: Int = 128,
    val pcmuPriority: Int = 128,
    val pcmaPriority: Int = 128,
    val g722Priority: Int = 128,
    val gsmPriority: Int = 128,
    val fcmToken: String = "",
    val packageName: String = "",
    val isEnabled: Boolean = true,
    /**
     * Per-account UUID used for the +sip.instance Contact-URI parameter (RFC 5626).
     * Assigned once at account creation time and stored permanently so the same
     * instance ID survives app restarts. An empty value means "not yet assigned";
     * the SIP engine will generate a fresh UUID and persist it before registering.
     */
    val sipInstanceId: String = ""
)

interface DataRepository {
    val callHistory: Flow<List<CallHistoryEntry>>
    val contacts: Flow<List<SipContact>>
    val settings: Flow<VoIpSettings>
    val accounts: Flow<List<SipAccountConfig>>
    val primaryAccountId: Flow<String?>
    
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

    private val _primaryAccountId = MutableStateFlow<String?>(null)
    override val primaryAccountId = _primaryAccountId.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "sip_accounts" -> loadAccounts()
            "primary_account_id" -> loadPrimaryAccountId()
            "settings" -> loadSettings()
            "contacts" -> loadContacts()
        }
    }
    
    init {
        migrateSipCredentialsIfNeeded()
        loadCallHistory()
        loadContacts()
        loadSettings()
        loadAccounts()
        loadPrimaryAccountId()
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


