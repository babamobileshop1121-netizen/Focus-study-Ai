package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Fireplace
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FocusLog
import com.example.data.model.Quiz
import com.example.data.model.StudySession
import com.example.data.model.UserProfile
import com.example.ui.components.AdMobBannerCard
import com.example.ui.components.RewardedAdCard
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.RoseError
import com.example.ui.theme.TealSecondary

data class AchievementBadge(
    val title: String,
    val description: String,
    val iconName: String,
    val isUnlocked: Boolean
)

@Composable
fun ProgressScreen(
    userProfile: UserProfile?,
    sessions: List<StudySession>,
    focusLogs: List<FocusLog>,
    quizzes: List<Quiz>,
    onWatchRewardedAd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalFocusMinutes = remember(focusLogs, userProfile) {
        val loggedMins = focusLogs.sumOf { it.actualMinutes }
        maxOf(loggedMins, userProfile?.totalFocusMinutes ?: 45)
    }

    val overallAccuracy = remember(quizzes, userProfile) {
        if (quizzes.isNotEmpty()) {
            quizzes.map { it.accuracy }.average().toInt()
        } else {
            userProfile?.averageQuizScore ?: 85
        }
    }

    // Weekly study report data (Monday to Sunday)
    val weeklyMinutes = remember(focusLogs) {
        listOf(
            "Mon" to 45f,
            "Tue" to 60f,
            "Wed" to 30f,
            "Thu" to 75f,
            "Fri" to 50f,
            "Sat" to 90f,
            "Sun" to 40f
        )
    }

    val badges = remember(totalFocusMinutes, overallAccuracy, userProfile) {
        listOf(
            AchievementBadge("First Focus", "Completed your 1st focus lock", "lock", true),
            AchievementBadge("3-Day Streak", "Study 3 days in a row", "fire", (userProfile?.streakDays ?: 1) >= 3),
            AchievementBadge("Deep Thinker", "Over 100 focus minutes logged", "brain", totalFocusMinutes >= 100),
            AchievementBadge("Quiz Ace", "Achieve 80%+ quiz accuracy", "star", overallAccuracy >= 80),
            AchievementBadge("Mastery", "Complete 10 study chapters", "trophy", sessions.count { it.isCompleted } >= 10)
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Progress & Analytics",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Track your study streaks, accuracy trends, and revision queue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Weekly Focus Time Bar Chart
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Weekly Study Time",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$totalFocusMinutes minutes logged this week",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(IndigoPrimary.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Target: 300m",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Custom Canvas Bar Chart
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val maxVal = 100f
                            val barWidth = 24.dp.toPx()
                            val spacing = (size.width - (barWidth * weeklyMinutes.size)) / (weeklyMinutes.size + 1)

                            weeklyMinutes.forEachIndexed { i, (_, value) ->
                                val x = spacing + i * (barWidth + spacing)
                                val barHeight = (value / maxVal) * (size.height - 25.dp.toPx())
                                val y = size.height - barHeight - 20.dp.toPx()

                                // Background slot
                                drawRoundRect(
                                    color = Color(0xFF1E293B),
                                    topLeft = Offset(x, 0f),
                                    size = Size(barWidth, size.height - 20.dp.toPx()),
                                    cornerRadius = CornerRadius(8f, 8f)
                                )

                                // Active bar
                                drawRoundRect(
                                    color = if (i == 5) TealSecondary else IndigoPrimary,
                                    topLeft = Offset(x, y),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(8f, 8f)
                                )
                            }
                        }

                        // Day Labels under bars
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            weeklyMinutes.forEach { (day, _) ->
                                Text(
                                    text = day,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quiz Accuracy & Weak Topics
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Quiz Accuracy & Performance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(14.dp)
                        ) {
                            Column {
                                Text("Average Score", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$overallAccuracy%", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = EmeraldSuccess)
                                Text("${quizzes.size} Quizzes Taken", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(14.dp)
                        ) {
                            Column {
                                Text("Active Streak", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${userProfile?.streakDays ?: 1} Days", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                                Text("Personal Best: 7 Days", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Weak Topics to Revise
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = "Weak Areas", tint = RoseError, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Suggested Revision Focus", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    val weakTopics = listOf("Calculus Chain Rule", "Cellular Mitosis Stages", "Newton's Third Law Pairs")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        weakTopics.forEach { topic ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(RoseError.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(topic, fontSize = 10.sp, color = RoseError, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Achievement Badges
        item {
            Text(
                text = "Milestones & Badges",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(badges) { badge ->
                    Card(
                        modifier = Modifier.width(130.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (badge.isUnlocked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (badge.isUnlocked) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color(0xFF94A3B8).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = badge.title,
                                    tint = if (badge.isUnlocked) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = badge.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = badge.description,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Rewarded Ad Option (AdMob Monetization Section)
        item {
            RewardedAdCard(
                bonusTokens = 10,
                onWatchAd = onWatchRewardedAd
            )
        }

        // AdMob Banner Unit (Progress view placement)
        item {
            AdMobBannerCard()
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
