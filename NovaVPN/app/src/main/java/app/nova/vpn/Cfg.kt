package app.nova.vpn

/**
 * The only file you must edit before release.
 */
object Cfg {
    /** Raw URL(s) of servers.json. The app tries them in order (add a mirror as 2nd item). */
    val CONFIG_URLS = listOf(
        "https://raw.githubusercontent.com/YOUR_USER/YOUR_REPO/main/servers.json"
    )

    /** Must be IDENTICAL to "appSecret" in tools/servers.source.json */
    const val APP_SECRET = "CHANGE_ME_TO_RANDOM_STRING"

    /** AdMob TEST interstitial id. Replace with your real unit id (and the app id in the manifest). */
    const val ADMOB_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"

    /** Used if servers.json has no "telegram" value. */
    const val DEFAULT_TELEGRAM = "https://t.me/YOUR_CHANNEL"

    /** Premium users skip the pre-connect ad. */
    const val SKIP_ADS_FOR_PREMIUM = true

    /** How long to wait for an ad to load before connecting anyway (ms). */
    const val AD_WAIT_MS = 4000L

    /** Fake ping ranges (ms), purely cosmetic. */
    val FREE_PING = 89..200
    val PREMIUM_PING = 69..140
}
