package com.example.service

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.example.data.model.BlockedAppInfo

object FocusLockManager {

    val POPULAR_DISTRACTING_APPS = listOf(
        BlockedAppInfo("com.instagram.android", "Instagram", "Social", true),
        BlockedAppInfo("com.google.android.youtube", "YouTube", "Video", true),
        BlockedAppInfo("com.zhiliaoapp.musically", "TikTok", "Social", true),
        BlockedAppInfo("com.snapchat.android", "Snapchat", "Social", true),
        BlockedAppInfo("com.facebook.katana", "Facebook", "Social", true),
        BlockedAppInfo("com.twitter.android", "X (Twitter)", "Social", true),
        BlockedAppInfo("com.reddit.frontpage", "Reddit", "Social", true),
        BlockedAppInfo("com.netflix.mediaclient", "Netflix", "Entertainment", false),
        BlockedAppInfo("tv.twitch.android.app", "Twitch", "Entertainment", false),
        BlockedAppInfo("com.discord", "Discord", "Social", false),
        BlockedAppInfo("com.roblox.client", "Roblox", "Games", true),
        BlockedAppInfo("com.king.candycrushsaga", "Candy Crush", "Games", false)
    )

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageStatsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun getInstalledUserApps(context: Context): List<BlockedAppInfo> {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val result = mutableListOf<BlockedAppInfo>()

        for (appInfo in packages) {
            // Ignore system apps without updates or our own app
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (appInfo.packageName == context.packageName) continue

            if (!isSystem || isUpdatedSystem) {
                val label = pm.getApplicationLabel(appInfo).toString()
                result.add(
                    BlockedAppInfo(
                        packageName = appInfo.packageName,
                        appName = label,
                        category = "Installed",
                        isBlocked = false
                    )
                )
            }
        }
        return result.sortedBy { it.appName }
    }
}
