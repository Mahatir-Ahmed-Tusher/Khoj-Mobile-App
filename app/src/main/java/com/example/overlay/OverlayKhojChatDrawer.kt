package com.example.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.repository.KhojChatRepository
import com.example.ui.screens.KhojChatView

/**
 * Overlay Khoj Chat Drawer.
 *
 * Connected directly to the single source of truth:
 * - Backend: [KhojChatRepository] singleton (shares all history, active sessions, and streaming LLM).
 * - Frontend: [KhojChatView] composable (identical UI, tabs, empty states, thinking bubbles, speech, and side drawer).
 *
 * Any update made to Khoj Chat in the app automatically reflects here with zero duplication.
 */
@Composable
fun OverlayKhojChatDrawer(
    onDismiss: () -> Unit,
    isBn: Boolean,
    fontToUse: FontFamily,
    isDark: Boolean,
    voiceManager: OverlayVoiceManager? = null
) {
    val context = LocalContext.current
    val chatRepository = remember(context) {
        KhojChatRepository.getInstance(context)
    }

    val drawerShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim background - tap outside to dismiss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                }
        )

        // Bottom drawer surface hosting the unified KhojChatView
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .align(Alignment.BottomCenter)
                .clip(drawerShape),
            shape = drawerShape,
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp
        ) {
            KhojChatView(
                repository = chatRepository,
                onClose = onDismiss,
                isBn = isBn,
                fontToUse = fontToUse,
                modifier = Modifier.fillMaxSize(),
                showDragHandle = true,
                headerIcon = {
                    Image(
                        painter = painterResource(id = R.drawable.khoj_chat_tooltip),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        }
    }
}
