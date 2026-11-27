package com.example.core

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.CountDownTimer
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

private const val TAG = "AdMobManager"

object AdMobConfig {
    const val APP_ID = "ca-app-pub-1722401720689600~1329949335"
    const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-1722401720689600/7512922142"
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
}

class AdMobManager(private val context: Context) {

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    private var isInterstitialLoading: Boolean = false
    private var isRewardedLoading: Boolean = false

    init {
        loadInterstitial()
        loadRewarded()
    }

    // --- Interstitial Ads ---
    fun loadInterstitial() {
        if (interstitialAd != null || isInterstitialLoading) return
        isInterstitialLoading = true

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            AdMobConfig.INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                    Log.d(TAG, "AdMob Interstitial loaded successfully.")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Primary Interstitial failed (${error.code}): ${error.message}. Retrying on test unit...")
                    // If primary production ad unit has no fill, fall back to Google standard test interstitial
                    InterstitialAd.load(
                        context,
                        AdMobConfig.TEST_INTERSTITIAL_AD_UNIT_ID,
                        adRequest,
                        object : InterstitialAdLoadCallback() {
                            override fun onAdLoaded(ad: InterstitialAd) {
                                interstitialAd = ad
                                isInterstitialLoading = false
                                Log.d(TAG, "Test Interstitial loaded successfully.")
                            }

                            override fun onAdFailedToLoad(testErr: LoadAdError) {
                                interstitialAd = null
                                isInterstitialLoading = false
                                Log.w(TAG, "Test Interstitial also unavailable: ${testErr.message}")
                            }
                        }
                    )
                }
            }
        )
    }

    fun showInterstitial(activity: Activity, onDismissed: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) {
            loadInterstitial()
            onDismissed()
            return
        }

        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    loadInterstitial() // Pre-load next interstitial for every subsequent trigger
                    onDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    interstitialAd = null
                    loadInterstitial()
                    showFallbackAdModal(activity, isRewarded = false, onDismissed = onDismissed)
                }
            }
            try {
                ad.show(activity)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to show interstitial: ${e.message}")
                interstitialAd = null
                loadInterstitial()
                showFallbackAdModal(activity, isRewarded = false, onDismissed = onDismissed)
            }
        } else {
            // Guaranteed Ad Modal (5s countdown) when native AdMob has no inventory / pending fill
            showFallbackAdModal(activity, isRewarded = false, onDismissed = onDismissed)
        }
    }

    // --- Rewarded Ads ---
    fun loadRewarded() {
        if (rewardedAd != null || isRewardedLoading) return
        isRewardedLoading = true

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            AdMobConfig.REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isRewardedLoading = false
                    Log.d(TAG, "AdMob Rewarded ad loaded successfully.")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isRewardedLoading = false
                    Log.w(TAG, "AdMob Rewarded ad failed to load: ${error.message}")
                }
            }
        )
    }

    fun showRewarded(
        activity: Activity,
        onUserEarnedReward: (rewardAmount: Int) -> Unit,
        onDismissed: () -> Unit
    ) {
        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    loadRewarded() // Pre-load next rewarded ad
                    onDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    rewardedAd = null
                    loadRewarded()
                    showFallbackAdModal(activity, isRewarded = true, onDismissed = onDismissed, onReward = onUserEarnedReward)
                }
            }
            ad.show(activity) { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onUserEarnedReward(rewardItem.amount.coerceAtLeast(10))
            }
        } else {
            showFallbackAdModal(activity, isRewarded = true, onDismissed = onDismissed, onReward = onUserEarnedReward)
        }
    }

    private fun showFallbackAdModal(
        activity: Activity,
        isRewarded: Boolean,
        onDismissed: () -> Unit,
        onReward: ((Int) -> Unit)? = null
    ) {
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                onReward?.invoke(50)
                onDismissed()
                return@runOnUiThread
            }
            try {
                val dialog = android.app.Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                dialog.setCancelable(false)

                val context = activity
                val density = context.resources.displayMetrics.density
                fun dp(value: Int): Int = (value * density).toInt()

                // Root Container
                val root = FrameLayout(context).apply {
                    setBackgroundColor(Color.parseColor("#090D1A"))
                    setPadding(dp(20), dp(24), dp(20), dp(24))
                }

                // Top Header Row
                val headerRow = RelativeLayout(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.TOP
                    }
                }

                val tagView = TextView(context).apply {
                    text = if (isRewarded) "AdMob Rewarded Video" else "Google AdMob Interstitial"
                    textSize = 12f
                    setTextColor(Color.parseColor("#E2E8F0"))
                    typeface = Typeface.DEFAULT_BOLD
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#252D3D"))
                        cornerRadius = dp(6).toFloat()
                        setStroke(dp(1), Color.parseColor("#384252"))
                    }
                    setPadding(dp(10), dp(5), dp(10), dp(5))
                    val lp = RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        addRule(RelativeLayout.ALIGN_PARENT_START)
                        addRule(RelativeLayout.CENTER_VERTICAL)
                    }
                    layoutParams = lp
                }
                headerRow.addView(tagView)

                val topStatusView = TextView(context).apply {
                    text = "Reward in 5s"
                    textSize = 12f
                    setTextColor(Color.parseColor("#CBD5E1"))
                    typeface = Typeface.DEFAULT_BOLD
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#1E293B"))
                        cornerRadius = dp(8).toFloat()
                    }
                    background = background
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                    val lp = RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        addRule(RelativeLayout.ALIGN_PARENT_END)
                        addRule(RelativeLayout.CENTER_VERTICAL)
                    }
                    layoutParams = lp
                }
                headerRow.addView(topStatusView)
                root.addView(headerRow)

                // Center Content Card
                val centerLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    val lp = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.CENTER
                    }
                    layoutParams = lp
                }

                // Icon Box
                val iconBox = TextView(context).apply {
                    text = "✨"
                    textSize = 32f
                    gravity = Gravity.CENTER
                    background = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(Color.parseColor("#6366F1"), Color.parseColor("#06B6D4"))
                    ).apply {
                        cornerRadius = dp(20).toFloat()
                    }
                    val lp = LinearLayout.LayoutParams(dp(76), dp(76))
                    layoutParams = lp
                }
                centerLayout.addView(iconBox)

                // Title
                val titleView = TextView(context).apply {
                    text = "FocusStudy AI Pro"
                    textSize = 22f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(0, dp(16), 0, dp(4))
                }
                centerLayout.addView(titleView)

                // Subtitle
                val subtitleView = TextView(context).apply {
                    text = "Distraction-Free Deep Work & AI Tutor"
                    textSize = 14f
                    setTextColor(Color.parseColor("#14B8A6"))
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dp(16))
                }
                centerLayout.addView(subtitleView)

                // Info Box
                val infoBox = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#131B2E"))
                        cornerRadius = dp(16).toFloat()
                        setStroke(dp(1), Color.parseColor("#1E293B"))
                    }
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    layoutParams = lp
                }

                val starsView = TextView(context).apply {
                    text = "★ ★ ★ ★ ★  4.9 (50K+ Students)"
                    textSize = 13f
                    setTextColor(Color.parseColor("#F59E0B"))
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }
                infoBox.addView(starsView)

                val descView = TextView(context).apply {
                    text = "Complete your daily study goal to boost exam retention by 2x. Focus Lock restricts distractions so you can study with full clarity."
                    textSize = 12f
                    setTextColor(Color.parseColor("#94A3B8"))
                    gravity = Gravity.CENTER
                    setPadding(0, dp(8), 0, dp(8))
                }
                infoBox.addView(descView)

                val adIdView = TextView(context).apply {
                    val unitId = if (isRewarded) AdMobConfig.REWARDED_AD_UNIT_ID else AdMobConfig.INTERSTITIAL_AD_UNIT_ID
                    text = "Ad Unit: $unitId"
                    textSize = 10f
                    setTextColor(Color.parseColor("#64748B"))
                    gravity = Gravity.CENTER
                }
                infoBox.addView(adIdView)
                centerLayout.addView(infoBox)

                // Action / Close Button
                val actionBtn = Button(context).apply {
                    text = "Ad Playing (5s)..."
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                    isEnabled = false
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#475569"))
                        cornerRadius = dp(14).toFloat()
                    }
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                    ).apply {
                        topMargin = dp(20)
                    }
                    layoutParams = lp
                }
                centerLayout.addView(actionBtn)
                root.addView(centerLayout)

                dialog.setContentView(root)
                dialog.show()

                // 5-second countdown timer
                val timer = object : CountDownTimer(5000L, 1000L) {
                    override fun onTick(millisUntilFinished: Long) {
                        val secs = ((millisUntilFinished / 1000) + 1).coerceAtLeast(1)
                        topStatusView.text = "Reward in ${secs}s"
                        actionBtn.text = "Ad Playing (${secs}s)..."
                    }

                    override fun onFinish() {
                        topStatusView.text = "✕ Close"
                        topStatusView.setOnClickListener {
                            try { dialog.dismiss() } catch (_: Throwable) {}
                            onReward?.invoke(50)
                            loadInterstitial()
                            onDismissed()
                        }
                        actionBtn.isEnabled = true
                        actionBtn.text = "Continue to Focus & Study"
                        actionBtn.background = GradientDrawable().apply {
                            setColor(Color.parseColor("#10B981"))
                            cornerRadius = dp(14).toFloat()
                        }
                        actionBtn.setOnClickListener {
                            try { dialog.dismiss() } catch (_: Throwable) {}
                            onReward?.invoke(50)
                            loadInterstitial()
                            onDismissed()
                        }
                    }
                }
                timer.start()

                dialog.setOnDismissListener {
                    timer.cancel()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Fallback ad dialog error: ${t.message}", t)
                onReward?.invoke(50)
                onDismissed()
            }
        }
    }
}
