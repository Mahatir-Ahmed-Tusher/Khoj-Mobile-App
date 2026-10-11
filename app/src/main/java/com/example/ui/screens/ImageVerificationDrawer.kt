package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.example.data.model.VerificationMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageVerificationDrawer(
    originalUri: Uri,
    onDismissRequest: () -> Unit,
    onSend: (croppedUri: Uri?, selectedModes: Set<VerificationMode>, additionalContext: String?) -> Unit
) {
    var croppedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedMode by remember { mutableStateOf(VerificationMode.FACT_CHECK) }
    var additionalContext by remember { mutableStateOf("") }
    var showValidationError by remember { mutableStateOf(false) }
    
    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            croppedUri = result.uriContent
        }
    }

    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("khoj_settings", android.content.Context.MODE_PRIVATE)
    }
    val appLangPref = prefs.getString("app_language", "bn")
    val isBn = appLangPref == "bn"
    val themePref = prefs.getString("theme_preference", "system")
    val isSystemDark = isSystemInDarkTheme()
    val isDarkTheme = when (themePref) {
        "dark" -> true
        "light" -> false
        else -> isSystemDark
    }

    val lightBlue = Color(0xFF38BDF8)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { false }),
        containerColor = if (isDarkTheme) Color(0xFF14171F) else MaterialTheme.colorScheme.surface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = if (isDarkTheme) Color(0xFF475569) else Color(0xFFCBD5E1)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = getString("verify_image_prompt", isBn),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isDarkTheme) Color.White else Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Image Preview with Frosted Crop Badge
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                    .border(
                        BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(LocalContext.current)
                            .data(croppedUri ?: originalUri)
                            .build()
                    ),
                    contentDescription = "Selected Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    onClick = {
                        val options = CropImageContractOptions(
                            uri = croppedUri ?: originalUri,
                            cropImageOptions = CropImageOptions(
                                imageSourceIncludeGallery = false,
                                imageSourceIncludeCamera = false
                            )
                        )
                        cropLauncher.launch(options)
                    },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.Crop,
                            contentDescription = "Crop Image",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isBn) "ক্রপ" else "Crop",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Mutually Exclusive Verification Options (Single Choice Only)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VerificationModeOption(
                    text = getString("fact_check", isBn),
                    icon = Icons.Default.FactCheck,
                    selected = selectedMode == VerificationMode.FACT_CHECK,
                    lightBlue = lightBlue,
                    isDarkTheme = isDarkTheme,
                    onSelect = { 
                        selectedMode = VerificationMode.FACT_CHECK 
                        showValidationError = false
                    }
                )

                VerificationModeOption(
                    text = getString("image_search", isBn),
                    icon = Icons.Default.Search,
                    selected = selectedMode == VerificationMode.IMAGE_SEARCH,
                    lightBlue = lightBlue,
                    isDarkTheme = isDarkTheme,
                    onSelect = { 
                        selectedMode = VerificationMode.IMAGE_SEARCH 
                        showValidationError = false
                    }
                )

                VerificationModeOption(
                    text = getString("ai_detection", isBn),
                    icon = Icons.Default.AutoAwesome,
                    selected = selectedMode == VerificationMode.AI_DETECTION,
                    lightBlue = lightBlue,
                    isDarkTheme = isDarkTheme,
                    onSelect = { 
                        selectedMode = VerificationMode.AI_DETECTION 
                        showValidationError = false
                    }
                )
            }

            // Additional Context Text Box (Only visible when Fact-Check is selected)
            AnimatedVisibility(
                visible = selectedMode == VerificationMode.FACT_CHECK,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    OutlinedTextField(
                        value = additionalContext,
                        onValueChange = { 
                            additionalContext = it 
                            if (it.isNotBlank()) showValidationError = false
                        },
                        isError = showValidationError,
                        placeholder = {
                            Text(
                                text = getString("additional_context_hint", isBn),
                                color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = lightBlue,
                            unfocusedBorderColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1),
                            focusedContainerColor = if (isDarkTheme) Color(0xFF1E293B).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                            unfocusedContainerColor = if (isDarkTheme) Color(0xFF1E293B).copy(alpha = 0.25f) else Color(0xFFF8FAFC),
                            focusedTextColor = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                            unfocusedTextColor = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                            cursorColor = lightBlue,
                            errorBorderColor = MaterialTheme.colorScheme.error
                        )
                    )
                    if (showValidationError) {
                        Text(
                            text = getString("claim_required_error", isBn),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 6.dp, top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = getString("discard", isBn),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Button(
                    onClick = {
                        if (selectedMode == VerificationMode.FACT_CHECK && additionalContext.trim().isBlank()) {
                            showValidationError = true
                            return@Button
                        }
                        onSend(
                            croppedUri,
                            setOf(selectedMode),
                            if (selectedMode == VerificationMode.FACT_CHECK) additionalContext.trim() else null
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = getString("send", isBn),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun VerificationModeOption(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    lightBlue: Color,
    isDarkTheme: Boolean,
    onSelect: () -> Unit
) {
    val backgroundColor = if (selected) {
        if (isDarkTheme) lightBlue.copy(alpha = 0.12f) else lightBlue.copy(alpha = 0.08f)
    } else {
        if (isDarkTheme) Color(0xFF1E293B).copy(alpha = 0.35f) else Color(0xFFF8FAFC)
    }
    val borderColor = if (selected) {
        lightBlue.copy(alpha = 0.5f)
    } else {
        if (isDarkTheme) Color(0xFF334155).copy(alpha = 0.6f) else Color(0xFFE2E8F0)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = lightBlue,
                unselectedColor = if (isDarkTheme) Color(0xFF64748B) else Color(0xFF94A3B8)
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) lightBlue else if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) {
                if (isDarkTheme) Color.White else Color(0xFF0F172A)
            } else {
                if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF334155)
            },
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
