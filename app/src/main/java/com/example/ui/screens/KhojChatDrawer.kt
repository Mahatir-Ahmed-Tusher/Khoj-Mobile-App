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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var extractedOcrText by remember { mutableStateOf("") }
    var showImageClaimError by remember { mutableStateOf(false) }
    var isExtractingImage by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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

    // Scroll to bottom when new messages arrive
    LaunchedEffect(chatMessages.size, chatState) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
    if (backDispatcher != null) {
        BackHandler(enabled = true) {
            if (isMenuOpen) {
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

            // Header Bar
            KhojChatDrawerHeader(
                onClose = onClose,
                onMenuClick = { isMenuOpen = !isMenuOpen },
                isBn = isBn,
                fontToUse = fontToUse,
                headerIcon = headerIcon
            )

            // Tabs / Modes Segment
            KhojChatDrawerTabs(
                activeMode = activeMode,
                onModeSelected = { repository.setChatMode(it) },
                isBn = isBn,
                fontToUse = fontToUse
            )

            // Content Area (Messages or Empty State)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
            ) {
                if (chatMessages.isEmpty()) {
                    KhojChatEmptyState(
                        activeMode = activeMode,
                        onPromptClick = { prompt ->
                            textInput = prompt
                            repository.sendChatMessage(prompt, onAutoSwitched = {
                                Toast.makeText(context, if (isBn) "🔍 তথ্য যাচাই মোড সক্রিয় হয়েছে!" else "🔍 Fact-Check mode activated!", Toast.LENGTH_LONG).show()
                            })
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        },
                        isBn = isBn,
                        fontToUse = fontToUse
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(chatMessages, key = { it.id }) { msg ->
                            KhojChatMessageBubble(
                                message = msg,
                                isBn = isBn,
                                fontToUse = fontToUse,
                                isLatestAiMessage = (!msg.isUser && msg == chatMessages.last()),
                                onRetry = { repository.retryLastChatMessage() },
                                ttsHelper = ttsHelper
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
                    }
                }
            }

            // Input Panel
            KhojChatInputPanel(
                text = textInput,
                onTextChange = {
                    textInput = it
                    if (it.isNotBlank()) showImageClaimError = false
                },
                isLoading = chatState != KhojChatState.Idle,
                selectedImageUri = selectedImageUri,
                isExtractingImage = isExtractingImage,
                showImageClaimError = showImageClaimError,
                onPickImage = {
                    pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemoveImage = {
                    selectedImageUri = null
                    extractedOcrText = ""
                    showImageClaimError = false
                },
                onSend = {
                    if (selectedImageUri != null && textInput.trim().isBlank()) {
                        showImageClaimError = true
                        return@KhojChatInputPanel
                    }
                    if (textInput.isNotBlank()) {
                        if (!NetworkErrorHelper.isDeviceOnline(context)) {
                            Toast.makeText(
                                context,
                                if (isBn) "ইন্টারনেট সংযোগ নেই। সংযোগ পরীক্ষা করুন।" else "No internet connection. Please check your network.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@KhojChatInputPanel
                        }
                        val userPrompt = textInput.trim()
                        val imageToSend = selectedImageUri?.toString()
                        val promptForEngine = if (extractedOcrText.isNotBlank()) {
                            "$userPrompt\n\n[ছবি থেকে প্রাপ্ত তথ্য/লেখা]:\n$extractedOcrText"
                        } else {
                            userPrompt
                        }
                        repository.sendChatMessage(
                            text = userPrompt,
                            imageUri = imageToSend,
                            promptForEngine = promptForEngine,
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
                fontToUse = fontToUse
            )
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

        // Smooth Slide-out Menu Overlay Container
        AnimatedVisibility(
            visible = isMenuOpen,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(durationMillis = 250)
            ) + fadeOut(animationSpec = tween(durationMillis = 250)),
            modifier = Modifier
                .fillMaxHeight()
                .width(300.dp)
                .align(Alignment.TopEnd)
                .shadow(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)), shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .padding(16.dp)
                    .clickable(enabled = true, onClick = {}) // consumes click within the panel
            ) {
                // Header Title
                Text(
                    text = if (isBn) "কথোপকথনসমূহ" else "Conversations",
                    fontFamily = fontToUse,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Option 1: New Conversation Button
                Button(
                    onClick = {
                        repository.createNewSession()
                        isMenuOpen = false
                        Toast.makeText(context, if (isBn) "নতুন কথোপকথন শুরু হয়েছে" else "New conversation started", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("new_conversation_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddComment,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBn) "নতুন চ্যাট শুরু করুন" else "New Conversation",
                        fontFamily = fontToUse,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Option 2: Conversation History Label
                Text(
                    text = if (isBn) "ইতিহাস (History)" else "Recent Chats",
                    fontFamily = fontToUse,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Scrollable List of History Items
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (chatSessions.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isBn) "কোনো চ্যাট ইতিহাস নেই" else "No chat history yet",
                                    fontFamily = fontToUse,
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    } else {
                        items(chatSessions, key = { it.id }) { session ->
                            val isSelected = session.id == activeSessionId
                            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

                            val itemBgColor = when {
                                isSelected && isDark -> Color(0xFF334155)
                                isSelected && !isDark -> Color(0xFFE0F2FE)
                                isDark -> Color.Transparent
                                else -> Color(0xFFF8FAFC)
                            }
                            val itemBorder = when {
                                isSelected && isDark -> BorderStroke(1.dp, Color(0xFF475569))
                                isSelected && !isDark -> BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f))
                                isDark -> null
                                else -> BorderStroke(1.dp, Color(0xFFE2E8F0).copy(alpha = 0.6f))
                            }
                            val titleColor = when {
                                isSelected && isDark -> Color.White
                                isSelected && !isDark -> Color(0xFF0369A1)
                                isDark -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            val iconTint = when {
                                isSelected && isDark -> Color(0xFF38BDF8)
                                isSelected && !isDark -> Color(0xFF0284C7)
                                isDark -> Color(0xFF94A3B8)
                                else -> Color(0xFF64748B)
                            }
                            val timestampColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(if (itemBorder != null) Modifier.border(itemBorder, RoundedCornerShape(8.dp)) else Modifier)
                                    .background(itemBgColor)
                                    .clickable {
                                        repository.loadSession(session.id)
                                        isMenuOpen = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.title,
                                        fontFamily = fontToUse,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        color = titleColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = formatSessionTimestamp(session.timestamp, isBn),
                                        fontFamily = fontToUse,
                                        fontSize = 10.sp,
                                        color = timestampColor
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                
                                // Delete single session
                                IconButton(
                                    onClick = {
                                        repository.deleteSession(session.id)
                                        Toast.makeText(context, if (isBn) "কথোপকথনটি মুছে ফেলা হয়েছে" else "Conversation deleted", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Delete conversation",
                                        tint = Color(0xFFEF4444).copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom: Clear All History Button
                if (chatSessions.isNotEmpty()) {
                    var showClearAllConfirm by remember { mutableStateOf(false) }

                    Spacer(modifier = Modifier.height(12.dp))
                    val isDarkSidebar = MaterialTheme.colorScheme.background.luminance() < 0.5f
                    HorizontalDivider(color = if (isDarkSidebar) Color(0xFF334155) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { showClearAllConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBn) "সব ইতিহাস মুছুন" else "Clear All History",
                            fontFamily = fontToUse,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color(0xFFEF4444)
                        )
                    }

                    if (showClearAllConfirm) {
                        AlertDialog(
                            onDismissRequest = { showClearAllConfirm = false },
                            title = {
                                Text(
                                    text = if (isBn) "সব কথোপকথন মুছে ফেলুন" else "Clear All History",
                                    fontFamily = fontToUse,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Text(
                                    text = if (isBn) "আপনি কি নিশ্চিতভাবে আপনার সমস্ত কথোপকথনের ইতিহাস মুছে ফেলতে চান?" else "Are you sure you want to delete all your conversations and history?",
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
    isBn: Boolean,
    fontToUse: FontFamily,
    headerIcon: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back Navigation Button
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = if (isBn) "ফিরে যান" else "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        if (headerIcon != null) {
            headerIcon()
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            Spacer(modifier = Modifier.width(4.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isBn) "খোঁজ চ্যাট" else "Khoj Chat",
                fontFamily = fontToUse,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Hamburger Menu Icon
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .size(44.dp)
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
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val modes = listOf(
            Triple("general", if (isBn) "খোঁজ চ্যাট" else "Khoj Chat", Icons.Default.ChatBubbleOutline),
            Triple("citizen-service", if (isBn) "নাগরিক সেবা" else "Citizen Service", Icons.Outlined.AccountBalance),
            Triple("fact-check", if (isBn) "যাচাই" else "Fact Check", Icons.Default.VerifiedUser)
        )

        modes.forEach { (modeCode, modeLabel, icon) ->
            val isSelected = activeMode == modeCode
            val backgroundBrush = if (isSelected) {
                Brush.horizontalGradient(
                    colors = when (modeCode) {
                        "fact-check" -> listOf(Color(0xFFE11D48), Color(0xFFF43F5E)) // Rose
                        "citizen-service" -> listOf(Color(0xFF0D9488), Color(0xFF14B8A6)) // Teal
                        else -> listOf(Color(0xFF0284C7), Color(0xFF0ea5e9)) // Sky Blue
                    }
                )
            } else {
                null
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (isSelected && backgroundBrush != null) {
                            Modifier.background(backgroundBrush)
                        } else {
                            Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        }
                    )
                    .clickable { onModeSelected(modeCode) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = modeLabel,
                        modifier = Modifier.size(14.dp),
                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = modeLabel,
                        fontFamily = fontToUse,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun KhojChatEmptyState(
    activeMode: String,
    onPromptClick: (String) -> Unit,
    isBn: Boolean,
    fontToUse: FontFamily
) {
    val prompts = when (activeMode) {
        "citizen-service" -> listOf(
            if (isBn) "এনআইডি কার্ড হারিয়ে গেলে নতুন কার্ড কীভাবে পাব?" else "How to get a new NID if I lost it?",
            if (isBn) "ই-পাসপোর্ট আবেদনের নতুন ফি কত?" else "What is the fee for E-passport?",
            if (isBn) "অনলাইনে জন্ম নিবন্ধন সংশোধনের নিয়ম কী?" else "How to correct birth certificate online?"
        )
        "fact-check" -> listOf(
            if (isBn) "ফেসবুকে রটা 'কোকাকোলা বন্ধ হচ্ছে' খবরটি কি সত্যি?" else "Is the news about 'Coca-Cola closing down' true?",
            if (isBn) "ইউটিউবের ভাইরাল ভিডিও কি সত্যি?" else "Is the viral video on YouTube authentic?",
            if (isBn) "লবণের দাম ১০০ টাকা হওয়ার দাবিটি কি গুজব?" else "Is the claim of salt price being 100 Taka a rumor?"
        )
        else -> listOf(
            if (isBn) "কৃত্রিম বুদ্ধিমত্তা কীভাবে কাজ করে?" else "How does Artificial Intelligence work?",
            if (isBn) "বাংলাদেশের ঐতিহাসিক দর্শনীয় স্থানগুলো কী কী?" else "What are the historical places in Bangladesh?",
            if (isBn) "স্মার্টফোন দীর্ঘক্ষণ চার্জ রাখার ৫টি উপায় কী?" else "What are 5 ways to keep phone charge longer?"
        )
    }

    val icon = when (activeMode) {
        "citizen-service" -> Icons.Outlined.AccountBalance
        "fact-check" -> Icons.Outlined.VerifiedUser
        else -> Icons.Outlined.ChatBubbleOutline
    }

    val descText = when (activeMode) {
        "citizen-service" -> if (isBn) "নাগরিক সেবা সম্পর্কিত যেকোনো জটিল সরকারি কাজের নিয়মকানুন জিজ্ঞাসা করুন।" else "Ask any queries regarding Bangladeshi administrative & citizen services."
        "fact-check" -> if (isBn) "অনলাইন গুজব বা যেকোনো সন্দেহজনক খবর যাচাই করতে এখানে দাবিটি লিখুন।" else "Fact check online claims, rumors, or suspicious news pieces here."
        else -> if (isBn) "বিজ্ঞান, প্রযুক্তি, রান্না বা পড়াশোনা নিয়ে যেকোনো সাধারণ প্রশ্ন জিজ্ঞাসা করুন।" else "Ask any general inquiries, questions, facts, or helpful instructions."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing central badge icon
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = when (activeMode) {
                    "fact-check" -> Color(0xFFF43F5E)
                    "citizen-service" -> Color(0xFF0D9488)
                    else -> Color(0xFF0284C7)
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isBn) "জিজ্ঞাসা করুন" else "Ask Anything",
            fontFamily = fontToUse,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = descText,
            fontFamily = fontToUse,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Onboarding suggestions
        Text(
            text = if (isBn) "নিচের যেকোনো একটিতে ট্যাপ করুন:" else "Suggested Prompt Examples:",
            fontFamily = fontToUse,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))

        prompts.forEach { prompt ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPromptClick(prompt) }
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFFEAB308)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = prompt,
                        fontFamily = fontToUse,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun KhojChatMessageBubble(
    message: KhojChatMessage,
    isBn: Boolean,
    fontToUse: FontFamily,
    isLatestAiMessage: Boolean = false,
    onRetry: (() -> Unit)? = null,
    ttsHelper: KhojTtsHelper? = null
) {
    val uriHandler = LocalUriHandler.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current
    
    var displayedText by remember { mutableStateOf(if (isLatestAiMessage) "" else message.text) }
    
    LaunchedEffect(message.text, isLatestAiMessage) {
        if (isLatestAiMessage && displayedText != message.text) {
            val fullText = message.text
            var currentIndex = displayedText.length
            while (currentIndex < fullText.length) {
                val chunkSize = (8..12).random().coerceAtMost(fullText.length - currentIndex)
                currentIndex += chunkSize
                displayedText = fullText.substring(0, currentIndex)
                kotlinx.coroutines.delay(16)
            }
        } else {
            displayedText = message.text
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
                    color = Color(0xFFE11D48)
                )
            }
        }

        if (message.isUser) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF0ea5e9))))
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
                            fontSize = 13,
                            lineHeight = 17,
                            textColor = Color.White,
                            isUser = true
                        )
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
                            fontSize = 14,
                            lineHeight = 22,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            isUser = false
                        )
                    }

                    // Render sources if any
                    if (message.sources.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isBn) "🔍 উৎস / তথ্যসূত্র:" else "🔍 Cited Sources:",
                            fontFamily = fontToUse,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        message.sources.take(3).forEach { source ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (source.url.startsWith("http")) {
                                            uriHandler.openUri(source.url)
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = Color(0xFF0084C7)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = source.title,
                                    fontFamily = fontToUse,
                                    fontSize = 10.sp,
                                    color = Color(0xFF0084C7),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Action Buttons (Copy & Download)
                    if (!message.isError && message.text.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
    val progressText = when (state) {
        is KhojChatState.AutoDetecting -> if (isBn) "গুজব সনাক্ত হয়েছে, যাচাই করা হচ্ছে..." else "Rumour detected, verifying..."
        is KhojChatState.Searching -> if (isBn) "ওয়েব তথ্য অনুসন্ধান করা হচ্ছে..." else "Searching live web..."
        is KhojChatState.Generating -> if (isBn) "খোঁজ এআই চিন্তা করছে..." else "Khoj AI is thinking..."
        else -> ""
    }

    if (progressText.isEmpty()) return

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
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = progressText,
            fontFamily = fontToUse,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.blur(1.dp)
        )
    }
}

@Composable
fun KhojChatInputPanel(
    text: String,
    onTextChange: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit,
    isBn: Boolean,
    fontToUse: FontFamily,
    selectedImageUri: Uri? = null,
    isExtractingImage: Boolean = false,
    showImageClaimError: Boolean = false,
    onPickImage: () -> Unit = {},
    onRemoveImage: () -> Unit = {}
) {
    val context = LocalContext.current
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(if ((MaterialTheme.colorScheme.background.luminance() < 0.5f)) Color(0xFF000000) else MaterialTheme.colorScheme.surface)
            .shadow(4.dp),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
        colors = CardDefaults.cardColors(containerColor = if ((MaterialTheme.colorScheme.background.luminance() < 0.5f)) Color(0xFF000000) else MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Attached Image Preview Chip / Card
            AnimatedVisibility(visible = selectedImageUri != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 2.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(42.dp)) {
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
                                        modifier = Modifier.size(18.dp),
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
                                color = if (isExtractingImage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isExtractingImage) {
                                    if (isBn) "Cloudflare ও Qwen ভিশন প্রসেসিং..." else "Vision OCR processing..."
                                } else {
                                    if (isBn) "ছবি প্রসেস হয়েছে, নিচে আপনার দাবি বা প্রশ্ন লিখুন" else "Image ready, please type your claim or question below"
                                },
                                fontFamily = fontToUse,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        IconButton(
                            onClick = onRemoveImage,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove attached image",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            if (showImageClaimError && selectedImageUri != null) {
                Text(
                    text = getString("claim_required_error", isBn),
                    color = Color(0xFFEF4444),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = fontToUse,
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
                )
            }

            // Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Photo Attachment Button
                IconButton(
                    onClick = onPickImage,
                    enabled = !isLoading && !isExtractingImage,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddPhotoAlternate,
                        contentDescription = if (isBn) "ছবি যোগ করুন" else "Attach Image",
                        tint = if (selectedImageUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(
                        fontFamily = fontToUse,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        platformStyle = PlatformTextStyle(
                            includeFontPadding = true
                        )
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!isLoading && !isExtractingImage) {
                                if (voiceInputManager.isListening) {
                                    voiceInputManager.stopListening()
                                }
                                onSend()
                            }
                        }
                    ),
                    maxLines = 4,
                    singleLine = false,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .border(
                            1.dp,
                            if (voiceInputManager.isListening || (showImageClaimError && selectedImageUri != null)) Color(0xFFEF4444)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) {
                                Text(
                                    text = if (voiceInputManager.isListening) getString("voice_listening", isBn)
                                           else if (isExtractingImage) (if (isBn) "ছবি থেকে তথ্য পড়া হচ্ছে..." else "Reading image...")
                                           else if (selectedImageUri != null) (if (isBn) "ছবিটির সাথে প্রাসঙ্গিক দাবি বা প্রশ্ন লিখুন..." else "Type claim or question regarding the image...")
                                           else (if (isBn) "এখানে বার্তা লিখুন..." else "Type message here..."),
                                    fontFamily = fontToUse,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    color = if (voiceInputManager.isListening) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Voice input mic button (Exact same technology from main page below search bar)
                Surface(
                    shape = CircleShape,
                    color = if (voiceInputManager.isListening) Color(0xFFEF4444).copy(alpha = 0.15f) else Color.Transparent,
                    modifier = Modifier.size(38.dp)
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
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = getString("voice_input", isBn),
                            tint = if (voiceInputManager.isListening) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Send Floating action button
                val canSend = !isLoading && !isExtractingImage && (text.isNotBlank() || selectedImageUri != null)
                IconButton(
                    enabled = canSend,
                    onClick = {
                        if (voiceInputManager.isListening) {
                            voiceInputManager.stopListening()
                        }
                        onSend()
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) {
                                Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF0ea5e9)))
                            } else {
                                Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            }
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(18.dp),
                        tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}
