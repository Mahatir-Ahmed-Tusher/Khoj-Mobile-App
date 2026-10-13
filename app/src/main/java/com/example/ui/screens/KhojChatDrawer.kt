package com.example.ui.screens

import android.Manifest
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.luminance
import android.widget.Toast
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.data.api.ImageOcrHelper
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import java.util.Calendar
import java.util.concurrent.TimeUnit
import com.example.data.repository.AuthRepository
import com.example.data.local.UserAvatarManager
import com.example.data.api.KhojChatSession
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.CitationEngine
import com.example.data.api.KhojChatEngine
import com.example.data.api.KhojChatMessage
import com.example.data.api.KhojChatSource
import com.example.data.api.KhojChatState
import com.example.ui.components.MarkdownText
import com.example.ui.components.OfflineStateCard
import com.example.ui.viewmodel.FactCheckViewModel
import com.example.utils.NetworkErrorHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KhojTtsHelper(private val context: android.content.Context) {
    private var tts: TextToSpeech? = null
    var speakingMessageId by mutableStateOf<String?>(null)
        private set

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("bn", "BD"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale("bn", "IN"))
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        speakingMessageId = utteranceId
                    }
                    override fun onDone(utteranceId: String?) {
                        if (speakingMessageId == utteranceId) {
                            speakingMessageId = null
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        if (speakingMessageId == utteranceId) {
                            speakingMessageId = null
                        }
                    }
                })
            }
        }
    }

    fun speak(messageId: String, text: String) {
        if (speakingMessageId == messageId) {
            stop()
            return
        }
        stop()
        val cleanText = cleanMarkdownForSpeech(text)
        if (cleanText.isBlank()) return
        speakingMessageId = messageId
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, messageId)
    }

    fun stop() {
        tts?.stop()
        speakingMessageId = null
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        speakingMessageId = null
    }

    private fun cleanMarkdownForSpeech(raw: String): String {
        return raw
            .replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1")
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("[*#_`~>•\\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

fun groupSessionsByTime(sessions: List<KhojChatSession>, isBn: Boolean = false): Map<String, List<KhojChatSession>> {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val startOfToday = cal.timeInMillis

    val startOfYesterday = startOfToday - TimeUnit.DAYS.toMillis(1)
    val startOf7Days = startOfToday - TimeUnit.DAYS.toMillis(6)
    val startOf30Days = startOfToday - TimeUnit.DAYS.toMillis(29)

    val groups = linkedMapOf<String, MutableList<KhojChatSession>>()

    val pinned = sessions.filter { it.isPinned }
    if (pinned.isNotEmpty()) {
        val pinnedHeader = if (isBn) "পিন করা" else "Pinned"
        groups[pinnedHeader] = pinned.toMutableList()
    }

    val unpinned = sessions.filter { !it.isPinned }
    val todayList = mutableListOf<KhojChatSession>()
    val yesterdayList = mutableListOf<KhojChatSession>()
    val last7DaysList = mutableListOf<KhojChatSession>()
    val last30DaysList = mutableListOf<KhojChatSession>()
    val olderList = mutableListOf<KhojChatSession>()

    unpinned.forEach { session ->
        when {
            session.timestamp >= startOfToday -> todayList.add(session)
            session.timestamp >= startOfYesterday -> yesterdayList.add(session)
            session.timestamp >= startOf7Days -> last7DaysList.add(session)
            session.timestamp >= startOf30Days -> last30DaysList.add(session)
            else -> olderList.add(session)
        }
    }

    val todayHeader = if (isBn) "আজ" else "Today"
    val yesterdayHeader = if (isBn) "গতকাল" else "Yesterday"
    val last7DaysHeader = if (isBn) "পূর্ববর্তী ৭ দিন" else "Previous 7 Days"
    val last30DaysHeader = if (isBn) "পূর্ববর্তী ৩০ দিন" else "Previous 30 Days"
    val olderHeader = if (isBn) "পূর্বের" else "Older"

    if (todayList.isNotEmpty()) groups[todayHeader] = todayList
    if (yesterdayList.isNotEmpty()) groups[yesterdayHeader] = yesterdayList
    if (last7DaysList.isNotEmpty()) groups[last7DaysHeader] = last7DaysList
    if (last30DaysList.isNotEmpty()) groups[last30DaysHeader] = last30DaysList
    if (olderList.isNotEmpty()) groups[olderHeader] = olderList

    return groups
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun KhojChatDrawer(
    isOpen: Boolean,
    onClose: () -> Unit,
    viewModel: FactCheckViewModel,
    isBn: Boolean,
    fontToUse: FontFamily
) {
    val scrimAlpha by animateFloatAsState(
        targetValue = if (isOpen) 0.6f else 0.0f,
        animationSpec = tween(durationMillis = 300),
        label = "scrimAlpha"
    )

    if (isOpen || scrimAlpha > 0.01f) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Dark Backdrop Scrim (tap outside disabled: drawer collapses ONLY on back button as requested)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
            )

            // Sliding Panel Drawer
            AnimatedVisibility(
                visible = isOpen,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(durationMillis = 250)
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = MaterialTheme.colorScheme.background)
            ) {
                KhojChatView(
                    repository = viewModel.khojChatRepository,
                    onClose = onClose,
                    isBn = isBn,
                    fontToUse = fontToUse,
                    applyTopInsets = true
                )
            }
        }
    }
}

/**
 * Unified, single-source-of-truth Composable for the entire Khoj Chat experience.
 *
 * Used both by:
 * 1. [KhojChatDrawer] in the main application.
 * 2. [com.example.overlay.OverlayKhojChatDrawer] in the floating overlay service.
 *
 * Any changes made to this UI (header, tabs, message bubbles, empty states, input panel,
 * or session history menu) are automatically reflected in both places.
 */
@Composable
fun KhojChatView(
    repository: com.example.data.repository.KhojChatRepository,
    onClose: () -> Unit,
    isBn: Boolean,
    fontToUse: FontFamily,
    modifier: Modifier = Modifier,
    showDragHandle: Boolean = false,
    applyTopInsets: Boolean = !showDragHandle,
    headerIcon: @Composable (() -> Unit)? = null
) {
    val chatMessages by repository.chatMessages.collectAsState()
    val chatState by repository.chatState.collectAsState()
    val activeMode by repository.activeChatMode.collectAsState()
    val chatSessions by repository.chatSessions.collectAsState()
    val activeSessionId by repository.activeSessionId.collectAsState()
    val animatedMessageIds by repository.animatedMessageIds.collectAsState()

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    val ttsHelper = remember(context) { KhojTtsHelper(context) }
    DisposableEffect(ttsHelper) {
        onDispose {
            ttsHelper.shutdown()
        }
    }

    var textInput by remember { mutableStateOf("") }
    var isMenuOpen by remember { mutableStateOf(false) }
    var activeSourcesSheet by remember { mutableStateOf<List<KhojChatSource>?>(null) }
    var showActionsDrawer by remember { mutableStateOf(false) }
    var isDeepSearchActive by remember { mutableStateOf(false) }
    var showDeepSearchOffMicroToast by remember { mutableStateOf(false) }
    var isAiTextDetectionActive by remember { mutableStateOf(false) }
    var showAiTextDetectionOffMicroToast by remember { mutableStateOf(false) }
    var isAiImageDetectionActive by remember { mutableStateOf(false) }
    var showAiImageDetectionOffMicroToast by remember { mutableStateOf(false) }
    var isWebSearchExplicitlyEnabled by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var extractedOcrText by remember { mutableStateOf("") }
    var showImageClaimError by remember { mutableStateOf(false) }
    var isExtractingImage by remember { mutableStateOf(false) }
    var selectedUserMessageActionId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    var wasImeOpen by remember { mutableStateOf(false) }

    // When software keyboard is summoned, immediately dismiss bottom sheets so they never sit behind keyboard
    LaunchedEffect(imeBottom) {
        val isNowOpen = imeBottom > 0
        if (isNowOpen && !wasImeOpen) {
            if (showActionsDrawer) {
                showActionsDrawer = false
            }
            if (activeSourcesSheet != null) {
                activeSourcesSheet = null
            }
        }
        wasImeOpen = isNowOpen
    }

    // When actions bottom sheet is opened, guarantee software keyboard is dismissed and focus cleared
    LaunchedEffect(showActionsDrawer) {
        if (showActionsDrawer) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    // When sources bottom sheet is opened, guarantee software keyboard is dismissed and focus cleared
    LaunchedEffect(activeSourcesSheet) {
        if (activeSourcesSheet != null) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    LaunchedEffect(showDeepSearchOffMicroToast) {
        if (showDeepSearchOffMicroToast) {
            delay(1200L)
            showDeepSearchOffMicroToast = false
        }
    }

    LaunchedEffect(showAiTextDetectionOffMicroToast) {
        if (showAiTextDetectionOffMicroToast) {
            delay(1200L)
            showAiTextDetectionOffMicroToast = false
        }
    }

    LaunchedEffect(showAiImageDetectionOffMicroToast) {
        if (showAiImageDetectionOffMicroToast) {
            delay(1200L)
            showAiImageDetectionOffMicroToast = false
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            extractedOcrText = ""
            showImageClaimError = false
            isExtractingImage = true
            scope.launch {
                try {
                    val extractedClaim = ImageOcrHelper.extractTextFromUri(context, uri)
                    if (extractedClaim.isNotBlank()) {
                        extractedOcrText = extractedClaim
                    } else {
                        Toast.makeText(
                            context,
                            if (isBn) "ছবিতে কোনো পাঠযোগ্য লেখা পাওয়া যায়নি।" else "No readable text found in image.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("KhojChatView", "OCR failed", e)
                    Toast.makeText(
                        context,
                        if (isBn) "ছবি থেকে লেখা পড়া সম্ভব হয়নি।" else "Failed to extract text from image.",
                        Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    isExtractingImage = false
                }
            }
        }
    }

    val takeCameraPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = cameraPhotoUri
        if (success && uri != null) {
            selectedImageUri = uri
            extractedOcrText = ""
            showImageClaimError = false
            isExtractingImage = true
            scope.launch {
                try {
                    val extractedClaim = ImageOcrHelper.extractTextFromUri(context, uri)
                    if (extractedClaim.isNotBlank()) {
                        extractedOcrText = extractedClaim
                    } else {
                        Toast.makeText(
                            context,
                            if (isBn) "ছবিতে কোনো পাঠযোগ্য লেখা পাওয়া যায়নি।" else "No readable text found in image.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("KhojChatView", "Camera OCR failed", e)
                    Toast.makeText(
                        context,
                        if (isBn) "ছবি থেকে লেখা পড়া সম্ভব হয়নি।" else "Failed to extract text from image.",
                        Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    isExtractingImage = false
                }
            }
        }
    }

    val pickDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: ""
            if (mimeType.startsWith("image/")) {
                selectedImageUri = uri
                extractedOcrText = ""
                showImageClaimError = false
                isExtractingImage = true
                scope.launch {
                    try {
                        val extractedClaim = ImageOcrHelper.extractTextFromUri(context, uri)
                        if (extractedClaim.isNotBlank()) {
                            extractedOcrText = extractedClaim
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("KhojChatView", "Doc OCR failed", e)
                    } finally {
                        isExtractingImage = false
                    }
                }
            } else {
                Toast.makeText(
                    context,
                    if (isBn) "ফাইল সংযুক্ত হয়েছে" else "File selected",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun launchCameraCapture() {
        try {
            val cacheDir = context.cacheDir
            val photoFile = java.io.File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            val photoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            cameraPhotoUri = photoUri
            takeCameraPhotoLauncher.launch(photoUri)
        } catch (e: Exception) {
            android.util.Log.e("KhojChatView", "Error launching camera", e)
            Toast.makeText(
                context,
                if (isBn) "ক্যামেরা চালু করা সম্ভব হয়নি" else "Could not launch camera",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(chatMessages.size, chatState) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
    if (backDispatcher != null) {
        BackHandler(enabled = true) {
            if (showActionsDrawer) {
                showActionsDrawer = false
            } else if (activeSourcesSheet != null) {
                activeSourcesSheet = null
            } else if (isMenuOpen) {
                isMenuOpen = false
            } else {
                onClose()
            }
        }
    }

    val columnModifier = if (applyTopInsets) {
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
    } else {
        Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = columnModifier
        ) {
            // Optional top drag handle for bottom-sheet overlay
            if (showDragHandle) {
                val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }

            // Header Bar with integrated mode tabs
            KhojChatDrawerHeader(
                onClose = onClose,
                onMenuClick = { isMenuOpen = !isMenuOpen },
                activeMode = activeMode,
                onModeSelected = { repository.setChatMode(it) },
                isBn = isBn,
                fontToUse = fontToUse,
                headerIcon = headerIcon
            )

            // Content Area (Messages or Empty State)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (chatMessages.isEmpty()) {
                    KhojChatEmptyState(
                        activeMode = activeMode,
                        isBn = isBn,
                        fontToUse = fontToUse
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 0.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(chatMessages, key = { it.id }) { msg ->
                            val shouldAnimate = !msg.isUser && (msg == chatMessages.last()) && (msg.id !in animatedMessageIds)
                            val isLatestUserMessage = msg.isUser && (chatMessages.filter { it.isUser }.lastOrNull()?.id == msg.id)
                            KhojChatMessageBubble(
                                message = msg,
                                activeMode = activeMode,
                                isBn = isBn,
                                fontToUse = fontToUse,
                                shouldAnimate = shouldAnimate,
                                onAnimationComplete = {
                                    repository.markMessageAnimated(msg.id)
                                },
                                onRetry = { repository.retryLastChatMessage() },
                                ttsHelper = ttsHelper,
                                onOpenSources = { activeSourcesSheet = it },
                                isActionMenuVisible = selectedUserMessageActionId == msg.id,
                                isLatestUserMessage = isLatestUserMessage,
                                onToggleActions = {
                                    selectedUserMessageActionId = if (selectedUserMessageActionId == msg.id) null else msg.id
                                },
                                onDismissActions = {
                                    if (selectedUserMessageActionId == msg.id) {
                                        selectedUserMessageActionId = null
                                    }
                                },
                                onEditMessage = { msgToEdit ->
                                    textInput = msgToEdit.text
                                    if (!msgToEdit.imageUri.isNullOrBlank()) {
                                        try {
                                            selectedImageUri = Uri.parse(msgToEdit.imageUri)
                                        } catch (_: Exception) {}
                                    }
                                    repository.prepareEditMessage(msgToEdit.id)
                                    selectedUserMessageActionId = null
                                }
                            )
                        }

                        if (chatState != KhojChatState.Idle) {
                            item {
                                KhojThinkingBubble(
                                    state = chatState,
                                    isBn = isBn,
                                    fontToUse = fontToUse
                                )
                            }
                        }

                        // Spacing between the last message and the input box (no padding on input box)
                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }

            // Input Panel (without extra top padding)
            KhojChatInputPanel(
                text = textInput,
                onTextChange = {
                    textInput = it
                    if (it.isNotBlank()) showImageClaimError = false
                },
                activeMode = activeMode,
                isLoading = chatState != KhojChatState.Idle,
                selectedImageUri = selectedImageUri,
                isExtractingImage = isExtractingImage,
                showImageClaimError = showImageClaimError,
                onPickImage = {
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)
                    if (activeSourcesSheet != null) {
                        activeSourcesSheet = null
                    }
                    showActionsDrawer = !showActionsDrawer
                },
                onInputFocused = {
                    if (showActionsDrawer) {
                        showActionsDrawer = false
                    }
                    if (activeSourcesSheet != null) {
                        activeSourcesSheet = null
                    }
                },
                onRemoveImage = {
                    selectedImageUri = null
                    extractedOcrText = ""
                    showImageClaimError = false
                },
                isWebSearchEnabled = isWebSearchExplicitlyEnabled,
                onToggleWebSearch = {
                    isWebSearchExplicitlyEnabled = !isWebSearchExplicitlyEnabled
                },
                onSend = {
                    if (selectedImageUri != null && textInput.trim().isBlank()) {
                        if (!isAiImageDetectionActive) {
                            showImageClaimError = true
                            return@KhojChatInputPanel
                        }
                    }
                    if (textInput.isNotBlank() || (selectedImageUri != null && isAiImageDetectionActive)) {
                        if (!NetworkErrorHelper.isDeviceOnline(context)) {
                            Toast.makeText(
                                context,
                                if (isBn) "ইন্টারনেট সংযোগ নেই। সংযোগ পরীক্ষা করুন।" else "No internet connection. Please check your network.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@KhojChatInputPanel
                        }
                        val userPrompt = if (textInput.trim().isBlank() && isAiImageDetectionActive) {
                            if (isBn) "ছবিটির কৃত্রিমতা যাচাই (AI Image Detection)" else "AI Image Detection"
                        } else {
                            textInput.trim()
                        }
                        val imageToSend = selectedImageUri?.toString()
                        val promptForEngine = if (extractedOcrText.isNotBlank()) {
                            "$userPrompt\n\n[ছবি থেকে প্রাপ্ত তথ্য/লেখা]:\n$extractedOcrText"
                        } else {
                            userPrompt
                        }
                        val wasDeepSearch = isDeepSearchActive
                        val wasAiTextDetection = isAiTextDetectionActive
                        val wasAiImageDetection = isAiImageDetectionActive
                        if (wasDeepSearch || wasAiTextDetection || wasAiImageDetection) {
                            repository.setChatMode("general")
                        }
                        repository.sendChatMessage(
                            text = userPrompt,
                            imageUri = imageToSend,
                            promptForEngine = promptForEngine,
                            isDeepSearch = wasDeepSearch,
                            onDeepSearchCompleted = {
                                if (isDeepSearchActive) {
                                    isDeepSearchActive = false
                                    showDeepSearchOffMicroToast = true
                                }
                            },
                            isAiTextDetection = wasAiTextDetection,
                            onAiTextDetectionCompleted = {
                                if (isAiTextDetectionActive) {
                                    isAiTextDetectionActive = false
                                    showAiTextDetectionOffMicroToast = true
                                }
                            },
                            isAiImageDetection = wasAiImageDetection,
                            onAiImageDetectionCompleted = {
                                if (isAiImageDetectionActive) {
                                    isAiImageDetectionActive = false
                                    showAiImageDetectionOffMicroToast = true
                                }
                            },
                            isWebSearchEnabled = isWebSearchExplicitlyEnabled,
                            onAutoSwitched = {
                                Toast.makeText(context, if (isBn) "🔍 তথ্য যাচাই মোড সক্রিয় হয়েছে!" else "🔍 Fact-Check mode activated!", Toast.LENGTH_LONG).show()
                            }
                        )
                        textInput = ""
                        selectedImageUri = null
                        extractedOcrText = ""
                        showImageClaimError = false
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                },
                isBn = isBn,
                fontToUse = fontToUse,
                isDeepSearchActive = isDeepSearchActive,
                isAiTextDetectionActive = isAiTextDetectionActive,
                isAiImageDetectionActive = isAiImageDetectionActive
            )
        }

        // Deepsearch mode off Micro-Toast popup
        AnimatedVisibility(
            visible = showDeepSearchOffMicroToast,
            enter = fadeIn(animationSpec = tween(150)) + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp)
        ) {
            val isDarkToast = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isDarkToast) Color(0xFF2C2D32) else Color(0xFF1E293B),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Deepsearch mode is off",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        fontFamily = fontToUse,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
                        )
                    )
                }
            }
        }

        // AI Text Detection mode off Micro-Toast popup
        AnimatedVisibility(
            visible = showAiTextDetectionOffMicroToast,
            enter = fadeIn(animationSpec = tween(150)) + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp)
        ) {
            val isDarkToast = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isDarkToast) Color(0xFF2C2D32) else Color(0xFF1E293B),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "AI Text Detection mode is off",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        fontFamily = fontToUse,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
                        )
                    )
                }
            }
        }

        // AI Image Detection mode off Micro-Toast popup
        AnimatedVisibility(
            visible = showAiImageDetectionOffMicroToast,
            enter = fadeIn(animationSpec = tween(150)) + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp)
        ) {
            val isDarkToast = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isDarkToast) Color(0xFF2C2D32) else Color(0xFF1E293B),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "AI Image Detection mode is off",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        fontFamily = fontToUse,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
                        )
                    )
                }
            }
        }

        // Actions Drawer Overlay Background Scrim (for clicking outside)
        if (showActionsDrawer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        showActionsDrawer = false
                    }
            )
        }

        // Actions Drawer Bottom Container (Smooth Slide-Up)
        AnimatedVisibility(
            visible = showActionsDrawer,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 280)
            ) + fadeIn(animationSpec = tween(durationMillis = 280)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 240)
            ) + fadeOut(animationSpec = tween(durationMillis = 240)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            KhojChatActionsDrawer(
                onDismiss = { showActionsDrawer = false },
                onTakePhoto = { launchCameraCapture() },
                onPickGallery = {
                    pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onUploadFiles = {
                    pickDocumentLauncher.launch("*/*")
                },
                isDeepSearchActive = isDeepSearchActive,
                onToggleDeepSearch = {
                    isDeepSearchActive = it
                    if (it) {
                        isAiTextDetectionActive = false
                        isAiImageDetectionActive = false
                    }
                },
                isAiTextDetectionActive = isAiTextDetectionActive,
                onToggleAiTextDetection = {
                    isAiTextDetectionActive = it
                    if (it) {
                        isDeepSearchActive = false
                        isAiImageDetectionActive = false
                    }
                },
                isAiImageDetectionActive = isAiImageDetectionActive,
                onToggleAiImageDetection = {
                    isAiImageDetectionActive = it
                    if (it) {
                        isDeepSearchActive = false
                        isAiTextDetectionActive = false
                    }
                },
                isBn = isBn,
                fontToUse = fontToUse
            )
        }

        // Sources Drawer Overlay Background Scrim (for clicking outside)
        if (activeSourcesSheet != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        activeSourcesSheet = null
                    }
            )
        }

        // Sources Drawer Bottom Container (Smooth Slide-Up, Minimal Padding)
        AnimatedVisibility(
            visible = activeSourcesSheet != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 280)
            ) + fadeIn(animationSpec = tween(durationMillis = 280)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 240)
            ) + fadeOut(animationSpec = tween(durationMillis = 240)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            if (activeSourcesSheet != null) {
                KhojChatSourcesDrawer(
                    sources = activeSourcesSheet!!,
                    onClose = { activeSourcesSheet = null },
                    fontToUse = fontToUse
                )
            }
        }

        // Slide-out Menu Overlay Background Scrim (for clicking outside)
        if (isMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        isMenuOpen = false
                    }
            )
        }

        // Smooth Slide-out Menu Overlay Container (From Right, Borderless, Clean)
        AnimatedVisibility(
            visible = isMenuOpen,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMillis = 280)
            ) + fadeIn(animationSpec = tween(durationMillis = 280)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(durationMillis = 240)
            ) + fadeOut(animationSpec = tween(durationMillis = 240)),
            modifier = Modifier
                .fillMaxHeight()
                .width(290.dp)
                .align(Alignment.TopEnd)
        ) {
            val isDarkSidebar = MaterialTheme.colorScheme.background.luminance() < 0.5f
            val sidebarBgColor = if (isDarkSidebar) Color(0xFF14161A) else Color(0xFFFFFFFF)

            var searchQuery by remember { mutableStateOf("") }
            var actionMenuSession by remember { mutableStateOf<KhojChatSession?>(null) }
            var renameTargetSession by remember { mutableStateOf<KhojChatSession?>(null) }
            var renameInputText by remember { mutableStateOf("") }
            var showClearAllConfirm by remember { mutableStateOf(false) }

            val currentUser = remember { AuthRepository.getCurrentUser() }
            val rawName = currentUser?.displayName ?: "User"
            val firstTwoNames = remember(rawName) {
                rawName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString(" ").ifBlank { "User" }
            }
            val avatarUrl = remember(currentUser) {
                UserAvatarManager.getEffectiveAvatarUrl(context, currentUser)
            }

            val filteredSessions = remember(chatSessions, searchQuery) {
                if (searchQuery.isBlank()) {
                    chatSessions
                } else {
                    val query = searchQuery.trim()
                    chatSessions.filter { it.title.contains(query, ignoreCase = true) }
                }
            }

            val groupedSessions = remember(filteredSessions, isBn) {
                groupSessionsByTime(filteredSessions, isBn)
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(290.dp)
                    .background(sidebarBgColor)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(vertical = 8.dp)
                    .clickable(enabled = true, onClick = {}) // consumes click within the panel
            ) {
                // 1. User Profile Row (Avatar + First two names, thin & borderless)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Profile",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF0EA5E9)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = firstTwoNames.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = firstTwoNames,
                        fontFamily = fontToUse,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            fontFamily = fontToUse,
                            fontSize = 14.5.sp,
                            lineHeight = 22.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 2. Search Bar (functional across chat titles, borderless)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDarkSidebar) Color(0xFF1E2024) else Color(0xFFF1F5F9)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = fontToUse,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                platformStyle = PlatformTextStyle(includeFontPadding = true),
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = if (isBn) "অনুসন্ধান..." else "Search",
                                            fontFamily = fontToUse,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 3. New Chat Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            repository.createNewSession()
                            isMenuOpen = false
                            Toast.makeText(context, if (isBn) "নতুন চ্যাট শুরু হয়েছে" else "New chat started", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "New Chat",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "New Chat",
                        fontFamily = fontToUse,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 4. Chronological Grouped History List (Today, Yesterday, Previous 7 Days, Previous 30 Days)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (filteredSessions.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) {
                                        if (isBn) "কোনো চ্যাট পাওয়া যায়নি" else "No chats found"
                                    } else {
                                        if (isBn) "কোনো চ্যাট ইতিহাস নেই" else "No chat history yet"
                                    },
                                    fontFamily = fontToUse,
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    } else {
                        groupedSessions.forEach { (sectionHeader, sessionsInSection) ->
                            item(key = "header_$sectionHeader") {
                                Text(
                                    text = sectionHeader,
                                    fontFamily = fontToUse,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                    style = TextStyle(
                                        fontFamily = fontToUse,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    ),
                                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 4.dp)
                                )
                            }

                            items(sessionsInSection, key = { it.id }) { session ->
                                val isSelected = session.id == activeSessionId

                                val itemBgColor = when {
                                    isSelected && isDarkSidebar -> Color(0xFF262930)
                                    isSelected && !isDarkSidebar -> Color(0xFFE2E8F0)
                                    else -> Color.Transparent
                                }

                                @OptIn(ExperimentalFoundationApi::class)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(itemBgColor)
                                        .combinedClickable(
                                            onClick = {
                                                repository.loadSession(session.id)
                                                isMenuOpen = false
                                            },
                                            onLongClick = {
                                                actionMenuSession = session
                                            }
                                        )
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = session.title,
                                        fontFamily = fontToUse,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = TextStyle(
                                            fontFamily = fontToUse,
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp,
                                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                                            lineHeightStyle = LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.None
                                            )
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (session.isPinned) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_pin),
                                            contentDescription = "Pinned",
                                            modifier = Modifier.size(12.dp),
                                            tint = Color(0xFF0284C7).copy(alpha = 0.85f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(2.dp))
                                    IconButton(
                                        onClick = { actionMenuSession = session },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Options",
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom: Subtle Clear All History Option
                if (chatSessions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = { showClearAllConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .height(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isBn) "সব ইতিহাস মুছুন" else "Clear All History",
                            fontFamily = fontToUse,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.5.sp,
                            color = Color(0xFFEF4444).copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Item Action Pop-up Modal (Long Press on a Chat or 3-dots)
            if (actionMenuSession != null) {
                val targetSession = actionMenuSession!!
                Dialog(onDismissRequest = { actionMenuSession = null }) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier.width(260.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(vertical = 10.dp, horizontal = 6.dp)
                        ) {
                            Text(
                                text = targetSession.title,
                                fontFamily = fontToUse,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            // 1. Rename
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val sessionToRename = targetSession
                                        actionMenuSession = null
                                        renameInputText = sessionToRename.title
                                        renameTargetSession = sessionToRename
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Rename",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (isBn) "নাম পরিবর্তন" else "Rename",
                                    fontFamily = fontToUse,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // 2. Pin / Unpin
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val sessionToPin = targetSession
                                        actionMenuSession = null
                                        repository.togglePinSession(sessionToPin.id)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_pin),
                                    contentDescription = if (targetSession.isPinned) "Unpin" else "Pin",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (targetSession.isPinned) Color(0xFF0284C7) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (targetSession.isPinned) {
                                        if (isBn) "আনপিন করুন" else "Unpin"
                                    } else {
                                        if (isBn) "পিন করুন" else "Pin"
                                    },
                                    fontFamily = fontToUse,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // 3. Delete
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val sessionToDelete = targetSession
                                        actionMenuSession = null
                                        repository.deleteSession(sessionToDelete.id)
                                        Toast.makeText(context, if (isBn) "চ্যাট মুছে ফেলা হয়েছে" else "Conversation deleted", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    modifier = Modifier.size(18.dp),
                                    tint = Color(0xFFEF4444)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (isBn) "মুছে ফেলুন" else "Delete",
                                    fontFamily = fontToUse,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }
            }

            // Rename Dialog
            if (renameTargetSession != null) {
                val sessionToRename = renameTargetSession!!
                AlertDialog(
                    onDismissRequest = { renameTargetSession = null },
                    title = {
                        Text(
                            text = if (isBn) "চ্যাটের নাম পরিবর্তন" else "Rename Chat",
                            fontFamily = fontToUse,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    },
                    text = {
                        OutlinedTextField(
                            value = renameInputText,
                            onValueChange = { renameInputText = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                fontFamily = fontToUse,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                platformStyle = PlatformTextStyle(includeFontPadding = true),
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            )
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (renameInputText.isNotBlank()) {
                                    repository.renameSession(sessionToRename.id, renameInputText.trim())
                                }
                                renameTargetSession = null
                            }
                        ) {
                            Text(text = if (isBn) "সংরক্ষণ" else "Save", fontFamily = fontToUse)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { renameTargetSession = null }) {
                            Text(text = if (isBn) "বাতিল" else "Cancel", fontFamily = fontToUse)
                        }
                    }
                )
            }

            // Clear All Confirm Dialog
            if (showClearAllConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearAllConfirm = false },
                    title = {
                        Text(
                            text = "Clear All History",
                            fontFamily = fontToUse,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = if (isBn) "আপনি কি নিশ্চিতভাবে সমস্ত কথোপকথনের ইতিহাস মুছে ফেলতে চান?" else "Are you sure you want to delete all conversations?",
                            fontFamily = fontToUse
                        )
                    },
                    confirmButton = {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            onClick = {
                                repository.clearAllSessions()
                                showClearAllConfirm = false
                                isMenuOpen = false
                                Toast.makeText(context, if (isBn) "সব ইতিহাস মুছে ফেলা হয়েছে" else "All history cleared", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(text = if (isBn) "হ্যাঁ, সব মুছুন" else "Yes, Delete All", fontFamily = fontToUse)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearAllConfirm = false }) {
                            Text(text = if (isBn) "বাতিল" else "Cancel", fontFamily = fontToUse)
                        }
                    }
                )
            }
        }
    }
}

fun formatSessionTimestamp(timestamp: Long, isBn: Boolean): String {
    val date = Date(timestamp)
    val sdf = if (isBn) {
        SimpleDateFormat("dd MMM, hh:mm a", Locale("bn", "BD"))
    } else {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH)
    }
    return sdf.format(date)
}

@Composable
fun KhojChatDrawerHeader(
    onClose: () -> Unit,
    onMenuClick: () -> Unit,
    activeMode: String,
    onModeSelected: (String) -> Unit,
    isBn: Boolean,
    fontToUse: FontFamily,
    headerIcon: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back Navigation Button (Never removed)
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = if (isBn) "ফিরে যান" else "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        if (headerIcon != null) {
            headerIcon()
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Mode tabs placed in the center of the navbar with thin, smart, uncolored borders
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            KhojChatDrawerTabs(
                activeMode = activeMode,
                onModeSelected = onModeSelected,
                isBn = isBn,
                fontToUse = fontToUse
            )
        }

        // Hamburger Menu Icon (Conversations history)
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .testTag("hamburger_menu_button")
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Conversation Menu",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun KhojChatDrawerTabs(
    activeMode: String,
    onModeSelected: (String) -> Unit,
    isBn: Boolean,
    fontToUse: FontFamily
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val modes = listOf(
            Triple("general", if (isBn) "খোঁজ চ্যাট" else "Khoj Chat", Icons.Default.ChatBubbleOutline),
            Triple("citizen-service", if (isBn) "নাগরিক সেবা" else "Citizen Service", Icons.Outlined.AccountBalance),
            Triple("fact-check", if (isBn) "যাচাই" else "Fact Check", Icons.Default.VerifiedUser)
        )

        modes.forEach { (modeCode, modeLabel, icon) ->
            val isSelected = activeMode == modeCode

            // Selected tab has full opacity, unselected tabs are less saturated/faded
            val contentAlpha = if (isSelected) 1f else 0.4f
            val contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onModeSelected(modeCode) }
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = modeLabel,
                        modifier = Modifier.size(13.dp),
                        tint = contentColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = modeLabel,
                        fontFamily = fontToUse,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 11.sp,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun KhojChatEmptyState(
    activeMode: String,
    isBn: Boolean,
    fontToUse: FontFamily
) {
    val animRes = when (activeMode) {
        "citizen-service" -> R.raw.pet_bot
        "fact-check" -> R.raw.jachai_bot
        else -> R.raw.loading
    }

    val subtitleText = when (activeMode) {
        "citizen-service" -> if (isBn) "নাগরিক সেবা ও সরকারি তথ্য সহায়তায়" else "Citizen Services & Civic Assistance"
        "fact-check" -> if (isBn) "আলাপে আলাপে হোক গুজব যাচাই" else "Fact-check rumors in conversation"
        else -> if (isBn) "খোঁজের সাথে আজ কী নিয়ে আলাপ করবেন?" else "What would you like to discuss with Khoj today?"
    }

    // Highly optimized single active Lottie composition
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(animRes))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = true
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Optimized Lottie animation without background or badge
        Box(
            modifier = Modifier.size(110.dp),
            contentAlignment = Alignment.Center
        ) {
            if (composition != null) {
                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = subtitleText,
            fontFamily = fontToUse,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            style = TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = true),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                )
            )
        )
    }
}

/**
 * Compact, interactive Source Attribution Badge situated at the bottom of the AI message bubble.
 * Combines an overlapping cluster of dynamically fetched favicons with a clear "Sources" label.
 * The label strictly stays "Sources" across all languages.
 */
@Composable
fun SourceAttributionBadge(
    sources: List<KhojChatSource>,
    onClick: () -> Unit,
    fontToUse: FontFamily,
    modifier: Modifier = Modifier
) {
    if (sources.isEmpty()) return

    // Extract unique domains for the favicon cluster (up to 3 favicons)
    val uniqueDomains = remember(sources) {
        sources.mapNotNull { source ->
            CitationEngine.extractDomainPublic(source.url).takeIf { it.isNotBlank() }
        }.distinct().take(3)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Overlapping Favicon cluster
        if (uniqueDomains.isEmpty()) {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val clusterWidth = (18 + (uniqueDomains.size - 1) * 11).dp
            Box(
                modifier = Modifier
                    .width(clusterWidth)
                    .height(18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                uniqueDomains.forEachIndexed { index, domain ->
                    val faviconUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=128"
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.background),
                        modifier = Modifier
                            .offset(x = (index * 11).dp)
                            .size(18.dp)
                    ) {
                        AsyncImage(
                            model = faviconUrl,
                            contentDescription = domain,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .padding(2.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Label: Strictly "Sources" in both English and Bengali per user requirement
        Text(
            text = "Sources",
            fontFamily = fontToUse,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            style = TextStyle(
                fontFamily = fontToUse,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = true),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                )
            )
        )
    }
}

/**
 * Slide-up Sources Drawer (Bottom Sheet) displaying cited sources.
 * Designed with minimal internal padding, numbered markers matching in-text citations,
 * dynamic domain favicons, published dates, and click-through navigation.
 *
 * Supports dragging/pulling higher up onto the screen (expandable height).
 * Light theme background: Pure White (Color.White) with zero tonal elevation tint.
 * Dark theme background: Exactly #212121 (Color(0xFF212121)).
 */
@Composable
fun KhojChatSourcesDrawer(
    sources: List<KhojChatSource>,
    onClose: () -> Unit,
    fontToUse: FontFamily,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    // Heights: compact starting height and expanded height when dragged higher up
    val compactHeight = (screenHeight * 0.44f).coerceIn(320.dp, 400.dp)
    val expandedHeight = (screenHeight * 0.85f).coerceAtLeast(500.dp)

    var isExpanded by remember { mutableStateOf(false) }
    val accumulatedDragY = remember { floatArrayOf(0f) }

    val animatedHeight by animateDpAsState(
        targetValue = if (isExpanded) expandedHeight else compactHeight,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "sourcesDrawerHeight"
    )

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // Light mode: Pure white (no blue tint); Dark mode: exactly #212121
    val drawerBgColor = if (isDark) Color(0xFF212121) else Color.White
    val borderColor = if (isDark) Color(0xFF333333) else Color(0xFFE2E8F0).copy(alpha = 0.8f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(animatedHeight)
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)),
        color = drawerBgColor,
        tonalElevation = 0.dp, // 0.dp prevents M3 primary (blue) tinting on white
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // Drag handle area (Drag up to expand higher, drag down to collapse / dismiss)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 6.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDragY[0] += dragAmount.y
                                if (accumulatedDragY[0] < -28f) {
                                    // Dragged upwards -> expand higher up on screen
                                    isExpanded = true
                                } else if (accumulatedDragY[0] > 28f) {
                                    // Dragged downwards -> collapse or close
                                    if (isExpanded) {
                                        isExpanded = false
                                    } else {
                                        onClose()
                                    }
                                }
                            },
                            onDragEnd = { accumulatedDragY[0] = 0f },
                            onDragCancel = { accumulatedDragY[0] = 0f }
                        )
                    }
                    .clickable { isExpanded = !isExpanded },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.5.dp)
                        .background(
                            if (isDark) Color(0xFF555555) else Color(0xFFCBD5E1),
                            RoundedCornerShape(3.dp)
                        )
                )
            }

            // Header row: "Sources" title + count badge + Expand/Collapse toggle + Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDragY[0] += dragAmount.y
                                if (accumulatedDragY[0] < -28f) {
                                    isExpanded = true
                                } else if (accumulatedDragY[0] > 28f) {
                                    if (isExpanded) isExpanded = false else onClose()
                                }
                            },
                            onDragEnd = { accumulatedDragY[0] = 0f },
                            onDragCancel = { accumulatedDragY[0] = 0f }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Sources",
                        fontFamily = fontToUse,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A),
                        style = TextStyle(
                            fontFamily = fontToUse,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDark) Color(0xFF333333) else Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = "${sources.size}",
                            fontFamily = fontToUse,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = if (isExpanded) "Collapse" else "Expand higher",
                            modifier = Modifier.size(22.dp),
                            tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(18.dp),
                            tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = if (isDark) Color(0xFF2F2F2F) else Color(0xFFE2E8F0),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Sources list filling remaining expanded/compact height with minimal padding
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(
                    items = sources,
                    key = { index, source -> source.url.ifBlank { "$index" } }
                ) { index, source ->
                    val domain = remember(source.url) { CitationEngine.extractDomainPublic(source.url) }
                    val faviconUrl = remember(domain) { "https://www.google.com/s2/favicons?domain=$domain&sz=64" }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (source.url.startsWith("http")) {
                                    uriHandler.openUri(source.url)
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Numbering matching [1], [2], etc.
                        Surface(
                            shape = CircleShape,
                            color = if (isDark) Color(0xFF2C2C2C) else Color(0xFFF1F5F9),
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${index + 1}",
                                    fontFamily = fontToUse,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color(0xFF0284C7),
                                    style = TextStyle(
                                        fontFamily = fontToUse,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Dynamic Favicon
                        Surface(
                            shape = CircleShape,
                            color = if (isDark) Color(0xFF282828) else Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, if (isDark) Color(0xFF383838) else Color(0xFFE2E8F0)),
                            modifier = Modifier.size(20.dp)
                        ) {
                            AsyncImage(
                                model = faviconUrl,
                                contentDescription = domain,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .padding(2.dp),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Domain, Title, and published date
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = domain,
                                    fontFamily = fontToUse,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF0284C7),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (!source.date.isNullOrBlank()) {
                                    Text(
                                        text = "•",
                                        fontSize = 10.sp,
                                        color = if (isDark) Color(0xFF666666) else Color(0xFF94A3B8)
                                    )
                                    Text(
                                        text = source.date,
                                        fontFamily = fontToUse,
                                        fontSize = 11.sp,
                                        color = if (isDark) Color(0xFF888888) else Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = source.title,
                                fontFamily = fontToUse,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                                color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    fontFamily = fontToUse,
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp,
                                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.None
                                    )
                                )
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open link",
                            modifier = Modifier
                                .size(14.dp)
                                .padding(top = 2.dp),
                            tint = if (isDark) Color(0xFF777777) else Color(0xFF94A3B8)
                        )
                    }

                    if (index < sources.size - 1) {
                        HorizontalDivider(
                            color = if (isDark) Color(0xFF2A2A2A) else Color(0xFFF1F5F9),
                            modifier = Modifier.padding(start = 32.dp, top = 2.dp, bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KhojChatMessageBubble(
    message: KhojChatMessage,
    activeMode: String,
    isBn: Boolean,
    fontToUse: FontFamily,
    shouldAnimate: Boolean = false,
    onAnimationComplete: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    ttsHelper: KhojTtsHelper? = null,
    onOpenSources: ((List<KhojChatSource>) -> Unit)? = null,
    isActionMenuVisible: Boolean = false,
    isLatestUserMessage: Boolean = false,
    onToggleActions: () -> Unit = {},
    onDismissActions: () -> Unit = {},
    onEditMessage: ((KhojChatMessage) -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current
    
    var displayedText by remember(message.id) {
        mutableStateOf(if (shouldAnimate) "" else message.text)
    }

    DisposableEffect(message.id) {
        onDispose {
            if (shouldAnimate) {
                onAnimationComplete?.invoke()
            }
        }
    }
    
    LaunchedEffect(message.text, shouldAnimate) {
        if (shouldAnimate && displayedText != message.text) {
            val fullText = message.text
            var currentIndex = displayedText.length
            while (currentIndex < fullText.length) {
                val chunkSize = (8..12).random().coerceAtMost(fullText.length - currentIndex)
                currentIndex += chunkSize
                displayedText = fullText.substring(0, currentIndex)
                kotlinx.coroutines.delay(16)
            }
            onAnimationComplete?.invoke()
        } else {
            displayedText = message.text
            if (shouldAnimate) {
                onAnimationComplete?.invoke()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        if (message.autoSwitched) {
            Row(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .background(Color(0xFFE11D48).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color(0xFFE11D48)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isBn) "স্বয়ংক্রিয়ভাবে তথ্য যাচাই সক্রিয় করা হয়েছে" else "Auto-switched to Fact Check",
                    fontFamily = fontToUse,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE11D48),
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.None
                        )
                    )
                )
            }
        }

        if (message.isUser) {
            // User message bubble dynamically reflects the currently active tab mode:
            // Khoj Chat -> Sky Blue, Citizen Service -> Emerald Green, Fact Check -> Crimson Red
            val userBubbleGradient = when (activeMode) {
                "citizen-service" -> Brush.horizontalGradient(listOf(Color(0xFF0D9488), Color(0xFF10B981)))
                "fact-check" -> Brush.horizontalGradient(listOf(Color(0xFFE11D48), Color(0xFFF43F5E)))
                else -> Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF0EA5E9)))
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp))
                    .background(userBubbleGradient)
                    .combinedClickable(
                        onClick = { onDismissActions() },
                        onLongClick = { onToggleActions() }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .widthIn(max = 280.dp)
            ) {
                Column {
                    if (!message.imageUri.isNullOrBlank()) {
                        AsyncImage(
                            model = message.imageUri,
                            contentDescription = "User uploaded image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .padding(bottom = 8.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (message.text.isNotBlank()) {
                        MarkdownText(
                            text = message.text,
                            fontToUse = fontToUse,
                            fontSize = 14,
                            lineHeight = 20,
                            textColor = Color.White,
                            isUser = true
                        )
                    }
                }
            }

            // Long-press Actions Bar (Copy icon + Edit icon for latest user message)
            AnimatedVisibility(
                visible = isActionMenuVisible,
                enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
            ) {
                Row(
                    modifier = Modifier.padding(top = 4.dp, end = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Copy Action Button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(28.dp)
                            .clickable {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(message.text))
                                Toast.makeText(context, if (isBn) "কপি করা হয়েছে" else "Copied", Toast.LENGTH_SHORT).show()
                                onDismissActions()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = if (isBn) "কপি করুন" else "Copy",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Edit Action Button (Strictly for latest sent user message)
                    if (isLatestUserMessage && onEditMessage != null) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                            modifier = Modifier
                                .size(28.dp)
                                .clickable {
                                    onEditMessage(message)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = if (isBn) "এডিট করুন" else "Edit",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // AI Response (Free-floating, without avatar as requested)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                    // Error indicator
                    if (message.isError) {
                        Row(
                            modifier = Modifier.padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isBn) "ত্রুটি ঘটেছে" else "Error Occurred",
                                fontFamily = fontToUse,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Verdict indicator for fact checks
                    if (message.verdict != null) {
                        val (verdictLabel, verdictColor) = when (message.verdict) {
                            "true" -> Pair(if (isBn) "সত্য" else "TRUE", Color(0xFF10B981))
                            "false" -> Pair(if (isBn) "অসত্য" else "FALSE", Color(0xFFEF4444))
                            "partial" -> Pair(if (isBn) "আংশিক সত্য" else "PARTIALLY TRUE", Color(0xFFF59E0B))
                            "misleading" -> Pair(if (isBn) "বিভ্রান্তিকর" else "MISLEADING", Color(0xFFF97316))
                            else -> Pair(if (isBn) "নিশ্চিত নয়" else "UNVERIFIED", Color(0xFF6B7280))
                        }

                        Row(
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .background(verdictColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(verdictColor, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = verdictLabel,
                                fontFamily = fontToUse,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = verdictColor
                            )
                        }
                    }

                    if (message.isError) {
                        val isNetErr = NetworkErrorHelper.isNetworkException(Exception(displayedText))
                        if (isNetErr) {
                            OfflineStateCard(
                                isBn = isBn,
                                fontToUse = fontToUse,
                                featureName = if (isBn) "খোঁজ চ্যাট" else "Khoj Chat",
                                compact = true,
                                onRetry = onRetry
                            )
                        } else {
                            Text(
                                text = displayedText,
                                fontFamily = fontToUse,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        MarkdownText(
                            text = displayedText,
                            fontToUse = fontToUse,
                            fontSize = 15,
                            lineHeight = 23,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            isUser = false,
                            onCitationClick = { citationNum ->
                                val index = citationNum - 1
                                if (index in message.sources.indices) {
                                    val targetSource = message.sources[index]
                                    if (targetSource.url.startsWith("http")) {
                                        uriHandler.openUri(targetSource.url)
                                    }
                                } else if (message.sources.isNotEmpty()) {
                                    onOpenSources?.invoke(message.sources)
                                }
                            }
                        )
                    }

                    // Action Buttons (Copy, Download, Speaker & Interactive Sources Badge)
                    if (!message.isError && (message.text.isNotBlank() || message.sources.isNotEmpty())) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (message.text.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(message.text))
                                        Toast.makeText(context, if (isBn) "কপি করা হয়েছে" else "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                
                                IconButton(
                                    onClick = {
                                        try {
                                            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                            val file = java.io.File(downloadsDir, "KhojChat_${System.currentTimeMillis()}.txt")
                                            file.writeText(message.text)
                                            Toast.makeText(context, if (isBn) "Downloads ফোল্ডারে সেভ করা হয়েছে" else "Saved to Downloads", Toast.LENGTH_LONG).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, if (isBn) "সেভ করতে সমস্যা হয়েছে" else "Failed to save file", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Bangla TTS Speaker Button (Just beside Copy and Download)
                                if (ttsHelper != null) {
                                    val isSpeakingThis = ttsHelper.speakingMessageId == message.id
                                    IconButton(
                                        onClick = {
                                            ttsHelper.speak(message.id, message.text)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isSpeakingThis) Icons.Default.VolumeOff else Icons.Outlined.VolumeUp,
                                            contentDescription = if (isSpeakingThis) "পড়া বন্ধ করুন" else "পড়ে শোনান",
                                            tint = if (isSpeakingThis) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }

                            // Interactive Source Attribution Badge (Directly beside Speaker in the marked place)
                            if (message.sources.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(2.dp))
                                SourceAttributionBadge(
                                    sources = message.sources,
                                    onClick = { onOpenSources?.invoke(message.sources) },
                                    fontToUse = fontToUse
                                )
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
fun KhojThinkingBubble(
    state: KhojChatState,
    isBn: Boolean,
    fontToUse: FontFamily
) {
    if (state == KhojChatState.Idle) return

    val progressText = when (state) {
        is KhojChatState.AutoDetecting -> if (isBn) "গুজব সনাক্ত হয়েছে, যাচাই করা হচ্ছে..." else "Rumour detected, verifying..."
        is KhojChatState.Searching -> if (isBn) "ওয়েব তথ্য অনুসন্ধান করা হচ্ছে..." else "Searching live web..."
        is KhojChatState.DeepSearching -> state.stageText
        is KhojChatState.AiTextDetecting -> state.stageText
        is KhojChatState.AiImageDetecting -> state.stageText
        is KhojChatState.Generating -> "" // Removed thinking text per user instruction
        else -> ""
    }

    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dot1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 0, easing = LinearEasing), RepeatMode.Reverse), label = "dot1"
    )
    val dot2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 200, easing = LinearEasing), RepeatMode.Reverse), label = "dot2"
    )
    val dot3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 400, easing = LinearEasing), RepeatMode.Reverse), label = "dot3"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF0284C7).copy(alpha = 0.3f + (0.7f * dot1))))
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF0284C7).copy(alpha = 0.3f + (0.7f * dot2))))
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF0284C7).copy(alpha = 0.3f + (0.7f * dot3))))
        }
        if (progressText.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = progressText,
                fontFamily = fontToUse,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.None
                    )
                )
            )
        }
    }
}

@Composable
fun KhojChatInputPanel(
    text: String,
    onTextChange: (String) -> Unit,
    activeMode: String,
    isLoading: Boolean,
    onSend: () -> Unit,
    isBn: Boolean,
    fontToUse: FontFamily,
    selectedImageUri: Uri? = null,
    isExtractingImage: Boolean = false,
    showImageClaimError: Boolean = false,
    onPickImage: () -> Unit = {},
    onRemoveImage: () -> Unit = {},
    isDeepSearchActive: Boolean = false,
    onInputFocused: () -> Unit = {},
    isAiTextDetectionActive: Boolean = false,
    isAiImageDetectionActive: Boolean = false,
    isWebSearchEnabled: Boolean = false,
    onToggleWebSearch: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val voiceInputManager = remember(context) { VoiceInputManager(context) }
    DisposableEffect(voiceInputManager) {
        onDispose {
            voiceInputManager.destroy()
        }
    }

    val registryOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current
    val audioPermissionLauncher = if (registryOwner != null) {
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                val initialText = text.trim()
                voiceInputManager.startListening(
                    isBn = isBn,
                    onPartialResult = { newText ->
                        onTextChange(if (initialText.isNotEmpty()) "$initialText $newText" else newText)
                    },
                    onFinalResult = { newText ->
                        onTextChange(if (initialText.isNotEmpty()) "$initialText $newText" else newText)
                    },
                    onError = { errKey, isPermError ->
                        Toast.makeText(context, getString(errKey, isBn), Toast.LENGTH_SHORT).show()
                        if (isPermError) {
                            try {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", context.packageName, null)
                                ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, getString("voice_perm_required", isBn), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            } else {
                Toast.makeText(context, getString("voice_perm_required", isBn), Toast.LENGTH_LONG).show()
            }
        }
    } else null

    val tabAccentColor = when (activeMode) {
        "citizen-service" -> Color(0xFF10B981)
        "fact-check" -> Color(0xFFEF4444)
        else -> Color(0xFF0284C7)
    }

    val canSend = !isLoading && !isExtractingImage && (text.isNotBlank() || selectedImageUri != null)

    // Outer input card container without extra top padding
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 6.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = if (isDark) Color(0xFF1E2024) else Color(0xFFF1F5F9),
            border = BorderStroke(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Attached image thumbnail preview chip
                AnimatedVisibility(visible = selectedImageUri != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) Color(0xFF2A2D35) else Color(0xFFE2E8F0),
                        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.06f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(38.dp)) {
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = "Selected image",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                if (isExtractingImage) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isExtractingImage) {
                                        if (isBn) "ছবি থেকে তথ্য পড়া হচ্ছে..." else "Extracting text from image..."
                                    } else {
                                        if (isBn) "ছবি সংযুক্ত হয়েছে" else "Image attached"
                                    },
                                    fontFamily = fontToUse,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isExtractingImage) tabAccentColor else MaterialTheme.colorScheme.onSurface,
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    )
                                )
                            }
                            IconButton(
                                onClick = onRemoveImage,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove attached image",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                if (showImageClaimError && selectedImageUri != null && !isAiImageDetectionActive) {
                    Text(
                        text = getString("claim_required_error", isBn),
                        color = Color(0xFFEF4444),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = fontToUse,
                        modifier = Modifier.padding(bottom = 6.dp),
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        )
                    )
                }

                // Top Prompt Input Text Field with full diacritics support
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(
                        fontFamily = fontToUse,
                        fontSize = 14.5.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        platformStyle = PlatformTextStyle(
                            includeFontPadding = true
                        ),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.None
                        )
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Default
                    ),
                    maxLines = 5,
                    minLines = 1,
                    cursorBrush = SolidColor(tabAccentColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                onInputFocused()
                            }
                        },
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) {
                                val placeholderText = if (isAiImageDetectionActive) {
                                    if (isBn) "AI যাচাইয়ের জন্য ছবি সংযুক্ত করুন (লেখা ঐচ্ছিক)..." else "Attach image for AI detection (text optional)..."
                                } else if (isAiTextDetectionActive) {
                                    if (isBn) "AI যাচাইয়ের জন্য টেক্সট লিখুন..." else "Enter text for AI detection..."
                                } else if (isDeepSearchActive) {
                                    if (isBn) "ডিপ রিসার্চ অনুসন্ধান লিখুন..." else "Deep Research query..."
                                } else when (activeMode) {
                                    "citizen-service" -> "Ask about Civic services"
                                    "fact-check" -> "Let's factcheck in chat"
                                    else -> "Ask anything"
                                }
                                Text(
                                    text = if (voiceInputManager.isListening) getString("voice_listening", isBn)
                                           else if (isExtractingImage) (if (isBn) "ছবি থেকে তথ্য পড়া হচ্ছে..." else "Reading image...")
                                           else if (selectedImageUri != null) (
                                               if (isAiImageDetectionActive) (if (isBn) "ছবি সংযুক্ত হয়েছে (মন্তব্য ঐচ্ছিক)..." else "Image attached (comment optional)...")
                                               else if (isBn) "ছবিটির সাথে প্রাসঙ্গিক দাবি বা প্রশ্ন লিখুন..." else "Type claim or question regarding the image..."
                                           )
                                           else placeholderText,
                                    fontFamily = fontToUse,
                                    fontSize = 14.5.sp,
                                    lineHeight = 22.sp,
                                    color = if (voiceInputManager.isListening) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Row (Plus button on left, Mic & Send button on right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Plus Button for Image Upload / Actions Drawer (compact squircle)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) Color(0xFF2A2D35) else Color(0xFFE2E8F0))
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                                ),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = !isLoading && !isExtractingImage) { onPickImage() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = if (isBn) "মেন্যু" else "Menu",
                            tint = if (selectedImageUri != null || isDeepSearchActive || isAiTextDetectionActive || isAiImageDetectionActive) tabAccentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Right Side: Web Toggle (only in general mode), Voice Mic & Dynamic Send Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Web Search Toggle Button (Exclusive to Khoj Chat "general" mode)
                        if (activeMode == "general") {
                            val isAutoWebTriggered: Boolean = remember(text) {
                                KhojChatEngine.containsAutoWebTriggerKeywords(text)
                            }
                            val isWebActive = isWebSearchEnabled || isAutoWebTriggered

                            Surface(
                                shape = RoundedCornerShape(17.dp),
                                color = if (isWebActive) {
                                    tabAccentColor.copy(alpha = if (isDark) 0.22f else 0.14f)
                                } else {
                                    if (isDark) Color(0xFF2A2D35).copy(alpha = 0.7f) else Color(0xFFF1F5F9)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isWebActive) {
                                        tabAccentColor.copy(alpha = if (isDark) 0.65f else 0.45f)
                                    } else {
                                        if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.07f)
                                    }
                                ),
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(17.dp))
                                    .clickable(enabled = !isLoading) { onToggleWebSearch() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Language,
                                        contentDescription = if (isBn) "ওয়েব অনুসন্ধান" else "Web Search",
                                        tint = if (isWebActive) tabAccentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = if (isBn) "ওয়েব" else "Web",
                                        fontFamily = fontToUse,
                                        fontSize = 12.sp,
                                        fontWeight = if (isWebActive) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (isWebActive) tabAccentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                        style = TextStyle(
                                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                                            lineHeightStyle = LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.None
                                            )
                                        )
                                    )
                                }
                            }
                        }

                        // Voice Mic Button
                        Surface(
                            shape = CircleShape,
                            color = if (voiceInputManager.isListening) Color(0xFFEF4444).copy(alpha = 0.15f) else Color.Transparent,
                            modifier = Modifier.size(34.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (!voiceInputManager.isAvailable) {
                                        Toast.makeText(context, getString("voice_not_available", isBn), Toast.LENGTH_SHORT).show()
                                        return@IconButton
                                    }

                                    if (voiceInputManager.isListening) {
                                        voiceInputManager.stopListening()
                                        return@IconButton
                                    }

                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                    if (hasPermission) {
                                        val initialText = text.trim()
                                        voiceInputManager.startListening(
                                            isBn = isBn,
                                            onPartialResult = { newText ->
                                                onTextChange(if (initialText.isNotEmpty()) "$initialText $newText" else newText)
                                            },
                                            onFinalResult = { newText ->
                                                onTextChange(if (initialText.isNotEmpty()) "$initialText $newText" else newText)
                                            },
                                            onError = { errKey, isPermError ->
                                                Toast.makeText(context, getString(errKey, isBn), Toast.LENGTH_SHORT).show()
                                                if (isPermError) {
                                                    try {
                                                        val intent = android.content.Intent(
                                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                            android.net.Uri.fromParts("package", context.packageName, null)
                                                        ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {
                                                        Toast.makeText(context, getString("voice_perm_required", isBn), Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        )
                                    } else {
                                        if (audioPermissionLauncher != null) {
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        } else {
                                            Toast.makeText(context, getString("voice_perm_required", isBn), Toast.LENGTH_LONG).show()
                                            try {
                                                val intent = android.content.Intent(
                                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    android.net.Uri.fromParts("package", context.packageName, null)
                                                ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Mic,
                                    contentDescription = getString("voice_input", isBn),
                                    tint = if (voiceInputManager.isListening) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        // Send Button (Only visible when user has typed something or image attached)
                        AnimatedVisibility(
                            visible = canSend,
                            enter = fadeIn(animationSpec = tween(150)) + scaleIn(animationSpec = tween(150)),
                            exit = fadeOut(animationSpec = tween(150)) + scaleOut(animationSpec = tween(150))
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(tabAccentColor)
                                    .clickable(enabled = canSend) {
                                        if (voiceInputManager.isListening) {
                                            voiceInputManager.stopListening()
                                        }
                                        onSend()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
