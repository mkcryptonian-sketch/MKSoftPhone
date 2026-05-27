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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ConnectivityObserver(private val context: Context) {

    companion object {
        private const val TAG = "ConnectivityObserver"
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val sipEngineManager = SipEngineManager.getInstance(context)
    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.d(TAG, "Network available: $network. Triggering SIP re-registration sync.")
            sipEngineManager.ensureThreadRegistered()
            observerScope.launch {
                sipEngineManager.syncActiveAccountRegistrations()
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            Log.d(TAG, "Network lost: $network")
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            val isInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            Log.d(TAG, "Network capabilities changed for $network. Internet available: $isInternet")
            if (isInternet) {
                observerScope.launch {
                    sipEngineManager.syncActiveAccountRegistrations()
                }
            }
        }
    }

    fun start() {
        try {
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
        }
    }
}
