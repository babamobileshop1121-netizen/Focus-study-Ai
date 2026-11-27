package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import com.example.data.model.Quiz
import com.example.data.model.QuizQuestion
import com.example.data.repository.AiQuizRepository
import com.example.ui.components.AdMobBannerCard
import com.example.ui.components.RewardedAdCard
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.RoseError
import com.example.ui.theme.TealSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.UUID

enum class QuizFlowState {
    INPUT,
    PLAYING,
    RESULTS
}

@Composable
fun QuizScreen(
    aiQuizRepository: AiQuizRepository,
    onSaveQuizResult: (Quiz) -> Unit,
    onShowInterstitial: (onDismissed: () -> Unit) -> Unit = { it() },
    onWatchRewardedAd: (onReward: (Int) -> Unit) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var flowState by remember { mutableStateOf(QuizFlowState.INPUT) }
    var selectedSubject by remember { mutableStateOf("Mathematics") }
    var topicDescription by remember { mutableStateOf("") }
    var selectedQuestionCount by remember { mutableIntStateOf(5) }
    var selectedDifficulty by remember { mutableStateOf("Medium") }

    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var generationError by remember { mutableStateOf<String?>(null) }

    // Active Quiz State
    var currentQuizTitle by remember { mutableStateOf("Study Quiz") }
    var quizQuestions by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    val userAnswers = remember { mutableStateMapOf<Int, Int>() } // questionIndex -> selectedOptionIndex

    // Tutor Explanations Map
    val simpleExplanations = remember { mutableStateMapOf<Int, String>() }
    var loadingExplanationIndex by remember { mutableStateOf<Int?>(null) }

    // Android Zero-permission Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val input: InputStream? = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(input)
                    withContext(Dispatchers.Main) {
                        selectedImageBitmap = bitmap
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        generationError = "Failed reading image: ${e.message}"
                    }
                }
            }
        }
    }

    when (flowState) {
        QuizFlowState.INPUT -> {
            QuizInputView(
                selectedSubject = selectedSubject,
                onSubjectChange = { selectedSubject = it },
                topicDescription = topicDescription,
                onTopicChange = { topicDescription = it },
                questionCount = selectedQuestionCount,
                onCountChange = { selectedQuestionCount = it },
                difficulty = selectedDifficulty,
                onDifficultyChange = { selectedDifficulty = it },
                selectedBitmap = selectedImageBitmap,
                onPickPhoto = {
                    photoPickerLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                onClearPhoto = { selectedImageBitmap = null },
                isGenerating = isGenerating,
                errorMessage = generationError,
                onWatchRewardedAd = onWatchRewardedAd,
                onGenerateQuiz = {
                    isGenerating = true
                    generationError = null
                    scope.launch {
                        val result = if (selectedImageBitmap != null) {
                            aiQuizRepository.generateQuizFromPhoto(
                                bitmap = selectedImageBitmap!!,
                                subject = selectedSubject,
                                questionCount = selectedQuestionCount,
                                difficulty = selectedDifficulty
                            )
                        } else {
                            aiQuizRepository.generateQuizFromTopic(
                                topic = topicDescription.ifBlank { "$selectedSubject Core Concepts" },
                                subject = selectedSubject,
                                questionCount = selectedQuestionCount,
                                difficulty = selectedDifficulty
                            )
                        }

                        isGenerating = false
                        result.onSuccess { (title, questions) ->
                            currentQuizTitle = title
                            quizQuestions = questions
                            currentQuestionIndex = 0
                            userAnswers.clear()
                            simpleExplanations.clear()
                            flowState = QuizFlowState.PLAYING
                        }.onFailure { err ->
                            generationError = err.localizedMessage ?: "Failed generating quiz"
                        }
                    }
                },
                modifier = modifier
            )
        }

        QuizFlowState.PLAYING -> {
            QuizPlayerView(
                quizTitle = currentQuizTitle,
                questions = quizQuestions,
                currentIndex = currentQuestionIndex,
                selectedOption = userAnswers[currentQuestionIndex],
                onSelectOption = { userAnswers[currentQuestionIndex] = it },
                onNextQuestion = {
                    if (currentQuestionIndex < quizQuestions.size - 1) {
                        currentQuestionIndex++
                    } else {
                        onShowInterstitial {
                            flowState = QuizFlowState.RESULTS
                        }
                    }
                },
                onPreviousQuestion = {
                    if (currentQuestionIndex > 0) {
                        currentQuestionIndex--
                    }
                },
                modifier = modifier
            )
        }

        QuizFlowState.RESULTS -> {
            QuizResultsView(
                title = currentQuizTitle,
                subject = selectedSubject,
                questions = quizQuestions,
                userAnswers = userAnswers,
                simpleExplanations = simpleExplanations,
                loadingExplanationIndex = loadingExplanationIndex,
                onRequestExplanation = { index, question, correctOpt ->
                    loadingExplanationIndex = index
                    scope.launch {
                        val res = aiQuizRepository.explainConceptSimply(question, correctOpt)
                        loadingExplanationIndex = null
                        simpleExplanations[index] = res.getOrDefault("Concept verified.")
                    }
                },
                onRetryWrongAnswers = {
                    // Filter to only questions the user got incorrect
                    val wrongQuestions = quizQuestions.filterIndexed { index, q ->
                        userAnswers[index] != q.correctAnswerIndex
                    }
                    if (wrongQuestions.isNotEmpty()) {
                        quizQuestions = wrongQuestions
                        currentQuestionIndex = 0
                        userAnswers.clear()
                        flowState = QuizFlowState.PLAYING
                    }
                },
                onGenerateAgain = {
                    flowState = QuizFlowState.INPUT
                },
                onSaveQuiz = { quiz ->
                    onSaveQuizResult(quiz)
                },
                modifier = modifier
            )
        }
    }
}

@Composable
fun QuizInputView(
    selectedSubject: String,
    onSubjectChange: (String) -> Unit,
    topicDescription: String,
    onTopicChange: (String) -> Unit,
    questionCount: Int,
    onCountChange: (Int) -> Unit,
    difficulty: String,
    onDifficultyChange: (String) -> Unit,
    selectedBitmap: Bitmap?,
    onPickPhoto: () -> Unit,
    onClearPhoto: () -> Unit,
    isGenerating: Boolean,
    errorMessage: String?,
    onWatchRewardedAd: (onReward: (Int) -> Unit) -> Unit = {},
    onGenerateQuiz: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "GEMINI VISION QUIZ ENGINE",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.4.sp,
                color = TealSecondary
            )
            Text(
                text = "AI Photo-to-Quiz",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Scan textbook pages or select a syllabus topic for instant practice",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Photo Upload Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("photo_upload_card"),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (selectedBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            Image(
                                bitmap = selectedBitmap.asImageBitmap(),
                                contentDescription = "Uploaded Note Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Button(
                                onClick = onClearPhoto,
                                colors = ButtonDefaults.buttonColors(containerColor = RoseError.copy(alpha = 0.9f)),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Remove", fontSize = 11.sp)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(144.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(IndigoPrimary.copy(alpha = 0.08f))
                                .border(1.dp, IndigoPrimary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .clickable { onPickPhoto() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(IndigoPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = "Upload Note",
                                        tint = IndigoPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Pick Textbook or Notes Photo",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = IndigoPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Zero-permission Photo Picker",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Or generate directly from a chapter topic below",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Subject Selector
        item {
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
                        text = "SUBJECT & TOPIC",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("Mathematics", "Physics", "Biology", "History").forEach { subj ->
                            val isSelected = selectedSubject == subj
                            val (accent, _) = getSubjectColorPalette(subj)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) accent
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    )
                                    .clickable { onSubjectChange(subj) }
                                    .padding(vertical = 9.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = subj,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = topicDescription,
                        onValueChange = onTopicChange,
                        label = { Text("Chapter / Concepts Focus") },
                        placeholder = { Text("e.g. Calculus power rules, or Cellular ATP") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Questions Count & Difficulty
        item {
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
                        text = "QUIZ CONFIGURATION",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Count chips (5, 10, 20, 30)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Questions", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(5, 10, 20, 30).forEach { count ->
                                val isSelected = questionCount == count
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) TealSecondary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        .clickable { onCountChange(count) }
                                        .padding(horizontal = 11.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "$count",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Difficulty chips (Easy, Medium, Hard)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Difficulty", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Easy", "Medium", "Hard").forEach { diff ->
                                val isSelected = difficulty == diff
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) IndigoPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        .clickable { onDifficultyChange(diff) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = diff,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Error Banner
        if (errorMessage != null) {
            item {
                Text(
                    text = errorMessage,
                    color = RoseError,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        // Rewarded Ad CTA for extra AI Questions / Token Credits
        item {
            RewardedAdCard(
                bonusTokens = 10,
                onWatchAd = {
                    onWatchRewardedAd { bonus ->
                        android.widget.Toast.makeText(context, "+$bonus AI Quiz Questions Earned!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // Generate CTA
        item {
            Button(
                onClick = onGenerateQuiz,
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("generate_quiz_button"),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Gemini AI is analyzing notes...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "AI")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedBitmap != null) "Generate Quiz from Photo" else "Generate Study Quiz ($questionCount Qs)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // AdMob Banner Unit
        item {
            AdMobBannerCard()
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun QuizPlayerView(
    quizTitle: String,
    questions: List<QuizQuestion>,
    currentIndex: Int,
    selectedOption: Int?,
    onSelectOption: (Int) -> Unit,
    onNextQuestion: () -> Unit,
    onPreviousQuestion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentQuestion = questions.getOrNull(currentIndex) ?: return
    val progress = (currentIndex + 1).toFloat() / questions.size.toFloat()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Progress Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = quizTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            Text(
                text = "Question ${currentIndex + 1}/${questions.size}",
                style = MaterialTheme.typography.labelMedium,
                color = TealSecondary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = IndigoPrimary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Question Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = currentQuestion.question,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Options List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(currentQuestion.options) { index, optionText ->
                val isSelected = selectedOption == index
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectOption(index) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) IndigoPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                    ),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, IndigoPrimary) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) IndigoPrimary else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ('A' + index).toString(),
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Text(
                            text = optionText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Bottom Navigation Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = onPreviousQuestion,
                enabled = currentIndex > 0,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Previous")
            }

            Button(
                onClick = onNextQuestion,
                enabled = selectedOption != null,
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("next_question_button")
            ) {
                Text(
                    text = if (currentIndex == questions.size - 1) "Submit Quiz" else "Next Question",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun QuizResultsView(
    title: String,
    subject: String,
    questions: List<QuizQuestion>,
    userAnswers: Map<Int, Int>,
    simpleExplanations: Map<Int, String>,
    loadingExplanationIndex: Int?,
    onRequestExplanation: (index: Int, question: String, correctOption: String) -> Unit,
    onRetryWrongAnswers: () -> Unit,
    onGenerateAgain: () -> Unit,
    onSaveQuiz: (Quiz) -> Unit,
    modifier: Modifier = Modifier
) {
    val correctCount = questions.indices.count { userAnswers[it] == questions[it].correctAnswerIndex }
    val totalCount = questions.size
    val accuracy = if (totalCount > 0) ((correctCount.toFloat() / totalCount.toFloat()) * 100).toInt() else 0
    var savedToHistory by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Result Score Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "QUIZ COMPLETED",
                        style = MaterialTheme.typography.labelMedium,
                        color = TealSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$accuracy%",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (accuracy >= 70) EmeraldSuccess else RoseError
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "$correctCount of $totalCount Correct Questions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (!savedToHistory) {
                                    val quiz = Quiz(
                                        id = UUID.randomUUID().toString(),
                                        title = title,
                                        subject = subject,
                                        questionCount = totalCount,
                                        score = correctCount,
                                        totalQuestions = totalCount,
                                        accuracy = accuracy,
                                        questionsJson = questions.map { it.question }.joinToString(" | ")
                                    )
                                    onSaveQuiz(quiz)
                                    savedToHistory = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (savedToHistory) EmeraldSuccess else IndigoPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Bookmark, contentDescription = "Save")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (savedToHistory) "Saved to History!" else "Save Quiz")
                        }

                        if (correctCount < totalCount) {
                            OutlinedButton(
                                onClick = onRetryWrongAnswers,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Replay, contentDescription = "Retry")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry Incorrect")
                            }
                        }
                    }
                }
            }
        }

        // Detailed Breakdown Header
        item {
            Text(
                text = "Questions & Detailed Explanations",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Review items with "Explain Simply" Gemini AI buttons
        itemsIndexed(questions) { index, question ->
            val userOptIdx = userAnswers[index]
            val isCorrect = userOptIdx == question.correctAnswerIndex
            val correctOptText = question.options.getOrNull(question.correctAnswerIndex) ?: ""

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                                contentDescription = if (isCorrect) "Correct" else "Wrong",
                                tint = if (isCorrect) EmeraldSuccess else RoseError,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Question ${index + 1}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        if (!isCorrect) {
                            TextButton(
                                onClick = {
                                    onRequestExplanation(index, question.question, correctOptText)
                                },
                                enabled = loadingExplanationIndex != index
                            ) {
                                Icon(Icons.Default.Lightbulb, contentDescription = "Explain", modifier = Modifier.size(16.dp), tint = IndigoPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Explain Simply", fontSize = 12.sp, color = IndigoPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = question.question,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Correct Answer: $correctOptText",
                        fontSize = 13.sp,
                        color = EmeraldSuccess,
                        fontWeight = FontWeight.Medium
                    )

                    if (!isCorrect && userOptIdx != null) {
                        Text(
                            text = "Your Answer: ${question.options.getOrNull(userOptIdx) ?: "None"}",
                            fontSize = 13.sp,
                            color = RoseError
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Explanation: ${question.explanation}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Gemini AI Dynamic Simplified Explanation
                    if (simpleExplanations.containsKey(index)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(IndigoPrimary.copy(alpha = 0.08f))
                                .border(1.dp, IndigoPrimary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = IndigoPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Gemini AI Tutor Explanation:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndigoPrimary)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = simpleExplanations[index]!!,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else if (loadingExplanationIndex == index) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = IndigoPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gemini is simplifying concept...", fontSize = 11.sp, color = IndigoPrimary)
                        }
                    }
                }
            }
        }

        // AdMob Banner Placement on Quiz Results
        item {
            AdMobBannerCard()
        }

        // Action to generate another quiz
        item {
            Button(
                onClick = onGenerateAgain,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "New")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Another Quiz", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
