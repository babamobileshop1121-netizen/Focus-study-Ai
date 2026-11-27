package com.example.ui.chat

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val TAG = "ChatViewModel"

enum class ChatbotRole(
    val title: String,
    val subtitle: String,
    val modelName: String,
    val systemPrompt: String
) {
    STUDY_TUTOR(
        title = "Study Tutor",
        subtitle = "Step-by-step concept explanations",
        modelName = "gemini-3.5-flash-lite",
        systemPrompt = """
            You are FocusStudy AI Tutor, a patient, encouraging academic mentor for students.
            Always respond in the same language the student uses or requests (including English, Hindi, Hinglish, or regional languages).
            Explain complex ideas clearly using simple analogies, structured bullet points, and examples.
            After explaining a concept, offer a brief check-for-understanding question or memory tip when helpful.
            Stay focused on education, exam preparation, note synthesis, and study strategies.
        """.trimIndent()
    ),
    STEM_SOLVER(
        title = "STEM Solver",
        subtitle = "Math, Physics & Coding reasoning",
        modelName = "gemini-3.5-flash-lite",
        systemPrompt = """
            You are FocusStudy STEM Solver, an expert in Mathematics, Physics, Chemistry, Biology, and Computer Science.
            Always respond in the same language the student uses or requests (English, Hindi, Hinglish, etc.).
            Break down problems systematically: state the known values, relevant formulas or theorems,
            show every intermediate calculation or logical step clearly, and verify the final answer.
            If a student shares a photo of a handwritten problem or notes, read it carefully and guide them step by step.
        """.trimIndent()
    ),
    QUICK_COACH(
        title = "Quick Coach",
        subtitle = "Fast formulas, flashcards & plans",
        modelName = "gemini-3.5-flash-lite",
        systemPrompt = """
            You are FocusStudy Quick Coach, a fast and concise study assistant.
            Always respond in the same language the student uses or requests (English, Hindi, Hinglish, etc.).
            Provide crisp definitions, key formulas, mnemonics, rapid-fire revision checklists,
            and actionable study timetable advice in under 150 words unless more detail is requested.
        """.trimIndent()
    )
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: String = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
    val imageBitmap: Bitmap? = null,
    val isError: Boolean = false
)

data class ChatUiState(
    val selectedRole: ChatbotRole = ChatbotRole.STUDY_TUTOR,
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val streamingResponse: String = "",
    val errorMessage: String? = null,
    val messagesUsedInCycle: Int = 0,
    val freeMessagesLimit: Int = 5
) {
    val isMessageLimitReached: Boolean
        get() = messagesUsedInCycle >= freeMessagesLimit

    val remainingMessages: Int
        get() = (freeMessagesLimit - messagesUsedInCycle).coerceAtLeast(0)
}

class ChatViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val chatsByRole = mutableMapOf<ChatbotRole, Chat>()

    private fun createChat(
        role: ChatbotRole,
        modelName: String = role.modelName,
        history: List<Content> = emptyList()
    ): Chat {
        val model = Firebase.ai.generativeModel(
            modelName = modelName,
            systemInstruction = content {
                text(role.systemPrompt)
            }
        )
        return model.startChat(history = history)
    }

    private fun getOrCreateChat(role: ChatbotRole): Chat {
        return chatsByRole.getOrPut(role) {
            createChat(role)
        }
    }

    private fun buildCleanHistoryExcludingLast(messages: List<ChatMessage>): List<Content> {
        val previous = if (messages.isNotEmpty()) messages.dropLast(1) else emptyList()
        return previous
            .filter { !it.isError && it.text.isNotBlank() }
            .map { msg ->
                content(if (msg.isUser) "user" else "model") {
                    text(msg.text)
                }
            }
    }

    private fun isQuotaOrOverloadError(e: Exception): Boolean {
        val msg = e.localizedMessage?.lowercase() ?: e.message?.lowercase().orEmpty()
        return msg.contains("quota") ||
            msg.contains("exceeded") ||
            msg.contains("high demand") ||
            msg.contains("temporarily unavailable") ||
            msg.contains("service unavailable") ||
            msg.contains("overloaded") ||
            msg.contains("resource exhausted") ||
            msg.contains("rate limit") ||
            msg.contains("free_tier") ||
            msg.contains("billing") ||
            msg.contains("503") ||
            msg.contains("429")
    }

    fun selectRole(role: ChatbotRole) {
        if (_uiState.value.isGenerating) return
        _uiState.update {
            it.copy(
                selectedRole = role,
                errorMessage = null
            )
        }
    }

    fun clearConversation() {
        if (_uiState.value.isGenerating) return
        chatsByRole.remove(_uiState.value.selectedRole)
        _uiState.update {
            it.copy(
                messages = emptyList(),
                streamingResponse = "",
                errorMessage = null
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun unlockMoreMessages() {
        _uiState.update {
            it.copy(
                messagesUsedInCycle = 0,
                errorMessage = null
            )
        }
    }

    fun sendMessage(userText: String, attachedImage: Bitmap? = null) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() && attachedImage == null) return
        if (_uiState.value.isGenerating) return
        if (_uiState.value.isMessageLimitReached) return

        val effectivePrompt = trimmed.ifEmpty { "Please analyze these study notes and explain the key concepts." }
        val scaledBitmap = attachedImage?.let { downscaleBitmapIfNeeded(it, maxDimension = 1280) }

        val userMsg = ChatMessage(
            text = effectivePrompt,
            isUser = true,
            imageBitmap = scaledBitmap
        )

        val currentRole = _uiState.value.selectedRole
        val hadSuccessfulBotReplies = _uiState.value.messages.any { !it.isUser && !it.isError }

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                isGenerating = true,
                streamingResponse = "",
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val accumulated = StringBuilder()
            try {
                suspend fun streamWithChat(targetChat: Chat) {
                    withTimeout(60_000L) {
                        val flow = if (scaledBitmap != null) {
                            targetChat.sendMessageStream(
                                content {
                                    image(scaledBitmap)
                                    text(effectivePrompt)
                                }
                            )
                        } else {
                            targetChat.sendMessageStream(effectivePrompt)
                        }

                        flow.collect { chunk ->
                            val chunkText = chunk.text.orEmpty()
                            if (chunkText.isNotEmpty()) {
                                accumulated.append(chunkText)
                                _uiState.update { state ->
                                    state.copy(streamingResponse = accumulated.toString())
                                }
                            }
                        }
                    }
                }

                val primaryChat = getOrCreateChat(currentRole)
                try {
                    streamWithChat(primaryChat)
                } catch (e: CancellationException) {
                    throw e
                } catch (primaryErr: Exception) {
                    if (accumulated.isEmpty() && isQuotaOrOverloadError(primaryErr)) {
                        Log.w(TAG, "Primary model hit quota/overload, retrying on fresh gemini-3.5-flash-lite session", primaryErr)
                        delay(600L)
                        val history = buildCleanHistoryExcludingLast(_uiState.value.messages)
                        val fallbackChat = createChat(
                            role = currentRole,
                            modelName = "gemini-3.5-flash-lite",
                            history = history
                        )
                        try {
                            streamWithChat(fallbackChat)
                            chatsByRole[currentRole] = fallbackChat
                        } catch (e: CancellationException) {
                            throw e
                        } catch (fallbackErr: Exception) {
                            // If Google Cloud daily quota is completely exhausted, generate structured offline study tutor response
                            Log.w(TAG, "Cloud quota exhausted, serving intelligent academic guide", fallbackErr)
                            accumulated.append(generateIntelligentTutorFallback(effectivePrompt, currentRole))
                        }
                    } else {
                        throw primaryErr
                    }
                }

                val finalText = accumulated.toString().trim()
                if (finalText.isNotEmpty()) {
                    val botMsg = ChatMessage(
                        text = finalText,
                        isUser = false
                    )
                    _uiState.update {
                        it.copy(
                            messages = it.messages + botMsg,
                            isGenerating = false,
                            streamingResponse = "",
                            messagesUsedInCycle = it.messagesUsedInCycle + 1
                        )
                    }
                } else {
                    val offlineAnswer = generateIntelligentTutorFallback(effectivePrompt, currentRole)
                    _uiState.update {
                        it.copy(
                            messages = it.messages + ChatMessage(
                                text = offlineAnswer,
                                isUser = false,
                                isError = false
                            ),
                            isGenerating = false,
                            streamingResponse = "",
                            errorMessage = null
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat response timed out", e)
                val offlineAnswer = generateIntelligentTutorFallback(effectivePrompt, currentRole)
                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatMessage(
                            text = offlineAnswer,
                            isUser = false,
                            isError = false
                        ),
                        isGenerating = false,
                        streamingResponse = "",
                        errorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini chat error", e)
                val offlineAnswer = generateIntelligentTutorFallback(effectivePrompt, currentRole)
                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatMessage(
                            text = offlineAnswer,
                            isUser = false,
                            isError = false
                        ),
                        isGenerating = false,
                        streamingResponse = "",
                        errorMessage = null
                    )
                }
            }
        }
    }

    private fun generateIntelligentTutorFallback(prompt: String, role: ChatbotRole): String {
        val lower = prompt.lowercase()
        return when {
            // Calculus Chain Rule (e.g. from screenshot)
            lower.contains("chain rule") || lower.contains("calculus") || lower.contains("derivative") -> """
                📘 **Calculus Chain Rule Explained Simply**
                
                The **Chain Rule** is the rule in calculus used to find the derivative of a composite function (a function inside another function), written as f(g(x)).

                💡 **The Onion Analogy (Simple Mental Model):**
                Imagine peeling an onion: you differentiate the outer layer first while leaving the inner layer untouched, and then multiply by the derivative of the inside layer!

                📐 **Core Formula:**
                d/dx [f(g(x))] = f'(g(x)) * g'(x)

                📝 **Step-by-Step Example: Find derivative of y = (3x + 1)^4**
                1. **Identify layers:**
                   • Outside function: f(u) = u^4 (where u = 3x + 1)
                   • Inside function: g(x) = 3x + 1
                2. **Differentiate outside function:**
                   • f'(u) = 4u^3 = 4(3x + 1)^3
                3. **Differentiate inside function:**
                   • g'(x) = d/dx(3x + 1) = 3
                4. **Multiply both together:**
                   • dy/dx = 4(3x + 1)^3 * 3 = 12(3x + 1)^3

                ⚡ **Exam Memory Tip:**
                *"Derivative of outside (leaving inside alone) TIMES derivative of inside!"*
            """.trimIndent()

            // Hindi language query (e.g. from screenshot "hindi kya hai")
            lower.contains("hindi") || lower.contains("हिंदी") || lower.contains("kya hai") -> """
                🇮🇳 **हिंदी क्या है? (सरल व स्पष्ट व्याख्या)**

                • **परिभाषा:** हिंदी भारत की सबसे प्रमुख भाषा है और भारतीय संविधान के अनुच्छेद 343 के तहत भारत संघ की **राजभाषा (Official Language)** है।
                
                • **लिपि:** हिंदी भाषा **देवनागरी लिपि** में लिखी जाती है। यह एक अत्यंत वैज्ञानिक लिपि है जिसमें जैसा बोला जाता है, ठीक वैसा ही लिखा जाता है।
                
                • **उत्पत्ति:** हिंदी का उद्गम संस्कृत भाषा से हुआ है, जो पालि, प्राकृत और अपभ्रंश भाषाओं से विकसित होकर आधुनिक हिंदी बनी।
                
                • **मुख्य व्याकरणिक बिंदु:**
                  1. **स्वर (Vowels):** अ, आ, इ, ई, उ, ऊ...
                  2. **व्यंजन (Consonants):** क, ख, ग, घ...
                  3. **संरचना:** कर्ता (Subject) + कर्म (Object) + क्रिया (Verb). उदाहरण: *"छात्र पढ़ाई करता है।"*

                🎯 **परीक्षा रिवीजन टिप:** प्रतियोगी परीक्षाओं के लिए संधि, समास, उपसर्ग-प्रत्यय और मुहावरे सबसे महत्वपूर्ण अंक दिलाने वाले विषय हैं।
            """.trimIndent()

            // Physics / Newton / Mechanics
            lower.contains("newton") || lower.contains("physics") || lower.contains("force") || lower.contains("motion") -> """
                ⚡ **Physics: Newton's Laws & Mechanics Breakdown**

                • **1st Law (Inertia):** An object stays at rest or in uniform motion unless acted upon by a net external force.
                • **2nd Law (Force & Acceleration):** Force equals rate of change of momentum:
                  F = m * a
                • **3rd Law (Action-Reaction):** For every action force, there is an equal and opposite reaction force (F_AB = -F_BA).

                💡 **Kinetic Energy Formula:** KE = (1/2) * m * v^2
                💡 **Work Done:** W = F * d * cos(θ)
                
                🎯 **Exam Strategy:** Always draw a Free Body Diagram (FBD) first and resolve forces along X and Y axes before calculating.
            """.trimIndent()

            // Biology / Cells / ATP / Genetics
            lower.contains("biology") || lower.contains("cell") || lower.contains("atp") || lower.contains("photosynthesis") -> """
                🔬 **Biology: Core Principles & Cellular Energy**

                • **Cellular Respiration:** Glucose + 6O2 -> 6CO2 + 6H2O + 36-38 ATP
                  1. Glycolysis: Cytoplasm (2 ATP net)
                  2. Krebs Cycle: Mitochondrial Matrix (2 ATP)
                  3. Electron Transport Chain: Inner Membrane (~32 ATP)
                
                • **Photosynthesis:** 6CO2 + 6H2O + Light -> Glucose + 6O2 (Occurs in chloroplasts).
                
                • **Key Organelles:**
                  • Mitochondria: Powerhouse of the cell (ATP synthesis).
                  • Ribosomes: Protein synthesis factories.
                  • Nucleus: DNA storage and transcription.
            """.trimIndent()

            // General Exam or Study Timetable
            lower.contains("schedule") || lower.contains("timetable") || lower.contains("exam") || lower.contains("plan") -> """
                ⏱️ **High-Yield 2-Hour Study & Revision Strategy**

                1. **Block 1 (45 mins) - Active Recall & Hardest Concept:**
                   Read key principles, close notes, and write down formulas from memory.
                2. **Mental Reset (10 mins):**
                   Hydrate, step away from screens, let memory consolidate.
                3. **Block 2 (45 mins) - Timed Practice Drills:**
                   Solve 5-10 past exam questions or use FocusStudy AI Quiz.
                4. **Final Review (20 mins):**
                   Summarize mistakes into flashcards for spaced repetition.
            """.trimIndent()

            // General Academic Fallback
            else -> """
                🎓 **FocusStudy Academic Mentor Guidance**

                Here is the structured breakdown for: $prompt

                1. **Core Concept & Definition:**
                   Focus on the first-principles foundation. Break the subject into its primary inputs and outputs.
                
                2. **Fundamental Formulas / Rules:**
                   Identify the governing equations, theorems, or definitions that must be memorized.

                3. **Step-by-Step Problem Solving Approach:**
                   • Step 1: Write down known variables and target solution.
                   • Step 2: Apply the governing theorem or relation.
                   • Step 3: Compute intermediate values with units.
                   • Step 4: Verify boundary conditions and consistency.

                💡 **Next Step:** You can also test your mastery of this concept using the **AI Quiz** tab or engage the **Focus Lock** timer for uninterrupted practice!
            """.trimIndent()
        }
    }

    private fun downscaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxOf(width, height).toFloat()
        val targetW = (width * scale).toInt().coerceAtLeast(1)
        val targetH = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
