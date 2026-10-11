package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.SupabaseNotification
import com.example.data.repository.NotificationTarget
import com.example.ui.theme.SlatePrimary
import com.example.ui.viewmodel.NotificationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    viewModel: NotificationViewModel,
    isBn: Boolean = true,
    fontToUse: FontFamily = FontFamily.Default,
    onBack: () -> Unit,
    onNavigateToTarget: (NotificationTarget) -> Unit = {}
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isResolving by viewModel.isResolvingTarget.collectAsStateWithLifecycle()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isBn) "বিজ্ঞপ্তি" else "Notifications",
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontToUse
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (notifications.any { !it.isRead }) {
                        TextButton(onClick = { viewModel.markAllRead() }) {
                            Text(
                                text = if (isBn) "সব পড়া হয়েছে" else "Mark all read",
                                fontSize = 13.sp,
                                color = SlatePrimary,
                                fontFamily = fontToUse
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading && notifications.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SlatePrimary)
                }
            } else if (notifications.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SlatePrimary.copy(alpha = 0.1f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.NotificationsNone,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = SlatePrimary
                                )
                            }
                        }
                        Text(
                            text = if (isBn) "কোনো নতুন বিজ্ঞপ্তি নেই" else "No notifications yet",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            fontFamily = fontToUse
                        )
                        Text(
                            text = if (isBn)
                                "আপনার রিপোর্টে কেউ লাইক বা মন্তব্য করলে অথবা নতুন কোনো ফ্যাক্টচেক বা ব্লগ প্রকাশিত হলে এখানে দেখতে পাবেন।"
                            else
                                "When someone likes or comments on your reports, or when new fact checks and blogs are published, you'll see them here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontFamily = fontToUse
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(notifications, key = { it.id }) { notification ->
                        NotificationItem(
                            notification = notification,
                            isBn = isBn,
                            isDark = isDark,
                            fontToUse = fontToUse,
                            onClick = {
                                viewModel.resolveTarget(notification) { target ->
                                    onNavigateToTarget(target)
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }

            if (isResolving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = SlatePrimary)
                            Text(
                                text = if (isBn) "রিপোর্ট লোড হচ্ছে..." else "Loading report...",
                                fontSize = 14.sp,
                                fontFamily = fontToUse
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItem(
    notification: SupabaseNotification,
    isBn: Boolean,
    isDark: Boolean,
    fontToUse: FontFamily,
    onClick: () -> Unit
) {
    val isUnread = !notification.isRead
    val isBroadcast = notification.isBroadcast || notification.type.startsWith("admin_")

    val bgColor = if (isUnread) {
        if (isDark) SlatePrimary.copy(alpha = 0.10f) else SlatePrimary.copy(alpha = 0.05f)
    } else {
        Color.Transparent
    }

    val displayTitle = notification.getDisplayTitle(isBn)
    val displayMessage = notification.getDisplayMessage(isBn)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Leading Icon / Avatar
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            if (!notification.actorAvatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = notification.actorAvatarUrl,
                    contentDescription = notification.actorName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else if (isBroadcast) {
                Surface(
                    shape = CircleShape,
                    color = if (notification.type == "admin_blog") Color(0xFF6366F1).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (notification.type == "admin_blog") Icons.Default.MenuBook else Icons.Default.Verified,
                            contentDescription = null,
                            tint = if (notification.type == "admin_blog") Color(0xFF6366F1) else Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = when (notification.type) {
                        "like" -> Color(0xFFE11D48).copy(alpha = 0.15f)
                        "comment" -> SlatePrimary.copy(alpha = 0.15f)
                        "reply" -> Color(0xFF8B5CF6).copy(alpha = 0.15f)
                        else -> Color(0xFF10B981).copy(alpha = 0.15f)
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (notification.type) {
                                "like" -> Icons.Default.Favorite
                                "comment" -> Icons.Default.ChatBubble
                                "reply" -> Icons.Default.Reply
                                else -> Icons.Default.Notifications
                            },
                            contentDescription = null,
                            tint = when (notification.type) {
                                "like" -> Color(0xFFE11D48)
                                "comment" -> SlatePrimary
                                "reply" -> Color(0xFF8B5CF6)
                                else -> Color(0xFF10B981)
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Small badge icon for avatar
            if (!notification.actorAvatarUrl.isNullOrBlank()) {
                Surface(
                    shape = CircleShape,
                    color = when (notification.type) {
                        "like" -> Color(0xFFE11D48)
                        "comment" -> SlatePrimary
                        "reply" -> Color(0xFF8B5CF6)
                        else -> SlatePrimary
                    },
                    modifier = Modifier.size(16.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (notification.type) {
                                "like" -> Icons.Default.Favorite
                                "comment" -> Icons.Default.ChatBubble
                                else -> Icons.Default.Notifications
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }

        // Main Text Content
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = displayTitle,
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = fontToUse
                )
                if (isBroadcast) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = SlatePrimary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = if (isBn) "অফিশিয়াল" else "Official",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = SlatePrimary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            fontFamily = fontToUse
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = displayMessage,
                fontSize = 13.sp,
                color = if (isUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                fontFamily = fontToUse
            )

            if (!notification.postTitle.isNullOrBlank() && !isBroadcast) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "“${notification.postTitle}”",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontFamily = fontToUse
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = formatLocalizedRelativeTime(notification.createdAt, isBn),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontFamily = fontToUse
            )
        }

        // Optional Thumbnail or Unread Dot
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isUnread) {
                Surface(
                    shape = CircleShape,
                    color = SlatePrimary,
                    modifier = Modifier.size(8.dp)
                ) {}
            }

            if (!notification.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = notification.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    )
}

fun formatLocalizedRelativeTime(isoString: String, isBn: Boolean): String {
    return try {
        val cleanString = isoString.replace("Z", "+0000")
        val formatPattern = if (cleanString.contains(".")) "yyyy-MM-dd'T'HH:mm:ss.SSSZ" else "yyyy-MM-dd'T'HH:mm:ssZ"
        val sdf = java.text.SimpleDateFormat(formatPattern, java.util.Locale.US)
        val date = sdf.parse(cleanString)
        if (date != null) {
            val diffMs = System.currentTimeMillis() - date.time
            val diffSeconds = diffMs / 1000
            if (isBn) {
                when {
                    diffSeconds < 60 -> "এইমাত্র"
                    diffSeconds < 3600 -> "${toBengaliNumeral(diffSeconds / 60)} মিনিট আগে"
                    diffSeconds < 86400 -> "${toBengaliNumeral(diffSeconds / 3600)} ঘন্টা আগে"
                    diffSeconds < 604800 -> "${toBengaliNumeral(diffSeconds / 86400)} দিন আগে"
                    else -> {
                        val outputSdf = java.text.SimpleDateFormat("d MMM", java.util.Locale.US)
                        outputSdf.format(date)
                    }
                }
            } else {
                when {
                    diffSeconds < 60 -> "just now"
                    diffSeconds < 3600 -> "${diffSeconds / 60}m ago"
                    diffSeconds < 86400 -> "${diffSeconds / 3600}h ago"
                    diffSeconds < 604800 -> "${diffSeconds / 86400}d ago"
                    else -> {
                        val outputSdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
                        outputSdf.format(date)
                    }
                }
            }
        } else {
            isoString.take(10)
        }
    } catch (e: Throwable) {
        isoString.take(10)
    }
}

private fun toBengaliNumeral(number: Long): String {
    val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    return number.toString().map { if (it in '0'..'9') bnDigits[it - '0'] else it }.joinToString("")
}
