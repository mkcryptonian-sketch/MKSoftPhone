package com.mksoft.phone.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.mksoft.phone.MainActivity
import com.mksoft.phone.R
import com.mksoft.phone.core.sip.SipEngineManager
import com.mksoft.phone.core.sip.SipEngineState
import com.mksoft.phone.core.sip.SipCallState
import com.mksoft.phone.data.DefaultDataRepository
import com.mksoft.phone.receiver.CallActionReceiver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import com.mksoft.phone.core.ConnectivityObserver

class SipService : Service(), android.hardware.SensorEventListener {

    companion object {
        val isRunning = kotlinx.coroutines.flow.MutableStateFlow(false)

        private const val TAG = "SipService"
        private const val CHANNEL_ID = "SipServiceChannel"
        private const val CALL_CHANNEL_ID = "VoIpCallChannel"
        private const val NOTIFICATION_ID = 8888

        const val ACTION_START = "com.mksoft.phone.service.START"
        const val ACTION_STOP = "com.mksoft.phone.service.STOP"
        const val ACTION_SCHEDULE_ALARM = "com.mksoft.phone.service.SCHEDULE_ALARM"

        const val ACTION_SHOW_INCOMING_CALL_UI = "com.mksoft.phone.service.SHOW_INCOMING_CALL_UI"
        const val ACTION_HIDE_INCOMING_CALL_UI = "com.mksoft.phone.service.HIDE_INCOMING_CALL_UI"
        const val ACTION_SHOW_MISSED_CALL = "com.mksoft.phone.service.SHOW_MISSED_CALL"
        const val EXTRA_CALL_ID = "com.mksoft.phone.service.extra.CALL_ID"
        const val EXTRA_PEER_URI = "com.mksoft.phone.service.extra.PEER_URI"
        
        fun start(context: Context) {
            val intent = Intent(context, SipService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SipService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    lateinit var sipEngineManager: SipEngineManager
    
    private val ringtoneManager by lazy { CallRingtoneManager(applicationContext) }
    private var lastActiveIncomingCallId: Int? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var highPerfWifiLock: WifiManager.WifiLock? = null

    private var sensorManager: android.hardware.SensorManager? = null
    private var proximitySensor: android.hardware.Sensor? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null

    private var connectivityObserver: ConnectivityObserver? = null

    override fun onCreate() {
        super.onCreate()
        isRunning.value = true
        Log.d(TAG, "SipService Created")
        acquireLocks()
        createNotificationChannel()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        proximitySensor = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY)
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                proximityWakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "VoIPApp::ProximityWakeLock")
            }
        }
        
        observeSettingsChanges()

        // Start network observer so WiFi/mobile switches trigger SIP re-registration
        connectivityObserver = ConnectivityObserver(applicationContext)
        connectivityObserver?.start()
    }

    private fun observeSettingsChanges() {
        val repository = DefaultDataRepository.getInstance(applicationContext)
        serviceScope.launch {
            var lastSettings = repository.settings.value
            repository.settings.collect { newSettings ->
                Log.d(TAG, "Settings changed: $newSettings")
                
                // 1. WakeLock / WiFi Lock
                if (newSettings.wakeLockEnabled != lastSettings.wakeLockEnabled) {
                    Log.d(TAG, "wakeLockEnabled changed to ${newSettings.wakeLockEnabled}. Re-applying locks.")
                    releaseLocks()
                    acquireLocks()
                }
                
                // 2. Audio Config (AEC/AGC)
                if ((newSettings.aecEnabled != lastSettings.aecEnabled) || 
                    (newSettings.agcEnabled != lastSettings.agcEnabled)) {
                    Log.d(TAG, "AEC/AGC settings changed. Triggering engine update.")
                    sipEngineManager.updateMediaSettings()
                }
                
                // 3. Keep-Alive Alarm
                if (newSettings.registrationExpiry != lastSettings.registrationExpiry ||
                    newSettings.backgroundKeepAliveEnabled != lastSettings.backgroundKeepAliveEnabled) {
                    Log.d(TAG, "Keep-alive settings changed. Re-scheduling alarm.")
                    scheduleKeepAliveAlarm()
                }

                lastSettings = newSettings
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action: ${intent?.action}")
        
        if (!::sipEngineManager.isInitialized) {
            sipEngineManager = SipEngineManager.getInstance(applicationContext)
        }

        if (intent?.action != ACTION_STOP) {
            startForegroundWithNotification()
        }

        when (intent?.action) {
            ACTION_START -> {
                sipEngineManager.initialize()
                // Force a registration sync on every start (critical for push wakeups)
                serviceScope.launch {
                    sipEngineManager.syncActiveAccountRegistrations()
                }
                observeSipStates()
                observeActiveCallsForProximity()
                scheduleKeepAliveAlarm()
            }
            ACTION_SHOW_INCOMING_CALL_UI -> {
                val callId = intent.getIntExtra(EXTRA_CALL_ID, -1)
                val peerUri = intent.getStringExtra(EXTRA_PEER_URI) ?: ""
                if (callId != -1) {
                    lastActiveIncomingCallId = callId
                    ringtoneManager.startRinging()
                    
                    // Force the screen to turn on
                    try {
                        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                        @Suppress("DEPRECATION")
                        val screenWakeLock = pm.newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                            "VoIPApp::IncomingCallScreenWakeLock"
                        )
                        screenWakeLock.acquire(10000L) // 10 seconds screen wake
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to acquire screen wake lock: ${e.message}")
                    }
                    
                    // Direct activity launch to guarantee the call screen pops up immediately when locked/off
                    try {
                        val activityIntent = Intent(this, MainActivity::class.java).apply {
                            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(activityIntent)
                        Log.d(TAG, "Successfully triggered MainActivity directly for incoming call")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch MainActivity directly: ${e.message}")
                    }
                    
                    showIncomingCallNotification(callId, peerUri)
                }
            }
            ACTION_HIDE_INCOMING_CALL_UI -> {
                val callId = intent.getIntExtra(EXTRA_CALL_ID, -1)
                if (callId != -1) {
                    ringtoneManager.stopRinging()
                    cancelIncomingCallNotification(callId)
                    if (lastActiveIncomingCallId == callId) {
                        lastActiveIncomingCallId = null
                    }
                }
            }
            ACTION_SCHEDULE_ALARM -> {
                scheduleKeepAliveAlarm()
            }
            ACTION_SHOW_MISSED_CALL -> {
                val peerUri = intent.getStringExtra(EXTRA_PEER_URI) ?: ""
                showMissedCallNotification(peerUri)
            }
            ACTION_STOP -> {
                cancelKeepAliveAlarm()
                sipEngineManager.shutdown()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved: App task swiped away from recents. Service will continue to run to receive calls.")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.value = false
        Log.d(TAG, "SipService Destroyed")
        cancelKeepAliveAlarm()
        unregisterProximitySensor()
        connectivityObserver?.stop()
        releaseLocks()
        serviceScope.cancel()
    }

    private fun acquireLocks() {
        val repository = DefaultDataRepository.getInstance(applicationContext)
        val settings = repository.settings.value
        if (!settings.wakeLockEnabled) {
            Log.d(TAG, "Permanent WakeLock and WifiLock are disabled in settings; skipping acquisition.")
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            // High performance lock will be acquired only during active calls to save battery
            highPerfWifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "VoIPApp::SipServiceHighPerfLock").apply {
                setReferenceCounted(false)
            }
            return
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoIPApp::SipServiceWakeLock").apply {
            setReferenceCounted(false)
            acquire() // Held indefinitely; released explicitly in releaseLocks() or onDestroy()
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        // Basic WiFi lock to keep radio awake
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "VoIPApp::SipServiceWifiLock").apply {
            setReferenceCounted(false)
            acquire()
        }
        
        // High performance lock will be acquired only during active calls to save battery
        highPerfWifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "VoIPApp::SipServiceHighPerfLock").apply {
            setReferenceCounted(false)
        }
        
        Log.d(TAG, "Acquired WakeLock and standard WifiLock")
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
            if (highPerfWifiLock?.isHeld == true) highPerfWifiLock?.release()
            Log.d(TAG, "Released all Wake/Wifi locks")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing locks: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_service_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_service_description)
            }
            
            val callChannel = NotificationChannel(
                CALL_CHANNEL_ID,
                getString(R.string.channel_call_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_call_description)
                setSound(null, null)
                enableVibration(false)
            }
            
            val missedChannel = NotificationChannel(
                "MissedCallChannel",
                "Missed Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for missed VoIP calls"
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(callChannel)
            manager.createNotificationChannel(missedChannel)
        }
    }

    private fun startForegroundWithNotification() {
        val notification = buildServiceNotification(
            getString(R.string.service_starting_title),
            getString(R.string.service_starting_text)
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(title: String, text: String, requireMicrophone: Boolean = false) {
        val notification = buildServiceNotification(title, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (requireMicrophone) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildServiceNotification(title: String, text: String): Notification {
        val pendingIntent = run {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun observeSipStates() {
        serviceScope.launch {
            combine(
                sipEngineManager.engineState,
                sipEngineManager.activeAccounts,
                sipEngineManager.activeCalls
            ) { engineState, accounts, calls ->
                Triple(engineState, accounts, calls)
            }.collect { (engineState, accounts, calls) ->
                val title: String
                val text: String
                var requireMicrophone = false

                when (engineState) {
                    is SipEngineState.Error -> {
                        title = getString(R.string.service_error_title)
                        text = engineState.message
                    }
                    SipEngineState.Initializing -> {
                        title = getString(R.string.service_connecting_title)
                        text = getString(R.string.service_connecting_text)
                    }
                    SipEngineState.Ready -> {
                        val activeCallsList = calls.values.filter { it.callState != SipCallState.Disconnected }
                        if (activeCallsList.isNotEmpty()) {
                            val activeCall = activeCallsList.first()
                            title = getString(R.string.service_active_call_title)
                            text = getString(R.string.service_active_call_text, activeCall.peerUri)
                            // Require microphone foreground type for any call state to ensure early media works
                            requireMicrophone = true
                        } else {
                            val registeredCount = accounts.values.count { 
                                it.registrationState is com.mksoft.phone.core.sip.RegistrationState.Registered 
                            }
                            title = getString(R.string.app_name)
                            text = if (registeredCount > 0) {
                                getString(R.string.service_registered_text, registeredCount)
                            } else {
                                getString(R.string.service_no_accounts_text)
                            }
                        }
                    }
                    SipEngineState.Uninitialized -> {
                        title = getString(R.string.service_offline_title)
                        text = getString(R.string.service_offline_text)
                    }
                }
                updateNotification(title, text, requireMicrophone)
            }
        }
    }

    private fun observeActiveCallsForProximity() {
        val repository = DefaultDataRepository.getInstance(applicationContext)
        serviceScope.launch {
            sipEngineManager.activeCalls.collect { calls ->
                val hasActiveCall = calls.values.any { 
                    it.callState == SipCallState.Confirmed || 
                    it.callState == SipCallState.Outgoing || 
                    it.callState == SipCallState.Incoming 
                }
                
                val proximityEnabled = repository.settings.value.proximitySensorEnabled

                // Handle Proximity
                if (hasActiveCall && proximityEnabled) {
                    registerProximitySensor()
                } else {
                    unregisterProximitySensor()
                }

                // Handle High-Performance WiFi Lock
                if (hasActiveCall) {
                    if (highPerfWifiLock?.isHeld == false) {
                        highPerfWifiLock?.acquire()
                        Log.d(TAG, "Acquired High-Performance WiFi Lock for active call")
                    }
                } else {
                    if (highPerfWifiLock?.isHeld == true) {
                        highPerfWifiLock?.release()
                        Log.d(TAG, "Released High-Performance WiFi Lock (no active calls)")
                    }
                }
            }
        }
    }

    private fun registerProximitySensor() {
        proximitySensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Registered proximity sensor listener")
        }
    }

    private fun unregisterProximitySensor() {
        sensorManager?.unregisterListener(this)
        try {
            if (proximityWakeLock?.isHeld == true) {
                proximityWakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing proximity wake lock: ${e.message}")
        }
        Log.d(TAG, "Unregistered proximity sensor listener")
    }

    override fun onSensorChanged(event: android.hardware.SensorEvent?) {
        if (event == null) return
        val distance = event.values[0]
        val maxRange = proximitySensor?.maximumRange ?: 5f
        
        // standard threshold is often 5cm, but some sensors only report 0/maxRange
        val isNear = distance < maxRange && distance < 5f 
        
        if (isNear) {
            if (proximityWakeLock?.isHeld == false) {
                proximityWakeLock?.acquire()
                Log.d(TAG, "Proximity NEAR (dist=$distance): Screen OFF")
            }
        } else {
            if (proximityWakeLock?.isHeld == true) {
                proximityWakeLock?.release()
                Log.d(TAG, "Proximity FAR (dist=$distance): Screen ON")
            }
        }
    }

    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}

    private fun scheduleKeepAliveAlarm() {
        try {
            val repository = DefaultDataRepository.getInstance(applicationContext)
            val settings = repository.settings.value
            
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, com.mksoft.phone.receiver.KeepAliveReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                999,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Calculate interval: half of registration expiry
            // For push accounts, SipEngineManager sets timeoutSec to 600s
            val hasPushAccounts = repository.accounts.value.any { it.isPushEnabled }
            if (hasPushAccounts && !settings.backgroundKeepAliveEnabled) {
                Log.d(TAG, "Push accounts active and backgroundKeepAliveEnabled is false; skipping keep-alive alarm scheduling.")
                return
            }
            val expiry = if (hasPushAccounts) 600 else settings.registrationExpiry
            val intervalMs = expiry * 1000L / 2
            val triggerAtMs = System.currentTimeMillis() + intervalMs

            Log.d(TAG, "Scheduling keep-alive alarm: interval=${intervalMs/1000}s, pushEnabled=$hasPushAccounts")

            // On API 31+ we need SCHEDULE_EXACT_ALARM runtime permission.
            // If the user hasn't granted it yet, fall back to setAndAllowWhileIdle (inexact
            // but still wakes the device) so the keep-alive works without crashing.
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true // Permission not required below API 31
            }

            when {
                canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMs,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled exact keep-alive alarm in ${intervalMs / 1000}s")
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    // Inexact fallback — won't be pinpoint but keeps the service alive
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMs,
                        pendingIntent
                    )
                    Log.w(TAG, "SCHEDULE_EXACT_ALARM not granted; using inexact keep-alive alarm in ${intervalMs / 1000}s")
                }
                else -> {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMs,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled keep-alive alarm in ${intervalMs / 1000}s")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling keep-alive alarm: ${e.message}")
        }
    }

    private fun cancelKeepAliveAlarm() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, com.mksoft.phone.receiver.KeepAliveReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                999,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Canceled keep-alive alarms")
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling keep-alive alarm: ${e.message}")
        }
    }


    private fun showIncomingCallNotification(callId: Int, peerUri: String) {
        val cleanPeerUri = if (peerUri.startsWith("sip:")) peerUri.substring(4) else peerUri

        // 1. Full screen intent pointing to MainActivity
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            callId,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Answer intent (broadcast to CallActionReceiver)
        val answerIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_ANSWER
            putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            this,
            callId + 100,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Decline intent (broadcast to CallActionReceiver)
        val declineIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_DECLINE
            putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            this,
            callId + 200,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 4. Build premium Heads-Up Notification using system CallStyle
        val caller = Person.Builder()
            .setName(cleanPeerUri)
            .setImportant(true)
            .build()

        val notification = NotificationCompat.Builder(this, CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(getString(R.string.incoming_call_title))
            .setContentText(cleanPeerUri)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    caller,
                    declinePendingIntent,
                    answerPendingIntent
                )
            )
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(callId, notification)
        Log.d(TAG, "Showed premium CallStyle incoming notification for callId: $callId")
    }

    private fun cancelIncomingCallNotification(callId: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(callId)
        Log.d(TAG, "Cancelled incoming call notification for callId: $callId")
    }

    private fun showMissedCallNotification(peerUri: String) {
        val cleanPeerUri = if (peerUri.startsWith("sip:")) peerUri.substring(4) else peerUri
        
        val pendingIntent = run {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navigate_to", "history")
            }
            PendingIntent.getActivity(
                this,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(this, "MissedCallChannel")
            .setContentTitle("Missed Call")
            .setContentText(cleanPeerUri)
            .setSmallIcon(android.R.drawable.stat_notify_missed_call)
            .setColor(android.graphics.Color.RED)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Make it visible on lockscreen
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = peerUri.hashCode()
        manager.notify(notificationId, notification)
        Log.d(TAG, "Showed missed call notification for: $cleanPeerUri")
    }
}


