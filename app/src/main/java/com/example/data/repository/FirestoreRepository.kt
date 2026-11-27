package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.FocusLog
import com.example.data.model.Quiz
import com.example.data.model.StudySession
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val TAG = "FirestoreRepo"

class FirestoreRepository(private val context: Context) {
    private val firestore: FirebaseFirestore by lazy {
        val databaseId = context.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(databaseId)
    }

    private fun isAuthenticatedOwner(userId: String): Boolean {
        val currentUid = try {
            Firebase.auth.currentUser?.uid
        } catch (_: Exception) {
            null
        }
        return userId.isNotBlank() && currentUid != null && currentUid == userId
    }

    // --- User Profile ---
    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        if (!isAuthenticatedOwner(userId)) {
            return flowOf(null)
        }
        return firestore.collection("users")
            .document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w(TAG, "Stopped observing user profile: ${e.message}")
                emit(null)
            }
    }

    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        return try {
            require(profile.userId.isNotBlank()) { "userId must not be blank" }
            val docRef = firestore.collection("users").document(profile.userId)
            val doc = docRef.get().await()
            if (doc.exists()) {
                docRef.update(profile.toUpdateMap()).await()
            } else {
                docRef.set(profile.toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user profile", e)
            Result.failure(e)
        }
    }

    suspend fun addCoins(userId: String, amount: Int): Result<Int> {
        return try {
            require(userId.isNotBlank()) { "userId must not be blank" }
            val docRef = firestore.collection("users").document(userId)
            val doc = docRef.get().await()
            val currentCoins = doc.getLong("coins")?.toInt() ?: 100
            val newCoins = (currentCoins + amount).coerceAtLeast(0)
            docRef.update("coins", newCoins, "updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp()).await()
            Result.success(newCoins)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add coins", e)
            Result.failure(e)
        }
    }

    suspend fun unlockItem(userId: String, itemKey: String, cost: Int): Result<Boolean> {
        return try {
            require(userId.isNotBlank()) { "userId must not be blank" }
            val docRef = firestore.collection("users").document(userId)
            val doc = docRef.get().await()
            val currentCoins = doc.getLong("coins")?.toInt() ?: 100
            if (currentCoins < cost) {
                return Result.success(false)
            }
            @Suppress("UNCHECKED_CAST")
            val currentItems = (doc.get("unlockedItems") as? List<String>) ?: emptyList()
            if (currentItems.contains(itemKey)) {
                return Result.success(true)
            }
            val newItems = currentItems + itemKey
            val newCoins = currentCoins - cost
            docRef.update(
                mapOf(
                    "coins" to newCoins,
                    "unlockedItems" to newItems,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed unlocking item", e)
            Result.failure(e)
        }
    }

    suspend fun redeemReferral(userId: String, code: String): Result<Boolean> {
        return try {
            require(userId.isNotBlank()) { "userId must not be blank" }
            val docRef = firestore.collection("users").document(userId)
            val doc = docRef.get().await()
            val currentCoins = doc.getLong("coins")?.toInt() ?: 100
            docRef.update(
                mapOf(
                    "coins" to currentCoins + 100,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed redeeming referral", e)
            Result.failure(e)
        }
    }

    // --- Study Sessions (Timetable) ---
    fun observeSessions(userId: String): Flow<List<StudySession>> {
        if (!isAuthenticatedOwner(userId)) {
            return flowOf(emptyList())
        }
        return firestore.collection("users")
            .document(userId)
            .collection("sessions")
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(StudySession::class.java, ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w(TAG, "Stopped observing sessions: ${e.message}")
                emit(emptyList())
            }
    }

    suspend fun addSession(session: StudySession): Result<Unit> {
        return try {
            require(session.userId.isNotBlank()) { "userId must not be blank" }
            val sessionId = if (session.id.isNotBlank()) session.id else firestore.collection("users").document(session.userId).collection("sessions").document().id
            val finalSession = session.copy(id = sessionId)
            firestore.collection("users")
                .document(session.userId)
                .collection("sessions")
                .document(sessionId)
                .set(finalSession.toCreateMap())
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed adding study session", e)
            Result.failure(e)
        }
    }

    suspend fun updateSession(session: StudySession): Result<Unit> {
        return try {
            require(session.userId.isNotBlank() && session.id.isNotBlank()) { "userId and id must not be blank" }
            firestore.collection("users")
                .document(session.userId)
                .collection("sessions")
                .document(session.id)
                .update(session.toUpdateMap())
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed updating study session", e)
            Result.failure(e)
        }
    }

    suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> {
        return try {
            require(userId.isNotBlank() && sessionId.isNotBlank()) { "userId and sessionId required" }
            firestore.collection("users")
                .document(userId)
                .collection("sessions")
                .document(sessionId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleting session", e)
            Result.failure(e)
        }
    }

    // --- Focus Logs ---
    fun observeFocusLogs(userId: String): Flow<List<FocusLog>> {
        if (!isAuthenticatedOwner(userId)) {
            return flowOf(emptyList())
        }
        return firestore.collection("users")
            .document(userId)
            .collection("focus_logs")
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(FocusLog::class.java, ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w(TAG, "Stopped observing focus logs: ${e.message}")
                emit(emptyList())
            }
    }

    suspend fun addFocusLog(log: FocusLog): Result<Unit> {
        return try {
            require(log.userId.isNotBlank()) { "userId must not be blank" }
            val logId = if (log.id.isNotBlank()) log.id else firestore.collection("users").document(log.userId).collection("focus_logs").document().id
            val finalLog = log.copy(id = logId)
            firestore.collection("users")
                .document(log.userId)
                .collection("focus_logs")
                .document(logId)
                .set(finalLog.toCreateMap())
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed adding focus log", e)
            Result.failure(e)
        }
    }

    // --- Quizzes ---
    fun observeQuizzes(userId: String): Flow<List<Quiz>> {
        if (!isAuthenticatedOwner(userId)) {
            return flowOf(emptyList())
        }
        return firestore.collection("users")
            .document(userId)
            .collection("quizzes")
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(Quiz::class.java, ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w(TAG, "Stopped observing quizzes: ${e.message}")
                emit(emptyList())
            }
    }

    suspend fun saveQuiz(quiz: Quiz): Result<Unit> {
        return try {
            require(quiz.userId.isNotBlank()) { "userId must not be blank" }
            val quizId = if (quiz.id.isNotBlank()) quiz.id else firestore.collection("users").document(quiz.userId).collection("quizzes").document().id
            val finalQuiz = quiz.copy(id = quizId)
            firestore.collection("users")
                .document(quiz.userId)
                .collection("quizzes")
                .document(quizId)
                .set(finalQuiz.toCreateMap())
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving quiz", e)
            Result.failure(e)
        }
    }

    suspend fun deleteQuiz(userId: String, quizId: String): Result<Unit> {
        return try {
            require(userId.isNotBlank() && quizId.isNotBlank()) { "userId and quizId required" }
            firestore.collection("users")
                .document(userId)
                .collection("quizzes")
                .document(quizId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleting quiz", e)
            Result.failure(e)
        }
    }
}
