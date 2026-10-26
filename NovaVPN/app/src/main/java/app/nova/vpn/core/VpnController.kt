package app.nova.vpn.core

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class Candidate(val id: String, val name: String, val built: XrayConfig.Built)

object VpnController {
    /** [candidates] are tried in order; the first one that works is used (automatic fallback). */
    fun start(context: Context, candidates: List<Candidate>, splitApps: Set<String>) {
        val i = Intent(context, AppVpnService::class.java)
            .setAction(AppVpnService.ACTION_START)
            .putStringArrayListExtra(AppVpnService.EXTRA_CONFIGS, ArrayList(candidates.map { it.built.json }))
            .putStringArrayListExtra(AppVpnService.EXTRA_NAMES, ArrayList(candidates.map { it.name }))
            .putStringArrayListExtra(AppVpnService.EXTRA_IDS, ArrayList(candidates.map { it.id }))
            .putStringArrayListExtra(AppVpnService.EXTRA_PROTOS, ArrayList(candidates.map { it.built.protocol }))
            .putStringArrayListExtra(AppVpnService.EXTRA_SPLIT, ArrayList(splitApps))
        ContextCompat.startForegroundService(context, i)
    }

    fun stop(context: Context) {
        context.startService(Intent(context, AppVpnService::class.java).setAction(AppVpnService.ACTION_STOP))
    }
}
