package app.nova.vpn.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import app.nova.vpn.data.AppSettings
import app.nova.vpn.data.ConfigRepository
import app.nova.vpn.data.Prefs
import kotlinx.coroutines.runBlocking

/** "Connect after device boot": starts the last selected server if the VPN permission was granted before. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!AppSettings.connectOnBoot || !Prefs.signedIn) return
        val pending = goAsync()
        Thread {
            try {
                // without a prior VPN permission grant (or in proxy mode) there is nothing to show here
                val needsPermission = AppSettings.vpnMode && VpnService.prepare(context) != null
                if (!needsPermission) {
                    runBlocking { ConfigRepository.load(context) }
                    val plan = ConnectPlanner.build(Prefs.selectedServer)
                    if (plan.candidates.isNotEmpty()) VpnController.start(context, plan.candidates, plan.httpPort)
                }
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}
