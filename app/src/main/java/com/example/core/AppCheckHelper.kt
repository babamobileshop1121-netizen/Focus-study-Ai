package com.example.core

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val HARNESS_PREFS = "focusstudy_appcheck_prefs"
private const val HARNESS_TOKEN_VERIFIED_KEY = "has_harness_debug_token"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val appCheck = FirebaseAppCheck.getInstance()
        val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
        val harnessPrefs = context.getSharedPreferences(HARNESS_PREFS, Context.MODE_PRIVATE)
        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val debugStorePrefs = context.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )

        if (BuildConfig.DEBUG && !debugToken.isNullOrEmpty()) {
            // Harness / emulator launched with an explicit backend-registered debug token
            debugStorePrefs.edit()
                .putString(DEBUG_SECRET_KEY, debugToken)
                .commit()
            harnessPrefs.edit()
                .putBoolean(HARNESS_TOKEN_VERIFIED_KEY, true)
                .commit()

            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed with harness debug token.")
            return
        }

        val hasVerifiedHarnessToken = BuildConfig.DEBUG &&
            harnessPrefs.getBoolean(HARNESS_TOKEN_VERIFIED_KEY, false) &&
            !debugStorePrefs.getString(DEBUG_SECRET_KEY, null).isNullOrEmpty()

        if (hasVerifiedHarnessToken) {
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed with persisted harness token.")
            return
        }

        // Physical devices running either debug or release APKs use the provisioned reCAPTCHA Enterprise key
        val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
        val siteKey = if (resId != 0) context.getString(resId).trim() else ""

        if (siteKey.isNotEmpty()) {
            appCheck.installAppCheckProviderFactory(
                RecaptchaAppCheckProviderFactory.getInstance(siteKey)
            )
            Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
        } else if (BuildConfig.DEBUG) {
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.w(TAG, "No reCAPTCHA site key found; falling back to DebugAppCheckProviderFactory.")
        } else {
            Log.e(TAG, "recaptcha_site_key string resource not found or blank.")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
