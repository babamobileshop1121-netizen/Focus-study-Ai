package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "BootReceiver"

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d(TAG, "Device rebooted. Checking active focus session to restore...")
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val activeSession = db.focusDao().getActiveSession()
                    if (activeSession != null && activeSession.isActive && activeSession.remainingSeconds > 0) {
                        Log.d(TAG, "Restoring focus lock session: ${activeSession.subject}, ${activeSession.remainingSeconds}s remaining")
                        val serviceIntent = Intent(context, FocusLockService::class.java).apply {
                            action = FocusLockService.ACTION_START
                            putExtra(FocusLockService.EXTRA_SUBJECT, activeSession.subject)
                            putExtra(FocusLockService.EXTRA_DURATION_SECONDS, activeSession.remainingSeconds)
                            putExtra(
                                FocusLockService.EXTRA_BLOCKED_PACKAGES,
                                activeSession.blockedPackages.split(",").filter { it.isNotBlank() }.toTypedArray()
                            )
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restore focus session on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
