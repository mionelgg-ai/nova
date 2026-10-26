package app.nova.vpn.core

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.nova.vpn.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick Settings tile: tap to connect / disconnect from the notification shade. */
class VpnTileService : TileService() {
    private var scope: CoroutineScope? = null
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val s = CoroutineScope(Dispatchers.Main)
        scope = s
        job = s.launch { VpnState.status.collect { refresh(it) } }
    }

    override fun onStopListening() {
        job?.cancel()
        scope?.cancel()
        super.onStopListening()
    }

    private fun refresh(status: VpnStatus) {
        val tile = qsTile ?: return
        tile.state = when (status) {
            is VpnStatus.Connected -> Tile.STATE_ACTIVE
            VpnStatus.Connecting -> Tile.STATE_ACTIVE
            VpnStatus.Disconnected -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (VpnState.status.value !is VpnStatus.Disconnected) {
            VpnController.stop(this)
            return
        }
        // Connecting needs the VPN permission dialog / ad, so hand over to the app.
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("auto_connect", true)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(this, 5, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
