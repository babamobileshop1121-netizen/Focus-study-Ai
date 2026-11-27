package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val streakDays: Int = 0,
    val totalFocusMinutes: Int = 0,
    val totalQuizzesTaken: Int = 0,
    val averageQuizScore: Int = 0,
    val lastActiveDate: String = "",
    val coins: Int = 100,
    val referralCode: String = "",
    val unlockedItems: List<String> = emptyList(),
    val referredUsersCount: Int = 0,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "displayName" to displayName,
        "email" to email,
        "photoUrl" to photoUrl,
        "streakDays" to streakDays,
        "totalFocusMinutes" to totalFocusMinutes,
        "totalQuizzesTaken" to totalQuizzesTaken,
        "averageQuizScore" to averageQuizScore,
        "lastActiveDate" to lastActiveDate,
        "coins" to coins,
        "referralCode" to referralCode,
        "unlockedItems" to unlockedItems,
        "referredUsersCount" to referredUsersCount,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }

    fun toUpdateMap(): Map<String, Any?> = mapOf(
        "displayName" to displayName,
        "photoUrl" to photoUrl,
        "streakDays" to streakDays,
        "totalFocusMinutes" to totalFocusMinutes,
        "totalQuizzesTaken" to totalQuizzesTaken,
        "averageQuizScore" to averageQuizScore,
        "lastActiveDate" to lastActiveDate,
        "coins" to coins,
        "referralCode" to referralCode,
        "unlockedItems" to unlockedItems,
        "referredUsersCount" to referredUsersCount,
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }
}

@Keep
@IgnoreExtraProperties
data class StudySession(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val subject: String = "",
    val chapter: String = "",
    val scheduledDate: String = "",
    val scheduledTime: String = "",
    val durationMinutes: Int = 30,
    @get:PropertyName("isCompleted")
    @PropertyName("isCompleted")
    val isCompleted: Boolean = false,
    @get:PropertyName("isRecurring")
    @PropertyName("isRecurring")
    val isRecurring: Boolean = false,
    val recurrenceRule: String = "NONE",
    val notes: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "id" to id,
        "userId" to userId,
        "subject" to subject,
        "chapter" to chapter,
        "scheduledDate" to scheduledDate,
        "scheduledTime" to scheduledTime,
        "durationMinutes" to durationMinutes,
        "isCompleted" to isCompleted,
        "isRecurring" to isRecurring,
        "recurrenceRule" to recurrenceRule,
        "notes" to notes,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "subject" to subject,
        "chapter" to chapter,
        "scheduledDate" to scheduledDate,
        "scheduledTime" to scheduledTime,
        "durationMinutes" to durationMinutes,
        "isCompleted" to isCompleted,
        "isRecurring" to isRecurring,
        "recurrenceRule" to recurrenceRule,
        "notes" to notes,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FocusLog(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val subject: String = "",
    val targetMinutes: Int = 25,
    val actualMinutes: Int = 25,
    val blockedAppsCount: Int = 0,
    val completed: Boolean = true,
    val date: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "id" to id,
        "userId" to userId,
        "subject" to subject,
        "targetMinutes" to targetMinutes,
        "actualMinutes" to actualMinutes,
        "blockedAppsCount" to blockedAppsCount,
        "completed" to completed,
        "date" to date,
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Quiz(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val subject: String = "",
    val questionCount: Int = 5,
    val score: Int = 0,
    val totalQuestions: Int = 5,
    val accuracy: Int = 0,
    val questionsJson: String = "",
    val sourceType: String = "PHOTO",
    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "id" to id,
        "userId" to userId,
        "title" to title,
        "subject" to subject,
        "questionCount" to questionCount,
        "score" to score,
        "totalQuestions" to totalQuestions,
        "accuracy" to accuracy,
        "questionsJson" to questionsJson,
        "sourceType" to sourceType,
        "createdAt" to FieldValue.serverTimestamp()
    )
}

data class QuizQuestion(
    val id: String,
    val question: String,
    val options: List<String>,
    val correctAnswerIndex: Int,
    val explanation: String,
    val type: String = "MCQ", // MCQ, TRUE_FALSE, SHORT_ANSWER
    val userAnswerIndex: Int? = null,
    val isCorrect: Boolean? = null
)

data class BlockedAppInfo(
    val packageName: String,
    val appName: String,
    val category: String = "Social",
    val isBlocked: Boolean = false
)
