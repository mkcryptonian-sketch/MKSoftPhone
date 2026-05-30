package com.mksoft.phone.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.mksoft.phone.core.sip.SipEngineManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ConnectivityObserver(private val context: Context) {

    companion object {
        private const val TAG = "ConnectivityObserver"
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val sipEngineManager = SipEngineManager.getInstance(context)
    
    private var observerJob = SupervisorJob()
    private var observerScope = CoroutineScope(observerJob + Dispatchers.Default)
    private var syncJob: Job? = null
    private val syncMutex = Mutex()

    private fun debouncedSync() {
        observerScope.launch {
            syncMutex.withLock {
                syncJob?.cancel()
                syncJob = observerScope.launch {
                    delay(2000)
                    sipEngineManager.syncActiveAccountRegistrations()
                }
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.d(TAG, "Network available: $network. Triggering SIP re-registration sync.")
            sipEngineManager.ensureThreadRegistered()
            sipEngineManager.setNetworkReachable(true)
            debouncedSync()
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            Log.d(TAG, "Network lost: $network")
            sipEngineManager.setNetworkReachable(false)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            val isInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                true
            }
            Log.d(TAG, "Network capabilities changed for $network. Internet available: $isInternet, Validated: $isValidated")
            if (isInternet && isValidated) {
                sipEngineManager.setNetworkReachable(true)
                debouncedSync()
            }
        }
    }

    fun start() {
        try {
            if (observerJob.isCancelled) {
                observerJob = SupervisorJob()
                observerScope = CoroutineScope(observerJob + Dispatchers.Default)
            }
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            Log.d(TAG, "Started connectivity observer")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    fun stop() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Log.d(TAG, "Stopped connectivity observer")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister network callback: ${e.message}")
        } finally {
            observerJob.cancel()
        }
    }
}
