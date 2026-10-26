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
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Ads (AdMob):
 *  - showThen(): full-screen video ad before every connection
 *  - showRewarded(): "watch an ad to unlock bonus servers"
 * If an ad can't load in time (e.g. Google services blocked) the user simply continues.
 * To use another network (Tapsell, Yektanet …) replace only this file.
 */
object AdManager {
    private lateinit var appContext: Context
    private val handler = Handler(Looper.getMainLooper())

    private var ad: InterstitialAd? = null
    private var loading = false
    private var waiter: ((InterstitialAd?) -> Unit)? = null

    private var rewarded: RewardedAd? = null
    private var loadingRewarded = false
    private var rewardedWaiter: ((RewardedAd?) -> Unit)? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        runCatching {
            MobileAds.initialize(appContext)
            load()
            loadRewarded()
        }
    }

    // ------------------------------------------------------------------ interstitial

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

    // ------------------------------------------------------------------ rewarded

    private fun loadRewarded() {
        if (rewarded != null || loadingRewarded) return
        loadingRewarded = true
        RewardedAd.load(
            appContext, Cfg.ADMOB_REWARDED, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
                    loadingRewarded = false
                    rewarded = loaded
                    rewardedWaiter?.let { w -> rewardedWaiter = null; w(loaded) }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingRewarded = false
                    rewardedWaiter?.let { w -> rewardedWaiter = null; w(null) }
                }
            }
        )
    }

    /** [onResult] is called with true only if the user watched the ad to the end. */
    fun showRewarded(activity: Activity, onResult: (Boolean) -> Unit) {
        rewarded?.let { presentRewarded(activity, it, onResult); return }

        var handled = false
        val timeout = Runnable {
            if (!handled) { handled = true; rewardedWaiter = null; onResult(false) }
        }
        rewardedWaiter = { loaded ->
            if (!handled) {
                handled = true
                handler.removeCallbacks(timeout)
                if (loaded != null) presentRewarded(activity, loaded, onResult) else onResult(false)
            }
        }
        handler.postDelayed(timeout, 8000)
        loadRewarded()
    }

    private fun presentRewarded(activity: Activity, shown: RewardedAd, onResult: (Boolean) -> Unit) {
        rewarded = null
        var earned = false
        shown.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { loadRewarded(); onResult(earned) }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { loadRewarded(); onResult(false) }
        }
        shown.show(activity) { earned = true }
    }
}
