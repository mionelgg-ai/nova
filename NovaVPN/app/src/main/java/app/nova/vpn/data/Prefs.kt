package app.nova.vpn.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.time.LocalDate

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("nova", Context.MODE_PRIVATE)
    }

    var cachedConfig: String?
        get() = sp.getString("cfg", null)
        set(v) = sp.edit().putString("cfg", v).apply()

    var premiumCode: String?
        get() = sp.getString("code", null)
        set(v) = sp.edit().putString("code", v).apply()

    var selectedServer: String?
        get() = sp.getString("selected", null)
        set(v) = sp.edit().putString("selected", v).apply()

    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding", false)
        set(v) = sp.edit().putBoolean("onboarding", v).apply()

    var dismissedNotice: String?
        get() = sp.getString("notice_dismissed", null)
        set(v) = sp.edit().putString("notice_dismissed", v).apply()

    var dismissedUpdate: Int
        get() = sp.getInt("update_dismissed", 0)
        set(v) = sp.edit().putInt("update_dismissed", v).apply()

    var bypassIran: Boolean
        get() = sp.getBoolean("bypass_ir", true)
        set(v) = sp.edit().putBoolean("bypass_ir", v).apply()

    var splitApps: Set<String>
        get() = sp.getStringSet("split", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("split", HashSet(v)).apply()

    /** epoch millis until which bonus servers are unlocked */
    var bonusUntil: Long
        get() = sp.getLong("bonus_until", 0L)
        set(v) = sp.edit().putLong("bonus_until", v).apply()

    // ---- account (placeholder sign-in; becomes real when the backend exists)
    var signedIn: Boolean
        get() = sp.getBoolean("signed_in", false)
        set(v) = sp.edit().putBoolean("signed_in", v).apply()

    /** "google" | "email" */
    var authMethod: String
        get() = sp.getString("auth_method", "email") ?: "email"
        set(v) = sp.edit().putString("auth_method", v).apply()

    var userName: String
        get() = sp.getString("user_name", "") ?: ""
        set(v) = sp.edit().putString("user_name", v).apply()

    var userEmail: String
        get() = sp.getString("user_email", "") ?: ""
        set(v) = sp.edit().putString("user_email", v).apply()

    var avatarVersion: Int
        get() = sp.getInt("avatar_version", 0)
        set(v) = sp.edit().putInt("avatar_version", v).apply()

    fun signOut() {
        sp.edit().putBoolean("signed_in", false).putString("user_name", "").putString("user_email", "").apply()
    }

    var geoVersion: Int
        get() = sp.getInt("geo_version", 0)
        set(v) = sp.edit().putInt("geo_version", v).apply()

    // ---- per-day counters ("2026-10-25" -> value)
    @Synchronized
    private fun readMap(key: String): Map<String, Long> {
        val o = JSONObject(sp.getString(key, "{}") ?: "{}")
        return o.keys().asSequence().associateWith { o.optLong(it) }
    }

    @Synchronized
    private fun addToMap(key: String, day: String, value: Long) {
        if (value <= 0) return
        val o = JSONObject(sp.getString(key, "{}") ?: "{}")
        o.put(day, o.optLong(day) + value)
        val cutoff = LocalDate.now().minusDays(365).toString()
        o.keys().asSequence().toList().filter { it < cutoff }.forEach { o.remove(it) }
        sp.edit().putString(key, o.toString()).apply()
    }

    fun dailySeconds(): Map<String, Long> = readMap("daily")
    fun addSeconds(day: String, seconds: Long) = addToMap("daily", day, seconds)

    fun dailyTraffic(): Map<String, Long> = readMap("traffic")
    fun addTraffic(day: String, bytes: Long) = addToMap("traffic", day, bytes)
}
