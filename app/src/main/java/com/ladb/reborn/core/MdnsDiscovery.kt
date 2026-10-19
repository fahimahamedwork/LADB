package com.ladb.reborn.core

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Discovers the device's own Wireless debugging ports via mDNS:
 *  - _adb-tls-connect._tcp  -> the port used by `adb connect`
 *  - _adb-tls-pairing._tcp  -> the port shown in the "Pair device with pairing code" dialog
 */
class MdnsDiscovery(context: Context) {

    companion object {
        private const val TYPE_CONNECT = "_adb-tls-connect._tcp"
        private const val TYPE_PAIR = "_adb-tls-pairing._tcp"
    }

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _connectPort = MutableStateFlow<Int?>(null)
    val connectPort: StateFlow<Int?> = _connectPort

    private val _pairingPort = MutableStateFlow<Int?>(null)
    val pairingPort: StateFlow<Int?> = _pairingPort

    @Volatile
    private var started = false

    fun start() {
        if (started) return
        started = true
        discover(TYPE_CONNECT, _connectPort)
        discover(TYPE_PAIR, _pairingPort)
    }

    /** Suspend until a connect port is discovered or [timeoutMs] elapses. */
    suspend fun awaitConnectPort(timeoutMs: Long): Int? =
        withTimeoutOrNull(timeoutMs) { _connectPort.filterNotNull().first() }

    private fun discover(type: String, sink: MutableStateFlow<Int?>) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                // Retry once after a moment; NSD is flaky right after boot.
                mainHandler.postDelayed({ runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, this) } }, 3_000)
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolve(serviceInfo, sink, attempt = 0)
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        mainHandler.post {
            runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
        }
    }

    private fun resolve(serviceInfo: NsdServiceInfo, sink: MutableStateFlow<Int?>, attempt: Int) {
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE && attempt < 10) {
                    mainHandler.postDelayed({ resolve(info, sink, attempt + 1) }, 250)
                    // Do not decrement: the retry owns the pending slot now.
                    return
                }
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                if (info.port > 0) sink.value = info.port
            }
        }
        mainHandler.post {
            runCatching { nsd.resolveService(serviceInfo, listener) }
        }
    }
}
