package app.nova.vpn.data

import android.content.Context
import app.nova.vpn.Cfg
import app.nova.vpn.core.ExpiryReminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    private var appContext: Context? = null

    /** Newest of: cached copy, bundled assets/servers.json. */
    suspend fun load(context: Context) = withContext(Dispatchers.Default) {
        appContext = context.applicationContext
        val cached = Prefs.cachedConfig?.let { runCatching { parse(it) }.getOrNull() }
        val bundled = runCatching {
            parse(context.assets.open("servers.json").bufferedReader().use { it.readText() })
        }.getOrNull()
        _config.value = listOfNotNull(cached, bundled).maxByOrNull { it.rev }
        revalidate()
    }

    /** Downloads servers.json from every URL in parallel and keeps the newest. True if any source answered. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val results = coroutineScope {
            Cfg.CONFIG_URLS.map { url ->
                async { runCatching { download(url).let { it to parse(it) } }.getOrNull() }
            }.awaitAll()
        }.filterNotNull()

        val best = results.maxByOrNull { it.second.rev } ?: return@withContext false
        val current = _config.value
        if (current == null || best.second.rev >= current.rev) {
            Prefs.cachedConfig = best.first
            _config.value = best.second
            revalidate()
        }
        true
    }

    private fun download(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 6000
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
                        bonus = s.optBoolean("bonus", false),
                        tag = s.optString("tag", ""),
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
        val notice = o.optJSONObject("notice")?.let {
            val text0 = it.optString("text")
            if (text0.isBlank() && it.optString("textFa").isBlank()) null
            else Notice(it.optString("id", text0.hashCode().toString()), text0, it.optString("textFa"), it.optString("url"))
        }
        val update = o.optJSONObject("update")?.let {
            UpdateInfo(
                versionCode = it.optInt("version", 0),
                versionName = it.optString("name", ""),
                url = it.optString("url"),
                force = it.optBoolean("force", false),
                text = it.optString("text"),
                textFa = it.optString("textFa"),
            )
        }
        return RemoteConfig(
            rev = o.optLong("rev", 0L),
            salt = salt,
            telegram = o.optString("telegram").ifBlank { Cfg.DEFAULT_TELEGRAM },
            shareText = o.optString("shareText"),
            bonusMinutes = o.optInt("bonusMinutes", Cfg.BONUS_MINUTES),
            offer = offer,
            notice = notice,
            update = update,
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
            appContext?.let { ExpiryReminder.schedule(it, r.expires) }
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
        appContext?.let { ExpiryReminder.schedule(it, _premium.value.expires) }
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

    /** Decrypts the share link of [server]. Null if premium and locked. Bonus servers use the free key. */
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
