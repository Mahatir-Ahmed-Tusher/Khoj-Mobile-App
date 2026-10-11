package com.example.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.lifecycle.*
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.data.api.FactCheckProgress
import com.example.data.repository.FactCheckRepository
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect

private const val TAG = "OverlayModeService"

/**
 * Foreground service that manages all overlay windows: FAB, action dialogs, and result drawer.
 *
 * ── MediaProjection crash fix ──────────────────────────────────────────────────────────────
 * Android 14+ requires the service to be actively running as
 * [ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION] at the moment
 * [android.media.projection.MediaProjectionManager.getMediaProjection] is called.
 * The fix: before calling [ScreenshotCaptureManager.initialize], re-call [startForeground]
 * with the MEDIA_PROJECTION type. After the capture session ends, revert to SPECIAL_USE.
 *
 * ── Gesture conflict fix ───────────────────────────────────────────────────────────────────
 * The FAB tooltip was broken because `.clickable` and `.pointerInput(detectDragGestures)` were
 * stacked on the same Box. The drag handler swallowed all touch events. Fixed in [OverlayFabView]
 * by using a single pointerInput block with tap-slop discrimination.
 *
 * ── New action dialog flow ─────────────────────────────────────────────────────────────────
 * Each FAB action now opens a dedicated floating dialog. The bottom drawer only opens AFTER the
 * user explicitly taps "Fact Check" inside the dialog. This matches the user's specified UX:
 *   Select  → OverlayClipboardDialog  → [Fact Check] → drawer
 *   Screenshot → MediaProjection → OverlayScreenshotDialog → [Fact Check] → drawer
 *   Mic    → OverlayMicDialog (live STT) → [Fact Check] → drawer
 *   Search  → Open MainActivity
 */
class OverlayModeService : LifecycleService() {

    // ── Window management ───────────────────────────────────────────────────
    private lateinit var windowManager: WindowManager
    private var fabWindowView: View? = null
    private var dialogWindowView: View? = null
    private var drawerWindowView: View? = null
    
    private var dismissTargetView: View? = null
    private var isHoveringDismiss by mutableStateOf(false)

    // ── FAB position ────────────────────────────────────────────────────────
    private var fabX: Int = 0
    private var fabY: Int = 300
    private var isMenuExpanded by mutableStateOf(false)

    // ── Sub-managers ────────────────────────────────────────────────────────
    private val screenshotManager by lazy { ScreenshotCaptureManager(this) }
    private val voiceManager by lazy { OverlayVoiceManager(this) }

    // ── Coroutine scope (cancelled in onDestroy) ────────────────────────────
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var aiDetectionJob: Job? = null

    // ── Fact-check repository ────────────────────────────────────────────────
    private val factCheckRepository by lazy { FactCheckRepository(application) }

    // ── Preference flows (reactively observed by overlay windows) ───────────
    private lateinit var prefs: android.content.SharedPreferences
    private val themePreferenceFlow = MutableStateFlow("system")
    private val appLanguageFlow = MutableStateFlow("bn")
    private val bengaliFontFlow = MutableStateFlow("tiro")

    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        when (key) {
            "theme_preference" -> themePreferenceFlow.value = sp.getString("theme_preference", "system") ?: "system"
            "app_language_option" -> appLanguageFlow.value = sp.getString("app_language_option", "bn") ?: "bn"
            "bengali_font_preference" -> bengaliFontFlow.value = sp.getString("bengali_font_preference", "tiro") ?: "tiro"
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Companion
    // ──────────────────────────────────────────────────────────────────────
    companion object {
        var isRunning: Boolean = false
            private set

        const val CHANNEL_ID = "khoj_overlay_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_STOP = "com.example.overlay.ACTION_STOP"
        const val ACTION_MEDIA_PROJECTION_RESULT = "com.example.overlay.ACTION_MEDIA_PROJECTION_RESULT"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        fun start(context: Context) {
            val intent = Intent(context, OverlayModeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayModeService::class.java))
        }

        fun deliverMediaProjectionResult(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, OverlayModeService::class.java).apply {
                action = ACTION_MEDIA_PROJECTION_RESULT
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    // ──────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        // Initialize preferences and listen for real-time changes
        prefs = getSharedPreferences("khoj_settings", Context.MODE_PRIVATE)
        themePreferenceFlow.value = prefs.getString("theme_preference", "system") ?: "system"
        appLanguageFlow.value = prefs.getString("app_language_option", "bn") ?: "bn"
        bengaliFontFlow.value = prefs.getString("bengali_font_preference", "tiro") ?: "tiro"
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        // Start with SPECIAL_USE type (overlay window management)
        startForegroundWithType(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        showFab()

        // Observe dialog + drawer state and show/hide windows accordingly
        serviceScope.launch {
            OverlayStateRepository.activeDialog.collect { dialog ->
                when (dialog) {
                    OverlayStateRepository.ActiveDialog.NONE -> removeDialog()
                    OverlayStateRepository.ActiveDialog.CLIPBOARD -> showClipboardDialog()
                    OverlayStateRepository.ActiveDialog.SCREENSHOT -> { /* shown after capture */ }
                    OverlayStateRepository.ActiveDialog.MIC -> showMicDialog()
                }
            }
        }

        serviceScope.launch {
            OverlayStateRepository.activeDrawerType.collect { drawerType ->
                when (drawerType) {
                    OverlayStateRepository.ActiveDrawerType.NONE -> removeDrawer()
                    OverlayStateRepository.ActiveDrawerType.FACT_CHECK -> showFactCheckDrawer()
                    OverlayStateRepository.ActiveDrawerType.AI_TEXT_DETECTOR -> showAiTextDrawer()
                    OverlayStateRepository.ActiveDrawerType.KHOJ_CHAT -> showKhojChatDrawer()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_STOP -> stopSelf()

            ACTION_MEDIA_PROJECTION_RESULT -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode == Activity.RESULT_OK && data != null) {
                    // ── CRITICAL FIX ──────────────────────────────────────────────────────
                    // Before calling getMediaProjection(), the service MUST be running as
                    // FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION. Re-call startForeground with
                    // the new type — this is the only API-level-safe way to promote the type
                    // before Android 15's updateServiceType() was added.
                    startForegroundWithType(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                    // ─────────────────────────────────────────────────────────────────────

                    screenshotManager.initialize(resultCode, data)
                    OverlayStateRepository.setMediaProjectionReady(true)
                    serviceScope.launch { performScreenCapture() }
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        }
        removeFab()
        removeDialog()
        removeDrawer()
        aiDetectionJob?.cancel()
        screenshotManager.release()
        voiceManager.destroy()
        serviceScope.cancel()
        OverlayStateRepository.fullReset()
        super.onDestroy()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Foreground service type management
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Promotes (or demotes) the foreground service type.
     *
     * Android 14: startForeground() with the desired type is the only way to change it.
     * Android 15: Service.updateServiceType() is available but we target 14+, so we always
     * re-call startForeground() for maximum compatibility.
     */
    private fun startForegroundWithType(type: Int) {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed with type $type: ${e.message}", e)
        }
    }

    /** Reverts service type back to SPECIAL_USE after screenshot capture completes. */
    private fun revertToSpecialUseType() {
        startForegroundWithType(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Notification
    // ──────────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Khoj Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Khoj fact-check overlay is active"
                setShowBadge(false)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayModeService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            this, 1,
            Intent(this, com.example.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Khoj Overlay Active")
            .setContentText("Tap the floating button to fact-check anything")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(openPi)
            .addAction(Notification.Action.Builder(null, "Stop", stopPi).build())
            .setOngoing(true)
            .build()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Window helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun addOverlayComposeWindow(
        params: WindowManager.LayoutParams,
        content: @androidx.compose.runtime.Composable () -> Unit
    ): ComposeView {
        // Build a SavedStateRegistryOwner with a fresh INITIALIZED lifecycle.
        // See OverlayWindowFactory.buildSavedStateOwner() for the full explanation.
        val savedStateOwner = OverlayWindowFactory.buildSavedStateOwner(this)
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayModeService as LifecycleOwner)
            setViewTreeSavedStateRegistryOwner(savedStateOwner)
            setContent(content)
        }
        windowManager.addView(view, params)
        return view
    }

    private fun removeView(view: View?) {
        view?.let { runCatching { windowManager.removeView(it) } }
    }

    // ──────────────────────────────────────────────────────────────────────
    // FAB window
    // ──────────────────────────────────────────────────────────────────────

    private fun updateMenuExpandedState(expanded: Boolean) {
        isMenuExpanded = expanded
        val p = fabWindowView?.layoutParams as? WindowManager.LayoutParams ?: return
        p.flags = if (expanded) {
            p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        runCatching { windowManager.updateViewLayout(fabWindowView, p) }
    }

    private fun showFab() {
        if (fabWindowView != null) return

        val params = OverlayWindowFactory.fabParams(fabX, fabY)

        val composeView = addOverlayComposeWindow(params) {
            val themePref by themePreferenceFlow.collectAsState()
            val appLang by appLanguageFlow.collectAsState()
            val fontPref by bengaliFontFlow.collectAsState()

            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (themePref) {
                "dark" -> true
                "light" -> false
                else -> isSystemDark
            }
            val isBn = appLang == "bn"

            MyApplicationTheme(darkTheme = darkTheme, isBn = isBn, bengaliFont = fontPref) {
                OverlayFabView(
                    menuExpanded = isMenuExpanded,
                    isBn = isBn,
                    isDark = darkTheme,
                    onCloseMenu = { updateMenuExpandedState(false) },
                    onSelectAction = ::onSelectAction,
                    onScreenshotAction = ::onScreenshotAction,
                    onMicrophoneAction = ::onMicrophoneAction,
                    onKhojChatAction = ::onKhojChatAction,
                    onSearchAction = ::onSearchAction
                )
            }
        }

        var initialX = 0
        var initialY = 0
        var initialRawX = 0f
        var initialRawY = 0f
        var isDragging = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        composeView.setOnTouchListener { v, event ->
            val fabHeightPx = 58.dpToPx()
            val isTouchOnFab = event.y >= (v.height - fabHeightPx)

            if (isMenuExpanded && !isTouchOnFab) {
                return@setOnTouchListener false
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = fabX
                    initialY = fabY
                    initialRawX = event.rawX
                    initialRawY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialRawX
                    val dy = event.rawY - initialRawY

                    if (!isDragging && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                        isDragging = true
                        if (isMenuExpanded) {
                            updateMenuExpandedState(false)
                        }
                        showDismissTarget()
                    }

                    if (isDragging) {
                        val displayMetrics = resources.displayMetrics
                        val screenHeight = displayMetrics.heightPixels
                        val screenWidth = displayMetrics.widthPixels
                        val dismissZoneY = screenHeight - 150.dpToPx()

                        val newX = (initialX + dx).toInt()
                        val newY = (initialY + dy).toInt()

                        if (newY > dismissZoneY && dy > 0) {
                            isHoveringDismiss = true
                            fabX = (screenWidth / 2) - 24.dpToPx()
                            fabY = dismissZoneY + 30.dpToPx()
                        } else {
                            isHoveringDismiss = false
                            fabX = newX
                            fabY = newY
                        }

                        val p = fabWindowView?.layoutParams as? WindowManager.LayoutParams
                        if (p != null) {
                            p.x = fabX
                            p.y = fabY
                            runCatching { windowManager.updateViewLayout(fabWindowView, p) }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        handleDragEnd()
                    } else {
                        updateMenuExpandedState(!isMenuExpanded)
                    }
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        handleDragEnd()
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }

        fabWindowView = composeView
    }

    private fun removeFab() {
        removeView(fabWindowView)
        fabWindowView = null
    }

    // ──────────────────────────────────────────────────────────────────────
    // Dismiss Target & Dragging
    // ──────────────────────────────────────────────────────────────────────

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun showDismissTarget() {
        if (dismissTargetView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            150.dpToPx(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        
        dismissTargetView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayModeService as LifecycleOwner)
            setViewTreeSavedStateRegistryOwner(OverlayWindowFactory.buildSavedStateOwner(this@OverlayModeService))
            setContent {
                MyApplicationTheme {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val scale by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (isHoveringDismiss) 1.2f else 1.0f
                        )
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .scale(scale)
                                .background(Color.Red.copy(alpha = 0.8f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
        windowManager.addView(dismissTargetView, params)
    }

    private fun hideDismissTarget() {
        dismissTargetView?.let { runCatching { windowManager.removeView(it) } }
        dismissTargetView = null
        isHoveringDismiss = false
    }

    private fun handleDragEnd() {
        hideDismissTarget()
        if (isHoveringDismiss) {
            removeFab()
            stopSelf()
        } else {
            animateFabToNearestEdge()
        }
    }

    private fun animateFabToNearestEdge() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val fabWidth = 48.dpToPx()
        val nearestEdgeX = if (fabX < (screenWidth - fabWidth) / 2) 0 else screenWidth - fabWidth
        
        val startX = fabX
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = android.view.animation.DecelerateInterpolator(1.5f)
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                fabX = (startX + (nearestEdgeX - startX) * fraction).toInt()
                val p = fabWindowView?.layoutParams as? WindowManager.LayoutParams ?: return@addUpdateListener
                p.x = fabX
                p.y = fabY
                runCatching { windowManager.updateViewLayout(fabWindowView, p) }
            }
            start()
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Dialog windows
    // ──────────────────────────────────────────────────────────────────────

    private fun showClipboardDialog() {
        removeDialog()
        // Do NOT read clipboard here — the app may not have focus at this point
        // (Android 10+ denies clipboard access to non-focused apps).
        // The dialog composable reads it internally AFTER the window takes focus.
        dialogWindowView = addOverlayComposeWindow(OverlayWindowFactory.fullScreenParams()) {
            val themePref by themePreferenceFlow.collectAsState()
            val appLang by appLanguageFlow.collectAsState()
            val fontPref by bengaliFontFlow.collectAsState()

            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (themePref) {
                "dark" -> true
                "light" -> false
                else -> isSystemDark
            }
            val isBn = appLang == "bn"

            MyApplicationTheme(darkTheme = darkTheme, isBn = isBn, bengaliFont = fontPref) {
                OverlayClipboardDialog(
                    onVerify = { claim ->
                        OverlayStateRepository.dismissDialog()
                        OverlayStateRepository.setPendingClaimText(claim, source = "clipboard")
                        runFactCheck(claim)
                    },
                    onAiCheck = { text ->
                        onAiCheckAction(text)
                    },
                    onDismiss = {
                        OverlayStateRepository.dismissDialog()
                    }
                )
            }
        }
    }

    private fun showScreenshotDialog(bitmap: Bitmap) {
        removeDialog()

        dialogWindowView = addOverlayComposeWindow(OverlayWindowFactory.fullScreenParams()) {
            val themePref by themePreferenceFlow.collectAsState()
            val appLang by appLanguageFlow.collectAsState()
            val fontPref by bengaliFontFlow.collectAsState()

            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (themePref) {
                "dark" -> true
                "light" -> false
                else -> isSystemDark
            }
            val isBn = appLang == "bn"

            MyApplicationTheme(darkTheme = darkTheme, isBn = isBn, bengaliFont = fontPref) {
                OverlayScreenshotDialog(
                    bitmap = bitmap,
                    isBn = isBn,
                    onVerify = { bmp, userClaim ->
                        OverlayStateRepository.dismissDialog()
                        OverlayStateRepository.setPendingBitmap(bmp)
                        runImageFactCheck(bmp, userClaim)
                    },
                    onDismiss = {
                        OverlayStateRepository.dismissDialog()
                        // Release MediaProjection and revert service type now that screenshot is done
                        screenshotManager.release()
                        revertToSpecialUseType()
                    }
                )
            }
        }
    }

    private fun showMicDialog() {
        removeDialog()

        dialogWindowView = addOverlayComposeWindow(OverlayWindowFactory.fullScreenParams()) {
            val themePref by themePreferenceFlow.collectAsState()
            val appLang by appLanguageFlow.collectAsState()
            val fontPref by bengaliFontFlow.collectAsState()

            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (themePref) {
                "dark" -> true
                "light" -> false
                else -> isSystemDark
            }
            val isBn = appLang == "bn"

            MyApplicationTheme(darkTheme = darkTheme, isBn = isBn, bengaliFont = fontPref) {
                val transcription by OverlayStateRepository.voiceTranscription.collectAsState()
                val isListening by voiceManager.isListening.collectAsState()

                OverlayMicDialog(
                    transcribedText = transcription,
                    isListening = isListening,
                    isBn = isBn,
                    onToggleLanguage = {
                        val newValue = !isBn
                        prefs.edit().putString("app_language_option", if (newValue) "bn" else "en").apply()
                        if (isListening) {
                            startVoiceListening(newValue)
                        }
                    },
                    onStartListening = { startVoiceListening(isBn) },
                    onStopListening = {
                        voiceManager.stopListening()
                        revertToSpecialUseType()
                    },
                    onVerify = { claim ->
                        voiceManager.stopListening()
                        revertToSpecialUseType()
                        OverlayStateRepository.dismissDialog()
                        OverlayStateRepository.setPendingClaimText(claim, source = "voice")
                        runFactCheck(claim)
                    },
                    onDismiss = {
                        voiceManager.stopListening()
                        revertToSpecialUseType()
                        OverlayStateRepository.setVoiceTranscription("")
                        OverlayStateRepository.dismissDialog()
                    }
                )
            }
        }

        // Auto-start listening when the dialog opens
        val isBnInitial = appLanguageFlow.value == "bn"
        startVoiceListening(isBnInitial)
    }

    private fun removeDialog() {
        removeView(dialogWindowView)
        dialogWindowView = null
    }

    // ──────────────────────────────────────────────────────────────────────
    // Result drawer window
    // ──────────────────────────────────────────────────────────────────────

    private fun showFactCheckDrawer() {
        removeDrawer()

        drawerWindowView = addOverlayComposeWindow(OverlayWindowFactory.fullScreenParams()) {
            val themePref by themePreferenceFlow.collectAsState()
            val appLang by appLanguageFlow.collectAsState()
            val fontPref by bengaliFontFlow.collectAsState()

            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (themePref) {
                "dark" -> true
                "light" -> false
                else -> isSystemDark
            }
            val isBn = appLang == "bn"
            val fontToUse = if (isBn) {
                if (fontPref == "anek") com.example.ui.theme.AnekBanglaFamily else com.example.ui.theme.TiroBanglaFamily
            } else {
                androidx.compose.ui.text.font.FontFamily.Default
            }

            MyApplicationTheme(darkTheme = darkTheme, isBn = isBn, bengaliFont = fontPref) {
                val factCheckState by OverlayStateRepository.factCheckState.collectAsState()
                val streamingMarkdown by OverlayStateRepository.streamingMarkdown.collectAsState()
                val pendingClaimText by OverlayStateRepository.pendingClaimText.collectAsState()
                val sessionSource by OverlayStateRepository.sessionSource.collectAsState()
                val isListening by voiceManager.isListening.collectAsState()

                OverlayResultDrawer(
                    factCheckState = factCheckState,
                    streamingMarkdown = streamingMarkdown,
                    pendingClaimText = pendingClaimText,
                    sessionSource = sessionSource,
                    isListening = isListening,
                    onVerify = { claim -> runFactCheck(claim) },
                    onDismiss = { OverlayStateRepository.closeDrawer() },
                    onReset = { OverlayStateRepository.resetSession() },
                    fontToUse = fontToUse
                )
            }
        }
    }

    private fun showAiTextDrawer() {
        removeDrawer()

        drawerWindowView = addOverlayComposeWindow(OverlayWindowFactory.fullScreenParams()) {
            val themePref by themePreferenceFlow.collectAsState()
            val appLang by appLanguageFlow.collectAsState()
            val fontPref by bengaliFontFlow.collectAsState()

            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (themePref) {
                "dark" -> true
                "light" -> false
                else -> isSystemDark
            }
            val isBn = appLang == "bn"
            val fontFamily = if (fontPref == "anek") com.example.ui.theme.AnekBanglaFamily else com.example.ui.theme.TiroBanglaFamily

            MyApplicationTheme(darkTheme = darkTheme, isBn = isBn, bengaliFont = fontPref) {
                val detectionState by OverlayStateRepository.aiDetectionState.collectAsState()
                val inputText by OverlayStateRepository.aiDetectorInputText.collectAsState()

                OverlayAiTextDrawer(
                    state = detectionState,
                    inputText = inputText,
                    onRetry = { runAiTextDetection(inputText) },
                    onCancel = {
                        aiDetectionJob?.cancel()
                        OverlayStateRepository.setAiDetectionState(com.example.data.api.BanglaAiTextDetectionState.Idle)
                    },
                    onDismiss = {
                        aiDetectionJob?.cancel()
                        OverlayStateRepository.closeDrawer()
                    },
                    isBn = isBn,
                    fontFamily = fontFamily,
                    isDark = darkTheme
                )
            }
        }
    }

    private fun showKhojChatDrawer() {
        removeDrawer()

        drawerWindowView = addOverlayComposeWindow(OverlayWindowFactory.chatDrawerParams()) {
            val themePref by themePreferenceFlow.collectAsState()
            val appLang by appLanguageFlow.collectAsState()
            val fontPref by bengaliFontFlow.collectAsState()

            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (themePref) {
                "dark" -> true
                "light" -> false
                else -> isSystemDark
            }
            val isBn = appLang == "bn"
            val fontFamily = if (fontPref == "anek") com.example.ui.theme.AnekBanglaFamily else com.example.ui.theme.TiroBanglaFamily

            MyApplicationTheme(darkTheme = darkTheme, isBn = isBn, bengaliFont = fontPref) {
                OverlayKhojChatDrawer(
                    onDismiss = { OverlayStateRepository.closeDrawer() },
                    isBn = isBn,
                    fontToUse = fontFamily,
                    isDark = darkTheme,
                    voiceManager = voiceManager
                )
            }
        }
    }

    private fun removeDrawer() {
        removeView(drawerWindowView)
        drawerWindowView = null
    }

    private fun onAiCheckAction(text: String) {
        if (text.isBlank()) return
        OverlayStateRepository.dismissDialog()
        OverlayStateRepository.setAiDetectorInputText(text)
        OverlayStateRepository.openAiTextDrawer()
        runAiTextDetection(text)
    }

    private fun runAiTextDetection(text: String, mode: String = "balanced") {
        aiDetectionJob?.cancel()
        aiDetectionJob = serviceScope.launch {
            OverlayStateRepository.setAiDetectionState(com.example.data.api.BanglaAiTextDetectionState.Loading)
            try {
                val response = com.example.data.api.BanglaAiTextDetectorApi.detect(text, operatingMode = mode)
                OverlayStateRepository.setAiDetectionState(com.example.data.api.BanglaAiTextDetectionState.Success(response))
            } catch (e: Exception) {
                Log.e(TAG, "Overlay AI text detection failed: ${e.message}", e)
                OverlayStateRepository.setAiDetectionState(
                    com.example.data.api.BanglaAiTextDetectionState.Error(
                        e.message ?: "বিশ্লেষণ সম্পন্ন করা সম্ভব হয়নি"
                    )
                )
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // FAB action handlers
    // ──────────────────────────────────────────────────────────────────────

    private fun onSelectAction() {
        OverlayStateRepository.showDialog(OverlayStateRepository.ActiveDialog.CLIPBOARD)
    }

    private fun onScreenshotAction() {
        // Always request fresh consent (on-demand approach — no reusing old tokens)
        screenshotManager.release()
        val intent = Intent(this, MediaProjectionConsentActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun onMicrophoneAction() {
        OverlayStateRepository.setVoiceTranscription("")
        OverlayStateRepository.showDialog(OverlayStateRepository.ActiveDialog.MIC)
    }

    private fun onKhojChatAction() {
        OverlayStateRepository.openKhojChatDrawer()
    }

    private fun onSearchAction() {
        startActivity(
            Intent(this, com.example.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Voice STT
    // ──────────────────────────────────────────────────────────────────────

    private fun startVoiceListening(isBn: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForegroundWithType(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        }

        voiceManager.startListening(
            isBn = isBn,
            onPartialResult = { partial ->
                OverlayStateRepository.setVoiceTranscription(partial)
            },
            onFinalResult = { final ->
                OverlayStateRepository.setVoiceTranscription(final)
                revertToSpecialUseType()
            },
            onError = { _: String -> 
                revertToSpecialUseType() 
            }
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Screenshot capture pipeline
    // ──────────────────────────────────────────────────────────────────────

    private suspend fun performScreenCapture() {
        try {
            val bitmap = screenshotManager.captureFullScreen()

            // After capture, we no longer need the MEDIA_PROJECTION type during dialog display
            // (the projection itself has already captured the frame). Revert here so the service
            // is back to SPECIAL_USE while the user reviews the dialog.
            revertToSpecialUseType()

            // Show screenshot dialog on the main thread
            withContext(Dispatchers.Main) {
                OverlayStateRepository.showDialog(OverlayStateRepository.ActiveDialog.SCREENSHOT)
                showScreenshotDialog(bitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Screen capture failed: ${e.message}", e)
            revertToSpecialUseType()
            OverlayStateRepository.setFactCheckState(
                FactCheckProgress.Error(
                    when {
                        e.message?.contains("protect", ignoreCase = true) == true ->
                            "This screen is protected and cannot be captured."
                        else ->
                            "Screenshot failed: ${e.message ?: "Unknown error"}"
                    }
                )
            )
            OverlayStateRepository.setDrawerOpen(true)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Text fact-check pipeline
    // ──────────────────────────────────────────────────────────────────────

    private fun runFactCheck(claim: String, isFromImage: Boolean = false) {
        if (claim.isBlank()) return

        OverlayStateRepository.setDrawerOpen(true)
        OverlayStateRepository.resetStreamingMarkdown()

        serviceScope.launch {
            val responseLanguageManager = com.example.data.preferences.ResponseLanguageManager(this@OverlayModeService)
            val currentResponseLanguage = responseLanguageManager.responseLanguage.value

            factCheckRepository.performFactCheck(claim, currentResponseLanguage, isFromImage).collect { progress ->
                when (progress) {
                    is FactCheckProgress.StreamingChunk -> {
                        OverlayStateRepository.appendStreamingMarkdown(progress.chunk)
                        OverlayStateRepository.setFactCheckState(FactCheckProgress.GeneratingVerdict)
                    }
                    else -> OverlayStateRepository.setFactCheckState(progress)
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Image fact-check pipeline
    // ──────────────────────────────────────────────────────────────────────

    private fun runImageFactCheck(bitmap: Bitmap, userClaim: String) {
        OverlayStateRepository.setDrawerOpen(true)
        OverlayStateRepository.setFactCheckState(FactCheckProgress.ClaimNormalization)
        OverlayStateRepository.resetStreamingMarkdown()

        serviceScope.launch {
            try {
                val extractedText = try {
                    com.example.data.api.ImageOcrHelper.extractTextFromBitmap(bitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "All OCR tiers failed in overlay", e)
                    ""
                }
                
                val queryToFactCheck = if (extractedText.isNotBlank()) {
                    "$userClaim\n\n[স্ক্রিনশটের বিবরণ/লেখা]:\n$extractedText"
                } else {
                    userClaim
                }

                OverlayStateRepository.setPendingClaimText(queryToFactCheck, source = "screenshot")
                runFactCheck(queryToFactCheck, isFromImage = true)
            } catch (e: Exception) {
                Log.e(TAG, "Image fact-check processing failed: ${e.message}", e)
                OverlayStateRepository.setFactCheckState(
                    FactCheckProgress.Error("Image processing failed: ${e.message}")
                )
            }
        }
    }
}
