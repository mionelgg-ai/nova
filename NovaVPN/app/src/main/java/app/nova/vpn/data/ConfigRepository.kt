package app.nova.vpn.data

import android.content.Context
import app.nova.vpn.Cfg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate

object ConfigRepository {
    private val _config = MutableStateFlow<RemoteConfig?>(null)
    val config: StateFlow<RemoteConfig?> = _config

    private val _premium = MutableStateFlow(PremiumState())
    val premium: StateFlow<PremiumState> = _premium

    private var freeKeyCache: Pair<String, ByteArray>? = null

    /** Cached copy first, bundled assets/servers.json as a last resort. */
    suspend fun load(context: Context) = withContext(Dispatchers.Default) {
        val cached = Prefs.cachedConfig
        val cfg = cached?.let { runCatching { parse(it) }.getOrNull() }
            ?: runCatching {
                parse(context.assets.open("servers.json").bufferedReader().use { it.readText() })
            }.getOrNull()
        _config.value = cfg
        revalidate()
    }

    /** Downloads servers.json. Returns true if a valid file was fetched. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        for (url in Cfg.CONFIG_URLS) {
            val text = runCatching { download(url) }.getOrNull() ?: continue
            val parsed = runCatching { parse(text) }.getOrNull() ?: continue
            Prefs.cachedConfig = text
            _config.value = parsed
            revalidate()
            return@withContext true
        }
        false
    }

    private fun download(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (c.responseCode != 200) error("http ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun parse(text: String): RemoteConfig {
        val o = JSONObject(text)
        val salt = android.util.Base64.decode(o.getString("salt"), android.util.Base64.DEFAULT)

        val servers = buildList {
            val arr = o.getJSONArray("servers")
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                add(
                    Server(
                        id = s.getString("id"),
                        name = s.getString("name"),
                        nameFa = s.optString("nameFa", ""),
                        cc = s.getString("cc"),
                        city = s.optString("city", ""),
                        premium = s.optBoolean("premium", false),
                        linkEnc = s.getString("link"),
                    )
                )
            }
        }
        val codes = buildList {
            val arr = o.optJSONArray("codes")
            if (arr != null) for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                add(CodeEntry(c.getString("id"), c.getString("wrapped"), c.optString("expires", "")))
            }
        }
        val offer = o.optJSONObject("offer")?.let {
            val ends = runCatching { Instant.parse(it.getString("endsAt")).toEpochMilli() }.getOrDefault(0L)
            Offer(
                it.optString("title"), it.optString("titleFa"),
                it.optString("text"), it.optString("textFa"),
                it.optString("price"), ends,
            )
        }
        return RemoteConfig(
            salt = salt,
            telegram = o.optString("telegram").ifBlank { Cfg.DEFAULT_TELEGRAM },
            offer = offer,
            servers = servers,
            codes = codes,
        )
    }

    // ------------------------------------------------------------------ premium

    suspend fun activate(rawCode: String): ActivateResult = withContext(Dispatchers.Default) {
        val cfg = _config.value ?: return@withContext ActivateResult.Invalid
        val r = tryCode(cfg, rawCode)
        if (r is ActivateResult.Ok) {
            Prefs.premiumCode = Crypto.normalize(rawCode)
            _premium.value = PremiumState(true, r.expires, r.key)
        }
        r
    }

    /** Re-checks the saved code against the current config (revoked / expired codes stop working). */
    suspend fun revalidate() = withContext(Dispatchers.Default) {
        val cfg = _config.value
        val code = Prefs.premiumCode
        if (cfg == null || code == null) {
            _premium.value = PremiumState()
            return@withContext
        }
        _premium.value = when (val r = tryCode(cfg, code)) {
            is ActivateResult.Ok -> PremiumState(true, r.expires, r.key)
            else -> PremiumState()
        }
    }

    private fun tryCode(cfg: RemoteConfig, raw: String): ActivateResult {
        val code = Crypto.normalize(raw)
        if (code.isEmpty()) return ActivateResult.Invalid
        val entry = cfg.codes.firstOrNull { it.id == Crypto.codeId(code) } ?: return ActivateResult.Invalid
        if (entry.expires.isNotBlank()) {
            val exp = runCatching { LocalDate.parse(entry.expires) }.getOrNull()
            if (exp != null && exp.isBefore(LocalDate.now())) return ActivateResult.Expired
        }
        val key = try {
            Crypto.decrypt(entry.wrapped, Crypto.codeKey(code, cfg.salt))
        } catch (e: Exception) {
            return ActivateResult.Invalid
        }
        return ActivateResult.Ok(entry.expires.ifBlank { null }, key)
    }

    /** Decrypts the share link of [server]. Null if premium and locked. */
    fun linkFor(server: Server): String? {
        val cfg = _config.value ?: return null
        val key = if (server.premium) {
            _premium.value.key ?: return null
        } else {
            val saltKey = android.util.Base64.encodeToString(cfg.salt, android.util.Base64.NO_WRAP)
            freeKeyCache?.takeIf { it.first == saltKey }?.second
                ?: Crypto.freeKey(cfg.salt).also { freeKeyCache = saltKey to it }
        }
        return runCatching { String(Crypto.decrypt(server.linkEnc, key)) }.getOrNull()
    }
}
