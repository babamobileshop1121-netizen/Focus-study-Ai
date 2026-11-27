package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.FocusSessionEntity
import com.example.ui.FocusBlockerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

private const val TAG = "FocusLockService"
private const val CHANNEL_ID = "focus_study_lock_channel"
private const val NOTIFICATION_ID = 1001

class FocusLockService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var monitorJob: Job? = null

    private var targetSeconds: Long = 1500L
    private var remainingSeconds: Long = 1500L
    private var isPaused: Boolean = false
    private var subject: String = "Mathematics"
    private var blockedPackagesSet: MutableSet<String> = mutableSetOf()
    private var lastInterceptionTimeMs: Long = 0L

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"

        const val EXTRA_SUBJECT = "EXTRA_SUBJECT"
        const val EXTRA_DURATION_SECONDS = "EXTRA_DURATION_SECONDS"
        const val EXTRA_BLOCKED_PACKAGES = "EXTRA_BLOCKED_PACKAGES"

        var isRunning: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val subj = intent.getStringExtra(EXTRA_SUBJECT) ?: "General Study"
                val duration = intent.getLongExtra(EXTRA_DURATION_SECONDS, 1500L)
                val blocked = intent.getStringArrayExtra(EXTRA_BLOCKED_PACKAGES)?.toList() ?: emptyList()
                startSession(subj, duration, blocked)
            }
            ACTION_PAUSE -> {
                pauseSession()
            }
            ACTION_RESUME -> {
                resumeSession()
            }
            ACTION_STOP -> {
                stopSession(userCancelled = true)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startSession(subj: String, duration: Long, blocked: List<String>) {
        isRunning = true
        subject = subj
        targetSeconds = duration
        remainingSeconds = duration
        isPaused = false
        blockedPackagesSet.clear()
        blockedPackagesSet.addAll(blocked)

        val notification = buildNotification("Focusing on $subject", formatTime(remainingSeconds), isPaused)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Persist session to Room
        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(applicationContext)
            db.focusDao().saveActiveSession(
                FocusSessionEntity(
                    id = 1,
                    subject = subject,
                    targetSeconds = targetSeconds,
                    remainingSeconds = remainingSeconds,
                    isPaused = isPaused,
                    isActive = true,
                    startTimeMs = System.currentTimeMillis(),
                    lastTickTimeMs = System.currentTimeMillis(),
                    blockedPackages = blockedPackagesSet.joinToString(",")
                )
            )
        }

        startMonitoringLoop()
    }

    private fun pauseSession() {
        isPaused = true
        serviceScope.launch(Dispatchers.IO) {
            AppDatabase.getInstance(applicationContext).focusDao().setPaused(true)
        }
        updateNotification()
    }

    private fun resumeSession() {
        isPaused = false
        serviceScope.launch(Dispatchers.IO) {
            AppDatabase.getInstance(applicationContext).focusDao().setPaused(false)
        }
        updateNotification()
    }

    private fun stopSession(userCancelled: Boolean) {
        isRunning = false
        monitorJob?.cancel()

        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(applicationContext)
            db.focusDao().clearActiveSession()
        }

        if (!userCancelled && remainingSeconds <= 0) {
            triggerCompletionAlert()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startMonitoringLoop() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive && remainingSeconds > 0) {
                delay(1000L)

                if (!isPaused) {
                    remainingSeconds--
                    // Persist to Room every second
                    AppDatabase.getInstance(applicationContext).focusDao().updateRemainingTime(
                        remainingSeconds,
                        System.currentTimeMillis()
                    )

                    // Check if foreground app is blocked
                    checkForegroundApp()
                }

                updateNotification()

                if (remainingSeconds <= 0) {
                    stopSession(userCancelled = false)
                    break
                }
            }
        }
    }

    private fun checkForegroundApp() {
        val currentFgPackage = getForegroundPackageName() ?: return
        if (currentFgPackage == packageName) return // FocusStudy AI itself is allowed

        if (blockedPackagesSet.contains(currentFgPackage)) {
            val now = System.currentTimeMillis()
            // Throttle overlay launches to avoid spamming the intent
            if (now - lastInterceptionTimeMs > 2000L) {
                lastInterceptionTimeMs = now
                launchBlockerOverlay(currentFgPackage)
            }
        }
    }

    private fun getForegroundPackageName(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val time = System.currentTimeMillis()
        val usageEvents = usm.queryEvents(time - 10000L, time)
        val event = UsageEvents.Event()
        var lastForegroundApp: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastForegroundApp = event.packageName
            }
        }
        return lastForegroundApp
    }

    private fun launchBlockerOverlay(blockedPkg: String) {
        val intent = Intent(this, FocusBlockerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(FocusBlockerActivity.EXTRA_BLOCKED_PACKAGE, blockedPkg)
            putExtra(FocusBlockerActivity.EXTRA_SUBJECT, subject)
            putExtra(FocusBlockerActivity.EXTRA_REMAINING_SECONDS, remainingSeconds)
        }
        startActivity(intent)
    }

    private fun triggerCompletionAlert() {
        // Vibrate
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(500)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibrate alert ignored: ${e.message}")
        }

        // Post completion notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎉 Focus Session Completed!")
            .setContentText("Great job studying $subject! All blocked apps are now unlocked.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(1002, completeNotification)
    }

    private fun updateNotification() {
        val text = if (isPaused) "Paused - ${formatTime(remainingSeconds)} left" else "${formatTime(remainingSeconds)} left"
        val notification = buildNotification("Focusing on $subject", text, isPaused)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, content: String, paused: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeAction = if (paused) {
            val resumeIntent = Intent(this, FocusLockService::class.java).apply { action = ACTION_RESUME }
            val resumePendingIntent = PendingIntent.getService(this, 1, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action.Builder(0, "Resume", resumePendingIntent).build()
        } else {
            val pauseIntent = Intent(this, FocusLockService::class.java).apply { action = ACTION_PAUSE }
            val pausePendingIntent = PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action.Builder(0, "Pause", pausePendingIntent).build()
        }

        val stopIntent = Intent(this, FocusLockService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopAction = NotificationCompat.Action.Builder(0, "Stop", stopPendingIntent).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openPendingIntent)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Lock Active Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing focus timer countdown and distraction restriction status."
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun formatTime(seconds: Long): String {
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
    }
}
