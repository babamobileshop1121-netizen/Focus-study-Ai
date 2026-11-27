package com.example

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.core.AdMobManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.configureAppCheck
import com.example.data.local.AppDatabase
import com.example.data.local.FocusSessionEntity
import com.example.data.model.FocusLog
import com.example.data.model.Quiz
import com.example.data.model.StudySession
import com.example.data.model.UserProfile
import com.example.data.repository.AiQuizRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.FirestoreRepository
import com.example.ui.chat.ChatScreen
import com.example.ui.components.CoinBalanceBadge
import com.example.ui.components.RewardShopDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlannerScreen
import com.example.ui.screens.ProgressScreen
import com.example.ui.screens.QuizScreen
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FocusStudyTheme
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.TealSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

enum class AppTab(val title: String) {
    HOME("Home"),
    PLANNER("Planner"),
    FOCUS("Focus Lock"),
    QUIZ("AI Quiz"),
    PROGRESS("Progress")
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        // STEP 2: Edge-to-edge support
        enableEdgeToEdge()

        // STEP 3: Initialize Google Mobile Ads SDK
        try {
            com.google.android.gms.ads.MobileAds.initialize(this) {}
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "MobileAds init: ${e.message}")
        }

        setContent {
            FocusStudyTheme {
                MainAppHost()
            }
        }
    }
}

@Composable
fun MainAppHost() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val authRepo = remember { AuthRepository(context) }
    val firestoreRepo = remember { FirestoreRepository(context) }
    val aiQuizRepo = remember { AiQuizRepository() }
    val localDb = remember { AppDatabase.getInstance(context) }

    val currentUser by authRepo.currentUser.collectAsState()
    var isGuestMode by remember { mutableStateOf(false) }

    // Silent startup auto sign-in attempt
    LaunchedEffect(Unit) {
        authRepo.attemptAutoSignIn(
            scope = scope,
            onSuccess = {},
            onFailure = {}
        )
    }

    if (currentUser == null && !isGuestMode) {
        AuthScreen(
            authRepository = authRepo,
            onAuthSuccess = {},
            onContinueAsGuest = { isGuestMode = true }
        )
    } else {
        val uid = currentUser?.uid ?: "local_guest_user"
        key(uid) {
            MainAppScaffold(
                userId = uid,
                displayName = currentUser?.displayName ?: "Focus Student",
                email = currentUser?.email ?: "student@focusstudy.ai",
                isGuest = isGuestMode,
                authRepo = authRepo,
                firestoreRepo = firestoreRepo,
                aiQuizRepo = aiQuizRepo,
                localDb = localDb,
                onSignOut = {
                    isGuestMode = false
                    authRepo.signOut(scope)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    userId: String,
    displayName: String,
    email: String,
    isGuest: Boolean,
    authRepo: AuthRepository,
    firestoreRepo: FirestoreRepository,
    aiQuizRepo: AiQuizRepository,
    localDb: AppDatabase,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adMobManager = remember { AdMobManager(context) }

    var currentTab by remember { mutableStateOf(AppTab.HOME) }
    var showUserMenu by remember { mutableStateOf(false) }
    var showRewardShopDialog by remember { mutableStateOf(false) }
    var showChatScreen by remember { mutableStateOf(false) }
    var guestCoins by remember { mutableIntStateOf(100) }
    val guestUnlockedItems = remember { mutableStateListOf<String>() }
    var pendingFocusSubject by remember { mutableStateOf<String?>(null) }
    var pendingFocusDuration by remember { mutableStateOf<Long?>(null) }

    // Observables from Room
    val activeSession by localDb.focusDao().getActiveSessionFlow().collectAsState(initial = null)
    var lastActiveFocusSession by remember { mutableStateOf<FocusSessionEntity?>(null) }

    // Preload interstitial ad whenever entering Focus Lock, AI Photo-to-Quiz, or AI Chat
    LaunchedEffect(currentTab, showChatScreen) {
        if (currentTab == AppTab.FOCUS || currentTab == AppTab.QUIZ || showChatScreen) {
            adMobManager.loadInterstitial()
        }
    }

    // Local in-memory logs & quizzes for offline/guest mode
    val localFocusLogs = remember { mutableStateListOf<FocusLog>() }
    val localQuizzes = remember { mutableStateListOf<Quiz>() }

    // Trigger FocusLog persistence and interstitial ad only after timer reaches 00:00 and session completes
    LaunchedEffect(activeSession) {
        val current = activeSession
        if (current != null && current.isActive) {
            lastActiveFocusSession = current
            adMobManager.loadInterstitial()
        } else if (lastActiveFocusSession != null) {
            val finished = lastActiveFocusSession!!
            lastActiveFocusSession = null

            val targetMins = (finished.targetSeconds / 60L).toInt().coerceAtLeast(1)
            val elapsedSecs = (finished.targetSeconds - finished.remainingSeconds).coerceAtLeast(0L)
            val actualMins = (elapsedSecs / 60L).toInt().coerceAtLeast(if (elapsedSecs > 0L) 1 else 0)
            val wasCompleted = finished.remainingSeconds <= 1L

            val newLog = FocusLog(
                id = UUID.randomUUID().toString(),
                userId = userId,
                subject = finished.subject,
                targetMinutes = targetMins,
                actualMinutes = actualMins,
                blockedAppsCount = finished.blockedPackages.split(",").count { it.isNotBlank() },
                completed = wasCompleted,
                date = LocalDate.now().toString()
            )
            localFocusLogs.add(0, newLog)
            if (!isGuest) {
                scope.launch {
                    firestoreRepo.addFocusLog(newLog)
                }
            }

            // Always trigger AdMob Interstitial when focus session terminates (completed or stopped early)
            val activity = context as? Activity
            if (activity != null) {
                adMobManager.showInterstitial(activity) {}
            }
        }
    }

    // Observables from Firestore (only active when signed in, never in Guest mode)
    val userProfile by remember(isGuest, userId) {
        if (isGuest) flowOf(null) else firestoreRepo.observeUserProfile(userId)
    }.collectAsState(initial = null)
    val firestoreSessions by remember(isGuest, userId) {
        if (isGuest) flowOf(emptyList()) else firestoreRepo.observeSessions(userId)
    }.collectAsState(initial = emptyList())
    val firestoreLogs by remember(isGuest, userId) {
        if (isGuest) flowOf(emptyList()) else firestoreRepo.observeFocusLogs(userId)
    }.collectAsState(initial = emptyList())
    val firestoreQuizzes by remember(isGuest, userId) {
        if (isGuest) flowOf(emptyList()) else firestoreRepo.observeQuizzes(userId)
    }.collectAsState(initial = emptyList())

    val todayStr = remember { LocalDate.now().toString() }
    val tomorrowStr = remember { LocalDate.now().plusDays(1).toString() }

    // Local in-memory session backup for offline/guest mode
    val localSessions = remember {
        mutableStateListOf(
            StudySession(
                id = "sample_session_1",
                userId = userId,
                subject = "Mathematics",
                chapter = "Calculus Derivatives & Integrals",
                scheduledDate = todayStr,
                scheduledTime = "09:00",
                durationMinutes = 45,
                isCompleted = false,
                notes = "Review practice set #1"
            ),
            StudySession(
                id = "sample_session_2",
                userId = userId,
                subject = "Physics",
                chapter = "Newton's Laws & Momentum",
                scheduledDate = todayStr,
                scheduledTime = "11:30",
                durationMinutes = 60,
                isCompleted = true,
                notes = "Completed chapter summary"
            ),
            StudySession(
                id = "sample_session_3",
                userId = userId,
                subject = "Biology",
                chapter = "Cellular Respiration & ATP",
                scheduledDate = todayStr,
                scheduledTime = "15:00",
                durationMinutes = 30,
                isCompleted = false,
                notes = "Prep for Friday test"
            ),
            StudySession(
                id = "sample_session_4",
                userId = userId,
                subject = "Computer Science",
                chapter = "Graph Traversal & Dynamic Programming",
                scheduledDate = tomorrowStr,
                scheduledTime = "10:00",
                durationMinutes = 60,
                isCompleted = false,
                notes = "Timed coding drills"
            )
        )
    }

    val activeSessionsList = remember(firestoreSessions, localSessions) {
        if (firestoreSessions.isNotEmpty()) firestoreSessions else localSessions
    }

    // Auto-create initial UserProfile in Firestore on first login
    LaunchedEffect(userId) {
        if (!isGuest && userProfile == null) {
            firestoreRepo.saveUserProfile(
                UserProfile(
                    userId = userId,
                    displayName = displayName,
                    email = email,
                    streakDays = 3,
                    totalFocusMinutes = 120,
                    totalQuizzesTaken = 4,
                    averageQuizScore = 85,
                    lastActiveDate = "2026-11-25"
                )
            )
        }
    }

    val effectiveCoins = if (isGuest || userProfile == null) guestCoins else userProfile!!.coins
    val effectiveUnlockedItems = if (isGuest || userProfile == null) guestUnlockedItems.toList() else userProfile!!.unlockedItems

    // Handle back button: return to Home tab if on sub-tab
    BackHandler(enabled = currentTab != AppTab.HOME && !showChatScreen) {
        currentTab = AppTab.HOME
    }

    if (showChatScreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            ChatScreen(
                onBack = { showChatScreen = false },
                onRequestUnlockAd = { onAdCompleted ->
                    val activity = context as? Activity
                    if (activity != null) {
                        adMobManager.showInterstitial(activity) {
                            onAdCompleted()
                        }
                    } else {
                        onAdCompleted()
                    }
                }
            )
        }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_app_icon_fg),
                            contentDescription = "FocusStudy AI",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "FocusStudy AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                actions = {
                    // Active Focus Badge
                    if (activeSession?.isActive == true) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(TealSecondary.copy(alpha = 0.18f))
                                .clickable { currentTab = AppTab.FOCUS }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "LOCK ON",
                                color = TealSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // AI Tutor Chat Trigger
                    IconButton(
                        onClick = { showChatScreen = true },
                        modifier = Modifier.testTag("open_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Study Tutor",
                            tint = TealSecondary
                        )
                    }

                    // Study Coins Badge & Store Trigger
                    CoinBalanceBadge(
                        coins = effectiveCoins,
                        onClick = { showRewardShopDialog = true }
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    // User Profile Avatar & Menu
                    Box {
                        IconButton(onClick = { showUserMenu = true }) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(IndigoPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = displayName.firstOrNull()?.uppercase() ?: "S",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showUserMenu,
                            onDismissRequest = { showUserMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(displayName, fontWeight = FontWeight.Bold)
                                        Text(email, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {}
                            )
                            DropdownMenuItem(
                                text = { Text(if (isGuest) "Switch Account" else "Sign Out") },
                                leadingIcon = {
                                    Icon(Icons.Default.ExitToApp, contentDescription = "Sign Out")
                                },
                                onClick = {
                                    showUserMenu = false
                                    onSignOut()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                AppTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    val icon = when (tab) {
                        AppTab.HOME -> Icons.Default.Home
                        AppTab.PLANNER -> Icons.Default.CalendarMonth
                        AppTab.FOCUS -> Icons.Default.Lock
                        AppTab.QUIZ -> Icons.Default.Psychology
                        AppTab.PROGRESS -> Icons.Default.BarChart
                    }

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(imageVector = icon, contentDescription = tab.title)
                        },
                        label = {
                            Text(text = tab.title, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            indicatorColor = IndigoPrimary
                        ),
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.HOME -> {
                    HomeScreen(
                        userProfile = userProfile,
                        sessions = activeSessionsList,
                        onStartStudyingClicked = { currentTab = AppTab.FOCUS },
                        onNavigateToPlanner = { currentTab = AppTab.PLANNER },
                        onNavigateToQuiz = { currentTab = AppTab.QUIZ },
                        onSessionCheckToggled = { session ->
                            val updated = session.copy(isCompleted = !session.isCompleted)
                            if (!isGuest) {
                                scope.launch {
                                    firestoreRepo.updateSession(updated)
                                }
                            }
                            val idx = localSessions.indexOfFirst { it.id == session.id }
                            if (idx != -1) localSessions[idx] = updated
                        },
                        onOpenRewardShop = { showRewardShopDialog = true },
                        onOpenChat = { showChatScreen = true },
                        displayedCoins = effectiveCoins
                    )
                }

                AppTab.PLANNER -> {
                    PlannerScreen(
                        sessions = activeSessionsList,
                        userCoins = effectiveCoins,
                        onAddSession = { newSession ->
                            val sessionWithUser = newSession.copy(userId = userId)
                            if (!isGuest) {
                                scope.launch {
                                    firestoreRepo.addSession(sessionWithUser)
                                }
                            }
                            localSessions.add(0, sessionWithUser)
                            Toast.makeText(context, "Session added to timetable!", Toast.LENGTH_SHORT).show()
                        },
                        onUpdateSession = { updatedSession ->
                            if (!isGuest) {
                                scope.launch {
                                    firestoreRepo.updateSession(updatedSession)
                                }
                            }
                            val idx = localSessions.indexOfFirst { it.id == updatedSession.id }
                            if (idx != -1) localSessions[idx] = updatedSession
                        },
                        onDeleteSession = { sessionId ->
                            if (!isGuest) {
                                scope.launch {
                                    firestoreRepo.deleteSession(userId, sessionId)
                                }
                            }
                            localSessions.removeAll { it.id == sessionId }
                        },
                        onStartFocusForSession = { subject, durationMinutes ->
                            pendingFocusSubject = subject
                            pendingFocusDuration = durationMinutes.toLong()
                            currentTab = AppTab.FOCUS
                        },
                        onOpenRewardShop = { showRewardShopDialog = true },
                        onAddCoins = { amount ->
                            if (isGuest || userProfile == null) {
                                guestCoins += amount
                            } else {
                                scope.launch {
                                    firestoreRepo.addCoins(userId, amount)
                                }
                            }
                        }
                    )
                }

                AppTab.FOCUS -> {
                    FocusScreen(
                        activeSession = activeSession,
                        initialSubject = pendingFocusSubject,
                        initialDurationMinutes = pendingFocusDuration,
                        onShowInterstitialAd = {
                            val activity = context as? Activity
                            if (activity != null) {
                                adMobManager.showInterstitial(activity) {}
                            }
                        },
                        onWatchRewardedAd = { onReward ->
                            val activity = context as? Activity
                            if (activity != null) {
                                adMobManager.showRewarded(
                                    activity = activity,
                                    onUserEarnedReward = onReward,
                                    onDismissed = {}
                                )
                            } else {
                                onReward(50)
                            }
                        },
                        onAddCoins = { amount ->
                            if (isGuest || userProfile == null) {
                                guestCoins += amount
                            } else {
                                scope.launch {
                                    firestoreRepo.addCoins(userId, amount)
                                }
                            }
                        },
                        onFocusSessionFinished = { subject, targetMins, actualMins ->
                            val log = FocusLog(
                                id = UUID.randomUUID().toString(),
                                userId = userId,
                                subject = subject,
                                targetMinutes = targetMins,
                                actualMinutes = actualMins,
                                blockedAppsCount = 4,
                                completed = true,
                                date = LocalDate.now().toString()
                            )
                            localFocusLogs.add(0, log)
                            if (!isGuest) {
                                scope.launch {
                                    firestoreRepo.addFocusLog(log)
                                }
                            }
                        }
                    )
                }

                AppTab.QUIZ -> {
                    QuizScreen(
                        aiQuizRepository = aiQuizRepo,
                        onSaveQuizResult = { quiz ->
                            val quizWithUser = quiz.copy(userId = userId)
                            localQuizzes.add(0, quizWithUser)
                            if (!isGuest) {
                                scope.launch {
                                    firestoreRepo.saveQuiz(quizWithUser)
                                }
                            }
                            Toast.makeText(context, "Quiz saved to study history!", Toast.LENGTH_SHORT).show()
                        },
                        onShowInterstitial = { onDismissed ->
                            val activity = context as? Activity
                            if (activity != null) {
                                adMobManager.showInterstitial(activity, onDismissed)
                            } else {
                                onDismissed()
                            }
                        },
                        onWatchRewardedAd = { onReward ->
                            val activity = context as? Activity
                            if (activity != null) {
                                adMobManager.showRewarded(
                                    activity = activity,
                                    onUserEarnedReward = onReward,
                                    onDismissed = {}
                                )
                            }
                        }
                    )
                }

                AppTab.PROGRESS -> {
                    ProgressScreen(
                        userProfile = userProfile,
                        sessions = activeSessionsList,
                        focusLogs = if (firestoreLogs.isNotEmpty()) firestoreLogs else localFocusLogs,
                        quizzes = if (firestoreQuizzes.isNotEmpty()) firestoreQuizzes else localQuizzes,
                        onWatchRewardedAd = {
                            val activity = context as? Activity
                            if (activity != null) {
                                adMobManager.showRewarded(
                                    activity = activity,
                                    onUserEarnedReward = { amount ->
                                        Toast.makeText(context, "🎉 Rewarded ad completed! +$amount AI Quiz Credits granted.", Toast.LENGTH_LONG).show()
                                    },
                                    onDismissed = {}
                                )
                            } else {
                                Toast.makeText(context, "🎉 +10 AI Quiz Credits granted.", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }
        }
    }

    if (showRewardShopDialog) {
        val refCode = userProfile?.referralCode?.ifBlank { "FOCUS-STUDY-${userId.take(6).uppercase()}" } ?: "FOCUS-STUDY-8842"
        RewardShopDialog(
            userCoins = effectiveCoins,
            referralCode = refCode,
            unlockedItems = effectiveUnlockedItems,
            onDismiss = { showRewardShopDialog = false },
            onWatchRewardedAd = {
                val activity = context as? Activity
                if (activity != null) {
                    adMobManager.showRewarded(
                        activity = activity,
                        onUserEarnedReward = { amount ->
                            if (isGuest || userProfile == null) {
                                guestCoins += 50
                                Toast.makeText(context, "🎉 +50 Coins Added from Sponsored Ad!", Toast.LENGTH_SHORT).show()
                            } else {
                                scope.launch {
                                    firestoreRepo.addCoins(userId, 50)
                                    Toast.makeText(context, "🎉 +50 Coins Added from Sponsored Ad!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDismissed = {}
                    )
                }
            },
            onUnlockItem = { item ->
                if (isGuest || userProfile == null) {
                    if (guestCoins >= item.costCoins && !guestUnlockedItems.contains(item.id)) {
                        guestCoins -= item.costCoins
                        guestUnlockedItems.add(item.id)
                        Toast.makeText(context, "🎉 Unlocked ${item.title}!", Toast.LENGTH_SHORT).show()
                    } else if (guestUnlockedItems.contains(item.id)) {
                        Toast.makeText(context, "${item.title} is already unlocked!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Need ${item.costCoins} coins to unlock!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    scope.launch {
                        val res = firestoreRepo.unlockItem(userId, item.id, item.costCoins)
                        if (res.getOrDefault(false)) {
                            Toast.makeText(context, "🎉 Unlocked ${item.title}!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Need ${item.costCoins} coins to unlock!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onRedeemReferralCode = { code ->
                if (isGuest || userProfile == null) {
                    guestCoins += 100
                    Toast.makeText(context, "🎉 Referral code accepted! +100 Coins credited!", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        val res = firestoreRepo.redeemReferral(userId, code)
                        if (res.isSuccess) {
                            Toast.makeText(context, "🎉 Referral code accepted! +100 Coins credited!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }
}
