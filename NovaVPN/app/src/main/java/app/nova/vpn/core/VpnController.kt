package app.nova.vpn.core

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object VpnController {
    fun start(context: Context, built: XrayConfig.Built, serverName: String) {
        VpnState.protocol.value = built.protocol
        val i = Intent(context, AppVpnService::class.java)
            .setAction(AppVpnService.ACTION_START)
            .putExtra(AppVpnService.EXTRA_CONFIG, built.json)
            .putExtra(AppVpnService.EXTRA_NAME, serverName)
        ContextCompat.startForegroundService(context, i)
    }

    fun stop(context: Context) {
        context.startService(Intent(context, AppVpnService::class.java).setAction(AppVpnService.ACTION_STOP))
    }
}
