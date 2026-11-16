package com.darkrockstudios.apps.pcvolumemixer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log

data class DiscoveredServer(val name: String, val host: String, val port: Int)

/**
 * Browses for PcVolumeControl servers advertised over mDNS. Results are delivered on the main thread.
 */
class ServerDiscovery(
    context: Context,
    private val onChanged: (List<DiscoveredServer>) -> Unit
) {
    companion object {
        private val TAG = ServerDiscovery::class.java.simpleName
        private const val SERVICE_TYPE = "_pcvolumecontrol._tcp."
        private const val TXT_PROTOCOL = "protocol"
    }

    private val nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // Only touched on the main thread.
    private val servers = LinkedHashMap<String, DiscoveredServer>()
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        if (listener != null) return

        val newListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Start discovery failed: $errorCode")
                mainHandler.post { listener = null }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Stop discovery failed: $errorCode")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                mainHandler.post {
                    resolveQueue.addLast(serviceInfo)
                    resolveNext()
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                mainHandler.post {
                    if (servers.remove(serviceInfo.serviceName) != null) {
                        publish()
                    }
                }
            }
        }

        listener = newListener
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, newListener)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Unable to start discovery", e)
            listener = null
        }
    }

    fun stop() {
        listener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Unable to stop discovery", e)
            }
        }
        listener = null
        resolveQueue.clear()
        resolving = false
        if (servers.isNotEmpty()) {
            servers.clear()
            publish()
        }
    }

    // NsdManager only allows one resolve at a time.
    private fun resolveNext() {
        if (resolving) return
        val next = resolveQueue.removeFirstOrNull() ?: return
        resolving = true

        @Suppress("DEPRECATION")
        nsdManager.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                mainHandler.post { finishResolve() }
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                mainHandler.post {
                    if (listener != null) addServer(serviceInfo)
                    finishResolve()
                }
            }
        })
    }

    private fun finishResolve() {
        resolving = false
        resolveNext()
    }

    private fun addServer(info: NsdServiceInfo) {
        val protocol = info.attributes[TXT_PROTOCOL]?.let { String(it) }
        if (protocol != PROTOCOL_VERSION.toString()) {
            Log.d(TAG, "Ignoring ${info.serviceName}: protocol $protocol")
            return
        }

        val host = info.host?.hostAddress ?: return
        servers[info.serviceName] = DiscoveredServer(info.serviceName, host, info.port)
        publish()
    }

    private fun publish() {
        onChanged(servers.values.toList())
    }
}
