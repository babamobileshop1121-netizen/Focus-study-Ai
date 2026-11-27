package com.example.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.core.AdMobManager
import com.example.service.FocusLockService
import com.example.ui.theme.FocusStudyTheme
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.RoseError
import com.example.ui.theme.TealSecondary
import kotlinx.coroutines.delay
import java.util.Locale

class FocusBlockerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "EXTRA_BLOCKED_PACKAGE"
        const val EXTRA_SUBJECT = "EXTRA_SUBJECT"
        const val EXTRA_REMAINING_SECONDS = "EXTRA_REMAINING_SECONDS"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val blockedPkg = intent?.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: "This application"
        val subject = intent?.getStringExtra(EXTRA_SUBJECT) ?: "Your Study Goal"
        val initialSeconds = intent?.getLongExtra(EXTRA_REMAINING_SECONDS, 1500L) ?: 1500L

        setContent {
            FocusStudyTheme(darkTheme = true) {
                FocusBlockerScreen(
                    blockedPackage = blockedPkg,
                    subject = subject,
                    initialRemainingSeconds = initialSeconds,
                    onReturnToStudy = { returnToStudyApp() },
                    onEmergencyUnlock = { emergencyUnlock() }
                )
            }
        }
    }

    private fun returnToStudyApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
        finish()
    }

    private fun emergencyUnlock() {
        val adMobManager = AdMobManager(this)
        adMobManager.showInterstitial(this) {
            val stopIntent = Intent(this, FocusLockService::class.java).apply {
                action = FocusLockService.ACTION_STOP
            }
            startService(stopIntent)
            returnToStudyApp()
        }
    }
}

@Composable
fun FocusBlockerScreen(
    blockedPackage: String,
    subject: String,
    initialRemainingSeconds: Long,
    onReturnToStudy: () -> Unit,
    onEmergencyUnlock: () -> Unit
) {
    var remainingSeconds by remember { mutableLongStateOf(initialRemainingSeconds) }
    var showEmergencyDialog by remember { mutableStateOf(false) }

    // Live countdown while viewing blocker
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
        }
        onReturnToStudy()
    }

    // Intercept back button to redirect back to study app
    BackHandler {
        onReturnToStudy()
    }

    val appLabel = remember(blockedPackage) {
        when {
            blockedPackage.contains("instagram") -> "Instagram"
            blockedPackage.contains("youtube") -> "YouTube"
            blockedPackage.contains("tiktok") || blockedPackage.contains("musically") -> "TikTok"
            blockedPackage.contains("facebook") -> "Facebook"
            blockedPackage.contains("twitter") -> "X (Twitter)"
            blockedPackage.contains("reddit") -> "Reddit"
            blockedPackage.contains("snapchat") -> "Snapchat"
            blockedPackage.contains("netflix") -> "Netflix"
            blockedPackage.contains("discord") -> "Discord"
            else -> blockedPackage.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF070B14)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Glowing Lock Header
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(IndigoPrimary.copy(alpha = 0.4f), Color.Transparent)
                            )
                        )
                        .border(2.dp, IndigoPrimary.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Focus Lock Shield",
                        tint = TealSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "FOCUS LOCK ACTIVE",
                    style = MaterialTheme.typography.labelLarge,
                    color = TealSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$appLabel is Restricted",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Subject Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "CURRENT STUDY TARGET",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subject,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Timer Display
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E293B))
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = formatSeconds(remainingSeconds),
                                style = MaterialTheme.typography.displaySmall,
                                color = TealSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Remaining to unlock distractions",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Motivational Quote
                Text(
                    text = "\"Small daily disciplines repeated consistently lead to monumental exam success. Keep going!\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFCBD5E1),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Primary Action: Return to Study
                Button(
                    onClick = onReturnToStudy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("return_to_study_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Return to Study Session",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Secondary Emergency Bypass
                OutlinedButton(
                    onClick = { showEmergencyDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("emergency_unlock_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
                ) {
                    Text(
                        text = "Emergency Unlock",
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (showEmergencyDialog) {
            AlertDialog(
                onDismissRequest = { showEmergencyDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Emergency Alert",
                        tint = RoseError
                    )
                },
                title = {
                    Text("Emergency Unlock?", fontWeight = FontWeight.Bold)
                },
                text = {
                    Text("Breaking your focus session early stops your streak and unlocks all restricted apps. Only use this for genuine urgent matters.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showEmergencyDialog = false
                            onEmergencyUnlock()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoseError)
                    ) {
                        Text("Unlock & Exit")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmergencyDialog = false }) {
                        Text("Stay Focused")
                    }
                }
            )
        }
    }
}

private fun formatSeconds(totalSecs: Long): String {
    val hrs = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
