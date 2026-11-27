package com.example.ui.screens

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlin.math.roundToLong
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppDatabase
import com.example.data.local.BlockedAppEntity
import com.example.data.local.FocusSessionEntity
import com.example.data.model.BlockedAppInfo
import com.example.service.FocusLockManager
import com.example.service.FocusLockService
import android.widget.Toast
import com.example.ui.components.AdMobBannerCard
import com.example.ui.components.RewardedAdCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.RoseError
import com.example.ui.theme.TealSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(
    activeSession: FocusSessionEntity?,
    onFocusSessionFinished: (subject: String, targetMins: Int, actualMins: Int) -> Unit,
    onShowInterstitialAd: () -> Unit = {},
    onWatchRewardedAd: (onReward: (Int) -> Unit) -> Unit = {},
    onAddCoins: (Int) -> Unit = {},
    initialSubject: String? = null,
    initialDurationMinutes: Long? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedDurationMinutes by remember { mutableLongStateOf((initialDurationMinutes ?: 25L).coerceIn(5L, 360L)) }
    var selectedSubject by remember { mutableStateOf(initialSubject ?: "Mathematics") }
    var showAppPickerSheet by remember { mutableStateOf(false) }
    var showStopConfirmDialog by remember { mutableStateOf(false) }
    var showCustomDurationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialSubject, initialDurationMinutes, activeSession?.isActive) {
        if (activeSession?.isActive != true) {
            if (!initialSubject.isNullOrBlank()) {
                selectedSubject = initialSubject
            }
            if (initialDurationMinutes != null && initialDurationMinutes > 0L) {
                selectedDurationMinutes = initialDurationMinutes.coerceIn(5L, 360L)
            }
        }
    }

    var hasUsagePermission by remember { mutableStateOf(FocusLockManager.hasUsageStatsPermission(context)) }
    var hasOverlayPermission by remember { mutableStateOf(FocusLockManager.hasOverlayPermission(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            hasUsagePermission = FocusLockManager.hasUsageStatsPermission(context)
            hasOverlayPermission = FocusLockManager.hasOverlayPermission(context)
            delay(2000L)
        }
    }

    var blockedAppsList by remember { mutableStateOf(FocusLockManager.POPULAR_DISTRACTING_APPS) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            val saved = db.focusDao().getAllBlockedApps()
            if (saved.isEmpty()) {
                val defaultEntities = FocusLockManager.POPULAR_DISTRACTING_APPS.map {
                    BlockedAppEntity(it.packageName, it.appName, it.category, it.isBlocked)
                }
                db.focusDao().insertDefaultApps(defaultEntities)
            } else {
                blockedAppsList = saved.map {
                    BlockedAppInfo(it.packageName, it.appName, it.category, it.isBlocked)
                }
            }
        }
    }

    val isSessionActive = activeSession != null && activeSession.isActive
    val displaySubject = if (isSessionActive) activeSession.subject else selectedSubject
    val (subjectAccent, _) = getSubjectColorPalette(displaySubject)
    val permissionsReady = hasUsagePermission && hasOverlayPermission

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Executive Header
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DEEP WORK SHIELD",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.4.sp,
                        color = TealSecondary
                    )
                    Text(
                        text = "Focus Lock",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                val statusLabel = when {
                    isSessionActive && activeSession?.isPaused == true -> "PAUSED"
                    isSessionActive -> "LOCKED"
                    permissionsReady -> "SHIELD READY"
                    else -> "SETUP"
                }
                val statusColor = when {
                    isSessionActive && activeSession?.isPaused == true -> AmberWarning
                    isSessionActive -> TealSecondary
                    permissionsReady -> EmeraldSuccess
                    else -> AmberWarning
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.14f))
                        .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = statusLabel,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = statusColor
                    )
                }
            }
        }

        // Precision Chronometer Hero Card
        item {
            val totalSeconds = if (isSessionActive) activeSession!!.targetSeconds else (selectedDurationMinutes * 60)
            val remainingSeconds = if (isSessionActive) activeSession!!.remainingSeconds else (selectedDurationMinutes * 60)
            val progress = if (totalSeconds > 0) (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f) else 0f
            val trackColor = MaterialTheme.colorScheme.surfaceVariant
            val tickBaseColor = MaterialTheme.colorScheme.onSurfaceVariant

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSessionActive) TealSecondary.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Card top metadata bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(subjectAccent.copy(alpha = 0.14f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(subjectAccent)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = displaySubject,
                                style = MaterialTheme.typography.labelMedium,
                                color = subjectAccent
                            )
                        }

                        Text(
                            text = if (isSessionActive) {
                                "${(totalSeconds / 60)}M TARGET"
                            } else {
                                "${blockedAppsList.count { it.isBlocked }} APPS RESTRICTED"
                            },
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Precision Chronograph Dial
                    Box(
                        modifier = Modifier.size(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 12.dp.toPx()
                            val outerRadius = size.minDimension / 2f
                            val arcRadius = outerRadius - strokeWidth - 8.dp.toPx()

                            // Precision tick marks around perimeter
                            for (i in 0 until 60) {
                                val angleRad = Math.toRadians((i * 6.0) - 90.0)
                                val isMajor = i % 5 == 0
                                val inner = outerRadius - (if (isMajor) 8.dp.toPx() else 4.dp.toPx())
                                val start = Offset(
                                    x = center.x + (inner * cos(angleRad)).toFloat(),
                                    y = center.y + (inner * sin(angleRad)).toFloat()
                                )
                                val end = Offset(
                                    x = center.x + (outerRadius * cos(angleRad)).toFloat(),
                                    y = center.y + (outerRadius * sin(angleRad)).toFloat()
                                )
                                drawLine(
                                    color = tickBaseColor.copy(alpha = if (isMajor) 0.45f else 0.18f),
                                    start = start,
                                    end = end,
                                    strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }

                            // Inner background track
                            drawCircle(
                                color = trackColor,
                                radius = arcRadius,
                                style = Stroke(width = strokeWidth)
                            )

                            // Progress arc
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(IndigoPrimary, TealSecondary, subjectAccent, IndigoPrimary)
                                ),
                                startAngle = -90f,
                                sweepAngle = progress * 360f,
                                useCenter = false,
                                topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                                size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (isSessionActive) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Focus Lock Status",
                                tint = if (isSessionActive) TealSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = formatTimerSeconds(remainingSeconds),
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    isSessionActive && activeSession?.isPaused == true -> "SESSION PAUSED"
                                    isSessionActive -> "LOCK ENGAGED"
                                    else -> "READY TO FOCUS"
                                },
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                color = if (isSessionActive && activeSession?.isPaused != true) EmeraldSuccess
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Primary Action Controls
                    if (isSessionActive) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (activeSession?.isPaused == true) {
                                Button(
                                    onClick = {
                                        val resumeIntent = Intent(context, FocusLockService::class.java).apply {
                                            action = FocusLockService.ACTION_RESUME
                                        }
                                        context.startService(resumeIntent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("resume_focus_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Resume", style = MaterialTheme.typography.labelLarge)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        val pauseIntent = Intent(context, FocusLockService::class.java).apply {
                                            action = FocusLockService.ACTION_PAUSE
                                        }
                                        context.startService(pauseIntent)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("pause_focus_button")
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pause", style = MaterialTheme.typography.labelLarge)
                                }
                            }

                            Button(
                                onClick = { showStopConfirmDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("stop_focus_button")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop Early", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                val blockedPkgs = blockedAppsList.filter { it.isBlocked }.map { it.packageName }
                                val durationSecs = selectedDurationMinutes * 60
                                val serviceIntent = Intent(context, FocusLockService::class.java).apply {
                                    action = FocusLockService.ACTION_START
                                    putExtra(FocusLockService.EXTRA_SUBJECT, selectedSubject)
                                    putExtra(FocusLockService.EXTRA_DURATION_SECONDS, durationSecs)
                                    putExtra(FocusLockService.EXTRA_BLOCKED_PACKAGES, blockedPkgs.toTypedArray())
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(serviceIntent)
                                } else {
                                    context.startService(serviceIntent)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("start_focus_lock_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Lock Distractions & Start (${formatShortDurationLabel(selectedDurationMinutes)})",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        // Configuration Section (Only shown when no session active)
        if (!isSessionActive) {
            // Custom User-Adjustable Target Duration Selector
            item {
                val quickPresets = remember {
                    listOf(
                        15L to "15m",
                        25L to "25m",
                        45L to "45m",
                        60L to "1h",
                        90L to "90m",
                        120L to "2h",
                        180L to "3h"
                    )
                }
                val studyModeLabel = remember(selectedDurationMinutes) {
                    when {
                        selectedDurationMinutes <= 20L -> "Quick Sprint"
                        selectedDurationMinutes <= 35L -> "Pomodoro"
                        selectedDurationMinutes <= 55L -> "Standard Block"
                        selectedDurationMinutes <= 90L -> "Deep Work"
                        else -> "Exam Marathon"
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TODAY'S STUDY DURATION",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(TealSecondary.copy(alpha = 0.14f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = studyModeLabel,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TealSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Interactive Stepper (-5m / Custom Tap / +5m)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                onClick = {
                                    selectedDurationMinutes = (selectedDurationMinutes - 5L).coerceAtLeast(5L)
                                },
                                enabled = selectedDurationMinutes > 5L,
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("duration_minus_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Decrease 5 minutes",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "5m",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showCustomDurationDialog = true }
                                    .padding(vertical = 6.dp)
                                    .testTag("custom_duration_trigger")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = formatShortDurationLabel(selectedDurationMinutes),
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IndigoPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Set exact study duration",
                                        tint = IndigoPrimary.copy(alpha = 0.8f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Text(
                                    text = "${selectedDurationMinutes} mins • Tap to type exact time",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Surface(
                                onClick = {
                                    selectedDurationMinutes = (selectedDurationMinutes + 5L).coerceAtMost(360L)
                                },
                                enabled = selectedDurationMinutes < 360L,
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("duration_plus_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Increase 5 minutes",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "5m",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Continuous Slider (5m to 4h, snapping to 5-min steps)
                        Slider(
                            value = selectedDurationMinutes.coerceIn(5L, 240L).toFloat(),
                            onValueChange = { rawValue ->
                                val snapped = ((rawValue / 5f).roundToLong() * 5L).coerceIn(5L, 240L)
                                selectedDurationMinutes = snapped
                            },
                            valueRange = 5f..240f,
                            colors = SliderDefaults.colors(
                                thumbColor = IndigoPrimary,
                                activeTrackColor = IndigoPrimary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("duration_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("5m", "1h", "2h", "3h", "4h").forEach { mark ->
                                Text(
                                    text = mark,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick Preset Chips
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(quickPresets) { (mins, shortLabel) ->
                                val isSelected = selectedDurationMinutes == mins
                                Box(
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) IndigoPrimary
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        )
                                        .clickable { selectedDurationMinutes = mins }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = shortLabel,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Subject Selector Card
            item {
                val subjectOptions = remember(selectedSubject) {
                    (listOf(selectedSubject) + SUBJECT_PRESETS).distinct()
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "STUDY SUBJECT",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(subjectOptions) { subj ->
                                val isSelected = selectedSubject == subj
                                val (accent, _) = getSubjectColorPalette(subj)
                                Row(
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) accent.copy(alpha = 0.18f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        )
                                        .border(
                                            width = if (isSelected) 1.dp else 0.dp,
                                            color = if (isSelected) accent.copy(alpha = 0.6f) else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedSubject = subj }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(accent)
                                    )
                                    Spacer(modifier = Modifier.width(7.dp))
                                    Text(
                                        text = subj,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Distracting Apps Blocklist Card
            item {
                val blockedApps = blockedAppsList.filter { it.isBlocked }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(IndigoPrimary.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Apps,
                                        contentDescription = "Blocked Apps",
                                        tint = IndigoPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Distraction Blocklist",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${blockedApps.size} of ${blockedAppsList.size} apps intercepted",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(IndigoPrimary.copy(alpha = 0.14f))
                                    .clickable { showAppPickerSheet = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Manage Blocklist",
                                        tint = IndigoPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Manage",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = IndigoPrimary
                                    )
                                }
                            }
                        }

                        if (blockedApps.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                blockedApps.take(5).forEach { app ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = app.appName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (blockedApps.size > 5) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(IndigoPrimary.copy(alpha = 0.12f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "+${blockedApps.size - 5} more",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = IndigoPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // System Shield Permissions Checklist Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (permissionsReady) EmeraldSuccess.copy(alpha = 0.35f)
                        else AmberWarning.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (permissionsReady) Icons.Default.CheckCircle else Icons.Default.Security,
                                contentDescription = "System Shield",
                                tint = if (permissionsReady) EmeraldSuccess else AmberWarning,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (permissionsReady) "App Blocker Shield Active" else "System Shield Setup",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Grant Usage Access and Overlay permissions so distracting apps are intercepted automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        PermissionStatusRow(
                            title = "Usage Access",
                            subtitle = "Detects foreground distraction launches",
                            isGranted = hasUsagePermission,
                            onGrantClick = { FocusLockManager.openUsageStatsSettings(context) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        )

                        PermissionStatusRow(
                            title = "Display Over Apps",
                            subtitle = "Shows Focus Lock shield screen",
                            isGranted = hasOverlayPermission,
                            onGrantClick = { FocusLockManager.openOverlaySettings(context) }
                        )
                    }
                }
            }

            // AdMob Banner Unit (shown only outside active focus lock)
            item {
                AdMobBannerCard()
            }

            // Sponsored Study Ad Card for Coins
            item {
                RewardedAdCard(
                    bonusTokens = 50,
                    onWatchAd = {
                        onWatchRewardedAd { coins ->
                            onAddCoins(coins)
                            Toast.makeText(context, "🎉 +$coins Coins added to your balance!", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(84.dp))
        }
    }

    // Modal Bottom Sheet: App Selection
    if (showAppPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAppPickerSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Distraction Blocklist",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Selected apps are intercepted while Focus Lock is active.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                ) {
                    items(blockedAppsList) { app ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = app.category,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = app.isBlocked,
                                onCheckedChange = { isChecked ->
                                    blockedAppsList = blockedAppsList.map {
                                        if (it.packageName == app.packageName) it.copy(isBlocked = isChecked) else it
                                    }
                                    scope.launch(Dispatchers.IO) {
                                        val db = AppDatabase.getInstance(context)
                                        db.focusDao().setAppBlocked(app.packageName, isChecked)
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = IndigoPrimary)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { showAppPickerSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                ) {
                    Text("Save Blocklist", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showStopConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showStopConfirmDialog = false },
            title = { Text("End Focus Lock Early?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = "Stopping early unlocks restricted apps immediately and logs your partial session time.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onShowInterstitialAd()
                        val stopIntent = Intent(context, FocusLockService::class.java).apply {
                            action = FocusLockService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                        showStopConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("End Session")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmDialog = false }) {
                    Text("Keep Focusing")
                }
            }
        )
    }

    if (showCustomDurationDialog) {
        CustomStudyDurationDialog(
            initialMinutes = selectedDurationMinutes,
            onDismiss = { showCustomDurationDialog = false },
            onConfirm = { newMinutes ->
                selectedDurationMinutes = newMinutes.coerceIn(5L, 360L)
                showCustomDurationDialog = false
            }
        )
    }
}

@Composable
private fun CustomStudyDurationDialog(
    initialMinutes: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var hoursText by remember { mutableStateOf((initialMinutes / 60L).toString()) }
    var minsText by remember { mutableStateOf((initialMinutes % 60L).toString()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Study Duration", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Adjust exactly how long you want Focus Lock active today (5 mins to 6 hours).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { input ->
                            hoursText = input.filter { it.isDigit() }.take(1)
                            errorMsg = null
                        },
                        label = { Text("Hours (0–6)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_hours_input")
                    )
                    OutlinedTextField(
                        value = minsText,
                        onValueChange = { input ->
                            minsText = input.filter { it.isDigit() }.take(3)
                            errorMsg = null
                        },
                        label = { Text("Minutes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_minutes_input")
                    )
                }
                if (errorMsg != null) {
                    Text(
                        text = errorMsg ?: "",
                        color = RoseError,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val hrs = hoursText.toLongOrNull() ?: 0L
                    val mins = minsText.toLongOrNull() ?: 0L
                    val total = (hrs * 60L) + mins
                    if (total < 5L || total > 360L) {
                        errorMsg = "Please choose between 5 minutes and 6 hours (360 mins)."
                    } else {
                        onConfirm(total)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_custom_duration_button")
            ) {
                Text("Apply Time")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PermissionStatusRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isGranted) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isGranted) {
            Text(
                text = "GRANTED",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = EmeraldSuccess
            )
        } else {
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AmberWarning.copy(alpha = 0.16f))
                    .clickable { onGrantClick() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Enable",
                    style = MaterialTheme.typography.labelMedium,
                    color = AmberWarning
                )
            }
        }
    }
}

private fun formatShortDurationLabel(minutes: Long): String {
    val hrs = minutes / 60L
    val mins = minutes % 60L
    return when {
        hrs > 0L && mins > 0L -> "${hrs}h ${mins}m"
        hrs > 0L -> "${hrs}h"
        else -> "${mins}m"
    }
}

private fun formatTimerSeconds(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
