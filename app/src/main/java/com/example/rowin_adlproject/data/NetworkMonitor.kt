package com.example.rowin_adlproject.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Practical #5 - network connectivity layer.
 *
 * Observes the system connectivity state and exposes it as a cold Flow so
 * the UI (Profile -> App Settings sheet) and [FirebaseSyncManager] can react
 * live: "Firebase Connected / Offline / Local-only mode".
 */
class NetworkMonitor(private val context: Context) {

    /** Emits true whenever any network with INTERNET capability is available. */
    val isOnline: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(currentlyOnline(manager))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                trySend(
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                )
            }
        }

        // Seed with the current state, then track changes.
        trySend(currentlyOnline(manager))
        runCatching {
            manager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback,
            )
        }

        awaitClose {
            runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }.distinctUntilChanged()

    /** One-shot connectivity check. */
    fun currentlyOnline(): Boolean =
        currentlyOnline(context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)

    private fun currentlyOnline(manager: ConnectivityManager): Boolean {
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
