package app.nova.vpn.data

data class Server(
    val id: String,
    val name: String,
    val nameFa: String,
    val cc: String,
    val city: String,
    val premium: Boolean,
    /** unlocked for a while after watching a rewarded ad */
    val bonus: Boolean,
    /** "", "new", "busy", "maintenance" */
    val tag: String,
    val linkEnc: String,
) {
    fun displayName(fa: Boolean) = if (fa && nameFa.isNotBlank()) nameFa else name
    val inMaintenance get() = tag == "maintenance"
}

fun Server.accessible(premiumActive: Boolean, bonusActive: Boolean): Boolean = when {
    premium -> premiumActive
    bonus -> premiumActive || bonusActive
    else -> true
}

data class Offer(
    val title: String,
    val titleFa: String,
    val text: String,
    val textFa: String,
    val price: String,
    val endsAtMillis: Long,
)

data class Notice(val id: String, val text: String, val textFa: String, val url: String) {
    fun localized(fa: Boolean) = if (fa && textFa.isNotBlank()) textFa else text
}

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val force: Boolean,
    val text: String,
    val textFa: String,
) {
    fun localized(fa: Boolean) = if (fa && textFa.isNotBlank()) textFa else text
}

data class CodeEntry(val id: String, val wrapped: String, val expires: String)

class RemoteConfig(
    val rev: Long,
    val salt: ByteArray,
    val telegram: String,
    val shareText: String,
    val bonusMinutes: Int,
    val offer: Offer?,
    val notice: Notice?,
    val update: UpdateInfo?,
    val servers: List<Server>,
    val codes: List<CodeEntry>,
)

class PremiumState(
    val active: Boolean = false,
    val expires: String? = null,
    val key: ByteArray? = null,
)

sealed interface ActivateResult {
    class Ok(val expires: String?, val key: ByteArray) : ActivateResult
    object Invalid : ActivateResult
    object Expired : ActivateResult
}

/** "CA" -> 🇨🇦 (works for any ISO country code, so you only ever edit the code in servers.json) */
fun flagEmoji(cc: String): String {
    if (cc.length != 2) return "🌐"
    return cc.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}
