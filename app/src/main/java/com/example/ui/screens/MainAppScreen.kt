package com.example.ui.screens

import androidx.compose.ui.graphics.luminance
import com.airbnb.lottie.compose.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.example.ui.theme.TiroBanglaFamily
import com.example.ui.theme.AnekBanglaFamily
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import coil.compose.AsyncImage
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.FactCheckProgress
import com.example.data.api.FactCheckEngine
import com.example.data.model.ContradictingEvidence
import com.example.data.model.FactCheckReport
import com.example.data.model.FactCheckSource
import com.example.data.model.DeepSearchReport
import com.example.data.repository.DeepSearchHistoryRepository
import com.example.data.repository.AuthRepository
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.example.data.model.SearchMode
import com.example.ui.theme.*
import com.example.ui.viewmodel.FactCheckViewModel
import com.example.ui.components.MarkdownText
import com.example.ui.components.LiquidGlassBottomNav
import com.example.ui.components.OfflineStateCard
import com.example.ui.components.ThemeSwitcher
import com.example.utils.NetworkErrorHelper
import io.github.jan.supabase.auth.status.SessionStatus
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.IntOffset
enum class AppScreen {
    HOME,
    REPORT,
    HISTORY,
    SETTINGS,
    ABOUT,
    FAQ,
    WRITE_US,
    FUNDING_TRANSPARENCY,
    E_LIBRARY,
    PROFILE,
    FEED,
    NEWS,
    NOTIFICATIONS,
    LANDING,
    LOGIN,
    AI_IMAGE_DETECTOR,
    SETTINGS_CONTROLS,
    ALL_FACTCHECKS,
    CMS_ARTICLE_DETAILS,
    ALL_BLOGS,
    BLOG_ARTICLE_DETAILS,
    DEEP_SEARCH,
    ALL_TRENDING_RUMOURS,
    IMAGE_SEARCH,
    WEB_SEARCH,
    WIDGET_SETTINGS,
    SETTINGS_LANGUAGE,
    SETTINGS_APPEARANCE,
    SETTINGS_HELP_CENTER,
    HELP_ARTICLE,
    UNIFIED_REPORT,
    BANGLA_AI_TEXT_DETECTOR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: FactCheckViewModel) {
    val authViewModel: com.example.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val feedViewModel: com.example.ui.viewmodel.FeedViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val newsViewModel: com.example.ui.viewmodel.NewsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val profileViewModel: com.example.ui.viewmodel.ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val notificationViewModel: com.example.ui.viewmodel.NotificationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val imageSearchViewModel: com.example.ui.viewmodel.ImageSearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val aiImageDetectionViewModel: com.example.ui.viewmodel.AiImageDetectionViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val unifiedImageVerificationViewModel: com.example.ui.viewmodel.UnifiedImageVerificationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val banglaAiTextDetectorViewModel: com.example.ui.viewmodel.BanglaAiTextDetectorViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val unreadCount by notificationViewModel.unreadCount.collectAsStateWithLifecycle()
    val sessionStatus by authViewModel.sessionStatus.collectAsStateWithLifecycle()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val hasCompletedOnboarding by authViewModel.hasCompletedOnboarding.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val networkMonitor = remember { com.example.utils.NetworkMonitor(context) }
    val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)

    var isInitialScreenSet by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var selectedReport by remember { mutableStateOf<FactCheckReport?>(null) }
    var selectedHelpArticleId by remember { mutableStateOf("") }
    var selectedDeepSearchReport by remember { mutableStateOf<DeepSearchReport?>(null) }
    var deepSearchOriginScreen by remember { mutableStateOf(AppScreen.HOME) }
    var selectedCmsArticle by remember { mutableStateOf<com.example.data.model.CmsArticle?>(null) }
    var selectedBlogArticle by remember { mutableStateOf<com.example.data.model.CmsArticle?>(null) }
    var isChatDrawerOpen by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    val saveableStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()

    androidx.activity.compose.BackHandler(enabled = currentScreen == AppScreen.DEEP_SEARCH) {
        val target = deepSearchOriginScreen
        selectedDeepSearchReport = null
        deepSearchOriginScreen = AppScreen.HOME
        currentScreen = target
    }

    var deepSearchQuery by rememberSaveable { mutableStateOf("") }
    var webSearchQuery by rememberSaveable { mutableStateOf("") }
    var initialHomeClaim by remember { mutableStateOf("") }
    var autoFocusSearch by remember { mutableStateOf(false) }
    var triggerVoiceOnLaunch by remember { mutableStateOf(false) }

    val incomingWidgetAction by viewModel.incomingWidgetAction.collectAsStateWithLifecycle()
    val currentCheckingState by viewModel.currentCheckingState.collectAsStateWithLifecycle()
    val streamingMarkdown by viewModel.streamingMarkdown.collectAsStateWithLifecycle()
    val renderingLanguage by viewModel.renderingLanguage.collectAsStateWithLifecycle()
    val responseLanguage by viewModel.responseLanguage.collectAsStateWithLifecycle()
    val isBn = renderingLanguage == "bn"
    val bengaliFontPreference by viewModel.bengaliFontPreference.collectAsStateWithLifecycle()
    val fontToUse = if (isBn) {
        if (bengaliFontPreference == "anek") AnekBanglaFamily else TiroBanglaFamily
    } else {
        FontFamily.Default
    }
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
    val isSystemDark = isSystemInDarkTheme()
    val isDarkTheme = when (themePreference) {
        "dark" -> true
        "light" -> false
        else -> isSystemDark
    }

    // Observe checking state and transition to report on success
    LaunchedEffect(currentCheckingState) {
        if (currentCheckingState is FactCheckProgress.Success) {
            val report = (currentCheckingState as FactCheckProgress.Success).report
            selectedReport = report
            currentScreen = AppScreen.REPORT
            viewModel.resetCheckingState()

            // Refresh other parts of the app instantly
            feedViewModel.loadFeed()
            AuthRepository.currentUserId?.let { profileViewModel.loadProfileData(it) }
        }
    }

    // Handle incoming home screen search widget intents
    LaunchedEffect(incomingWidgetAction) {
        val action = incomingWidgetAction ?: return@LaunchedEffect
        viewModel.consumeIncomingWidgetAction()
        isInitialScreenSet = true

        when (action.mode) {
            SearchMode.FACT_CHECK -> {
                if (!action.query.isNullOrBlank()) {
                    if (authViewModel.isLoggedIn) {
                        currentScreen = AppScreen.HOME
                        initialHomeClaim = action.query
                        viewModel.checkClaim(action.query)
                    } else {
                        viewModel.savePendingSearch(action.query, SearchMode.FACT_CHECK)
                        currentScreen = AppScreen.LOGIN
                    }
                } else {
                    currentScreen = AppScreen.HOME
                    autoFocusSearch = action.autoFocus
                }
            }
            SearchMode.WEB_SEARCH -> {
                webSearchQuery = action.query ?: ""
                currentScreen = AppScreen.WEB_SEARCH
            }
            SearchMode.DEEP_SEARCH -> {
                deepSearchQuery = action.query ?: ""
                currentScreen = AppScreen.DEEP_SEARCH
            }
            SearchMode.IMAGE_SEARCH -> {
                currentScreen = AppScreen.IMAGE_SEARCH
            }
            SearchMode.VOICE_SEARCH -> {
                currentScreen = AppScreen.HOME
                triggerVoiceOnLaunch = true
            }
        }
    }

    // Auto-resume deferred factcheck or navigate users based on session restoration & onboarding state
    LaunchedEffect(sessionStatus, hasCompletedOnboarding) {
        if (sessionStatus is io.github.jan.supabase.auth.status.SessionStatus.Authenticated) {
            authViewModel.setOnboardingCompleted()

            // 1. Process death & Google login immune DataStore check (with 15-min TTL)
            val validPending = viewModel.getValidPendingSearch()
            val pendingClaim = viewModel.pendingClaim.value
            val pendingImageUri = viewModel.pendingImageUri.value

            val pendingSession = viewModel.pendingSession.value

            if (pendingSession != null) {
                unifiedImageVerificationViewModel.initializeSession(
                    pendingSession.originalImageUri,
                    pendingSession.croppedImageUri,
                    pendingSession.selectedModes,
                    pendingSession.additionalContext
                )
                if (pendingSession.selectedModes.size == 1) {
                    when (pendingSession.selectedModes.first()) {
                        com.example.data.model.VerificationMode.FACT_CHECK -> currentScreen = AppScreen.HOME
                        com.example.data.model.VerificationMode.IMAGE_SEARCH -> currentScreen = AppScreen.IMAGE_SEARCH
                        com.example.data.model.VerificationMode.AI_DETECTION -> currentScreen = AppScreen.AI_IMAGE_DETECTOR
                    }
                } else {
                    currentScreen = AppScreen.UNIFIED_REPORT
                }
                viewModel.clearPendingActions()
            } else if (validPending != null) {
                when (validPending.mode) {
                    SearchMode.FACT_CHECK -> {
                        currentScreen = AppScreen.HOME
                        if (!validPending.query.isNullOrBlank()) {
                            initialHomeClaim = validPending.query
                            viewModel.checkClaim(validPending.query)
                        } else {
                            autoFocusSearch = true
                        }
                    }
                    SearchMode.WEB_SEARCH -> {
                        webSearchQuery = validPending.query ?: ""
                        currentScreen = AppScreen.WEB_SEARCH
                    }
                    SearchMode.DEEP_SEARCH -> {
                        deepSearchQuery = validPending.query ?: ""
                        currentScreen = AppScreen.DEEP_SEARCH
                    }
                    SearchMode.IMAGE_SEARCH -> {
                        currentScreen = AppScreen.IMAGE_SEARCH
                    }
                    SearchMode.VOICE_SEARCH -> {
                        currentScreen = AppScreen.HOME
                        triggerVoiceOnLaunch = true
                    }
                }
                viewModel.clearPendingActions()
            } else if (pendingClaim != null) {
                currentScreen = AppScreen.HOME
                viewModel.checkClaim(pendingClaim)
                viewModel.clearPendingActions()
            } else if (pendingImageUri != null) {
                currentScreen = AppScreen.HOME
                viewModel.extractClaimFromImage(pendingImageUri)
                viewModel.clearPendingActions()
            } else if (!isInitialScreenSet || currentScreen == AppScreen.LANDING || currentScreen == AppScreen.LOGIN) {
                currentScreen = AppScreen.HOME
            }
            isInitialScreenSet = true
        } else if (sessionStatus is io.github.jan.supabase.auth.status.SessionStatus.NotAuthenticated) {
            if (!isInitialScreenSet) {
                // Always show LANDING for unauthenticated cold starts.
                // The user can proceed as guest from LOGIN, which will set isInitialScreenSet and
                // route them to HOME. This ensures new & logged-out users always see onboarding.
                currentScreen = AppScreen.LANDING
                isInitialScreenSet = true
            }
        }
    }




    val uriHandler = LocalUriHandler.current
    var menuExpanded by remember { mutableStateOf(false) }

    if (showLogoutConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = {
                Text(
                    text = if (isBn) "অ্যাকাউন্ট থেকে লগআউট করবেন?" else "Log out of your account?",
                    fontFamily = fontToUse
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    showLogoutConfirmDialog = false
                    authViewModel.signOut()
                }) {
                    Text(text = if (isBn) "লগআউট" else "LOG OUT", fontFamily = fontToUse)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text(text = if (isBn) "বাতিল" else "CANCEL", fontFamily = fontToUse)
                }
            }
        )
    }

    BackHandler(enabled = menuExpanded) {
        menuExpanded = false
    }

    BackHandler(enabled = currentScreen == AppScreen.HISTORY) {
        currentScreen = AppScreen.HOME
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            if (!menuExpanded) {
                if (currentScreen == AppScreen.HOME) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.khoj_logo),
                                contentDescription = "Khoj Logo",
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = if (isBn) "খোঁজ" else "Khoj",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            ThemeSwitcher(
                                darkTheme = isDarkTheme,
                                size = 24.dp,
                                onClick = {
                                    viewModel.setThemePreference(if (isDarkTheme) "light" else "dark")
                                }
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = { viewModel.toggleRenderingLanguage() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_language_custom),
                                    contentDescription = "Toggle Language",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                } else if (currentScreen == AppScreen.REPORT) {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = getString("report", isBn),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { currentScreen = AppScreen.HOME }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            }
        },
        bottomBar = {
            if (currentScreen != AppScreen.REPORT && currentScreen != AppScreen.LOGIN && currentScreen != AppScreen.LANDING && currentScreen != AppScreen.DEEP_SEARCH) {
                LiquidGlassBottomNav(
                    currentScreen = currentScreen,
                    onNavigate = { screen ->
                        menuExpanded = false
                        currentScreen = screen
                    },
                    onActionClick = {
                        menuExpanded = false
                        isChatDrawerOpen = true
                    },
                    isBn = isBn,
                    fontToUse = fontToUse,
                    unreadNotificationCount = unreadCount,
                    avatarUrl = currentUser?.photoUrl
                )
            }
        }
    ) { innerPadding ->
        val fullScreenHeaderScreens = setOf(
            AppScreen.SETTINGS,
            AppScreen.SETTINGS_LANGUAGE,
            AppScreen.SETTINGS_APPEARANCE,
            AppScreen.WIDGET_SETTINGS,
            AppScreen.SETTINGS_CONTROLS,
            AppScreen.SETTINGS_HELP_CENTER,
            AppScreen.HELP_ARTICLE,
            AppScreen.ABOUT,
            AppScreen.FAQ,
            AppScreen.WRITE_US,
            AppScreen.FUNDING_TRANSPARENCY,
            AppScreen.E_LIBRARY,
            AppScreen.LANDING,
            AppScreen.DEEP_SEARCH,
            AppScreen.NOTIFICATIONS,
            AppScreen.FEED,
            AppScreen.HISTORY,
            AppScreen.IMAGE_SEARCH,
            AppScreen.AI_IMAGE_DETECTOR,
            AppScreen.ALL_TRENDING_RUMOURS
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = if (currentScreen in fullScreenHeaderScreens) 0.dp else innerPadding.calculateTopPadding(),
                    bottom = if (currentScreen == AppScreen.LANDING || currentScreen == AppScreen.DEEP_SEARCH) 0.dp else innerPadding.calculateBottomPadding()
                )
        ) {
            saveableStateHolder.SaveableStateProvider(currentScreen) {
                when (currentScreen) {
                    AppScreen.HOME -> HomeScreen(
                    viewModel = viewModel,
                    initialClaimInput = initialHomeClaim,
                    autoFocusSearch = autoFocusSearch,
                    triggerVoiceOnLaunch = triggerVoiceOnLaunch,
                    onSearchFocused = { autoFocusSearch = false },
                    onVoiceLaunched = { triggerVoiceOnLaunch = false },
                    onNavigateToHistory = { currentScreen = AppScreen.HISTORY },
                    onSelectReport = { report ->
                        selectedReport = report
                        currentScreen = AppScreen.REPORT
                    },
                    onOpenChatDrawer = { isChatDrawerOpen = true },
                    isLoggedIn = authViewModel.isLoggedIn,
                    onRequireLogin = { query, uri, session ->
                        if (query != null) viewModel.setPendingClaim(query)
                        if (uri != null) viewModel.setPendingImageUri(uri)
                        if (session != null) viewModel.savePendingSession(session)
                        currentScreen = AppScreen.LOGIN
                    },
                    onNavigateToDeepSearch = { query ->
                        deepSearchQuery = query
                        currentScreen = AppScreen.DEEP_SEARCH
                    },
                    onNavigateToWebSearch = { query ->
                        webSearchQuery = query
                        currentScreen = AppScreen.WEB_SEARCH
                    },
                    onNavigateToAllTrendingRumours = { currentScreen = AppScreen.ALL_TRENDING_RUMOURS },
                    onNavigateToAiImageDetector = { currentScreen = AppScreen.AI_IMAGE_DETECTOR },
                    onNavigateToBanglaAiTextDetector = { textToAnalyze ->
                        if (!textToAnalyze.isNullOrBlank()) {
                            banglaAiTextDetectorViewModel.onTextChange(textToAnalyze)
                            banglaAiTextDetectorViewModel.analyze()
                        }
                        currentScreen = AppScreen.BANGLA_AI_TEXT_DETECTOR
                    },
                    onNavigateToImageSearch = { currentScreen = AppScreen.IMAGE_SEARCH },
                    onNavigateToAllFactChecks = { currentScreen = AppScreen.ALL_FACTCHECKS },
                    onSelectCmsArticle = { article ->
                        selectedCmsArticle = article
                        currentScreen = AppScreen.CMS_ARTICLE_DETAILS
                    },
                    onStartUnifiedVerification = { uri, croppedUri, modes, contextStr ->
                        unifiedImageVerificationViewModel.initializeSession(uri, croppedUri, modes, contextStr)
                        if (modes.size == 1) {
                            val finalUri = croppedUri ?: uri
                            when (modes.first()) {
                                com.example.data.model.VerificationMode.FACT_CHECK -> {
                                    viewModel.extractClaimFromImage(finalUri, contextStr)
                                    currentScreen = AppScreen.HOME
                                }
                                com.example.data.model.VerificationMode.IMAGE_SEARCH -> {
                                    imageSearchViewModel.setSelectedUri(finalUri)
                                    currentScreen = AppScreen.IMAGE_SEARCH
                                }
                                com.example.data.model.VerificationMode.AI_DETECTION -> {
                                    aiImageDetectionViewModel.setSelectedImage(finalUri)
                                    aiImageDetectionViewModel.analyzeImage(finalUri)
                                    currentScreen = AppScreen.AI_IMAGE_DETECTOR
                                }
                            }
                        } else {
                            currentScreen = AppScreen.UNIFIED_REPORT
                        }
                    },
                    onNavigateToAllBlogs = { currentScreen = AppScreen.ALL_BLOGS },
                    onSelectBlogArticle = { article ->
                        selectedBlogArticle = article
                        currentScreen = AppScreen.BLOG_ARTICLE_DETAILS
                    },
                    userName = currentUser?.displayName
                )
                AppScreen.REPORT -> selectedReport?.let { report ->
                    ReportScreen(
                        report = report,
                        viewModel = viewModel,
                        onPrivacyChanged = { feedViewModel.loadFeed() },
                        onBack = { currentScreen = AppScreen.HOME }
                    )
                } ?: HomeScreen(
                    viewModel = viewModel,
                    onNavigateToHistory = { currentScreen = AppScreen.HISTORY },
                    onSelectReport = { report ->
                        selectedReport = report
                        currentScreen = AppScreen.REPORT
                    },
                    onOpenChatDrawer = { isChatDrawerOpen = true },
                    isLoggedIn = authViewModel.isLoggedIn,
                    onRequireLogin = { query, uri, session ->
                        if (query != null) viewModel.setPendingClaim(query)
                        if (uri != null) viewModel.setPendingImageUri(uri)
                        if (session != null) viewModel.savePendingSession(session)
                        currentScreen = AppScreen.LOGIN
                    },
                    onNavigateToAiImageDetector = { currentScreen = AppScreen.AI_IMAGE_DETECTOR },
                    onNavigateToBanglaAiTextDetector = { textToAnalyze ->
                        if (!textToAnalyze.isNullOrBlank()) {
                            banglaAiTextDetectorViewModel.onTextChange(textToAnalyze)
                            banglaAiTextDetectorViewModel.analyze()
                        }
                        currentScreen = AppScreen.BANGLA_AI_TEXT_DETECTOR
                    },
                    onNavigateToImageSearch = { currentScreen = AppScreen.IMAGE_SEARCH },
                    onNavigateToAllFactChecks = { currentScreen = AppScreen.ALL_FACTCHECKS },
                    onSelectCmsArticle = { article ->
                        selectedCmsArticle = article
                        currentScreen = AppScreen.CMS_ARTICLE_DETAILS
                    },
                    onStartUnifiedVerification = { uri, croppedUri, modes, contextStr ->
                        unifiedImageVerificationViewModel.initializeSession(uri, croppedUri, modes, contextStr)
                        if (modes.size == 1) {
                            val finalUri = croppedUri ?: uri
                            when (modes.first()) {
                                com.example.data.model.VerificationMode.FACT_CHECK -> {
                                    viewModel.extractClaimFromImage(finalUri, contextStr)
                                    currentScreen = AppScreen.HOME // Will auto start
                                }
                                com.example.data.model.VerificationMode.IMAGE_SEARCH -> {
                                    imageSearchViewModel.setSelectedUri(finalUri)
                                    currentScreen = AppScreen.IMAGE_SEARCH
                                }
                                com.example.data.model.VerificationMode.AI_DETECTION -> {
                                    aiImageDetectionViewModel.setSelectedImage(finalUri)
                                    aiImageDetectionViewModel.analyzeImage(finalUri)
                                    currentScreen = AppScreen.AI_IMAGE_DETECTOR
                                }
                            }
                        } else {
                            currentScreen = AppScreen.UNIFIED_REPORT
                        }
                    },
                    onNavigateToAllBlogs = { currentScreen = AppScreen.ALL_BLOGS },
                    onSelectBlogArticle = { article ->
                        selectedBlogArticle = article
                        currentScreen = AppScreen.BLOG_ARTICLE_DETAILS
                    },
                    userName = currentUser?.displayName
                )
                AppScreen.HISTORY -> HistoryScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = AppScreen.HOME },
                    onSelectReport = { report ->
                        selectedReport = report
                        currentScreen = AppScreen.REPORT
                    },
                    onSelectDeepSearchReport = { deepReport ->
                        selectedDeepSearchReport = deepReport
                        deepSearchOriginScreen = AppScreen.HISTORY
                        currentScreen = AppScreen.DEEP_SEARCH
                    }
                )
                AppScreen.SETTINGS -> SettingsMainScreen(
                    viewModel = viewModel,
                    onNavigateToLanguage = { currentScreen = AppScreen.SETTINGS_LANGUAGE },
                    onNavigateToAppearance = { currentScreen = AppScreen.SETTINGS_APPEARANCE },
                    onNavigateToWidgetSettings = { currentScreen = AppScreen.WIDGET_SETTINGS },
                    onNavigateToControls = { currentScreen = AppScreen.SETTINGS_CONTROLS },
                    onNavigateToHelpCenter = { currentScreen = AppScreen.SETTINGS_HELP_CENTER },
                    onBack = { currentScreen = AppScreen.HOME }
                )
                AppScreen.SETTINGS_LANGUAGE -> SettingsLanguageScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = AppScreen.SETTINGS }
                )
                AppScreen.SETTINGS_APPEARANCE -> SettingsAppearanceScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = AppScreen.SETTINGS }
                )
                AppScreen.SETTINGS_HELP_CENTER -> HelpCenterScreen(
                    viewModel = viewModel,
                    onNavigateToArticle = { articleId ->
                        // Pass article ID to the HelpArticleScreen (we need a state variable for this, let's create one below or use a basic string)
                        selectedHelpArticleId = articleId
                        currentScreen = AppScreen.HELP_ARTICLE
                    },
                    onBack = { currentScreen = AppScreen.SETTINGS }
                )
                AppScreen.HELP_ARTICLE -> HelpArticleScreen(
                    articleId = selectedHelpArticleId,
                    viewModel = viewModel,
                    onBack = { currentScreen = AppScreen.SETTINGS_HELP_CENTER }
                )
                AppScreen.WIDGET_SETTINGS -> WidgetSettingsScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = AppScreen.SETTINGS }
                )
                AppScreen.SETTINGS_CONTROLS -> SettingsControlsScreen(
                    aiViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                    factCheckViewModel = viewModel,
                    imageSearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                    isLoggedIn = authViewModel.isLoggedIn,
                    onRequireLogin = { currentScreen = AppScreen.LOGIN },
                    onBack = { currentScreen = AppScreen.SETTINGS }
                )
                AppScreen.UNIFIED_REPORT -> UnifiedReportScreen(
                    unifiedViewModel = unifiedImageVerificationViewModel,
                    onBack = { currentScreen = AppScreen.HOME }
                )
                AppScreen.ABOUT -> AboutScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = AppScreen.HOME }
                )
                AppScreen.FAQ -> FaqScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = AppScreen.HOME },
                    onNavigateToScreen = { screen -> currentScreen = screen }
                )
                AppScreen.WRITE_US -> WriteUsScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = AppScreen.HOME }
                )
                AppScreen.FUNDING_TRANSPARENCY -> FundingTransparencyScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = AppScreen.HOME },
                    onNavigateToScreen = { screen -> currentScreen = screen }
                )
                AppScreen.E_LIBRARY -> ELibraryScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = AppScreen.HOME }
                )
                AppScreen.PROFILE -> ProfileScreen(
                    factCheckViewModel = viewModel,
                    authViewModel = authViewModel,
                    profileViewModel = profileViewModel,
                    onLogout = { currentScreen = AppScreen.LANDING },
                    onSelectReport = { report ->
                        selectedReport = report
                        currentScreen = AppScreen.REPORT
                    },
                    onSelectDeepSearchReport = { deepReport ->
                        selectedDeepSearchReport = deepReport
                        deepSearchOriginScreen = AppScreen.PROFILE
                        currentScreen = AppScreen.DEEP_SEARCH
                    },
                    onNavigateToLogin = { currentScreen = AppScreen.LOGIN },
                    onBack = { currentScreen = AppScreen.HOME }
                )
                AppScreen.FEED -> FeedScreen(
                    feedViewModel = feedViewModel,
                    isBn = isBn,
                    fontToUse = fontToUse,
                    onSelectReport = { report ->
                        selectedReport = report
                        currentScreen = AppScreen.REPORT
                    },
                    onSelectDeepSearchReport = { deepReport ->
                        selectedDeepSearchReport = deepReport
                        deepSearchOriginScreen = AppScreen.FEED
                        currentScreen = AppScreen.DEEP_SEARCH
                    },
                    onSelectCmsArticle = { article ->
                        selectedCmsArticle = article
                        currentScreen = AppScreen.CMS_ARTICLE_DETAILS
                    },
                    onSelectBlogArticle = { article ->
                        selectedBlogArticle = article
                        currentScreen = AppScreen.BLOG_ARTICLE_DETAILS
                    },
                    onNavigateToNews = { currentScreen = AppScreen.NEWS },
                    onNavigateBack = { currentScreen = AppScreen.HOME }
                )
                AppScreen.NEWS -> NewsScreen(
                    newsViewModel = newsViewModel,
                    isBn = isBn,
                    fontToUse = fontToUse
                )
                AppScreen.NOTIFICATIONS -> NotificationScreen(
                    viewModel = notificationViewModel,
                    isBn = isBn,
                    fontToUse = fontToUse,
                    onBack = { currentScreen = AppScreen.HOME },
                    onNavigateToTarget = { target ->
                        when (target) {
                            is com.example.data.repository.NotificationTarget.FactCheck -> {
                                selectedReport = target.report
                                currentScreen = AppScreen.REPORT
                            }
                            is com.example.data.repository.NotificationTarget.DeepSearch -> {
                                selectedDeepSearchReport = target.report
                                deepSearchOriginScreen = AppScreen.NOTIFICATIONS
                                currentScreen = AppScreen.DEEP_SEARCH
                            }
                            is com.example.data.repository.NotificationTarget.Cms -> {
                                if (target.isBlog) {
                                    selectedBlogArticle = target.article
                                    currentScreen = AppScreen.BLOG_ARTICLE_DETAILS
                                } else {
                                    selectedCmsArticle = target.article
                                    currentScreen = AppScreen.CMS_ARTICLE_DETAILS
                                }
                            }
                            is com.example.data.repository.NotificationTarget.Unknown -> {
                                // No action if target cannot be found
                            }
                        }
                    }
                )
                AppScreen.LANDING -> LandingPageScreen(
                    onGetStarted = {
                        // Do NOT mark onboarding complete here — only mark it done
                        // when the user actually authenticates (handled in AuthViewModel)
                        // or explicitly chooses "Continue as Guest" on the login screen.
                        currentScreen = AppScreen.LOGIN
                    },
                    fontToUse = fontToUse,
                    isDarkTheme = isDarkTheme
                )
                AppScreen.LOGIN -> LoginScreen(
                    authViewModel = authViewModel,
                    onLoginSuccess = {
                        authViewModel.setOnboardingCompleted()
                        currentScreen = AppScreen.HOME
                    },
                    onContinueAsGuest = {
                        authViewModel.setOnboardingCompleted()
                        viewModel.clearPendingActions()
                        currentScreen = AppScreen.HOME
                    },
                    onNavigateBack = { currentScreen = AppScreen.LANDING }
                )
                AppScreen.AI_IMAGE_DETECTOR -> {
                    if (!isOnline) {
                        NoInternetScreen(
                            isBn = isBn,
                            fontToUse = fontToUse,
                            featureName = if (isBn) "এআই ইমেজ যাচাই" else "AI Image Detector",
                            onBack = { currentScreen = AppScreen.HOME }
                        )
                    } else {
                        AiImageDetectorScreen(
                            viewModel = aiImageDetectionViewModel,
                            onNavigateBack = { currentScreen = AppScreen.HOME },
                            onNavigateToSettingsControls = { currentScreen = AppScreen.SETTINGS_CONTROLS },
                            isBn = isBn
                        )
                    }
                }
                AppScreen.BANGLA_AI_TEXT_DETECTOR -> {
                    if (!isOnline) {
                        NoInternetScreen(
                            isBn = isBn,
                            fontToUse = fontToUse,
                            featureName = if (isBn) "AI লেখা যাচাই" else "AI Text Verify",
                            onBack = { currentScreen = AppScreen.HOME }
                        )
                    } else {
                        BanglaAiTextDetectorScreen(
                            viewModel = banglaAiTextDetectorViewModel,
                            onNavigateBack = { currentScreen = AppScreen.HOME },
                            isBn = isBn,
                            fontFamily = fontToUse
                        )
                    }
                }
                AppScreen.ALL_FACTCHECKS -> AllFactChecksScreen(
                    viewModel = viewModel,
                    isBn = isBn,
                    onBack = { currentScreen = AppScreen.HOME },
                    onSelectCmsArticle = { article ->
                        selectedCmsArticle = article
                        currentScreen = AppScreen.CMS_ARTICLE_DETAILS
                    }
                )
                AppScreen.CMS_ARTICLE_DETAILS -> selectedCmsArticle?.let { article ->
                    CmsArticleScreen(
                        article = article,
                        isBn = isBn,
                        bengaliFont = bengaliFontPreference,
                        onBack = { currentScreen = AppScreen.HOME }
                    )
                } ?: run {
                    currentScreen = AppScreen.HOME
                }
                AppScreen.ALL_BLOGS -> AllBlogsScreen(
                    viewModel = viewModel,
                    isBn = isBn,
                    onBack = { currentScreen = AppScreen.HOME },
                    onSelectBlogArticle = { article ->
                        selectedBlogArticle = article
                        currentScreen = AppScreen.BLOG_ARTICLE_DETAILS
                    }
                )
                AppScreen.BLOG_ARTICLE_DETAILS -> selectedBlogArticle?.let { article ->
                    BlogArticleScreen(
                        article = article,
                        isBn = isBn,
                        bengaliFont = bengaliFontPreference,
                        onBack = { currentScreen = AppScreen.HOME }
                    )
                } ?: run {
                    currentScreen = AppScreen.HOME
                }
                AppScreen.DEEP_SEARCH -> {
                    if (!isOnline && selectedDeepSearchReport == null) {
                        NoInternetScreen(
                            isBn = isBn,
                            fontToUse = fontToUse,
                            featureName = if (isBn) "ডিপ সার্চ" else "DeepSearch",
                            onBack = {
                                val target = deepSearchOriginScreen
                                selectedDeepSearchReport = null
                                deepSearchOriginScreen = AppScreen.HOME
                                currentScreen = target
                            }
                        )
                    } else {
                        DeepSearchScreen(
                            initialQuery = deepSearchQuery,
                            initialReport = selectedDeepSearchReport,
                            isBn = isBn,
                            fontToUse = fontToUse,
                            onBack = {
                                val target = deepSearchOriginScreen
                                selectedDeepSearchReport = null
                                deepSearchOriginScreen = AppScreen.HOME
                                currentScreen = target
                            }
                        )
                    }
                }
                AppScreen.ALL_TRENDING_RUMOURS -> AllTrendingRumoursScreen(
                    isBn = isBn,
                    onBack = { currentScreen = AppScreen.HOME }
                )
                AppScreen.IMAGE_SEARCH -> {
                    if (!isOnline) {
                        NoInternetScreen(
                            isBn = isBn,
                            fontToUse = fontToUse,
                            featureName = if (isBn) "ইমেজ সার্চ" else "Image Search",
                            onBack = { currentScreen = AppScreen.HOME }
                        )
                    } else {
                        ImageSearchScreen(
                            viewModel = imageSearchViewModel,
                            isBn = isBn,
                            onBack = { currentScreen = AppScreen.HOME },
                            onNavigateToSettings = { currentScreen = AppScreen.SETTINGS }
                        )
                    }
                }
                AppScreen.WEB_SEARCH -> {
                    if (!isOnline) {
                        NoInternetScreen(
                            isBn = isBn,
                            fontToUse = fontToUse,
                            featureName = if (isBn) "ওয়েব সার্চ" else "Web Search",
                            onBack = { currentScreen = AppScreen.HOME }
                        )
                    } else {
                        com.example.ui.screens.WebSearchScreen(
                            initialQuery = webSearchQuery,
                            isBn = isBn,
                            fontToUse = fontToUse,
                            onBack = { currentScreen = AppScreen.HOME }
                        )
                    }
                }
            }
            } // End of SaveableStateProvider

            // Check active progress scanning overlay
            val isCheckingImage by viewModel.isCheckingImage.collectAsStateWithLifecycle()
            if (currentCheckingState !is FactCheckProgress.Idle && currentCheckingState !is FactCheckProgress.Success) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { },
                    properties = androidx.compose.ui.window.DialogProperties(
                        usePlatformDefaultWidth = false,
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false
                    )
                ) {
                    CheckingProgressOverlay(
                        state = currentCheckingState,
                        isFromImage = isCheckingImage,
                        streamingMarkdown = streamingMarkdown,
                        isBn = isBn,
                        onCancel = { viewModel.resetCheckingState() },
                        onRetry = { viewModel.retryLastCheck() }
                    )
                }
            }

            // ── Full-Page Menu Screen (Over screen content, with bottom navbar visible) ──
            AnimatedVisibility(
                visible = menuExpanded,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(220)),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(180)),
                modifier = Modifier.fillMaxSize()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {
                        // ── Top Header Row (Title & Close Button) ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isBn) "মেনু" else "Menu",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Surface(
                                onClick = { menuExpanded = false },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Menu",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // ── Profile / Guest Card at Top ──
                        if (authViewModel.isLoggedIn && currentUser != null) {
                            val userDisplayName = currentUser?.displayName?.trim()?.ifBlank { null }
                                ?: currentUser?.email?.substringBefore("@")
                                ?: if (isBn) "ব্যবহারকারী" else "User"

                            Surface(
                                onClick = {
                                    menuExpanded = false
                                    currentScreen = AppScreen.PROFILE
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(SlatePrimary.copy(alpha = 0.15f))
                                            .border(1.2.dp, SlatePrimary.copy(alpha = 0.6f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = coil.request.ImageRequest.Builder(LocalContext.current)
                                                .data(com.example.data.local.UserAvatarManager.getEffectiveAvatarUrl(LocalContext.current, currentUser))
                                                .decoderFactory(coil.decode.SvgDecoder.Factory())
                                                .crossfade(true)
                                                .placeholder(R.drawable.khoj_logo)
                                                .error(R.drawable.khoj_logo)
                                                .fallback(R.drawable.khoj_logo)
                                                .build(),
                                            contentDescription = "Profile Avatar",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = userDisplayName,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(1.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = if (isBn) "প্রোফাইল দেখুন" else "View your profile",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                color = SlatePrimary
                                            )
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = null,
                                                tint = SlatePrimary,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Guest Account Card
                            Surface(
                                onClick = {
                                    menuExpanded = false
                                    currentScreen = AppScreen.LOGIN
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = SlatePrimary.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, SlatePrimary.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(SlatePrimary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = SlatePrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isBn) "লগইন করুন" else "Log In",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(Modifier.height(1.dp))
                                        Text(
                                            text = if (isBn) "সকল সুবিধা পেতে সাইন ইন করুন" else "Sign in to access all features",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Normal
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = SlatePrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), thickness = 0.8.dp)

                        // ── Scrollable Menu List ──
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 6.dp)
                        ) {
                            DrawerMenuItem(
                                icon = Icons.Outlined.History,
                                label = if (isBn) "একটিভিটির ইতিহাস" else "Activity History",
                                selected = currentScreen == AppScreen.HISTORY,
                                onClick = {
                                    menuExpanded = false
                                    currentScreen = AppScreen.HISTORY
                                }
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                thickness = 0.8.dp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            // Section 1: Features
                            Text(
                                text = if (isBn) "ফিচারসমূহ" else "Features",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SlatePrimary,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                            )

                            DrawerMenuItem(
                                icon = Icons.Default.Book,
                                label = if (isBn) "ই-গ্রন্থ সম্ভার" else "E-Library",
                                subtitle = if (isBn) "তথ্য যাচাইয়ের বই ও রিসোর্স" else "Fact-checking books & resources",
                                selected = currentScreen == AppScreen.E_LIBRARY,
                                onClick = {
                                    menuExpanded = false
                                    currentScreen = AppScreen.E_LIBRARY
                                }
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                thickness = 0.8.dp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            // Section 2: Info & Resources
                            Text(
                                text = if (isBn) "আমাদের সম্পর্কে" else "About Khoj",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SlatePrimary,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                            )

                            DrawerMenuItem(
                                icon = Icons.Default.Info,
                                label = if (isBn) "খোঁজ সম্পর্কে" else "About Us",
                                subtitle = if (isBn) "আমাদের লক্ষ্য ও পরিচয়" else "Our mission & team",
                                selected = currentScreen == AppScreen.ABOUT,
                                onClick = {
                                    menuExpanded = false
                                    currentScreen = AppScreen.ABOUT
                                }
                            )

                            DrawerMenuItem(
                                icon = Icons.AutoMirrored.Filled.HelpOutline,
                                label = if (isBn) "সচরাচর জিজ্ঞাসা" else "FAQ",
                                subtitle = if (isBn) "সাধারণ প্রশ্নের উত্তর" else "Frequently asked questions",
                                selected = currentScreen == AppScreen.FAQ,
                                onClick = {
                                    menuExpanded = false
                                    currentScreen = AppScreen.FAQ
                                }
                            )

                            DrawerMenuItem(
                                icon = Icons.Default.Edit,
                                label = if (isBn) "কীভাবে লিখবেন" else "Write Us",
                                subtitle = if (isBn) "মতামত বা রিপোর্ট পাঠান" else "Send feedback or reports",
                                selected = currentScreen == AppScreen.WRITE_US,
                                onClick = {
                                    menuExpanded = false
                                    currentScreen = AppScreen.WRITE_US
                                }
                            )

                            DrawerMenuItem(
                                icon = Icons.Default.AccountBalance,
                                label = if (isBn) "অর্থায়ন ও স্বচ্ছতা" else "Funding & Transparency",
                                subtitle = if (isBn) "অর্থায়ন ও নীতিমালার বিবরণ" else "Funding & financial ethics",
                                selected = currentScreen == AppScreen.FUNDING_TRANSPARENCY,
                                onClick = {
                                    menuExpanded = false
                                    currentScreen = AppScreen.FUNDING_TRANSPARENCY
                                }
                            )

                            DrawerMenuItem(
                                icon = Icons.Default.Settings,
                                label = if (isBn) "সেটিংস" else "Settings",
                                subtitle = if (isBn) "ভাষা ও থিম পরিবর্তন" else "Language, theme & preferences",
                                selected = currentScreen == AppScreen.SETTINGS,
                                onClick = {
                                    menuExpanded = false
                                    currentScreen = AppScreen.SETTINGS
                                }
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                thickness = 0.8.dp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            // Section 3: Policies & External
                            Text(
                                text = if (isBn) "নীতিমালা ও অন্যান্য" else "Policies & Legal",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SlatePrimary,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                            )

                            DrawerMenuItem(
                                icon = Icons.Default.VerifiedUser,
                                label = if (isBn) "গোপনীয়তা নীতি" else "Privacy Policy",
                                isExternal = true,
                                selected = false,
                                onClick = {
                                    menuExpanded = false
                                    uriHandler.openUri("https://khoj-ai.bd/privacy-policy")
                                }
                            )

                            DrawerMenuItem(
                                icon = Icons.Default.Description,
                                label = if (isBn) "শর্তাবলী" else "Terms of Service",
                                isExternal = true,
                                selected = false,
                                onClick = {
                                    menuExpanded = false
                                    uriHandler.openUri("https://khoj-ai.bd/terms")
                                }
                            )

                            DrawerMenuItem(
                                icon = Icons.Default.Code,
                                label = if (isBn) "এপিআই" else "API Docs",
                                isExternal = true,
                                selected = false,
                                onClick = {
                                    menuExpanded = false
                                    uriHandler.openUri("https://khoj-ai.bd/api-docs")
                                }
                            )

                            // ── Bottom Action Section (Log Out / Log In + Version) ──
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), thickness = 0.8.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (authViewModel.isLoggedIn && currentUser != null) {
                                    // Log Out Option
                                    Surface(
                                        onClick = {
                                            menuExpanded = false
                                            showLogoutConfirmDialog = true
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                                    contentDescription = "Log Out",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            Text(
                                                text = if (isBn) "লগআউট" else "Log Out",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.5.sp
                                                ),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                } else {
                                    // Log In Option if Guest
                                    Surface(
                                        onClick = {
                                            menuExpanded = false
                                            currentScreen = AppScreen.LOGIN
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = SlatePrimary.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, SlatePrimary.copy(alpha = 0.30f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(SlatePrimary.copy(alpha = 0.20f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Login,
                                                    contentDescription = "Log In",
                                                    tint = SlatePrimary,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            Text(
                                                text = if (isBn) "লগইন করুন" else "Log In",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.5.sp
                                                ),
                                                color = SlatePrimary
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = if (isBn) "খোঁজ সংস্করণ ২.০.১" else "Khoj Version 2.0.1",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.padding(top = 2.dp, bottom = 24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    KhojChatDrawer(
        isOpen = isChatDrawerOpen,
        onClose = { isChatDrawerOpen = false },
        viewModel = viewModel,
        isBn = isBn,
        fontToUse = fontToUse
    )
}
}

@Composable
fun DrawerMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean = false,
    subtitle: String? = null,
    isExternal: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) SlatePrimary.copy(alpha = 0.12f) else Color.Transparent,
        contentColor = if (selected) SlatePrimary else MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon in rounded container
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) SlatePrimary.copy(alpha = 0.20f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) SlatePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    ),
                    color = if (selected) SlatePrimary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isExternal) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(13.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (selected) SlatePrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
fun ActionRoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val baseColor = if (isActive) Color(0xFFEF4444) else (if (isDarkTheme) Color(0xFF38BDF8) else Color(0xFF0084C7))
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(baseColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .border(BorderStroke(1.dp, baseColor.copy(alpha = 0.3f)), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = baseColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun RecentSearchItem(
    report: FactCheckReport,
    isBn: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    timeText: String
) {
    val verdictType = report.verdict.lowercase()
    val badgeColor = when (verdictType) {
        "true" -> Color(0xFF16A34A) // Vibrant Emerald/Green matching screenshot
        "false" -> Color(0xFFDC2626) // Crimson Red matching screenshot
        else -> Color(0xFFD97706) // Golden Amber matching screenshot
    }
    val badgeBg = when (verdictType) {
        "true" -> Color(0xFFE2F6EA) // Very soft green circle background
        "false" -> Color(0xFFFDE8E8) // Very soft red circle background
        else -> Color(0xFFFEF3C7) // Very soft amber/yellow background
    }
    val badgeLabel = when (verdictType) {
        "true" -> getString("true", isBn)
        "false" -> getString("false", isBn)
        else -> getString("misleading", isBn)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF000000).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.6f)
        ),
        border = BorderStroke(1.dp, if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(badgeBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when (verdictType) {
                    "true" -> {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(11.dp)
                                    .padding(bottom = 1.dp)
                            )
                        }
                    }
                    "false" -> {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(11.dp)
                                    .padding(bottom = 1.dp)
                            )
                        }
                    }
                    else -> {
                        // Unverifiable: filled circle with question mark
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(badgeColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QuestionMark,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = report.claimText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        lineHeight = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        maxLines = 1,
                        softWrap = false
                    )

                    Text(
                        text = "•",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )

                    Text(
                        text = timeText,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.BookmarkBorder,
                    contentDescription = "Save",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: FactCheckViewModel,
    initialClaimInput: String = "",
    autoFocusSearch: Boolean = false,
    triggerVoiceOnLaunch: Boolean = false,
    onSearchFocused: () -> Unit = {},
    onVoiceLaunched: () -> Unit = {},
    onNavigateToHistory: () -> Unit,
    onSelectReport: (FactCheckReport) -> Unit,
    onOpenChatDrawer: () -> Unit,
    isLoggedIn: Boolean,
    onRequireLogin: (pendingQuery: String?, pendingImageUri: Uri?, pendingSession: com.example.data.model.VerificationSession?) -> Unit = { _, _, _ -> },
    onNavigateToDeepSearch: (String) -> Unit = {},
    onNavigateToWebSearch: (String) -> Unit = {},
    onNavigateToAllTrendingRumours: () -> Unit = {},
    onNavigateToAiImageDetector: () -> Unit,
    onNavigateToBanglaAiTextDetector: (String?) -> Unit = {},
    onNavigateToImageSearch: () -> Unit = {},
    onNavigateToAllFactChecks: () -> Unit,
    onSelectCmsArticle: (com.example.data.model.CmsArticle) -> Unit,
    onNavigateToAllBlogs: () -> Unit,
    onSelectBlogArticle: (com.example.data.model.CmsArticle) -> Unit,
    onStartUnifiedVerification: (Uri, Uri?, Set<com.example.data.model.VerificationMode>, String?) -> Unit = { _, _, _, _ -> },
    userName: String? = null
) {
    var claimInput by remember(initialClaimInput) { mutableStateOf(initialClaimInput) }
    val searchFocusRequester = remember { FocusRequester() }
    var isDeepSearchEnabled by rememberSaveable { mutableStateOf(false) }
    var isWebSearchEnabled by rememberSaveable { mutableStateOf(false) }
    var showDeepSearchDrawer by remember { mutableStateOf(false) }
    var showLargeTextActionModal by remember { mutableStateOf(false) }
    var pendingLargeTextQuery by remember { mutableStateOf("") }
    val responseLanguage by viewModel.responseLanguage.collectAsStateWithLifecycle()
    val renderingLanguage by viewModel.renderingLanguage.collectAsStateWithLifecycle()
    val bengaliFontPreference by viewModel.bengaliFontPreference.collectAsStateWithLifecycle()
    val isBn = renderingLanguage == "bn"
    val fontToUse = if (isBn) {
        if (bengaliFontPreference == "anek") AnekBanglaFamily else TiroBanglaFamily
    } else {
        FontFamily.Default
    }
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
    val isSystemDark = isSystemInDarkTheme()
    val isDarkTheme = when (themePreference) {
        "dark" -> true
        "light" -> false
        else -> isSystemDark
    }
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val executeFactCheck: (String) -> Unit = { query ->
        if (isDeepSearchEnabled) {
            onNavigateToDeepSearch(query)
        } else if (isWebSearchEnabled) {
            onNavigateToWebSearch(query)
        } else if (isLoggedIn) {
            viewModel.checkClaim(query)
        } else {
            onRequireLogin(query, null, null)
        }
    }

    val handleSearchSubmit: (String) -> Unit = { query ->
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            keyboardController?.hide()
            val wordCount = trimmed.split(Regex("\\s+")).count { it.isNotBlank() }
            if (wordCount > 140) {
                pendingLargeTextQuery = trimmed
                showLargeTextActionModal = true
            } else {
                executeFactCheck(trimmed)
            }
        }
    }

    val historyReports by viewModel.historyReports.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var imageForVerification by remember { mutableStateOf<Uri?>(null) }
    
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            imageForVerification = uri
        }
    }

    if (imageForVerification != null) {
        ImageVerificationDrawer(
            originalUri = imageForVerification!!,
            onDismissRequest = { imageForVerification = null },
            onSend = { croppedUri, selectedModes, additionalContext ->
                val original = imageForVerification!!
                imageForVerification = null // Hide drawer
                val session = com.example.data.model.VerificationSession(
                    originalImageUri = original,
                    croppedImageUri = croppedUri,
                    selectedModes = selectedModes,
                    additionalContext = additionalContext
                )
                if (isLoggedIn) {
                    onStartUnifiedVerification(
                        session.originalImageUri,
                        session.croppedImageUri,
                        session.selectedModes,
                        session.additionalContext
                    )
                } else {
                    onRequireLogin(null, session.originalImageUri, session)
                }
            }
        )
    }

    val voiceInputManager = remember(context) { VoiceInputManager(context) }
    DisposableEffect(voiceInputManager) {
        onDispose {
            voiceInputManager.destroy()
        }
    }

    val openAppSettings = remember(context) {
        {
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, getString("voice_perm_required", isBn), Toast.LENGTH_LONG).show()
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val initialText = claimInput.trim()
            voiceInputManager.startListening(
                isBn = isBn,
                onPartialResult = { newText ->
                    claimInput = if (initialText.isNotEmpty()) "$initialText $newText" else newText
                },
                onFinalResult = { newText ->
                    claimInput = if (initialText.isNotEmpty()) "$initialText $newText" else newText
                },
                onError = { errKey, isPermError ->
                    Toast.makeText(context, getString(errKey, isBn), Toast.LENGTH_SHORT).show()
                    if (isPermError) {
                        openAppSettings()
                    }
                }
            )
        } else {
            val activity = context as? android.app.Activity
            val canAskAgain = activity?.let {
                androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    it, android.Manifest.permission.RECORD_AUDIO
                )
            } ?: false

            if (canAskAgain) {
                // First denial — Android will still show the dialog on the next request.
                // Just inform the user so they know why the mic didn't start.
                Toast.makeText(context, getString("voice_perm_required", isBn), Toast.LENGTH_SHORT).show()
            } else {
                // Permanent denial ("Don't ask again" or denied twice).
                // Android won't show the dialog again, so we must send them to Settings.
                Toast.makeText(context, getString("voice_perm_required", isBn), Toast.LENGTH_LONG).show()
                openAppSettings()
            }
        }
    }

    LaunchedEffect(autoFocusSearch) {
        if (autoFocusSearch) {
            delay(150)
            try {
                searchFocusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
            onSearchFocused()
        }
    }

    LaunchedEffect(triggerVoiceOnLaunch) {
        if (triggerVoiceOnLaunch) {
            onVoiceLaunched()
            if (!voiceInputManager.isAvailable) {
                Toast.makeText(context, getString("voice_not_available", isBn), Toast.LENGTH_SHORT).show()
                return@LaunchedEffect
            }
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                val initialText = claimInput.trim()
                voiceInputManager.startListening(
                    isBn = isBn,
                    onPartialResult = { newText ->
                        claimInput = if (initialText.isNotEmpty()) "$initialText $newText" else newText
                    },
                    onFinalResult = { newText ->
                        claimInput = if (initialText.isNotEmpty()) "$initialText $newText" else newText
                    },
                    onError = { errKey, isPermError ->
                        Toast.makeText(context, getString(errKey, isBn), Toast.LENGTH_SHORT).show()
                        if (isPermError) {
                            openAppSettings()
                        }
                    }
                )
            } else {
                audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val demoReports = remember {
        listOf(
            FactCheckReport(
                id = "demo1",
                claimText = "Bangladesh announced free electricity for all citizens",
                normalizedClaim = "Bangladesh announced free electricity for all citizens",
                verdict = "true",
                confidence = 92,
                summaryBn = "বাংলাদেশ সরকার দেশের সকল মানুষের জন্য বিনামূল্যে বিদ্যুৎ সুবিধা দেওয়ার ঘোষণা করেছে।",
                summaryEn = "Bangladesh government announced a free electricity distribution package for disadvantaged citizens.",
                reasoningBn = "",
                reasoningEn = "",
                sourcesJson = "[]",
                contradictingSourcesJson = "[]",
                timestamp = System.currentTimeMillis() - 7200 * 1000
            ),
            FactCheckReport(
                id = "demo2",
                claimText = "COVID-19 vaccine causes infertility",
                normalizedClaim = "COVID-19 vaccine causes infertility",
                verdict = "false",
                confidence = 98,
                summaryBn = "কোভিড-১৯ ভ্যাকসিনের ফলে বন্ধ্যাত্ব হওয়ার কোনো বৈজ্ঞানিক প্রমাণ নেই এবং এটি সম্পূর্ণ অসত্য।",
                summaryEn = "There is no scientific proof connecting COVID-19 vaccination with infertility.",
                reasoningBn = "",
                reasoningEn = "",
                sourcesJson = "[]",
                contradictingSourcesJson = "[]",
                timestamp = System.currentTimeMillis() - 172800 * 1000
            ),
            FactCheckReport(
                id = "demo3",
                claimText = "Old photo of flood in Feni is from 2019",
                normalizedClaim = "Old photo of flood in Feni is from 2019",
                verdict = "unverifiable",
                confidence = 70,
                summaryBn = "ফেনীর বন্যার এই ছবিটি ২০১৯ সালের কি না তা নিশ্চিতভাবে যাচাই করা সম্ভব হয়নি।",
                summaryEn = "The origin of this viral flood image cannot be conclusively verified as 2019.",
                reasoningBn = "",
                reasoningEn = "",
                sourcesJson = "[]",
                contradictingSourcesJson = "[]",
                timestamp = System.currentTimeMillis() - 259200 * 1000
            ),
            FactCheckReport(
                id = "demo4",
                claimText = "YouTube removed dislike button permanently",
                normalizedClaim = "YouTube removed dislike button permanently",
                verdict = "true",
                confidence = 99,
                summaryBn = "ইউটিউব তাদের প্ল্যাটফর্ম থেকে ডিসলাইক কাউন্ট বা ডিসলাইক বাটন দেখার সুবিধা পাকাপাকিভাবে সরিয়ে নিয়েছে।",
                summaryEn = "YouTube permanently disabled the visible public dislike counter on all videos.",
                reasoningBn = "",
                reasoningEn = "",
                sourcesJson = "[]",
                contradictingSourcesJson = "[]",
                timestamp = System.currentTimeMillis() - 432000 * 1000
            )
        )
    }

    var isHomeRefreshing by remember { mutableStateOf(false) }
    val homeCoroutineScope = rememberCoroutineScope()

    var displayGreetingName by remember(userName) { mutableStateOf(userName) }

    LaunchedEffect(userName, isBn) {
        if (!userName.isNullOrBlank()) {
            val transliterated = com.example.data.local.NameTransliterationManager.getOrTransliterateName(
                context = context,
                originalName = userName,
                isBn = isBn
            )
            if (transliterated.isNotBlank()) {
                displayGreetingName = transliterated
            }
        }
    }

    // Dynamic Time-of-Day Greeting
    val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greetingText = remember(currentHour, isBn, displayGreetingName) {
        val greetingPrefix = when {
            currentHour in 3..11 -> if (isBn) "গুড মর্নিং" else "Good Morning"
            currentHour in 12..15 -> if (isBn) "শুভ মধ্যাহ্ন" else "Good Noon"
            currentHour in 16..23 || currentHour == 0 -> if (isBn) "গুড ইভনিং" else "Good Evening"
            else -> if (isBn) "গুড নাইট" else "Good Night"
        }
        val firstName = displayGreetingName?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }
        if (firstName != null) {
            "$greetingPrefix, $firstName"
        } else {
            greetingPrefix
        }
    }

    // Rotating Subtitle (swaps every 5 seconds)
    var subtitleIndex by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            subtitleIndex = (subtitleIndex + 1) % 2
        }
    }
    val subtitleText = remember(subtitleIndex, isBn) {
        if (subtitleIndex == 0) {
            if (isBn) "আজকে কি নিয়ে যাচাই করতে চান?" else "What would you like to verify today?"
        } else {
            if (isBn) "চলুন একটা ভাইরাল দাবি যাচাই করে দেখি" else "Let's verify a viral claim together"
        }
    }

    PullToRefreshBox(
        isRefreshing = isHomeRefreshing,
        onRefresh = {
            homeCoroutineScope.launch {
                isHomeRefreshing = true
                viewModel.refreshRecentCmsFactChecks()
                viewModel.refreshRecentCmsBlogArticles()
                kotlinx.coroutines.delay(600)
                isHomeRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero Header Block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                val imageRes = R.drawable.main_page_icon_light_theme
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .align(Alignment.CenterEnd)
                        .offset(x = 10.dp, y = 0.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, end = 65.dp)
                        .offset(y = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = greetingText,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            lineHeight = if (isBn) 20.sp else 20.sp,
                            fontSize = if (isBn) 15.sp else 15.sp,
                            fontFamily = fontToUse
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    AnimatedContent(
                        targetState = subtitleText,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(600))
                        },
                        label = "subtitle_rotation"
                    ) { targetText ->
                        Text(
                            text = targetText,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = fontToUse),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            // The Verification Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) Color(0xFF1E1E1E).copy(alpha = 0.5f) else Color(0xFFF3F4F6)
                ),
                border = BorderStroke(1.dp, if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(
                        value = claimInput,
                        onValueChange = { claimInput = it },
                        placeholder = {
                            Text(
                                text = if (voiceInputManager.isListening) getString("voice_listening", isBn) else getString("search_hint", isBn),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (voiceInputManager.isListening) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .focusRequester(searchFocusRequester)
                            .testTag("claim_input"),
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (voiceInputManager.isListening) Color(0xFFEF4444) else Color(0xFF0084C7),
                            unfocusedBorderColor = if (voiceInputManager.isListening) Color(0xFFEF4444) else (if (isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f)),
                            focusedContainerColor = if (isDarkTheme) Color(0xFF2B2B2E) else Color.White,
                            unfocusedContainerColor = if (isDarkTheme) Color(0xFF2B2B2E) else Color.White
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            handleSearchSubmit(claimInput)
                        })
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Plus (+) Icon + Persistent Active Mode Indicator Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { showDeepSearchDrawer = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Persistent Active Search Mode Badge (Always shows active mode: Factcheck by default, Deep, or Web)
                            val activeLabel = when {
                                isDeepSearchEnabled -> if (isBn) "রিসার্চ" else "Deep"
                                isWebSearchEnabled -> if (isBn) "সার্চ" else "Search"
                                else -> if (isBn) "ফ্যাক্টচেক" else "Factcheck"
                            }

                            val activeIcon = when {
                                isDeepSearchEnabled -> Icons.Default.AutoAwesome
                                isWebSearchEnabled -> Icons.Default.TravelExplore
                                else -> Icons.Default.VerifiedUser
                            }

                            val activeColor = when {
                                isDeepSearchEnabled -> com.example.ui.theme.SlatePrimary
                                isWebSearchEnabled -> Color(0xFF0EA5E9)
                                else -> Color(0xFF10B981) // Emerald Green for Factcheck / Verified
                            }

                            Surface(
                                onClick = { showDeepSearchDrawer = true },
                                shape = RoundedCornerShape(10.dp),
                                color = activeColor.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, activeColor.copy(alpha = 0.35f)),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
                                ) {
                                    Icon(
                                        imageVector = activeIcon,
                                        contentDescription = null,
                                        tint = activeColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = activeLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = activeColor,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Right: Attach, Camera & Mic icons (inline, horizontally close) + Search Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Demo Attachment Icon for Video/PDF/File upload
                            IconButton(
                                onClick = {
                                    Toast.makeText(context, if (isBn) "ফাইল, ভিডিও ও পিডিএফ আপলোডের ফিচার শিগগিরই আসছে!" else "File, video & PDF attachments coming soon!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AttachFile,
                                    contentDescription = "Attach File",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    pickMedia.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoCamera,
                                    contentDescription = "Scan Image",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

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

                                    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                    if (hasPermission) {
                                        val initialText = claimInput.trim()
                                        voiceInputManager.startListening(
                                            isBn = isBn,
                                            onPartialResult = { newText ->
                                                claimInput = if (initialText.isNotEmpty()) "$initialText $newText" else newText
                                            },
                                            onFinalResult = { newText ->
                                                claimInput = if (initialText.isNotEmpty()) "$initialText $newText" else newText
                                            },
                                            onError = { errKey, isPermError ->
                                                Toast.makeText(context, getString(errKey, isBn), Toast.LENGTH_SHORT).show()
                                                if (isPermError) {
                                                    openAppSettings()
                                                }
                                            }
                                        )
                                    } else {
                                        audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Mic,
                                    contentDescription = "Voice Input",
                                    tint = if (voiceInputManager.isListening) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Button(
                                onClick = {
                                    handleSearchSubmit(claimInput)
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier.testTag("submit_button")
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Check", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(getString("check", isBn), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }



            // More for Fact-Checking Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFFEAB308),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isBn) "ফ্যাক্টচেকিংয়ে আরও কিছু ফিচার" else "More for Fact-Checking...",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                val neumorphColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F4F6)

                @Composable
                fun NeumorphIconCard(title: String, onClick: () -> Unit, content: @Composable () -> Unit) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onClick() }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .shadow(
                                    elevation = if (isDark) 4.dp else 6.dp,
                                    shape = RoundedCornerShape(16.dp),
                                    ambientColor = if (isDark) Color.Black else Color.Gray,
                                    spotColor = if (isDark) Color.Black else Color.Gray
                                )
                                .background(neumorphColor, RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            content()
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = title,
                            fontFamily = fontToUse,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                // 1. Khoj Chat Card
                NeumorphIconCard(
                    title = if (isBn) "খোঁজ চ্যাট" else "Khoj Chat",
                    onClick = onOpenChatDrawer,
                    content = {
                        Image(
                            painter = painterResource(id = R.drawable.khoj_chat),
                            contentDescription = "Khoj Chat Icon",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                        )
                    }
                )

                // 2. AI Image Detector Card
                NeumorphIconCard(
                    title = if (isBn) "AI ছবি যাচাই" else "AI Verify",
                    onClick = onNavigateToAiImageDetector,
                    content = {
                        Image(
                            painter = painterResource(id = R.drawable.ai_img_detect),
                            contentDescription = "AI Image Detector Icon",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                        )
                    }
                )

                // 3. Image Search Card
                NeumorphIconCard(
                    title = if (isBn) "ইমেজ সার্চ" else "Image Search",
                    onClick = onNavigateToImageSearch,
                    content = {
                        Image(
                            painter = painterResource(id = R.drawable.img_search),
                            contentDescription = "Image Search Icon",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                        )
                    }
                )

                // 4. Bangla AI Text Detection Card
                NeumorphIconCard(
                    title = if (isBn) "লেখা যাচাই" else "Text Verify",
                    onClick = { onNavigateToBanglaAiTextDetector(null) },
                    content = {
                        Image(
                            painter = painterResource(id = R.drawable.bangla_ai_text_detection),
                            contentDescription = "Bangla AI Text Detection Icon",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                        )
                    }
                )

                // 5. Liberation War Corner
                NeumorphIconCard(
                    title = if (isBn) "মুক্তিযুদ্ধ কর্নার" else "Liberation War",
                    onClick = { Toast.makeText(context, if (isBn) "মুক্তিযুদ্ধ কর্নার শিগগিরই আসছে!" else "Liberation War Corner coming soon!", Toast.LENGTH_SHORT).show() },
                    content = {
                        Image(
                            painter = painterResource(id = R.drawable.mukti_corner_icon),
                            contentDescription = "Mukti Corner Icon",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                        )
                    }
                )
            }

            // Trending Rumours Block
            TrendingRumoursBlock(
                isBn = isBn,
                onNavigateToSeeAll = onNavigateToAllTrendingRumours
            )

            // Manual CMS Fact Checks Block
            ManualFactChecksSection(
                viewModel = viewModel,
                isBn = isBn,
                onNavigateToAllFactChecks = onNavigateToAllFactChecks,
                onSelectCmsArticle = onSelectCmsArticle
            )

            // Manual CMS Blog Reports Block
            ManualBlogsSection(
                viewModel = viewModel,
                isBn = isBn,
                onNavigateToAllBlogs = onNavigateToAllBlogs,
                onSelectBlogArticle = onSelectBlogArticle
            )
        }

        if (showDeepSearchDrawer) {
            DeepSearchOptionsDrawer(
                isDeepSearchEnabled = isDeepSearchEnabled,
                onDeepSearchToggled = { enabled ->
                    android.util.Log.d("DeepSearchFlow", "DeepSearch toggled: $enabled")
                    isDeepSearchEnabled = enabled
                },
                isWebSearchEnabled = isWebSearchEnabled,
                onWebSearchToggled = { enabled ->
                    android.util.Log.d("DeepSearchFlow", "WebSearch toggled: $enabled")
                    isWebSearchEnabled = enabled
                },
                isBn = isBn,
                onDismiss = { showDeepSearchDrawer = false }
            )
        }

        // Clean & Modern Modal for Large Texts (> 140 words)
        if (showLargeTextActionModal) {
            Dialog(
                onDismissRequest = { showLargeTextActionModal = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .wrapContentHeight()
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkTheme) Color(0xFF232326) else Color(0xFFFFFFFF)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (isBn) "বেশ বড় লেখা দিয়ে ফেললেন দেখছি।" else "That's quite a long text.",
                                style = TextStyle(
                                    fontFamily = fontToUse,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.None
                                    ),
                                    lineHeight = 26.sp
                                )
                            )
                            Text(
                                text = if (isBn) "তা, এটা দিয়ে কি করতে চান?" else "What would you like to do with it?",
                                style = TextStyle(
                                    fontFamily = fontToUse,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.None
                                    ),
                                    lineHeight = 22.sp
                                )
                            )
                        }

                        HorizontalDivider(
                            color = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            thickness = 1.dp
                        )

                        // Option 1: ফ্যাক্টচেক (Fact Check)
                        Surface(
                            onClick = {
                                showLargeTextActionModal = false
                                executeFactCheck(pendingLargeTextQuery)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isDarkTheme) Color(0xFF2C2C30) else Color(0xFFF3F4F6),
                            border = BorderStroke(
                                1.dp,
                                if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isBn) "ফ্যাক্টচেক" else "Fact Check",
                                        style = TextStyle(
                                            fontFamily = fontToUse,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                            lineHeightStyle = LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.None
                                            ),
                                            lineHeight = 22.sp
                                        )
                                    )
                                    Text(
                                        text = if (isBn) "দাবি ও তথ্যের সত্যতা অনুসন্ধান করুন" else "Verify claim authenticity and sources",
                                        style = TextStyle(
                                            fontFamily = fontToUse,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                            lineHeightStyle = LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.None
                                            ),
                                            lineHeight = 18.sp
                                        )
                                    )
                                }
                            }
                        }

                        // Option 2: AI যাচাই (AI Verification)
                        Surface(
                            onClick = {
                                showLargeTextActionModal = false
                                onNavigateToBanglaAiTextDetector(pendingLargeTextQuery)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isDarkTheme) Color(0xFF1E293B).copy(alpha = 0.7f) else Color(0xFFF0FDF4),
                            border = BorderStroke(
                                1.dp,
                                if (isDarkTheme) Color(0xFF38BDF8).copy(alpha = 0.3f) else Color(0xFF22C55E).copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isDarkTheme) Color(0xFF38BDF8).copy(alpha = 0.15f)
                                            else Color(0xFF16A34A).copy(alpha = 0.15f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (isDarkTheme) Color(0xFF38BDF8) else Color(0xFF16A34A),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isBn) "AI যাচাই" else "AI Verification",
                                        style = TextStyle(
                                            fontFamily = fontToUse,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                            lineHeightStyle = LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.None
                                            ),
                                            lineHeight = 22.sp
                                        )
                                    )
                                    Text(
                                        text = if (isBn) "লেখাটি কৃত্রিম বুদ্ধিমত্তা দিয়ে তৈরি কিনা জানুন" else "Detect if text was generated by AI",
                                        style = TextStyle(
                                            fontFamily = fontToUse,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                            lineHeightStyle = LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.None
                                            ),
                                            lineHeight = 18.sp
                                        )
                                    )
                                }
                            }
                        }

                        // Cancel Button
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            TextButton(onClick = { showLargeTextActionModal = false }) {
                                Text(
                                    text = if (isBn) "বাতিল" else "Cancel",
                                    style = TextStyle(
                                        fontFamily = fontToUse,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        ),
                                        lineHeight = 20.sp
                                    )
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
fun CheckingProgressOverlay(
    state: FactCheckProgress?,
    isFromImage: Boolean = false,
    streamingMarkdown: String = "",
    isBn: Boolean,
    onCancel: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )

    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                }
            }
            
            if (state is FactCheckProgress.Error) {
                val isNetErr = !NetworkErrorHelper.isDeviceOnline(context) || NetworkErrorHelper.isNetworkException(Exception(state.message))
                if (isNetErr) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        OfflineStateCard(
                            isBn = isBn,
                            featureName = if (isBn) "ফ্যাক্টচেক" else "Factcheck",
                            onRetry = onRetry ?: onCancel,
                            onBack = onCancel,
                            retryButtonText = if (isBn) "পুনরায় চেষ্টা করুন" else "Retry"
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = ColorFalse,
                            modifier = Modifier.size(72.dp)
                        )

                        Text(
                            text = getString("error_title", isBn),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = onCancel,
                            colors = ButtonDefaults.buttonColors(containerColor = ColorFalse)
                        ) {
                            Text(getString("ok", isBn), color = Color.White)
                        }
                    }
                }
            } else {
                // Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Searching Lottie Animation
                    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.browsing))
                    val progress by animateLottieCompositionAsState(
                        composition = composition,
                        iterations = LottieConstants.IterateForever
                    )
                    
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(160.dp)
                    ) {
                        LottieAnimation(
                            composition = composition,
                            progress = { progress },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Titles
                    Text(
                        text = getString("analyzing", isBn),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = getString("take_moments", isBn),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(48.dp))

                    // ── Static Steps List ─────────────────────────────────────────
                    val steps = mutableListOf<Pair<FactCheckProgress, String>>()
                    if (isFromImage) {
                        steps.add(FactCheckProgress.ExtractingImageText to "Extracting the texts from the image")
                    }
                    steps.addAll(listOf(
                        FactCheckProgress.ClaimNormalization to "Turning your claim into multiple queries",
                        FactCheckProgress.Searching("") to "Searching Khoj's curated database and the entire web.",
                        FactCheckProgress.RankingSources(0) to "Scoring and evaluating references.",
                        FactCheckProgress.GeneratingVerdict to "Khoj AI is writing the report."
                    ))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                            steps.forEachIndexed { index, (stepType, stepTitle) ->
                                val isDone = isStepCompleted(current = state, stepType = stepType)
                                val isActive = isStepActive(current = state, stepType = stepType)

                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Icon column with connecting line
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(24.dp)
                                    ) {
                                        if (isDone) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = "Done",
                                                tint = SlateTertiary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        } else if (isActive) {
                                            Icon(
                                                Icons.Outlined.Circle,
                                                contentDescription = "In progress",
                                                tint = SlateTertiary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        } else {
                                            Icon(
                                                Icons.Outlined.Circle,
                                                contentDescription = "Pending",
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        // Draw line to next item if not the last
                                        if (index < steps.size - 1) {
                                            Box(
                                                modifier = Modifier
                                                    .width(1.dp)
                                                    .height(36.dp)
                                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                            )
                                        }
                                    }

                                    // Text column
                                    Column(
                                        modifier = Modifier.padding(top = 2.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = stepTitle,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        val statusText = if (isDone) "Completed" else if (isActive) "In progress" else "Pending"
                                        Text(
                                            text = statusText,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }


                    }
                }
            }
        }
    }

private fun isStepCompleted(current: FactCheckProgress?, stepType: FactCheckProgress): Boolean {
    if (current == null || current is FactCheckProgress.Idle) return false
    return when (stepType) {
        is FactCheckProgress.ExtractingImageText -> current !is FactCheckProgress.ExtractingImageText
        is FactCheckProgress.ClaimNormalization -> current !is FactCheckProgress.ExtractingImageText && current !is FactCheckProgress.ClaimNormalization
        is FactCheckProgress.Searching -> current is FactCheckProgress.RankingSources || current is FactCheckProgress.GeneratingVerdict || current is FactCheckProgress.Success
        is FactCheckProgress.RankingSources -> current is FactCheckProgress.GeneratingVerdict || current is FactCheckProgress.Success
        is FactCheckProgress.GeneratingVerdict -> current is FactCheckProgress.Success
        else -> false
    }
}

private fun isStepActive(current: FactCheckProgress?, stepType: FactCheckProgress): Boolean {
    if (current == null || current is FactCheckProgress.Idle) return false
    return when (stepType) {
        is FactCheckProgress.ExtractingImageText -> current is FactCheckProgress.ExtractingImageText
        is FactCheckProgress.ClaimNormalization -> current is FactCheckProgress.ClaimNormalization
        is FactCheckProgress.Searching -> current is FactCheckProgress.QueryPlanning || current is FactCheckProgress.Searching
        is FactCheckProgress.RankingSources -> current is FactCheckProgress.RankingSources
        is FactCheckProgress.GeneratingVerdict -> current is FactCheckProgress.GeneratingVerdict
        else -> false
    }
}

@Composable
fun ReportScreen(
    report: FactCheckReport,
    viewModel: FactCheckViewModel,
    onPrivacyChanged: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val renderingLanguage by viewModel.renderingLanguage.collectAsStateWithLifecycle()
    val isBn = renderingLanguage == "bn"
    val bengaliFontPreference by viewModel.bengaliFontPreference.collectAsStateWithLifecycle()
    val fontToUse = if (isBn) {
        if (bengaliFontPreference == "anek") AnekBanglaFamily else TiroBanglaFamily
    } else {
        FontFamily.Default
    }

    val titleText = getString("report_title", isBn)
    val claimLabel = getString("claim_label", isBn)
    val confidenceLabel = getString("confidence_label", isBn)
    val sourcesLabel = getString("sources_label", isBn)
    val contradictingLabel = getString("contradicting_label", isBn)

    val verdictText = when (report.verdict.lowercase()) {
        "true" -> getString("true", isBn)
        "false" -> getString("false", isBn)
        else -> getString("misleading", isBn)
    }

    // Extract sources
    val engine = remember { FactCheckEngine(context) }
    val sources = remember(report) { engine.deserializeSources(report.sourcesJson) }
    val contradictions = remember(report) { engine.deserializeContradictions(report.contradictingSourcesJson) }

    var showShareModal by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

        // Original Claim Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = claimLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = report.claimText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 3-way Verdict Badge Card
        val verdictColor = when (report.verdict.lowercase()) {
            "true" -> Color(0xFF16A34A)
            "false" -> Color(0xFFDC2626)
            else -> Color(0xFFD97706)
        }

        val lottieRes = when (report.verdict.lowercase()) {
            "true" -> R.raw.anim_true
            "false" -> R.raw.anim_false
            "misleading" -> R.raw.anim_misleading
            else -> R.raw.anim_misleading
        }
        
        val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(lottieRes))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, verdictColor.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(verdictColor.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    LottieAnimation(
                        composition = composition,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Column {
                    Text(
                        text = getString("khoj_verdict", isBn),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = verdictText,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = verdictColor
                    )
                }
            }
        }

        // Confidence card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = confidenceLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${report.confidence}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = verdictColor
                    )
                }
                LinearProgressIndicator(
                    progress = report.confidence / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = verdictColor,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            }
        }

        // Privacy Switch Card (only if the user is the owner of the report)
        val currentUserId = AuthRepository.currentUserId
        val isOwner = !currentUserId.isNullOrEmpty() && report.userId == currentUserId

        if (isOwner) {
            var isPublicStatus by remember(report.id) { mutableStateOf(report.isPublic) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Globe or Lock icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (isPublicStatus) Color(0xFFE2F6EA) else Color(0xFFFEF3C7),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPublicStatus) Icons.Default.Language else Icons.Default.Lock,
                                contentDescription = "Privacy Icon",
                                tint = if (isPublicStatus) Color(0xFF16A34A) else Color(0xFFD97706),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (isPublicStatus) {
                                    if (isBn) "পাবলিক রিপোর্ট" else "Public Report"
                                } else {
                                    if (isBn) "প্রাইভেট রিপোর্ট" else "Private Report"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isPublicStatus) {
                                    if (isBn) "সবাই দেখতে পারবে" else "Everyone can see"
                                } else {
                                    if (isBn) "শুধু আপনি দেখতে পারবেন" else "Only you can see"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                              )
                          }
                      }

                      Switch(
                          checked = isPublicStatus,
                          onCheckedChange = { checked ->
                              isPublicStatus = checked
                              viewModel.updateReportPrivacy(report.id, checked) {
                                  onPrivacyChanged()
                              }
                          },
                          colors = SwitchDefaults.colors(
                              checkedThumbColor = Color.White,
                              checkedTrackColor = Color(0xFF10B981), // Emerald green matching screenshot
                              uncheckedThumbColor = Color.White,
                              uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                          )
                      )
                  }
              }
          }

        // ── Unified Markdown Report Body ────────────────────────────────────────
        // This is the single source of truth for the report body across all entry points:
        // new result, history, feed. Uses getEffectiveMarkdown() which handles both new
        // (markdownReport) and legacy (summaryBn + reasoningBn) reports gracefully.
        UnifiedFactCheckReportView(
            report = report,
            sources = sources,
            fontToUse = fontToUse,
            modifier = Modifier.fillMaxWidth()
        )

        // Contradicting evidence section (Transparent design)
        if (contradictions.isNotEmpty()) {
            val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ColorFalseContainer.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Contradict", tint = if (isDarkTheme) Color(0xFFFF5252) else ColorFalse)
                        Text(
                            text = contradictingLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color(0xFFFF5252) else OnColorFalse
                        )
                    }

                    contradictions.forEach { contra ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = contra.note,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (isDarkTheme) Color.Black else MaterialTheme.colorScheme.onSurface
                            )
                            if (contra.url.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.clickable {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(contra.url))
                                        context.startActivity(intent)
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = "Link", modifier = Modifier.size(14.dp), tint = SlatePrimary)
                                    Text(
                                        text = contra.title.ifEmpty { getString("open_contra", isBn) },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlatePrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Clickable Citations & Sources list
        Text(
            text = sourcesLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp)
        )

        if (sources.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getString("no_links", isBn),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            sources.forEachIndexed { idx, source ->
                SourceCardItem(source = source, index = idx + 1, isBn = isBn)
            }
        }

        // Re-check button
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SlatePrimary)
        ) {
            Icon(Icons.Default.Autorenew, contentDescription = "Retry")
            Spacer(modifier = Modifier.width(8.dp))
            Text(getString("verify_new", isBn))
        }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    
    val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }
    val offsetY = remember { androidx.compose.animation.core.Animatable(with(density) { 100.dp.toPx() }) }

    // Share FAB
    FloatingActionButton(
        onClick = { showShareModal = true },
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 16.dp)
            .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
                            val fabAndPadding = with(density) { (56.dp + 32.dp).toPx() } 
                            val threshold = -(screenWidthPx - fabAndPadding) / 2f
                            
                            val targetX = if (offsetX.value > threshold) 0f else -(screenWidthPx - fabAndPadding)
                            offsetX.animateTo(targetX, androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))
                        }
                    }
                ) { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset ->
                    change.consume()
                    coroutineScope.launch {
                        offsetX.snapTo(offsetX.value + dragAmount.x)
                        offsetY.snapTo(offsetY.value + dragAmount.y)
                    }
                }
            },
        containerColor = SlatePrimary,
        contentColor = Color.White,
        shape = CircleShape
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_share_custom),
            contentDescription = "Share",
            modifier = Modifier.size(24.dp)
        )
    }

    // Glassmorphic Share Modal Popup
    if (showShareModal) {
        val reportUrl = "https://khoj-ai.bd/factcheck-detail?id=${report.id}"
        val shareText = if (isBn) {
            "খোঁজ এআই দ্বারা যাচাইকৃত রিপোর্ট:\nদাবি: ${report.claimText}\nফলাফল: ${verdictText}\nবিস্তারিত দেখুন: $reportUrl"
        } else {
            "Factchecked Report by Khoj AI:\nClaim: ${report.claimText}\nVerdict: ${report.verdict}\nView details: $reportUrl"
        }

        Dialog(
            onDismissRequest = { showShareModal = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showShareModal = false },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            text = if (isBn) "এই রিপোর্ট শেয়ার করুন" else "Share this report",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontToUse,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // FB Messenger
                            ShareSocialIconButton(
                                iconRes = R.drawable.ic_facebook_custom,
                                onClick = {
                                    shareToApp(context, "com.facebook.orca", shareText, reportUrl)
                                    showShareModal = false
                                }
                            )
                            // WhatsApp
                            ShareSocialIconButton(
                                iconRes = R.drawable.ic_whatsapp_custom,
                                onClick = {
                                    shareToApp(context, "com.whatsapp", shareText, reportUrl)
                                    showShareModal = false
                                }
                            )
                            // Instagram
                            ShareSocialIconButton(
                                iconRes = R.drawable.ic_instagram_custom,
                                onClick = {
                                    shareToApp(context, "com.instagram.android", shareText, reportUrl)
                                    showShareModal = false
                                }
                            )
                            // X (Twitter)
                            ShareSocialIconButton(
                                iconRes = R.drawable.ic_x_custom,
                                onClick = {
                                    shareToApp(context, "com.twitter.android", shareText, reportUrl)
                                    showShareModal = false
                                }
                            )
                            // LinkedIn
                            ShareSocialIconButton(
                                iconRes = R.drawable.ic_linkedin_custom,
                                onClick = {
                                    shareToApp(context, "com.linkedin.android", shareText, reportUrl)
                                    showShareModal = false
                                }
                            )
                            // Copy Link
                            ShareSocialIconButton(
                                iconRes = R.drawable.ic_copy_custom,
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Fact Check Report Link", reportUrl)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(
                                        context,
                                        if (isBn) "লিংক কপি করা হয়েছে!" else "Link copied to clipboard!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showShareModal = false
                                }
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
fun SourceCardItem(source: FactCheckSource, index: Int, isBn: Boolean) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (source.url.isNotEmpty()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source.url))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast
                            .makeText(context, if (isBn) "লিঙ্ক খোলা সম্ভব হচ্ছে না" else "Cannot open link", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(SlateSecondary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = index.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateSecondary
                        )
                    }
                    val faviconUrl = if (source.domain.isNotEmpty()) {
                        "https://www.google.com/s2/favicons?sz=64&domain=${source.domain}"
                    } else {
                        "https://www.google.com/s2/favicons?sz=64&domain_url=${source.url}"
                    }
                    AsyncImage(
                        model = faviconUrl,
                        contentDescription = "Favicon",
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                    )
                    Text(
                        text = source.title.ifEmpty { getString("authoritative_source", isBn) },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Tier badge
                val tierText = if (isBn) "ধাপ ${source.credibilityTier}" else "Tier ${source.credibilityTier}"
                val tierColor = when (source.credibilityTier) {
                    1 -> ColorTrue
                    2 -> SlateTertiary
                    3 -> SlateSecondary
                    else -> Color.Gray
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(tierColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = tierText, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = tierColor)
                }
            }

            Text(
                text = source.snippet.ifEmpty { getString("source_details", isBn) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = source.domain.ifEmpty { "web-source" },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlatePrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        Icons.Default.Launch,
                        contentDescription = "Open",
                        modifier = Modifier.size(12.dp),
                        tint = SlatePrimary
                    )
                    Text(
                        text = getString("open_link", isBn),
                        fontSize = 10.sp,
                        color = SlatePrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

enum class HistoryCategory {
    FACT_CHECK,
    DEEP_SEARCH
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: FactCheckViewModel,
    onBack: () -> Unit = {},
    onSelectReport: (FactCheckReport) -> Unit,
    onSelectDeepSearchReport: (DeepSearchReport) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val deepSearchRepo = remember(context) { DeepSearchHistoryRepository.getInstance(context) }
    val deepSearchHistory by deepSearchRepo.historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(Unit) {
        deepSearchRepo.syncWithRemote()
    }

    val historyReports by viewModel.historyReports.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val renderingLanguage by viewModel.renderingLanguage.collectAsStateWithLifecycle()
    val isBn = renderingLanguage == "bn"
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    var selectedTab by rememberSaveable { mutableStateOf(HistoryCategory.FACT_CHECK) }

    val filteredDeepSearchHistory = remember(deepSearchHistory, searchQuery) {
        if (searchQuery.isBlank()) deepSearchHistory
        else deepSearchHistory.filter {
            it.query.contains(searchQuery, ignoreCase = true) ||
            it.reportContent.contains(searchQuery, ignoreCase = true)
        }
    }

    val hasItems = when (selectedTab) {
        HistoryCategory.FACT_CHECK -> historyReports.isNotEmpty()
        HistoryCategory.DEEP_SEARCH -> filteredDeepSearchHistory.isNotEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Compact Top Navigation Bar: [← ইতিহাস] on left and [সব মুছুন] on top-right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = getString("history", isBn),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (hasItems) {
                TextButton(
                    onClick = {
                        if (selectedTab == HistoryCategory.FACT_CHECK) {
                            viewModel.clearHistory()
                        } else {
                            coroutineScope.launch {
                                deepSearchRepo.clearAll()
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = ColorFalse)
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "Clear All",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = getString("clear_all", isBn),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Main Content Column with horizontal padding
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Segmented category tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.45f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Fact Check Tab
                val isFcSelected = selectedTab == HistoryCategory.FACT_CHECK
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isFcSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { selectedTab = HistoryCategory.FACT_CHECK }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isBn) "ফ্যাক্টচেক (${historyReports.size})" else "Fact Check (${historyReports.size})",
                        fontSize = 12.sp,
                        fontWeight = if (isFcSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isFcSelected) SlatePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Deep Search Tab
                val isDsSelected = selectedTab == HistoryCategory.DEEP_SEARCH
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDsSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { selectedTab = HistoryCategory.DEEP_SEARCH }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = if (isDsSelected) SlatePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isBn) "ডিপসার্চ (${deepSearchHistory.size})" else "Deep Search (${deepSearchHistory.size})",
                            fontSize = 12.sp,
                            fontWeight = if (isDsSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isDsSelected) SlatePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Compact modern search box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        BorderStroke(
                            width = 0.8.dp,
                            color = if (isDark) Color.White.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        ),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = getString("search_history", isBn),
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(SlatePrimary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.setSearchQuery("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            when (selectedTab) {
                HistoryCategory.FACT_CHECK -> {
                    if (historyReports.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.HistoryToggleOff,
                                    contentDescription = "Empty",
                                    modifier = Modifier.size(42.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                                Text(
                                    text = if (searchQuery.isNotEmpty()) getString("empty_search", isBn) else getString("empty_history", isBn),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(historyReports, key = { "fc_${it.id}" }) { report ->
                                HistoryReportCard(
                                    report = report,
                                    isBn = isBn,
                                    onSelect = { onSelectReport(report) },
                                    onDelete = { viewModel.deleteReport(report.id) }
                                )
                            }
                        }
                    }
                }
                HistoryCategory.DEEP_SEARCH -> {
                    if (filteredDeepSearchHistory.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "Empty",
                                    modifier = Modifier.size(42.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                                Text(
                                    text = if (searchQuery.isNotEmpty()) {
                                        if (isBn) "কোনো ফলাফল পাওয়া যায়নি" else "No matching Deep Search results"
                                    } else {
                                        if (isBn) "কোনো ডিপসার্চ রিপোর্ট নেই" else "No Deep Search reports yet"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredDeepSearchHistory, key = { "ds_${it.id}" }) { deepReport ->
                                DeepSearchHistoryCard(
                                    report = deepReport,
                                    isBn = isBn,
                                    onSelect = { onSelectDeepSearchReport(deepReport) },
                                    onDelete = {
                                        coroutineScope.launch {
                                            deepSearchRepo.deleteReport(deepReport.id)
                                        }
                                    }
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
fun DeepSearchHistoryCard(
    report: DeepSearchReport,
    isBn: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val sourceCount = report.sources.size
    val previewText = remember(report.reportContent) {
        val clean = report.reportContent.lines()
            .filter { !it.trim().startsWith("#") }
            .joinToString(" ")
            .trim()
        if (clean.length > 120) clean.take(120) + "..." else clean
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.background
        ),
        border = BorderStroke(
            width = 0.65.dp,
            color = if (isDark) Color.White.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header Row: DeepSearch badge + source count + delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // DeepSearch Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(SlatePrimary.copy(alpha = 0.12f))
                            .border(BorderStroke(0.5.dp, SlatePrimary.copy(alpha = 0.35f)), RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = SlatePrimary,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = if (isBn) "ডিপসার্চ" else "DeepSearch",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlatePrimary
                            )
                        }
                    }

                    // True Source count badge
                    if (sourceCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isBn) "${sourceCount}টি তথ্যসূত্র" else "$sourceCount sources",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // Question / Query
            Text(
                text = report.query,
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Content snippet
            if (previewText.isNotBlank()) {
                Text(
                    text = previewText,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun HistoryReportCard(
    report: FactCheckReport,
    isBn: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val verdictColor = when (report.verdict.lowercase()) {
        "true" -> Color(0xFF16A34A)
        "false" -> Color(0xFFDC2626)
        else -> Color(0xFFD97706)
    }

    val verdictText = when (report.verdict.lowercase()) {
        "true" -> getString("true", isBn)
        "false" -> getString("false", isBn)
        else -> getString("misleading", isBn)
    }

    val summary = if (isBn) report.summaryBn else report.summaryEn

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.background
        ),
        border = BorderStroke(
            width = 0.65.dp,
            color = if (isDark) Color.White.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = verdictText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = verdictColor,
                    maxLines = 1,
                    softWrap = false
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Text(
                text = report.claimText,
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun HomeGridCard(
    modifier: Modifier = Modifier,
    title: String,
    fontToUse: FontFamily,
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .aspectRatio(0.95f)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if ((MaterialTheme.colorScheme.background.luminance() < 0.5f)) Color(0xFF020202) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            iconContent()
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                fontFamily = fontToUse,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ShareSocialIconButton(iconRes: Int, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .padding(8.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.fillMaxSize()
        )
    }
}

fun shareToApp(context: Context, packageName: String, shareText: String, fallbackUrl: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
        `package` = packageName
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        val webUrl = when (packageName) {
            "com.twitter.android" -> "https://x.com/intent/tweet?text=${Uri.encode(shareText)}"
            "com.facebook.katana", "com.facebook.orca" -> "https://www.facebook.com/sharer/sharer.php?u=${Uri.encode(fallbackUrl)}"
            "com.whatsapp" -> "https://api.whatsapp.com/send?text=${Uri.encode(shareText)}"
            "com.linkedin.android" -> "https://www.linkedin.com/sharing/share-offsite/?url=${Uri.encode(fallbackUrl)}"
            else -> null
        }
        if (webUrl != null) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
            context.startActivity(webIntent)
        } else {
            val chooserIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(chooserIntent, "Share via"))
        }
    }
}
