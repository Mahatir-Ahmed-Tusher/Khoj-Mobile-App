package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.overlay.OemBatteryHelper
import com.example.overlay.OverlayModeService
import com.example.ui.theme.AnekBanglaFamily
import com.example.ui.theme.ColorFalse
import com.example.ui.theme.SlatePrimary
import com.example.ui.theme.TiroBanglaFamily
import com.example.ui.viewmodel.AiImageDetectionViewModel
import com.example.ui.viewmodel.FactCheckViewModel
import com.example.ui.viewmodel.ImageSearchViewModel

@Composable
fun SettingsControlsScreen(
    aiViewModel: AiImageDetectionViewModel? = null,
    factCheckViewModel: FactCheckViewModel,
    imageSearchViewModel: ImageSearchViewModel,
    isLoggedIn: Boolean = false,
    onRequireLogin: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val renderingLanguage by factCheckViewModel.renderingLanguage.collectAsStateWithLifecycle()
    val isBn = renderingLanguage == "bn"

    // ── Overlay mode state ────────────────────────────────────────────────
    var isOverlayEnabled by remember { mutableStateOf(OverlayModeService.isRunning) }
    var showLoginRequiredDialog by remember { mutableStateOf(false) }
    var showOemDialog by remember { mutableStateOf(false) }
    var showOverlayPermissionDisclosure by remember { mutableStateOf(false) }

    // Re-check overlay state when returning from Settings (permission grant flow)
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // User returned from system overlay permission Settings
        val hasPermission = android.provider.Settings.canDrawOverlays(context)
        if (hasPermission) {
            OverlayModeService.start(context)
            isOverlayEnabled = true
        }
    }

    fun handleOverlayToggle(enabled: Boolean) {
        if (enabled) {
            if (!isLoggedIn) {
                showLoginRequiredDialog = true
                return
            }
            // Check if SYSTEM_ALERT_WINDOW permission is already granted
            if (!android.provider.Settings.canDrawOverlays(context)) {
                showOverlayPermissionDisclosure = true
                return
            }
            // Check if this is first-time enable — show OEM onboarding if needed
            val prefs = context.getSharedPreferences("khoj_overlay", android.content.Context.MODE_PRIVATE)
            val firstTime = !prefs.getBoolean("oem_onboarding_shown", false)
            val oemInstructions = OemBatteryHelper.getInstructionsIfNeeded()
            if (firstTime && oemInstructions != null) {
                prefs.edit().putBoolean("oem_onboarding_shown", true).apply()
                showOemDialog = true
                // Continue to enable (OEM dialog is informational, doesn't block)
            }
            OverlayModeService.start(context)
            isOverlayEnabled = true
        } else {
            OverlayModeService.stop(context)
            isOverlayEnabled = false
        }
    }

    // OEM onboarding instructions state
    val oemInstructions = remember { OemBatteryHelper.getInstructionsIfNeeded() }

    // ── Dialogs ───────────────────────────────────────────────────────────

    // Login required dialog
    if (showLoginRequiredDialog) {
        AlertDialog(
            onDismissRequest = { showLoginRequiredDialog = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = SlatePrimary) },
            title = {
                Text(
                    text = if (isBn) "লগইন প্রয়োজন" else "Login Required",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isBn)
                        "\"অন্য অ্যাপে দেখুন\" ফিচারটি ব্যবহার করতে একটি Khoj অ্যাকাউন্টে লগইন করুন।"
                    else
                        "Log in to your Khoj account to use the \"View on Other Apps\" feature.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLoginRequiredDialog = false
                        onRequireLogin()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SlatePrimary)
                ) {
                    Text(if (isBn) "লগইন করুন" else "Log In")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoginRequiredDialog = false }) {
                    Text(if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
    }

    // SYSTEM_ALERT_WINDOW permission disclosure dialog
    if (showOverlayPermissionDisclosure) {
        AlertDialog(
            onDismissRequest = { showOverlayPermissionDisclosure = false },
            icon = {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = null,
                    tint = SlatePrimary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = if (isBn) "\"অন্য অ্যাপের উপর দেখান\" অনুমতি" else "\"Display Over Other Apps\" Permission",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isBn)
                            "এই ফিচারটি অন্য অ্যাপ ব্যবহারের সময় একটি ছোট ফ্লোটিং বাটন দেখানোর জন্য আপনার ডিভাইসের \"অন্য অ্যাপের উপর দেখান\" অনুমতি প্রয়োজন।"
                        else
                            "This feature needs the \"Display over other apps\" permission to show a small floating button while you use other apps.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (isBn)
                            "বাটনটি অন্য অ্যাপের কোনো তথ্য পড়ে না বা সংগ্রহ করে না। শুধুমাত্র আপনার সরাসরি ইন্টারঅ্যাকশনে কাজ করে।"
                        else
                            "The button cannot read or collect data from other apps — it only acts on your explicit interactions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverlayPermissionDisclosure = false
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        overlayPermissionLauncher.launch(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SlatePrimary)
                ) {
                    Text(if (isBn) "অনুমতি দিন" else "Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPermissionDisclosure = false }) {
                    Text(if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
    }

    // OEM battery onboarding dialog
    if (showOemDialog && oemInstructions != null) {
        AlertDialog(
            onDismissRequest = { showOemDialog = false },
            icon = {
                Icon(Icons.Default.Battery4Bar, contentDescription = null, tint = SlatePrimary)
            },
            title = {
                Text(
                    text = if (isBn) oemInstructions.titleBn else oemInstructions.titleEn,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (isBn)
                            "${oemInstructions.manufacturer} ডিভাইসে ওভারলে সার্ভিস সচল রাখতে নিচের ধাপগুলো অনুসরণ করুন:"
                        else
                            "Follow these steps to keep the overlay service active on your ${oemInstructions.manufacturer} device:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val steps = if (isBn) oemInstructions.stepsBn else oemInstructions.stepsEn
                    steps.forEachIndexed { index, step ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = SlatePrimary.copy(alpha = 0.1f),
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlatePrimary
                                    )
                                }
                            }
                            Text(text = step, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showOemDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SlatePrimary)
                ) {
                    Text(if (isBn) "বুঝেছি" else "Got it")
                }
            }
        )
    }

    val bengaliFontPreference by factCheckViewModel.bengaliFontPreference.collectAsStateWithLifecycle()
    val fontToUse = if (isBn) {
        if (bengaliFontPreference == "anek") AnekBanglaFamily else TiroBanglaFamily
    } else {
        androidx.compose.ui.text.font.FontFamily.Default
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(
            titleBn = "কন্ট্রোলস",
            titleEn = "Controls",
            subtitleBn = "ইঞ্জিন সেটিংস কাস্টমাইজ করুন",
            subtitleEn = "Customize engine settings",
            isBn = isBn,
            fontToUse = fontToUse,
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Image Search Engine Preference Card ──────────────────────────
            val currentImageSearchEngine by imageSearchViewModel.selectedEngine.collectAsStateWithLifecycle()
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = "Image Search Engine",
                            tint = SlatePrimary
                        )
                        Text(
                            text = getString("image_search_engine", isBn),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = getString("image_search_engine_desc", isBn),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            com.example.data.local.ImageSearchPreferences.ENGINE_GOOGLE_LENS to if (isBn) "Google Lens (ডিফল্ট)" else "Google Lens (Default)",
                            com.example.data.local.ImageSearchPreferences.ENGINE_YANDEX to "Yandex",
                            com.example.data.local.ImageSearchPreferences.ENGINE_BOTH to if (isBn) "Google Lens + Yandex (উভয়)" else "Google Lens + Yandex (Both)"
                        ).forEach { (code, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { imageSearchViewModel.updateEnginePreference(code) }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = (currentImageSearchEngine == code),
                                    onClick = { imageSearchViewModel.updateEnginePreference(code) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (currentImageSearchEngine == code) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ── "View on Other Apps" Overlay Card ─────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    // Gradient header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        SlatePrimary.copy(alpha = 0.85f),
                                        SlatePrimary.copy(alpha = 0.55f)
                                    )
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = if (isBn) "অন্য অ্যাপে দেখুন" else "View on Other Apps",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (!isLoggedIn) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color.White.copy(alpha = 0.2f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isBn) "লগইন প্রয়োজন" else "Login required",
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Toggle row
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    text = if (isBn) "ফ্লোটিং বাটন সক্রিয় করুন" else "Enable Floating Button",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isBn)
                                        "যেকোনো অ্যাপ থেকে তথ্য যাচাই করুন"
                                    else
                                        "Fact-check content from any app",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                            Switch(
                                checked = isOverlayEnabled,
                                onCheckedChange = { handleOverlayToggle(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                                    checkedTrackColor = SlatePrimary
                                )
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Feature bullets
                        val features = if (isBn) listOf(
                            Icons.Default.TextFields to "টেক্সট নির্বাচন করে তথ্য যাচাই করুন",
                            Icons.Default.ContentCopy to "ক্লিপবোর্ড থেকে স্বয়ংক্রিয়ভাবে পূরণ",
                            Icons.Default.ScreenshotMonitor to "স্ক্রিনশটের নির্দিষ্ট অংশ যাচাই করুন",
                            Icons.Default.Mic to "ভয়েস দিয়ে তথ্য যাচাই করুন"
                        ) else listOf(
                            Icons.Default.TextFields to "Select text and fact-check it",
                            Icons.Default.ContentCopy to "Auto-fills from your clipboard",
                            Icons.Default.ScreenshotMonitor to "Verify a region of your screen",
                            Icons.Default.Mic to "Speak a claim to verify it"
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            features.forEach { (icon, label) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = SlatePrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
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


