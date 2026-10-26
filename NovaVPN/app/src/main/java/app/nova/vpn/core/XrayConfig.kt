package app.nova.vpn.core

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** Turns a vless:// vmess:// trojan:// share link into a full Xray config (tun inbound). */
object XrayConfig {
    class Built(val protocol: String, val json: String)

    fun fromLink(link: String): Built {
        val l = link.trim()
        val (protocol, outbound) = when {
            l.startsWith("vless://") -> "VLESS" to vless(l)
            l.startsWith("vmess://") -> "VMess" to vmess(l)
            l.startsWith("trojan://") -> "Trojan" to trojan(l)
            else -> throw IllegalArgumentException("Unsupported link")
        }

        val config = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put(
                "inbounds", JSONArray().put(
                    JSONObject()
                        .put("tag", "tun-in")
                        .put("protocol", "tun")
                        .put("settings", JSONObject().put("name", "xray0").put("MTU", 1500).put("userLevel", 0))
                        .put(
                            "sniffing", JSONObject()
                                .put("enabled", true)
                                .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
                        )
                )
            )
            .put(
                "outbounds", JSONArray()
                    .put(outbound)
                    .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
                    .put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
            )
            .put("dns", JSONObject().put("servers", JSONArray().put("1.1.1.1").put("8.8.8.8")))
            .put("routing", JSONObject().put("domainStrategy", "AsIs"))
        return Built(protocol, config.toString())
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
