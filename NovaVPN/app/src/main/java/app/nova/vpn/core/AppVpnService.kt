package app.nova.vpn.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.ProxyInfo
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.core.app.NotificationCompat
import app.nova.vpn.Cfg
import app.nova.vpn.MainActivity
import app.nova.vpn.R
import app.nova.vpn.data.AppSettings
import app.nova.vpn.data.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

class AppVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectJob: Job? = null
    private var tickJob: Job? = null
    private var restartJob: Job? = null

    private var lastTick = 0L
    private var lastRx = 0L
    private var lastTx = 0L

    private var activeConfig: String? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var lastNetwork: Network? = null
    private var restartNeeded = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVpn()
            ACTION_START -> {
                val configs = intent.getStringArrayListExtra(EXTRA_CONFIGS).orEmpty()
                val names = intent.getStringArrayListExtra(EXTRA_NAMES).orEmpty()
                val ids = intent.getStringArrayListExtra(EXTRA_IDS).orEmpty()
                val protos = intent.getStringArrayListExtra(EXTRA_PROTOS).orEmpty()
                val httpPort = intent.getIntExtra(EXTRA_HTTP_PORT, 10809)
                if (configs.isEmpty()) stopVpn() else startVpn(configs, names, ids, protos, httpPort)
            }
        }
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------ connect

    private fun startVpn(
        configs: List<String>, names: List<String>, ids: List<String>, protos: List<String>, httpPort: Int,
    ) {
        connectJob?.cancel()
        VpnState.status.value = VpnStatus.Connecting
        startInForeground(names.firstOrNull().orEmpty())

        connectJob = scope.launch {
            try {
                if (AppSettings.vpnMode && tun == null) tun = buildTun(httpPort) ?: error("VPN permission revoked")

                var chosen = -1
                for (i in configs.indices) {
                    if (startCore(configs[i]) && probe()) { chosen = i; break }
                    XrayCore.stop()
                }

                var unverified = false
                if (chosen < 0) {
                    if (Cfg.STRICT_PROBE) {
                        VpnState.events.tryEmit("all_failed")
                        stopVpn()
                        return@launch
                    }
                    // lenient: nothing could be verified -> use the first server anyway
                    chosen = 0
                    unverified = true
                    if (!startCore(configs[0])) {
                        VpnState.events.tryEmit("failed")
                        stopVpn()
                        return@launch
                    }
                }

                activeConfig = configs[chosen]
                VpnState.protocol.value = protos.getOrElse(chosen) { "" }
                VpnState.activeServerId.value = ids.getOrNull(chosen)
                if (chosen > 0) {
                    VpnState.events.tryEmit("switched:" + names.getOrElse(chosen) { "" })
                    updateNotification(names.getOrElse(chosen) { "" })
                }
                if (unverified) VpnState.events.tryEmit("unverified")

                registerNetworkCallback()
                VpnState.status.value = VpnStatus.Connected(System.currentTimeMillis())
                startTicker()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                VpnState.events.tryEmit("failed")
                stopVpn()
            }
        }
    }

    private fun buildTun(httpPort: Int): ParcelFileDescriptor? {
        val b = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(AppSettings.mtu)
            .addAddress(AppSettings.vpnAddress + ".1", 30)

        // routes: everything, or everything except the LAN ranges
        if (AppSettings.bypassLan) {
            for ((ip, prefix) in NetUtil.routesBypassingLan()) b.addRoute(ip, prefix)
        } else {
            b.addRoute("0.0.0.0", 0)
        }
        if (AppSettings.ipv6) {
            b.addAddress("fdfe:dcba:9876::1", 126)
            b.addRoute("::", 0)
        }

        // DNS the phone itself uses inside the VPN
        val dns = AppSettings.vpnDns
        if (!runCatching { b.addDnsServer(dns) }.isSuccess) b.addDnsServer("1.1.1.1")

        // per-app: either "these apps bypass the VPN" or "only these apps use the VPN"
        val apps = if (AppSettings.perApp) Prefs.splitApps else emptySet()
        if (AppSettings.perAppMode == "proxy" && apps.isNotEmpty()) {
            for (pkg in apps) runCatching { b.addAllowedApplication(pkg) }
        } else {
            // Xray's own sockets must never loop back into the tunnel
            b.addDisallowedApplication(packageName)
            for (pkg in apps) runCatching { b.addDisallowedApplication(pkg) }
        }

        // let browsers use the local HTTP proxy directly (Android 10+)
        if (Build.VERSION.SDK_INT >= 29 && AppSettings.httpToVpn && AppSettings.bool(AppSettings.K.LOCAL_PROXY)) {
            runCatching { b.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", httpPort)) }
        }
        return b.establish()
    }

    /** Starts Xray on a fresh duplicate of the tun fd. */
    private fun startCore(config: String): Boolean = runCatching {
        XrayCore.stop()
        val fd = if (AppSettings.vpnMode) ParcelFileDescriptor.dup(tun!!.fileDescriptor).detachFd() else 0
        XrayCore.start(config, fd)
    }.isSuccess

    /** Real request through the proxy; false if it fails or takes too long. */
    private suspend fun probe(): Boolean {
        val d = scope.async(Dispatchers.IO) {
            runCatching { XrayCore.measureDelay(AppSettings.probeUrl) }.getOrDefault(-1L)
        }
        val ms = withTimeoutOrNull(Cfg.PROBE_TIMEOUT_MS) { d.await() } ?: -1L
        return ms >= 0
    }

    // ------------------------------------------------------------------ network changes

    private fun registerNetworkCallback() {
        if (netCallback != null) return
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val prev = lastNetwork
                lastNetwork = network
                runCatching { setUnderlyingNetworks(arrayOf(network)) }
                if (restartNeeded || (prev != null && prev != network)) {
                    restartNeeded = false
                    scheduleRestart()
                }
            }

            override fun onLost(network: Network) {
                if (lastNetwork == network) {
                    lastNetwork = null
                    restartNeeded = true
                }
            }
        }
        netCallback = cb
        runCatching { cm.registerDefaultNetworkCallback(cb) }
    }

    /** Wi-Fi <-> mobile switch: restart the core so its sockets use the new network. */
    private fun scheduleRestart() {
        val cfg = activeConfig ?: return
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(1200)
            startCore(cfg)
        }
    }

    // ------------------------------------------------------------------ stats

    private fun startTicker() {
        val uid = Process.myUid()
        lastTick = System.currentTimeMillis()
        lastRx = TrafficStats.getUidRxBytes(uid)
        lastTx = TrafficStats.getUidTxBytes(uid)
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                delay(5_000)
                flushStats()
            }
        }
    }

    private fun flushStats() {
        val day = LocalDate.now().toString()
        val now = System.currentTimeMillis()
        if (lastTick > 0) Prefs.addSeconds(day, (now - lastTick) / 1000)
        lastTick = now

        val uid = Process.myUid()
        val rx = TrafficStats.getUidRxBytes(uid)
        val tx = TrafficStats.getUidTxBytes(uid)
        if (rx >= 0 && lastRx >= 0 && tx >= 0 && lastTx >= 0) {
            Prefs.addTraffic(day, (rx - lastRx).coerceAtLeast(0) + (tx - lastTx).coerceAtLeast(0))
        }
        lastRx = rx
        lastTx = tx
    }

    // ------------------------------------------------------------------ stop

    private fun stopVpn() {
        connectJob?.cancel()
        tickJob?.cancel()
        restartJob?.cancel()
        if (lastTick > 0) flushStats()
        lastTick = 0
        netCallback?.let { cb ->
            runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
        }
        netCallback = null
        lastNetwork = null
        restartNeeded = false
        activeConfig = null
        XrayCore.stop()
        runCatching { tun?.close() }
        tun = null
        VpnState.activeServerId.value = null
        VpnState.status.value = VpnStatus.Disconnected
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() = stopVpn()

    override fun onDestroy() {
        if (VpnState.status.value !is VpnStatus.Disconnected) stopVpn()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ notification

    private fun buildNotification(serverName: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AppVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_connected, serverName))
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_disconnect), stop)
            .setOngoing(true)
            .setColor(0xFFB6FF1A.toInt())
            .build()
    }

    private fun startInForeground(serverName: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n = buildNotification(serverName)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun updateNotification(serverName: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(serverName))
    }

    companion object {
        const val ACTION_START = "app.nova.vpn.START"
        const val ACTION_STOP = "app.nova.vpn.STOP"
        const val EXTRA_CONFIGS = "configs"
        const val EXTRA_NAMES = "names"
        const val EXTRA_IDS = "ids"
        const val EXTRA_PROTOS = "protos"
        const val EXTRA_HTTP_PORT = "http_port"
        private const val CHANNEL = "vpn"
        private const val NOTIF_ID = 1
    }
}
