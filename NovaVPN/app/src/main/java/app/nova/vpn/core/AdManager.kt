package app.nova.vpn.core

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import app.nova.vpn.Cfg
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Shows one full-screen video ad before every connection (AdMob interstitial).
 * If the ad can't load within Cfg.AD_WAIT_MS (e.g. Google services blocked) the user connects anyway.
 * To use another network (Tapsell, Yektanet …) replace only this file.
 */
object AdManager {
    private lateinit var appContext: Context
    private var ad: InterstitialAd? = null
    private var loading = false
    private var waiter: ((InterstitialAd?) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        appContext = context.applicationContext
        runCatching {
            MobileAds.initialize(appContext)
            load()
        }
    }

    private fun load() {
        if (ad != null || loading) return
        loading = true
        InterstitialAd.load(
            appContext, Cfg.ADMOB_INTERSTITIAL, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    loading = false
                    ad = loaded
                    waiter?.let { w -> waiter = null; w(loaded) }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    waiter?.let { w -> waiter = null; w(null) }
                }
            }
        )
    }

    fun showThen(activity: Activity, onDone: () -> Unit) {
        ad?.let { present(activity, it, onDone); return }

        var handled = false
        val timeout = Runnable {
            if (!handled) { handled = true; waiter = null; onDone() }
        }
        waiter = { loaded ->
            if (!handled) {
                handled = true
                handler.removeCallbacks(timeout)
                if (loaded != null) present(activity, loaded, onDone) else onDone()
            }
        }
        handler.postDelayed(timeout, Cfg.AD_WAIT_MS)
        load()
    }

    private fun present(activity: Activity, shown: InterstitialAd, onDone: () -> Unit) {
        ad = null
        shown.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { load(); onDone() }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { load(); onDone() }
        }
        shown.show(activity)
    }
}
