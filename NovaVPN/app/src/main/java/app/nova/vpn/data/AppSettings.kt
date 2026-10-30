package app.nova.vpn.data

import android.content.Context
import android.content.SharedPreferences
import app.nova.vpn.Cfg
import kotlin.random.Random

/** Everything on the Settings screen. One place for keys + defaults. */
object AppSettings {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("nova_settings", Context.MODE_PRIVATE)
    }

    object K {
        const val MODE = "mode"                       // "vpn" | "proxy"
        const val IPV6 = "ipv6"
        const val PREFER_IPV6 = "prefer_ipv6"
        const val LOCAL_DNS = "local_dns"
        const val FAKE_DNS = "fake_dns"
        const val VPN_DNS = "vpn_dns"
        const val HTTP_TO_VPN = "http_to_vpn"
        const val BYPASS_LAN = "bypass_lan"
        const val VPN_ADDRESS = "vpn_address"
        const val MTU = "mtu"
        const val SNIFFING = "sniffing"
        const val ROUTE_ONLY = "route_only"
        const val LOCAL_PROXY = "local_proxy"
        const val ALLOW_LAN = "allow_lan"
        const val DYNAMIC_PORT = "dynamic_port"
        const val SOCKS_PORT = "socks_port"
        const val HTTP_PORT = "http_port"
        const val PROXY_USER = "proxy_user"
        const val PROXY_PASS = "proxy_pass"
        const val SOCKS_UDP = "socks_udp"
        const val REMOTE_DNS = "remote_dns"
        const val DIRECT_DNS = "direct_dns"
        const val DNS_HOSTS = "dns_hosts"
        const val LOG_LEVEL = "log_level"
        const val RESOLVE_OUTBOUND = "resolve_outbound"
        const val MUX = "mux"
        const val MUX_CONCURRENCY = "mux_concurrency"
        const val XUDP_CONCURRENCY = "xudp_concurrency"
        const val XUDP_QUIC = "xudp_quic"
        const val FRAGMENT = "fragment"
        const val FRAG_PACKETS = "frag_packets"
        const val FRAG_LENGTH = "frag_length"
        const val FRAG_INTERVAL = "frag_interval"
        const val OBSERVATORY = "observatory"
        const val OBS_URL = "obs_url"
        const val OBS_INTERVAL = "obs_interval"
        const val BOOT = "connect_on_boot"
        const val PROBE_URL = "probe_url"
        const val PER_APP = "per_app"
        const val PER_APP_MODE = "per_app_mode"       // "bypass" | "proxy"
        const val BYPASS_IRAN = "bypass_iran"
    }

    private val defaults: Map<String, Any> = mapOf(
        K.MODE to "vpn",
        K.IPV6 to false, K.PREFER_IPV6 to false,
        K.LOCAL_DNS to false, K.FAKE_DNS to false, K.VPN_DNS to "1.1.1.1",
        K.HTTP_TO_VPN to false, K.BYPASS_LAN to true, K.VPN_ADDRESS to "10.10.14", K.MTU to "1500",
        K.SNIFFING to true, K.ROUTE_ONLY to false,
        K.LOCAL_PROXY to false, K.ALLOW_LAN to false, K.DYNAMIC_PORT to false,
        K.SOCKS_PORT to "10808", K.HTTP_PORT to "10809", K.PROXY_USER to "", K.PROXY_PASS to "",
        K.SOCKS_UDP to true,
        K.REMOTE_DNS to "https://cloudflare-dns.com/dns-query", K.DIRECT_DNS to "223.5.5.5",
        K.DNS_HOSTS to "", K.LOG_LEVEL to "warning", K.RESOLVE_OUTBOUND to true,
        K.MUX to false, K.MUX_CONCURRENCY to "8", K.XUDP_CONCURRENCY to "8", K.XUDP_QUIC to "reject",
        K.FRAGMENT to false, K.FRAG_PACKETS to "tlshello", K.FRAG_LENGTH to "50-100", K.FRAG_INTERVAL to "10-20",
        K.OBSERVATORY to false, K.OBS_URL to "https://www.gstatic.com/generate_204", K.OBS_INTERVAL to "1m",
        K.BOOT to false, K.PROBE_URL to Cfg.PROBE_URL,
        K.PER_APP to false, K.PER_APP_MODE to "bypass", K.BYPASS_IRAN to true,
    )

    fun bool(key: String): Boolean = sp.getBoolean(key, defaults[key] as? Boolean ?: false)
    fun str(key: String): String = sp.getString(key, null) ?: (defaults[key] as? String ?: "")
    fun putBool(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    fun putStr(key: String, v: String) = sp.edit().putString(key, v).apply()

    private fun int(key: String, fallback: Int, range: IntRange): Int =
        (str(key).trim().toIntOrNull() ?: fallback).coerceIn(range)

    // ---- convenience for the VPN service / boot receiver
    val vpnMode get() = str(K.MODE) != "proxy"
    val ipv6 get() = bool(K.IPV6)
    val mtu get() = int(K.MTU, 1500, 576..9000)
    val bypassLan get() = bool(K.BYPASS_LAN)
    val vpnAddress get() = str(K.VPN_ADDRESS)
    val vpnDns get() = str(K.VPN_DNS).trim().ifBlank { "1.1.1.1" }
    val httpToVpn get() = bool(K.HTTP_TO_VPN)
    val perApp get() = bool(K.PER_APP)
    val perAppMode get() = str(K.PER_APP_MODE)
    val connectOnBoot get() = bool(K.BOOT)
    val probeUrl get() = str(K.PROBE_URL).trim().ifBlank { Cfg.PROBE_URL }
    val resolveOutbound get() = bool(K.RESOLVE_OUTBOUND)

    /** Snapshot used to build one Xray config. Ports are randomised here when "dynamic port" is on. */
    fun coreOptions(geoReady: Boolean): CoreOptions {
        val vpn = vpnMode
        val localProxy = bool(K.LOCAL_PROXY) || !vpn            // proxy mode needs the local proxy
        val dynamic = bool(K.DYNAMIC_PORT)
        val socks = if (dynamic) Random.nextInt(20000, 60000) else int(K.SOCKS_PORT, 10808, 1024..65535)
        var http = if (dynamic) socks + 1 else int(K.HTTP_PORT, 10809, 1024..65535)
        if (http == socks) http = if (socks < 65535) socks + 1 else socks - 1
        val localDns = bool(K.LOCAL_DNS)
        return CoreOptions(
            vpnMode = vpn, mtu = mtu,
            sniffing = bool(K.SNIFFING), routeOnly = bool(K.ROUTE_ONLY),
            localProxy = localProxy, allowLan = bool(K.ALLOW_LAN), socksPort = socks, httpPort = http,
            proxyUser = str(K.PROXY_USER).trim(), proxyPass = str(K.PROXY_PASS),
            socksUdp = bool(K.SOCKS_UDP),
            localDns = localDns, fakeDns = localDns && bool(K.FAKE_DNS),
            remoteDns = str(K.REMOTE_DNS).trim().ifBlank { "https://cloudflare-dns.com/dns-query" },
            directDns = str(K.DIRECT_DNS).trim(),
            hosts = parseHosts(str(K.DNS_HOSTS)),
            logLevel = str(K.LOG_LEVEL), preferIpv6 = bool(K.PREFER_IPV6),
            mux = bool(K.MUX), muxConcurrency = int(K.MUX_CONCURRENCY, 8, 1..1024),
            xudpConcurrency = int(K.XUDP_CONCURRENCY, 8, 1..1024), xudpQuic = str(K.XUDP_QUIC),
            fragment = bool(K.FRAGMENT), fragPackets = str(K.FRAG_PACKETS).trim().ifBlank { "tlshello" },
            fragLength = str(K.FRAG_LENGTH).trim().ifBlank { "50-100" },
            fragInterval = str(K.FRAG_INTERVAL).trim().ifBlank { "10-20" },
            observatory = bool(K.OBSERVATORY), obsUrl = str(K.OBS_URL).trim(), obsInterval = str(K.OBS_INTERVAL).trim().ifBlank { "1m" },
            bypassIran = bool(K.BYPASS_IRAN) && geoReady,
        )
    }

    /** "domain:address,domain2:address2" -> map (addresses may be IPv6, so split on the first ':' only). */
    private fun parseHosts(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (part in text.split(',', '\n')) {
            val p = part.trim()
            val i = p.indexOf(':')
            if (i <= 0 || i == p.length - 1) continue
            out[p.substring(0, i).trim()] = p.substring(i + 1).trim()
        }
        return out
    }
}

class CoreOptions(
    val vpnMode: Boolean, val mtu: Int,
    val sniffing: Boolean, val routeOnly: Boolean,
    val localProxy: Boolean, val allowLan: Boolean, val socksPort: Int, val httpPort: Int,
    val proxyUser: String, val proxyPass: String, val socksUdp: Boolean,
    val localDns: Boolean, val fakeDns: Boolean, val remoteDns: String, val directDns: String,
    val hosts: Map<String, String>, val logLevel: String, val preferIpv6: Boolean,
    val mux: Boolean, val muxConcurrency: Int, val xudpConcurrency: Int, val xudpQuic: String,
    val fragment: Boolean, val fragPackets: String, val fragLength: String, val fragInterval: String,
    val observatory: Boolean, val obsUrl: String, val obsInterval: String,
    val bypassIran: Boolean,
)
