package app.nova.vpn.core

import android.net.Uri
import android.util.Base64
import app.nova.vpn.data.CoreOptions
import org.json.JSONArray
import org.json.JSONObject

/** Turns a vless:// vmess:// trojan:// share link + the user's settings into a full Xray config. */
object XrayConfig {
    class Built(val protocol: String, val json: String)

    /** The server host inside a share link (used to pre-resolve it). */
    fun hostOf(link: String): String? = runCatching {
        val l = link.trim()
        if (l.startsWith("vmess://")) {
            val body = l.removePrefix("vmess://").trim().replace('-', '+').replace('_', '/')
            JSONObject(String(Base64.decode(body, Base64.DEFAULT))).optString("add").ifBlank { null }
        } else Uri.parse(l).host
    }.getOrNull()

    fun fromLink(link: String, o: CoreOptions, resolvedHosts: Map<String, String> = emptyMap()): Built {
        val l = link.trim()
        val (protocol, outbound) = when {
            l.startsWith("vless://") -> "VLESS" to vless(l)
            l.startsWith("vmess://") -> "VMess" to vmess(l)
            l.startsWith("trojan://") -> "Trojan" to trojan(l)
            else -> throw IllegalArgumentException("Unsupported link")
        }
        applyOutboundOptions(outbound, o)

        // ---- inbounds
        val destOverride = JSONArray().put("http").put("tls").put("quic")
        if (o.fakeDns) destOverride.put("fakedns")
        fun sniffing(): JSONObject {
            val s = JSONObject().put("enabled", o.sniffing)
            if (o.sniffing) s.put("destOverride", destOverride).put("routeOnly", o.routeOnly)
            return s
        }

        val inbounds = JSONArray()
        if (o.vpnMode) {
            inbounds.put(
                JSONObject()
                    .put("tag", "tun-in").put("protocol", "tun")
                    .put("settings", JSONObject().put("name", "xray0").put("MTU", o.mtu).put("userLevel", 0))
                    .put("sniffing", sniffing())
            )
        }
        if (o.localProxy) {
            val listen = if (o.allowLan) "0.0.0.0" else "127.0.0.1"
            val auth = o.proxyUser.isNotEmpty() && o.proxyPass.isNotEmpty()
            val accounts = JSONArray().put(JSONObject().put("user", o.proxyUser).put("pass", o.proxyPass))
            val socks = JSONObject().put("udp", o.socksUdp).put("userLevel", 0)
            if (auth) socks.put("auth", "password").put("accounts", accounts) else socks.put("auth", "noauth")
            inbounds.put(
                JSONObject().put("tag", "socks-in").put("protocol", "socks").put("listen", listen)
                    .put("port", o.socksPort).put("settings", socks).put("sniffing", sniffing())
            )
            val http = JSONObject().put("userLevel", 0)
            if (auth) http.put("accounts", accounts)
            inbounds.put(
                JSONObject().put("tag", "http-in").put("protocol", "http").put("listen", listen)
                    .put("port", o.httpPort).put("settings", http).put("sniffing", sniffing())
            )
        }

        // ---- outbounds
        val outbounds = JSONArray()
            .put(outbound)
            .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
            .put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
        if (o.fragment) {
            outbounds.put(
                JSONObject().put("tag", "fragment").put("protocol", "freedom").put(
                    "settings", JSONObject().put(
                        "fragment", JSONObject().put("packets", o.fragPackets).put("length", o.fragLength).put("interval", o.fragInterval)
                    )
                )
            )
        }
        if (o.localDns) outbounds.put(JSONObject().put("tag", "dns-out").put("protocol", "dns"))

        // ---- routing
        val rules = JSONArray()
        if (o.localDns) {
            rules.put(JSONObject().put("type", "field").put("port", "53").put("outboundTag", "dns-out"))
        }
        if (o.bypassIran) {
            rules.put(JSONObject().put("type", "field").put("domain", JSONArray().put("domain:ir")).put("outboundTag", "direct"))
            rules.put(JSONObject().put("type", "field").put("ip", JSONArray().put("geoip:ir")).put("outboundTag", "direct"))
        }

        // ---- dns
        val servers = JSONArray()
        if (o.fakeDns) servers.put("fakedns")
        servers.put(o.remoteDns)
        if (o.localDns && o.directDns.isNotEmpty()) {
            servers.put(
                JSONObject().put("address", o.directDns).put("domains", JSONArray().put("domain:ir")).put("skipFallback", true)
            )
        }
        val hosts = JSONObject()
        // keep the well-known DoH names resolvable without asking DNS (avoids a chicken-and-egg problem)
        hosts.put("cloudflare-dns.com", JSONArray().put("1.1.1.1").put("1.0.0.1"))
        hosts.put("dns.google", JSONArray().put("8.8.8.8").put("8.8.4.4"))
        hosts.put("dns.alidns.com", JSONArray().put("223.5.5.5").put("223.6.6.6"))
        for ((k, v) in resolvedHosts) hosts.put(k, v)
        for ((k, v) in o.hosts) hosts.put(k, v)
        val dns = JSONObject().put("hosts", hosts).put("servers", servers)
            .put("queryStrategy", if (o.preferIpv6) "UseIP" else "UseIPv4")

        val config = JSONObject()
            .put("log", JSONObject().put("loglevel", o.logLevel))
            .put("inbounds", inbounds)
            .put("outbounds", outbounds)
            .put("dns", dns)
            .put("routing", JSONObject().put("domainStrategy", "IPIfNonMatch").put("rules", rules))
        if (o.fakeDns) {
            config.put("fakedns", JSONArray().put(JSONObject().put("ipPool", "198.18.0.0/15").put("poolSize", 65535)))
        }
        if (o.observatory && o.obsUrl.isNotEmpty()) {
            config.put(
                "observatory", JSONObject().put("subjectSelector", JSONArray().put("proxy"))
                    .put("probeUrl", o.obsUrl).put("probeInterval", o.obsInterval)
            )
        }
        return Built(protocol, config.toString())
    }

    /** Mux and TLS fragment are properties of the proxy outbound. */
    private fun applyOutboundOptions(outbound: JSONObject, o: CoreOptions) {
        val hasFlow = outbound.toString().contains("\"flow\":\"")
        if (o.mux && !hasFlow) {
            outbound.put(
                "mux", JSONObject().put("enabled", true).put("concurrency", o.muxConcurrency)
                    .put("xudpConcurrency", o.xudpConcurrency).put("xudpProxyUDP443", o.xudpQuic)
            )
        }
        if (o.fragment) {
            val ss = outbound.optJSONObject("streamSettings")
            if (ss != null && ss.optString("security") != "none") {
                val sockopt = ss.optJSONObject("sockopt") ?: JSONObject()
                sockopt.put("dialerProxy", "fragment")
                ss.put("sockopt", sockopt)
            }
        }
    }

    // ---------------------------------------------------------------- protocols

    private fun vless(link: String): JSONObject {
        val u = Uri.parse(link)
        val p = params(u)
        val host = u.host ?: throw IllegalArgumentException("host")
        val user = JSONObject()
            .put("id", u.userInfo ?: throw IllegalArgumentException("id"))
            .put("encryption", p["encryption"] ?: "none")
        p["flow"]?.takeIf { it.isNotEmpty() }?.let { user.put("flow", it) }
        return JSONObject()
            .put("tag", "proxy").put("protocol", "vless")
            .put(
                "settings", JSONObject().put(
                    "vnext", JSONArray().put(
                        JSONObject().put("address", host).put("port", port(u))
                            .put("users", JSONArray().put(user))
                    )
                )
            )
            .put("streamSettings", stream(p["type"] ?: "tcp", p, host))
    }

    private fun trojan(link: String): JSONObject {
        val u = Uri.parse(link)
        val p = params(u).toMutableMap()
        val host = u.host ?: throw IllegalArgumentException("host")
        if (p["security"] == null) p["security"] = "tls"
        return JSONObject()
            .put("tag", "proxy").put("protocol", "trojan")
            .put(
                "settings", JSONObject().put(
                    "servers", JSONArray().put(
                        JSONObject().put("address", host).put("port", port(u))
                            .put("password", u.userInfo ?: throw IllegalArgumentException("password"))
                    )
                )
            )
            .put("streamSettings", stream(p["type"] ?: "tcp", p, host))
    }

    private fun vmess(link: String): JSONObject {
        val body = link.removePrefix("vmess://").trim().replace('-', '+').replace('_', '/')
        val j = JSONObject(String(Base64.decode(body, Base64.DEFAULT)))
        val host = j.getString("add")
        val p = mapOf(
            "security" to if (j.optString("tls") == "tls") "tls" else "none",
            "sni" to j.optString("sni"),
            "host" to j.optString("host"),
            "path" to j.optString("path"),
            "alpn" to j.optString("alpn"),
            "fp" to j.optString("fp"),
            "serviceName" to j.optString("path"),
        )
        val user = JSONObject()
            .put("id", j.getString("id"))
            .put("alterId", j.optString("aid", "0").toIntOrNull() ?: 0)
            .put("security", j.optString("scy").ifEmpty { "auto" })
        return JSONObject()
            .put("tag", "proxy").put("protocol", "vmess")
            .put(
                "settings", JSONObject().put(
                    "vnext", JSONArray().put(
                        JSONObject().put("address", host)
                            .put("port", j.optString("port", "443").toIntOrNull() ?: 443)
                            .put("users", JSONArray().put(user))
                    )
                )
            )
            .put("streamSettings", stream(j.optString("net", "tcp"), p, host))
    }

    // ---------------------------------------------------------------- transport

    private fun stream(net: String, p: Map<String, String>, address: String): JSONObject {
        val o = JSONObject().put("network", net)
        fun str(k: String) = p[k]?.takeIf { it.isNotEmpty() }

        when (net) {
            "ws" -> o.put(
                "wsSettings", JSONObject().put("path", str("path") ?: "/").apply {
                    str("host")?.let { put("headers", JSONObject().put("Host", it)) }
                }
            )
            "grpc" -> o.put(
                "grpcSettings", JSONObject()
                    .put("serviceName", str("serviceName") ?: "")
                    .put("multiMode", p["mode"] == "multi")
            )
            "httpupgrade" -> o.put(
                "httpupgradeSettings", JSONObject().put("path", str("path") ?: "/").put("host", str("host") ?: "")
            )
            "xhttp", "splithttp" -> {
                o.put("network", "xhttp")
                o.put(
                    "xhttpSettings", JSONObject()
                        .put("path", str("path") ?: "/").put("host", str("host") ?: "")
                        .put("mode", str("mode") ?: "auto")
                )
            }
        }

        when (val sec = p["security"] ?: "none") {
            "tls" -> {
                o.put("security", "tls")
                val tls = JSONObject()
                    .put("serverName", str("sni") ?: str("host") ?: address)
                    .put("fingerprint", str("fp") ?: "chrome")
                    .put("allowInsecure", p["allowInsecure"] == "1" || p["insecure"] == "1")
                str("alpn")?.let { tls.put("alpn", JSONArray(it.split(","))) }
                o.put("tlsSettings", tls)
            }
            "reality" -> {
                o.put("security", "reality")
                o.put(
                    "realitySettings", JSONObject()
                        .put("serverName", str("sni") ?: address)
                        .put("fingerprint", str("fp") ?: "chrome")
                        .put("publicKey", str("pbk") ?: "")
                        .put("shortId", str("sid") ?: "")
                        .put("spiderX", str("spx") ?: "")
                )
            }
            else -> o.put("security", if (sec == "none") "none" else sec)
        }
        return o
    }

    private fun params(u: Uri): Map<String, String> =
        u.queryParameterNames.associateWith { u.getQueryParameter(it).orEmpty() }

    private fun port(u: Uri) = if (u.port > 0) u.port else 443
}
