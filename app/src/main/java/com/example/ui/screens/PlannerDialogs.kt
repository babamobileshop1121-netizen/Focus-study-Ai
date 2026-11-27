package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StudySession
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TealSecondary
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

val SUBJECT_PRESETS = listOf(
    "Mathematics",
    "Physics",
    "Chemistry",
    "Biology",
    "Computer Science",
    "World History",
    "Literature",
    "Economics"
)

fun getSubjectColorPalette(subject: String): Pair<Color, Color> {
    return when (subject.lowercase()) {
        "mathematics" -> Color(0xFF6366F1) to Color(0xFF818CF8)
        "physics" -> Color(0xFF06B6D4) to Color(0xFF38BDF8)
        "chemistry" -> Color(0xFFF59E0B) to Color(0xFFFB923C)
        "biology" -> Color(0xFF10B981) to Color(0xFF34D399)
        "computer science" -> Color(0xFF3B82F6) to Color(0xFF60A5FA)
        "world history" -> Color(0xFFEC4899) to Color(0xFFF472B6)
        "literature" -> Color(0xFFA855F7) to Color(0xFFC084FC)
        "economics" -> Color(0xFFEAB308) to Color(0xFFFACC15)
        else -> IndigoPrimary to TealSecondary
    }
}

fun formatDurationCompact(minutes: Int): String {
    val hrs = minutes / 60
    val mins = minutes % 60
    return when {
        hrs > 0 && mins > 0 -> "${hrs}h ${mins}m"
        hrs > 0 -> "${hrs}h 00m"
        else -> "${mins}m"
    }
}

fun computeEndTime(startTime: String, durationMinutes: Int): String {
    return try {
        val parsed = LocalTime.parse(startTime.trim(), DateTimeFormatter.ofPattern("H:mm"))
        parsed.plusMinutes(durationMinutes.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) {
        ""
    }
}

fun normalizeTimeSortKey(time: String): Int {
    return try {
        val parsed = LocalTime.parse(time.trim(), DateTimeFormatter.ofPattern("H:mm"))
        parsed.hour * 60 + parsed.minute
    } catch (_: Exception) {
        9999
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SessionEditorDialog(
    initialDate: String,
    existingSession: StudySession? = null,
    onDismiss: () -> Unit,
    onConfirm: (StudySession) -> Unit
) {
    val isEditing = existingSession != null
    var subject by remember { mutableStateOf(existingSession?.subject?.ifBlank { SUBJECT_PRESETS.first() } ?: SUBJECT_PRESETS.first()) }
    var chapter by remember { mutableStateOf(existingSession?.chapter ?: "") }
    var scheduledDate by remember { mutableStateOf(existingSession?.scheduledDate?.ifBlank { initialDate } ?: initialDate) }
    var scheduledTime by remember { mutableStateOf(existingSession?.scheduledTime?.ifBlank { "09:00" } ?: "09:00") }
    var durationMinutes by remember { mutableIntStateOf(existingSession?.durationMinutes ?: 45) }
    var isRecurring by remember { mutableStateOf(existingSession?.isRecurring ?: false) }
    var notes by remember { mutableStateOf(existingSession?.notes ?: "") }
    var expandedDropdown by remember { mutableStateOf(false) }

    val dateOptions = remember {
        val today = LocalDate.now()
        (0..6).map { offset ->
            val d = today.plusDays(offset.toLong())
            val label = when (offset) {
                0 -> "Today"
                1 -> "Tmrw"
                else -> d.format(DateTimeFormatter.ofPattern("EEE d"))
            }
            label to d.format(DateTimeFormatter.ISO_LOCAL_DATE)
        }
    }

    val quickTimes = listOf("08:00", "09:30", "11:00", "14:00", "16:30", "19:00")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (isEditing) "Reschedule Study Block" else "New Timetable Block",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Plan subject, start slot, and focus duration",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = expandedDropdown,
                    onExpandedChange = { expandedDropdown = it }
                ) {
                    val (subjectAccent, _) = getSubjectColorPalette(subject)
                    OutlinedTextField(
                        value = subject,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Subject") },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(subjectAccent)
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false }
                    ) {
                        SUBJECT_PRESETS.forEach { preset ->
                            val (c, _) = getSubjectColorPalette(preset)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(c)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(preset)
                                    }
                                },
                                onClick = {
                                    subject = preset
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = chapter,
                    onValueChange = { chapter = it },
                    label = { Text("Topic / Chapter") },
                    placeholder = { Text("e.g. Calculus Derivatives") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Day selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "DATE",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        dateOptions.forEach { (label, isoDate) ->
                            val selected = scheduledDate == isoDate
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) IndigoPrimary
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    )
                                    .clickable { scheduledDate = isoDate }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Time selector + manual input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "START TIME (24H)",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickTimes.forEach { slot ->
                            val selected = scheduledTime == slot
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) IndigoPrimary
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    )
                                    .clickable { scheduledTime = slot }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = slot,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = scheduledTime,
                        onValueChange = { if (it.length <= 5) scheduledTime = it },
                        label = { Text("Custom time (HH:mm)") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = "Start time",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Duration Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "DURATION",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(25, 45, 60, 90, 120).forEach { mins ->
                            val selected = durationMinutes == mins
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) IndigoPrimary
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    )
                                    .clickable { durationMinutes = mins }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${mins}m",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Repeat Daily", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Include in daily recurring rotation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isRecurring,
                        onCheckedChange = { isRecurring = it }
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Study Goal / Notes") },
                    placeholder = { Text("e.g. Complete problem set 1-15") },
                    maxLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanTime = scheduledTime.trim().ifBlank { "09:00" }
                    val session = if (existingSession != null) {
                        existingSession.copy(
                            subject = subject,
                            chapter = chapter.ifBlank { "$subject Revision" },
                            scheduledDate = scheduledDate,
                            scheduledTime = cleanTime,
                            durationMinutes = durationMinutes,
                            isRecurring = isRecurring,
                            recurrenceRule = if (isRecurring) "DAILY" else "NONE",
                            notes = notes
                        )
                    } else {
                        StudySession(
                            id = UUID.randomUUID().toString(),
                            subject = subject,
                            chapter = chapter.ifBlank { "$subject Revision" },
                            scheduledDate = scheduledDate,
                            scheduledTime = cleanTime,
                            durationMinutes = durationMinutes,
                            isCompleted = false,
                            isRecurring = isRecurring,
                            recurrenceRule = if (isRecurring) "DAILY" else "NONE",
                            notes = notes
                        )
                    }
                    onConfirm(session)
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isEditing) "Save Changes" else "Add to Timetable")
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
fun RoutinePresetsDialog(
    targetDate: String,
    onDismiss: () -> Unit,
    onApplyPreset: (List<StudySession>) -> Unit
) {
    val presets = remember(targetDate) {
        listOf(
            Triple(
                "STEM Exam Sprint",
                "High-intensity block schedule across 4 core sciences",
                listOf(
                    StudySession(id = UUID.randomUUID().toString(), subject = "Mathematics", chapter = "Calculus & Limits", scheduledDate = targetDate, scheduledTime = "08:30", durationMinutes = 45, notes = "Timed derivative drills"),
                    StudySession(id = UUID.randomUUID().toString(), subject = "Physics", chapter = "Mechanics & Energy", scheduledDate = targetDate, scheduledTime = "10:00", durationMinutes = 60, notes = "Free-body problem sets"),
                    StudySession(id = UUID.randomUUID().toString(), subject = "Chemistry", chapter = "Bonding & Kinetics", scheduledDate = targetDate, scheduledTime = "14:00", durationMinutes = 45, notes = "Reaction mechanisms"),
                    StudySession(id = UUID.randomUUID().toString(), subject = "Biology", chapter = "Cellular Genetics", scheduledDate = targetDate, scheduledTime = "16:30", durationMinutes = 30, notes = "Active recall flashcards")
                )
            ),
            Triple(
                "Deep Focus Architecture",
                "Long uninterrupted blocks for complex problem solving",
                listOf(
                    StudySession(id = UUID.randomUUID().toString(), subject = "Computer Science", chapter = "Algorithms & Trees", scheduledDate = targetDate, scheduledTime = "09:00", durationMinutes = 90, notes = "Graph traversal practice"),
                    StudySession(id = UUID.randomUUID().toString(), subject = "Mathematics", chapter = "Linear Algebra", scheduledDate = targetDate, scheduledTime = "13:30", durationMinutes = 60, notes = "Eigenvalue proofs"),
                    StudySession(id = UUID.randomUUID().toString(), subject = "Economics", chapter = "Macro Policy Models", scheduledDate = targetDate, scheduledTime = "16:00", durationMinutes = 45, notes = "Case study summary")
                )
            ),
            Triple(
                "Evening Review Rotation",
                "Focused humanities & revision wind-down",
                listOf(
                    StudySession(id = UUID.randomUUID().toString(), subject = "World History", chapter = "Modern Revolutions", scheduledDate = targetDate, scheduledTime = "18:00", durationMinutes = 45, notes = "Timeline synthesis"),
                    StudySession(id = UUID.randomUUID().toString(), subject = "Literature", chapter = "Critical Essay Structure", scheduledDate = targetDate, scheduledTime = "19:30", durationMinutes = 45, notes = "Thesis & textual analysis")
                )
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = IndigoPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Routine Templates", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Populate $targetDate with a structured study timetable:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                presets.forEach { (title, subtitle, sessionList) ->
                    val totalMins = sessionList.sumOf { it.durationMinutes }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onApplyPreset(sessionList) },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${sessionList.size} blocks · ${formatDurationCompact(totalMins)}",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = IndigoPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                sessionList.forEach { s ->
                                    val (accent, _) = getSubjectColorPalette(s.subject)
                                    Box(
                                        modifier = Modifier
                                            .weight(s.durationMinutes.toFloat())
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(accent)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
