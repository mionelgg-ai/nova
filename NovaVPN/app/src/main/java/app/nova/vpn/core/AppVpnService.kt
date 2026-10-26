package app.nova.vpn.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import app.nova.vpn.MainActivity
import app.nova.vpn.R
import app.nova.vpn.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

class AppVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tickJob: Job? = null
    private var lastTick = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVpn()
            ACTION_START -> {
                val config = intent.getStringExtra(EXTRA_CONFIG)
                val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
                if (config == null) stopVpn() else startVpn(config, name)
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpn(config: String, serverName: String) {
        VpnState.status.value = VpnStatus.Connecting
        startInForeground(serverName)

        scope.launch {
            try {
                val builder = Builder()
                    .setSession(getString(R.string.app_name))
                    .setMtu(1500)
                    .addAddress("172.19.0.1", 30)
                    .addAddress("fdfe:dcba:9876::1", 126)
                    .addRoute("0.0.0.0", 0)
                    .addRoute("::", 0)
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")
                // Xray's own outbound sockets must not loop back into the tunnel.
                builder.addDisallowedApplication(packageName)

                val pfd = builder.establish() ?: error("VPN permission revoked")
                tun = pfd
                // Xray gets its own duplicate of the fd; we keep ours so we can always close the interface.
                val fd = ParcelFileDescriptor.dup(pfd.fileDescriptor).detachFd()
                XrayCore.start(config, fd)

                VpnState.status.value = VpnStatus.Connected(System.currentTimeMillis())
                startTicker()
            } catch (e: Throwable) {
                stopVpn()
            }
        }
    }

    private fun startTicker() {
        lastTick = System.currentTimeMillis()
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                delay(5_000)
                flushSeconds()
            }
        }
    }

    private fun flushSeconds() {
        val now = System.currentTimeMillis()
        if (lastTick > 0) Prefs.addSeconds(LocalDate.now().toString(), (now - lastTick) / 1000)
        lastTick = now
    }

    private fun stopVpn() {
        tickJob?.cancel()
        if (lastTick > 0) flushSeconds()
        lastTick = 0
        XrayCore.stop()
        runCatching { tun?.close() }
        tun = null
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

    // ---------------------------------------------------------------- notification

    private fun startInForeground(serverName: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AppVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_connected, serverName))
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_disconnect), stop)
            .setOngoing(true)
            .setColor(0xFFB6FF1A.toInt())
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    companion object {
        const val ACTION_START = "app.nova.vpn.START"
        const val ACTION_STOP = "app.nova.vpn.STOP"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_NAME = "name"
        private const val CHANNEL = "vpn"
        private const val NOTIF_ID = 1
    }
}
