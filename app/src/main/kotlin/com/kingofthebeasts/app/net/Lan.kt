package com.kingofthebeasts.app.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.kingofthebeasts.core.net.NetProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

/** Network interfaces for mobile data (rmnet, ccmni, pdp…) and VPNs (tun, ppp…). */
private val NOT_LOCAL = listOf("rmnet", "ccmni", "pdp", "v4-", "clat", "tun", "ppp", "ipsec", "dummy")

/** A game someone is hosting on this Wi-Fi. */
data class FoundGame(val name: String, val host: String, val port: Int)

/**
 * This phone's addresses on the local network, e.g. "192.168.1.23", for joining by hand: Wi-Fi, and
 * the hotspot when this phone shares one. Mobile data and VPN interfaces are left out, since a friend
 * can't reach those directly.
 */
fun localAddresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual && NOT_LOCAL.none { p -> it.name.startsWith(p) } }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .filter { it.isSiteLocalAddress }
        .map { it.hostAddress ?: "" }
        .filter { it.isNotEmpty() }
}.getOrDefault(emptyList())

/**
 * Hosts a game: listens for one friend and announces the game on the local network
 * (Network Service Discovery) so their app can list it.
 */
class LanHost(private val context: Context?, private val name: String) {
    // A fixed port makes joining by address easy; fall back to any free port if it's taken.
    private val server = runCatching { ServerSocket(DEFAULT_PORT) }.getOrElse { ServerSocket(0) }
    val port: Int get() = server.localPort
    private var registration: NsdManager.RegistrationListener? = null

    fun advertise() {
        val nsd = context?.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val info = NsdServiceInfo().apply {
            serviceName = name.take(40).ifBlank { "King of the Beasts" }
            serviceType = NetProtocol.SERVICE_TYPE
            port = this@LanHost.port
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) {}
        }
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }.onSuccess { registration = listener }
    }

    /** Waits for a friend to connect. */
    suspend fun accept(): Socket = withContext(Dispatchers.IO) { server.accept() }

    fun close() {
        registration?.let { l ->
            val nsd = context?.getSystemService(Context.NSD_SERVICE) as? NsdManager
            runCatching { nsd?.unregisterService(l) }
        }
        registration = null
        runCatching { server.close() }
    }
}

/** The port a host listens on when it can, so joining by address only needs the IP. */
const val DEFAULT_PORT = 47474

/** Connects to a host at [address]:[port]. */
suspend fun connectTo(address: String, port: Int): Socket = withContext(Dispatchers.IO) {
    Socket().apply { connect(InetSocketAddress(address, port), 5000) }
}

/** Lists games hosted on the local network. */
class LanBrowser(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val found = MutableStateFlow<List<FoundGame>>(emptyList())
    val games: StateFlow<List<FoundGame>> = found
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var discovery: NsdManager.DiscoveryListener? = null

    fun start() {
        if (discovery != null) return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, error: Int) {}
            override fun onStopDiscoveryFailed(type: String, error: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                synchronized(pending) { pending.addLast(info) }
                resolveNext()
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                found.value = found.value.filterNot { it.name == info.serviceName }
            }
        }
        discovery = listener
        runCatching { nsd.discoverServices(NetProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    // NsdManager resolves one service at a time.
    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val next = synchronized(pending) {
            if (resolving) return
            pending.removeFirstOrNull()?.also { resolving = true }
        } ?: return
        nsd.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, error: Int) = done()
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress
                if (host != null && host !in localAddresses()) {
                    val game = FoundGame(info.serviceName, host, info.port)
                    found.value = found.value.filterNot { it.name == game.name } + game
                }
                done()
            }
            private fun done() {
                synchronized(pending) { resolving = false }
                resolveNext()
            }
        })
    }

    fun stop() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        found.value = emptyList()
    }
}
