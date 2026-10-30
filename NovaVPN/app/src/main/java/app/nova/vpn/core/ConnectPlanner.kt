package app.nova.vpn.core

import app.nova.vpn.Cfg
import app.nova.vpn.data.AppSettings
import app.nova.vpn.data.ConfigRepository
import app.nova.vpn.data.accessible
import java.net.InetAddress

class Plan(val candidates: List<Candidate>, val httpPort: Int)

/**
 * Turns "the selected server" + the user's settings into the list of Xray configs the VPN service tries.
 * Does blocking work (key derivation, optional DNS lookup): always call it off the main thread.
 */
object ConnectPlanner {
    fun build(selectedId: String?): Plan {
        val cfg = ConfigRepository.config.value ?: return Plan(emptyList(), 0)
        val premium = ConfigRepository.premium.value.active
        val usable = cfg.servers.filter { !it.inMaintenance && it.accessible(premium, false) }
        if (usable.isEmpty()) return Plan(emptyList(), 0)

        val first = usable.firstOrNull { it.id == selectedId } ?: usable.first()
        val ordered = (listOf(first) + usable.filter { it.id != first.id }).take(Cfg.MAX_CANDIDATES)
        val options = AppSettings.coreOptions(XrayCore.geoReady)

        val candidates = ordered.mapNotNull { s ->
            val link = ConfigRepository.linkFor(s) ?: return@mapNotNull null
            val resolved = if (AppSettings.resolveOutbound) resolve(link) else emptyMap()
            val built = runCatching { XrayConfig.fromLink(link, options, resolved) }.getOrNull() ?: return@mapNotNull null
            Candidate(s.id, s.name, built)
        }
        return Plan(candidates, options.httpPort)
    }

    /** "Outbound domain resolution": look the server name up now and hand the IP to Xray's DNS hosts. */
    private fun resolve(link: String): Map<String, String> {
        val host = XrayConfig.hostOf(link) ?: return emptyMap()
        if (NetUtil.isIpLiteral(host)) return emptyMap()
        return runCatching {
            val all = InetAddress.getAllByName(host)
            val ip = (all.firstOrNull { it.address.size == 4 } ?: all.first()).hostAddress
            if (ip == null) emptyMap() else mapOf(host to ip)
        }.getOrDefault(emptyMap())
    }
}
