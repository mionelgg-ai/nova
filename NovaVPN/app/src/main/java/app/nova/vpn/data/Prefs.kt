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

    // ---- protected seconds per day ("2026-10-25" -> seconds)
    @Synchronized
    fun dailySeconds(): Map<String, Long> {
        val o = JSONObject(sp.getString("daily", "{}") ?: "{}")
        return o.keys().asSequence().associateWith { o.optLong(it) }
    }

    @Synchronized
    fun addSeconds(day: String, seconds: Long) {
        if (seconds <= 0) return
        val o = JSONObject(sp.getString("daily", "{}") ?: "{}")
        o.put(day, o.optLong(day) + seconds)
        // keep one year
        val cutoff = LocalDate.now().minusDays(365).toString()
        o.keys().asSequence().toList().filter { it < cutoff }.forEach { o.remove(it) }
        sp.edit().putString("daily", o.toString()).apply()
    }
}
