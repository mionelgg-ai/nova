package app.nova.vpn

/**
 * The only file you must edit before release.
 */
object Cfg {
    /**
     * Where servers.json is published. The app downloads ALL of them in parallel and keeps the newest
     * (highest "rev"), so one blocked address doesn't matter. Put an Iranian host first if you have one.
     */
    val CONFIG_URLS = listOf(
        "https://YOUR_USER.github.io/YOUR_REPO/servers.json",
        "https://cdn.jsdelivr.net/gh/YOUR_USER/YOUR_REPO@main/servers.json",
        "https://raw.githubusercontent.com/YOUR_USER/YOUR_REPO/main/servers.json",
    )

    /** Must be IDENTICAL to "appSecret" in tools/servers.source.json */
    const val APP_SECRET = "CHANGE_ME_TO_RANDOM_STRING"

    /** AdMob TEST ids. Replace with your real unit ids (and the app id in the manifest). */
    const val ADMOB_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val ADMOB_REWARDED = "ca-app-pub-3940256099942544/5224354917"

    /** Used if servers.json has no "telegram" value. */
    const val DEFAULT_TELEGRAM = "https://t.me/YOUR_CHANNEL"

    /** Premium users skip the pre-connect ad. */
    const val SKIP_ADS_FOR_PREMIUM = true

    /** How long to wait for an ad to load before connecting anyway (ms). */
    const val AD_WAIT_MS = 4000L

    /** Minutes of bonus access after a rewarded ad (can be overridden by "bonusMinutes" in servers.json). */
    const val BONUS_MINUTES = 60

    /** Fake ping ranges (ms), purely cosmetic. */
    val FREE_PING = 89..200
    val PREMIUM_PING = 69..140

    // ---- connection check / automatic fallback
    const val PROBE_URL = "https://www.gstatic.com/generate_204"
    const val PROBE_TIMEOUT_MS = 7000L
    const val MAX_CANDIDATES = 4

    /**
     * false (default): if NO server passes the check, still connect to the first one (safer while testing).
     * true: show an error and disconnect instead.
     */
    const val STRICT_PROBE = false

    /** Days before expiry when the renewal reminder starts. */
    const val EXPIRY_WARN_DAYS = 3
}
