package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.data.model.StudySession
import com.example.ui.components.AdMobBannerCard
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TealSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class CalendarDay(
    val dayName: String,
    val dayNumber: String,
    val dateString: String,
    val isToday: Boolean
)

private enum class TimetableViewMode {
    TIMELINE,
    WEEK_GRID
}

@Composable
fun PlannerScreen(
    sessions: List<StudySession>,
    userCoins: Int,
    onAddSession: (StudySession) -> Unit,
    onUpdateSession: (StudySession) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStartFocusForSession: (subject: String, durationMinutes: Int) -> Unit = { _, _ -> },
    onOpenRewardShop: () -> Unit = {},
    onAddCoins: (amount: Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val todayDate = remember { LocalDate.now() }
    val todayIso = remember(todayDate) { todayDate.format(DateTimeFormatter.ISO_LOCAL_DATE) }
    val monthHeader = remember(todayDate) {
        todayDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")).uppercase()
    }

    var showEditorDialog by remember { mutableStateOf(false) }
    var editingSession by remember { mutableStateOf<StudySession?>(null) }
    var editorInitialDate by remember { mutableStateOf(todayIso) }
    var showPresetDialog by remember { mutableStateOf(false) }
    var selectedFilterSubject by remember { mutableStateOf("All") }
    var selectedDayFilter by remember { mutableStateOf(todayIso) }
    var viewMode by remember { mutableStateOf(TimetableViewMode.TIMELINE) }

    val calendarDays = remember(todayDate) {
        (0..6).map { offset ->
            val date = todayDate.plusDays(offset.toLong())
            CalendarDay(
                dayName = date.format(DateTimeFormatter.ofPattern("EEE")),
                dayNumber = date.format(DateTimeFormatter.ofPattern("d")),
                dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                isToday = offset == 0
            )
        }
    }

    val filteredSessions = remember(sessions, selectedFilterSubject, selectedDayFilter) {
        sessions
            .filter { session ->
                val matchesSubject = selectedFilterSubject == "All" ||
                    session.subject.equals(selectedFilterSubject, ignoreCase = true)
                val matchesDay = selectedDayFilter == "All" || session.scheduledDate == selectedDayFilter
                matchesSubject && matchesDay
            }
            .sortedWith(
                compareBy<StudySession> { it.scheduledDate }
                    .thenBy { normalizeTimeSortKey(it.scheduledTime) }
            )
    }

    val totalPlannedMinutes = remember(filteredSessions) { filteredSessions.sumOf { it.durationMinutes } }
    val completedMinutes = remember(filteredSessions) {
        filteredSessions.filter { it.isCompleted }.sumOf { it.durationMinutes }
    }
    val progressFraction = if (totalPlannedMinutes > 0) {
        (completedMinutes.toFloat() / totalPlannedMinutes.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val upcomingSession = remember(filteredSessions) { filteredSessions.firstOrNull { !it.isCompleted } }

    val targetPresetDate = if (selectedDayFilter == "All") todayIso else selectedDayFilter

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingSession = null
                    editorInitialDate = targetPresetDate
                    showEditorDialog = true
                },
                containerColor = IndigoPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_session_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Schedule Study Session")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                            text = monthHeader,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.4.sp,
                            color = TealSecondary
                        )
                        Text(
                            text = "Academic Timetable",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            IconButton(
                                onClick = { showPresetDialog = true },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Routine Templates",
                                    tint = IndigoPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            IconButton(
                                onClick = onOpenRewardShop,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CardGiftcard,
                                    contentDescription = "Rewards & Referrals",
                                    tint = TealSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 7-Day Minimalist Calendar Strip
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "7-DAY SCHEDULE",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = if (selectedDayFilter == "All") "All 7 Days" else selectedDayFilter,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { selectedDayFilter = "All" }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                val isAllSelected = selectedDayFilter == "All"
                                Column(
                                    modifier = Modifier
                                        .width(50.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isAllSelected) IndigoPrimary
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        )
                                        .clickable {
                                            selectedDayFilter = "All"
                                            viewMode = TimetableViewMode.TIMELINE
                                        }
                                        .padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "ALL",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAllSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "${sessions.size}",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAllSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Box(modifier = Modifier.height(5.dp))
                                }
                            }

                            items(calendarDays) { day ->
                                val isSelected = selectedDayFilter == day.dateString
                                val daySubjects = sessions
                                    .filter { it.scheduledDate == day.dateString }
                                    .map { it.subject }
                                    .distinct()
                                    .take(3)

                                Column(
                                    modifier = Modifier
                                        .width(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) IndigoPrimary
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        )
                                        .border(
                                            width = if (day.isToday && !isSelected) 1.dp else 0.dp,
                                            color = if (day.isToday && !isSelected) IndigoPrimary.copy(alpha = 0.6f) else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            selectedDayFilter = day.dateString
                                            viewMode = TimetableViewMode.TIMELINE
                                        }
                                        .padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = day.dayName.uppercase(),
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = day.dayNumber,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier.height(5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        daySubjects.forEach { subj ->
                                            val (dotColor, _) = getSubjectColorPalette(subj)
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) Color.White else dotColor)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Executive Study Allocation & Completion Gauge
            item {
                TimetableAllocationCard(
                    sessions = filteredSessions,
                    isAllDays = selectedDayFilter == "All",
                    totalPlannedMinutes = totalPlannedMinutes,
                    completedMinutes = completedMinutes,
                    progressFraction = progressFraction
                )
            }

            // Next Scheduled Focus Block
            if (upcomingSession != null && viewMode == TimetableViewMode.TIMELINE) {
                item {
                    UpNextFocusCard(
                        session = upcomingSession,
                        onStartFocus = {
                            onStartFocusForSession(upcomingSession.subject, upcomingSession.durationMinutes)
                        }
                    )
                }
            }

            // View Mode Switcher & Subject Filters
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Segmented View Toggle
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(modifier = Modifier.padding(3.dp)) {
                            listOf(
                                Triple(TimetableViewMode.TIMELINE, "Timeline", Icons.Default.ViewAgenda),
                                Triple(TimetableViewMode.WEEK_GRID, "Week Grid", Icons.Default.GridView)
                            ).forEach { (mode, label, icon) ->
                                val active = viewMode == mode
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) IndigoPrimary else Color.Transparent)
                                        .clickable { viewMode = mode }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = if (viewMode == TimetableViewMode.TIMELINE) "${filteredSessions.size} blocks" else "7 days",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (viewMode == TimetableViewMode.TIMELINE) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val filterOptions = listOf("All") + SUBJECT_PRESETS
                        items(filterOptions) { subject ->
                            val isSelected = selectedFilterSubject == subject
                            val (sColor, _) = getSubjectColorPalette(subject)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) sColor
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    )
                                    .clickable { selectedFilterSubject = subject }
                                    .padding(horizontal = 11.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (subject != "All") {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color.White else sColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = subject,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Main Timetable Content (Chronological Timeline or Week Grid)
            if (viewMode == TimetableViewMode.WEEK_GRID) {
                items(calendarDays) { day ->
                    val daySessions = sessions.filter { it.scheduledDate == day.dateString }
                    WeekMatrixDayCard(
                        day = day,
                        sessions = daySessions,
                        onSelectDay = {
                            selectedDayFilter = day.dateString
                            viewMode = TimetableViewMode.TIMELINE
                        },
                        onQuickAddForDay = {
                            editingSession = null
                            editorInitialDate = day.dateString
                            showEditorDialog = true
                        }
                    )
                }
            } else if (filteredSessions.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Empty Timetable",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No study blocks scheduled",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Add a custom block or load a structured routine template.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        editingSession = null
                                        editorInitialDate = targetPresetDate
                                        showEditorDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Add Block")
                                }
                                OutlinedButton(
                                    onClick = { showPresetDialog = true },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Templates")
                                }
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = filteredSessions,
                    key = { _, session -> session.id }
                ) { index, session ->
                    TimelineBlockRow(
                        session = session,
                        isNextUp = upcomingSession?.id == session.id,
                        showDateBadge = selectedDayFilter == "All",
                        isLastInList = index == filteredSessions.lastIndex,
                        onToggleComplete = {
                            val newComplete = !session.isCompleted
                            onUpdateSession(session.copy(isCompleted = newComplete))
                            if (newComplete) {
                                onAddCoins(25)
                                Toast.makeText(
                                    context,
                                    "Session completed · +25 Study Coins",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onEdit = {
                            editingSession = session
                            editorInitialDate = session.scheduledDate.ifBlank { targetPresetDate }
                            showEditorDialog = true
                        },
                        onDelete = {
                            onDeleteSession(session.id)
                        },
                        onStartFocus = {
                            onStartFocusForSession(session.subject, session.durationMinutes)
                        }
                    )
                }
            }

            // Understated Student Rewards & Referral Hub Row
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenRewardShop() }
                        .testTag("refer_earn_banner"),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = TealSecondary.copy(alpha = 0.28f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(TealSecondary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CardGiftcard,
                                    contentDescription = "Rewards",
                                    tint = TealSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Refer Classmates · +100 Coins",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Balance: $userCoins coins · Unlock themes & AI perks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open Store",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // AdMob Banner Placement
            item {
                AdMobBannerCard()
                Spacer(modifier = Modifier.height(84.dp))
            }
        }
    }

    if (showEditorDialog) {
        SessionEditorDialog(
            initialDate = editorInitialDate,
            existingSession = editingSession,
            onDismiss = {
                showEditorDialog = false
                editingSession = null
            },
            onConfirm = { savedSession ->
                if (editingSession != null) {
                    onUpdateSession(savedSession)
                    Toast.makeText(context, "Timetable block updated", Toast.LENGTH_SHORT).show()
                } else {
                    onAddSession(savedSession)
                }
                showEditorDialog = false
                editingSession = null
            }
        )
    }

    if (showPresetDialog) {
        RoutinePresetsDialog(
            targetDate = targetPresetDate,
            onDismiss = { showPresetDialog = false },
            onApplyPreset = { presetSessions ->
                presetSessions.forEach { onAddSession(it) }
                showPresetDialog = false
                Toast.makeText(context, "Routine template applied to $targetPresetDate", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
