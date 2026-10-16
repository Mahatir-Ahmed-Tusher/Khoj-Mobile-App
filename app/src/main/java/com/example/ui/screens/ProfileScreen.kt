package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.composed
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.DeepSearchReport
import com.example.data.model.FactCheckReport
import com.example.data.model.SupabaseUser
import com.example.data.model.UserSearchHistory
import com.example.ui.components.AvatarCustomizerDialog
import com.example.ui.components.DeleteConfirmationDialog
import com.example.ui.theme.AnekBanglaFamily as AnekFont
import com.example.ui.theme.TiroBanglaFamily
import com.example.ui.theme.SlatePrimary
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.FactCheckViewModel
import com.example.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import com.example.ui.components.MarkdownText
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

val activeBengaliFont = mutableStateOf<androidx.compose.ui.text.font.FontFamily>(AnekFont)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    factCheckViewModel: FactCheckViewModel,
    authViewModel: AuthViewModel,
    profileViewModel: ProfileViewModel = viewModel(),
    onLogout: () -> Unit,
    onSelectReport: (FactCheckReport) -> Unit,
    onSelectDeepSearchReport: ((DeepSearchReport) -> Unit)? = null,
    onNavigateToLogin: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val isLoggedIn = authViewModel.isLoggedIn
    val renderingLanguage by factCheckViewModel.renderingLanguage.collectAsStateWithLifecycle()
    val isBn = renderingLanguage == "bn"
    val isDark = (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val bengaliFontPreference by factCheckViewModel.bengaliFontPreference.collectAsStateWithLifecycle()
    SideEffect {
        activeBengaliFont.value = if (bengaliFontPreference == "anek") AnekFont else TiroBanglaFamily
    }

    // Dialog state for name editing and avatar editing
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showAvatarCustomizerDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    val customAvatarUrl by profileViewModel.customAvatarUrl.collectAsStateWithLifecycle()
    val effectiveAvatarUrl by profileViewModel.effectiveAvatarUrl.collectAsStateWithLifecycle()

    // Sync history & avatar on load
    LaunchedEffect(currentUser?.id) {
        currentUser?.id?.let { profileViewModel.loadProfileData(it) }
        profileViewModel.loadAvatarState(currentUser)
    }

    if (!isLoggedIn) {
        // Locked State for Guests
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (isBn) "লগইন করুন" else "Sign In Required",
                    fontFamily = activeBengaliFont.value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isBn) "ড্যাশবোর্ড দেখতে এবং আপনার সার্চ হিস্ট্রি সংরক্ষণ করতে লগইন করুন।" else "Please log in to view your profile and sync your search history.",
                    fontFamily = activeBengaliFont.value,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onNavigateToLogin,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SlatePrimary),
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(50.dp)
                ) {
                    Text(
                        if (isBn) "লগইন পেজে যান" else "Go to Login",
                        fontFamily = activeBengaliFont.value,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        return
    }

    if (showLogoutConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = {
                Text(
                    text = if (isBn) "অ্যাকাউন্ট থেকে লগআউট করবেন?" else "Log out of your account?",
                    fontFamily = activeBengaliFont.value
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    showLogoutConfirmDialog = false
                    authViewModel.signOut()
                    onLogout()
                }) {
                    Text(text = if (isBn) "লগআউট" else "LOG OUT", fontFamily = activeBengaliFont.value)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text(text = if (isBn) "বাতিল" else "CANCEL", fontFamily = activeBengaliFont.value)
                }
            }
        )
    }

    // Dialog confirmation for Name Editing
    if (showEditNameDialog) {
        var newName by remember { mutableStateOf(currentUser?.displayName ?: "") }
        val isUpdatingName by profileViewModel.isUpdatingName.collectAsStateWithLifecycle()
        val updateNameError by profileViewModel.updateNameError.collectAsStateWithLifecycle()

        AlertDialog(
            onDismissRequest = { if (!isUpdatingName) showEditNameDialog = false },
            title = {
                Text(
                    text = if (isBn) "নাম পরিবর্তন করুন" else "Edit Name",
                    fontFamily = activeBengaliFont.value,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(if (isBn) "নাম" else "Name", fontFamily = activeBengaliFont.value) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isUpdatingName
                    )
                    updateNameError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontFamily = activeBengaliFont.value)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        currentUser?.id?.let { uid ->
                            profileViewModel.updateProfileName(uid, newName) {
                                authViewModel.loadProfile() // refresh Auth profile state
                                showEditNameDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SlatePrimary),
                    enabled = !isUpdatingName && newName.isNotBlank()
                ) {
                    if (isUpdatingName) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(if (isBn) "সংরক্ষণ করুন" else "Save", fontFamily = activeBengaliFont.value)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditNameDialog = false },
                    enabled = !isUpdatingName
                ) {
                    Text(if (isBn) "বাতিল" else "Cancel", fontFamily = activeBengaliFont.value)
                }
            }
        )
    }

    // Dialog confirmation for Avatar Editing
    if (showAvatarCustomizerDialog) {
        AvatarCustomizerDialog(
            isBn = isBn,
            currentAvatarUrl = effectiveAvatarUrl,
            hasCustomAvatar = !customAvatarUrl.isNullOrBlank(),
            userEmail = currentUser?.email,
            onDismiss = { showAvatarCustomizerDialog = false },
            onFinalizeAvatar = { newUrl ->
                profileViewModel.saveCustomAvatar(currentUser, newUrl)
                showAvatarCustomizerDialog = false
            },
            onDeleteAvatar = {
                profileViewModel.deleteCustomAvatar(currentUser)
                showAvatarCustomizerDialog = false
            }
        )
    }

    // Authenticated Profile Page
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = if (isBn) listOf("অ্যানালিটিক্স", "সার্চ হিস্ট্রি") else listOf("Analytics", "Search History")
    val searchHistory by profileViewModel.searchHistory.collectAsStateWithLifecycle()
    val isLoadingHistory by profileViewModel.isLoadingHistory.collectAsStateWithLifecycle()

    val sharedPrefs = remember(context) { context.getSharedPreferences("profile_prefs", android.content.Context.MODE_PRIVATE) }
    var customCoverIndex by remember(currentUser?.id) {
        val saved = sharedPrefs.getInt("cover_index_${currentUser?.id}", -1)
        mutableIntStateOf(saved)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        item {
            ProfileHeader(
                isBn = isBn,
                isDark = isDark,
                user = currentUser,
                effectiveAvatarUrl = effectiveAvatarUrl,
                historyCount = searchHistory.size,
                isLoadingHistory = isLoadingHistory,
                customCoverIndex = customCoverIndex,
                onEditNameClick = { showEditNameDialog = true },
                onEditAvatarClick = { showAvatarCustomizerDialog = true },
                onLogoutClick = {
                    showLogoutConfirmDialog = true
                },
                onShuffleCoverClick = {
                    val totalCovers = com.example.ui.utils.ALL_COVER_RESOURCES.size
                    val nextIndex = if (customCoverIndex < 0) {
                        (kotlin.math.abs(currentUser?.id?.hashCode() ?: 0) + 1) % totalCovers
                    } else {
                        (customCoverIndex + 1) % totalCovers
                    }
                    customCoverIndex = nextIndex
                    sharedPrefs.edit().putInt("cover_index_${currentUser?.id}", nextIndex).apply()
                },
                onBack = onBack
            )
        }

        item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = SlatePrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = SlatePrimary,
                        height = 3.dp
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontFamily = activeBengaliFont.value,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 16.sp,
                                color = if (selectedTab == index) SlatePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (selectedTab == 0) {
            item {
                AnalyticsTab(
                    viewModel = profileViewModel,
                    isBn = isBn,
                    isDark = isDark
                )
            }
        } else {
            item {
                HistoryTab(
                    viewModel = profileViewModel,
                    factCheckViewModel = factCheckViewModel,
                    isBn = isBn,
                    isDark = isDark,
                    onSelectReport = onSelectReport
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
fun ProfileHeader(
    isBn: Boolean,
    isDark: Boolean,
    user: SupabaseUser?,
    effectiveAvatarUrl: String,
    historyCount: Int,
    isLoadingHistory: Boolean = false,
    customCoverIndex: Int,
    onEditNameClick: () -> Unit,
    onEditAvatarClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onShuffleCoverClick: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val name = user?.displayName ?: "User"
    val email = user?.email ?: ""
    val avatarUrl = effectiveAvatarUrl.ifBlank { user?.photoUrl }

    Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
        // Profile Cover Background
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(
                id = com.example.ui.utils.getProfileCoverResource(user?.id, customCoverIndex.takeIf { it >= 0 })
            ),
            contentDescription = "Profile Cover",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        )

        // Back Button (Top Left corner of cover image)
        if (onBack != null) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp, top = 14.dp)
                    .size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Shuffle Cover Button (Bottom Right corner of cover image)
        Surface(
            onClick = onShuffleCoverClick,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 14.dp, top = 88.dp)
                .size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle Cover",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Floating Log Out Icon Button (Top Right corner of cover image using R.drawable.logout)
        Surface(
            onClick = onLogoutClick,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 14.dp, top = 14.dp)
                .size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.logout),
                    contentDescription = "Log Out",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // User Info Layout overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Picture with Edit Avatar overlay
            Box(
                modifier = Modifier.size(104.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1E293B) else Color.White)
                        .border(4.dp, MaterialTheme.colorScheme.background, CircleShape)
                        .clickable { onEditAvatarClick() },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(avatarUrl.takeIf { !it.isNullOrBlank() && it != "null" })
                            .decoderFactory(SvgDecoder.Factory())
                            .crossfade(true)
                            .placeholder(R.drawable.khoj_logo)
                            .error(R.drawable.khoj_logo)
                            .fallback(R.drawable.khoj_logo)
                            .build(),
                        contentDescription = "Profile DP",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                    )
                }

                // Edit Avatar Button Badge
                Surface(
                    onClick = onEditAvatarClick,
                    shape = CircleShape,
                    color = SlatePrimary,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Edit Avatar",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Profile name row with edit icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    text = name,
                    fontFamily = activeBengaliFont.value,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Name",
                    tint = SlatePrimary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onEditNameClick() }
                )
            }

            // Email and Stats layout
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (email.isNotBlank()) {
                    Text(
                        text = email,
                        fontFamily = activeBengaliFont.value,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (isLoadingHistory && historyCount == 0) {
                    Box(
                        modifier = Modifier
                            .width(68.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .shimmerEffect()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .background(SlatePrimary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isBn) "$historyCount টি সার্চ" else "$historyCount Searches",
                            fontFamily = activeBengaliFont.value,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlatePrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryTab(
    viewModel: ProfileViewModel,
    factCheckViewModel: FactCheckViewModel,
    isBn: Boolean,
    isDark: Boolean,
    onSelectReport: (FactCheckReport) -> Unit,
    onSelectDeepSearchReport: ((DeepSearchReport) -> Unit)? = null
) {
    val history by viewModel.searchHistory.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingHistory.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var itemPendingDelete by remember { mutableStateOf<UserSearchHistory?>(null) }

    DeleteConfirmationDialog(
        isOpen = itemPendingDelete != null,
        isBn = isBn,
        fontFamily = activeBengaliFont.value,
        onConfirm = {
            itemPendingDelete?.let { item ->
                viewModel.deleteHistoryItem(item)
            }
            itemPendingDelete = null
        },
        onDismiss = {
            itemPendingDelete = null
        }
    )

    if (isLoading) {
        val isDarkTheme = isDark || MaterialTheme.colorScheme.background.luminance() < 0.5f
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(5) {
                HistorySkeletonItem(isDark = isDarkTheme)
            }
        }
    } else if (errorMessage != null) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(text = "Error: $errorMessage", color = MaterialTheme.colorScheme.error)
        }
    } else if (history.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isBn) "কোনো হিস্ট্রি পাওয়া যায়নি।" else "No history found.",
                fontFamily = activeBengaliFont.value,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            history.forEach { item ->
                HistoryItemRow(
                    item = item,
                    factCheckViewModel = factCheckViewModel,
                    isBn = isBn,
                    isDark = isDark,
                    onSelectReport = onSelectReport,
                    onSelectDeepSearchReport = onSelectDeepSearchReport,
                    onDelete = { itemPendingDelete = item }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun HistoryItemRow(
    item: UserSearchHistory,
    factCheckViewModel: FactCheckViewModel,
    isBn: Boolean,
    isDark: Boolean,
    onSelectReport: (FactCheckReport) -> Unit,
    onSelectDeepSearchReport: ((DeepSearchReport) -> Unit)? = null,
    onDelete: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val formattedDate = remember(item.createdAt) {
        try {
            val dateStr = item.createdAt ?: ""
            if (dateStr.isNotBlank()) {
                val cleanString = dateStr.replace("Z", "+0000")
                val formatPattern = if (cleanString.contains(".")) "yyyy-MM-dd'T'HH:mm:ss.SSSZ" else "yyyy-MM-dd'T'HH:mm:ssZ"
                val sdf = SimpleDateFormat(formatPattern, Locale.US)
                val date = sdf.parse(cleanString)
                if (date != null) {
                    val outputSdf = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.US)
                    outputSdf.format(date)
                } else {
                    "Recent"
                }
            } else {
                "Recent"
            }
        } catch (e: Exception) {
            "Recent"
        }
    }

    val typeLabel = remember(item.searchType) {
        val map = mapOf(
            "factcheck" to if (isBn) "ফ্যাক্টচেক" else "Factcheck",
            "deepsearch" to if (isBn) "ডিপ সার্চ" else "Deep Search",
            "deep_search" to if (isBn) "ডিপ সার্চ" else "Deep Search",
            "mukti-corner" to if (isBn) "মুক্তি কর্নার" else "Mukti Corner",
            "mythbusting" to if (isBn) "মিথ বাস্টিং" else "Mythbusting",
            "source-search" to if (isBn) "উৎস সন্ধান" else "Source Search"
        )
        map[item.searchType] ?: item.searchType
    }

    val openReportAction = {
        coroutineScope.launch {
            if (item.searchType == "deepsearch" || item.searchType == "deep_search") {
                val deepRepo = com.example.data.repository.DeepSearchHistoryRepository.getInstance(context)
                val report = if (!item.reportId.isNullOrBlank()) {
                    deepRepo.fetchReportById(item.reportId) ?: deepRepo.getReportById(item.reportId)
                } else null

                if (report != null) {
                    onSelectDeepSearchReport?.invoke(report)
                } else {
                    // Fallback to navigating with query
                    onSelectDeepSearchReport?.invoke(
                        com.example.data.model.DeepSearchReport(
                            query = item.query,
                            reportContent = ""
                        )
                    )
                }
                return@launch
            }

            var foundReport: FactCheckReport? = null
            if (!item.reportId.isNullOrBlank()) {
                // 1. Prioritize local Room database which contains the full markdownReport
                val dbRepo = com.example.data.repository.FactCheckRepository(context)
                val localReport = dbRepo.getReportById(item.reportId)
                if (localReport != null && !localReport.markdownReport.isNullOrBlank()) {
                    foundReport = localReport
                } else {
                    // 2. Check loaded reports in FactCheckViewModel
                    val vmReport = factCheckViewModel.remoteHistoryReports.value.find { it.id == item.reportId }
                    if (vmReport != null && !vmReport.markdownReport.isNullOrBlank() &&
                        (localReport == null || vmReport.markdownReport.length > (localReport.markdownReport?.length ?: 0))) {
                        foundReport = vmReport
                    } else {
                        // 3. Fallback to Supabase remote retrieval mapping or local fallback
                        foundReport = dbRepo.getRemoteHistoryReports(item.userId).find { it.id == item.reportId } ?: localReport
                    }
                }
            }
            if (foundReport != null) {
                onSelectReport(foundReport)
            } else {
                // Generate fresh check for the query
                factCheckViewModel.checkClaim(item.query)
            }
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(SlatePrimary.copy(alpha = 0.1f), CircleShape)
                    .clickable { openReportAction() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = SlatePrimary)
            }

            Spacer(Modifier.width(12.dp))

            // Info Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.query,
                    fontFamily = activeBengaliFont.value,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { openReportAction() }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formattedDate,
                        fontFamily = activeBengaliFont.value,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .background(SlatePrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = typeLabel,
                            fontFamily = activeBengaliFont.value,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlatePrimary
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Action Buttons Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // View button
                IconButton(
                    onClick = { openReportAction() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = "View Report",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                // Share button
                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Khoj Search Query")
                            putExtra(Intent.EXTRA_TEXT, "Query: ${item.query}\nVerify news on Khoj Fact Checker!")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share query"))
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(item.query))
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share query",
                        tint = SlatePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                // Delete button
                IconButton(
                    onClick = { onDelete() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete query",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AnalyticsTab(
    viewModel: ProfileViewModel,
    isBn: Boolean,
    isDark: Boolean
) {
    val analytics by viewModel.analyticsResult.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val analysisError by viewModel.analysisError.collectAsStateWithLifecycle()
    val history by viewModel.searchHistory.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingHistory.collectAsStateWithLifecycle()

    if (isLoading && history.isEmpty()) {
        AnalyticsSkeleton(isDark = isDark)
        return
    }

    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = if (isBn) "অ্যানালিটিক্স তৈরির জন্য যথেষ্ট হিস্ট্রি নেই।" else "Not enough history for analytics.",
                fontFamily = activeBengaliFont.value,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Stats grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isBn) "মোট সার্চ" else "Total Searches",
                        fontFamily = activeBengaliFont.value,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = history.size.toString(),
                        fontFamily = activeBengaliFont.value,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val factCheckCount = history.count { it.searchType == "factcheck" }
                    Text(
                        text = if (isBn) "ফ্যাক্টচেক" else "Fact Check",
                        fontFamily = activeBengaliFont.value,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = factCheckCount.toString(),
                        fontFamily = activeBengaliFont.value,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Type Breakdown
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isBn) "সার্চ ধরন বিশ্লেষণ" else "Search Type Breakdown",
                    fontFamily = activeBengaliFont.value,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))
                val types = listOf("factcheck", "mukti-corner", "mythbusting", "source-search")
                types.forEach { type ->
                    val count = history.count { it.searchType == type }
                    val pct = if (history.isNotEmpty()) (count.toFloat() / history.size) else 0f
                    val label = when (type) {
                        "factcheck" -> if (isBn) "ফ্যাক্টচেক" else "Factcheck"
                        "mukti-corner" -> if (isBn) "মুক্তি কর্নার" else "Mukti Corner"
                        "mythbusting" -> if (isBn) "মিথ বাস্টিং" else "Mythbusting"
                        else -> if (isBn) "উৎস সন্ধান" else "Source Search"
                    }
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, fontFamily = activeBengaliFont.value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                            Text("${count} (${(pct * 100).toInt()}%)", fontFamily = activeBengaliFont.value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SlatePrimary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { pct },
                            color = SlatePrimary,
                            trackColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }

        // AI Habit analysis
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isBn) "AI সার্চ হ্যাবিট বিশ্লেষণ" else "AI Search Habit Analysis",
                        fontFamily = activeBengaliFont.value,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isBn) "আপনার সকল সার্চ কুয়েরি বিশ্লেষণ করে খোঁজ AI বলবে আপনি কোন ধরনের গুজবের সম্মুখীন বেশি হন।" else "Analyzing your searches, Khoj AI detects what type of rumors you encounter most.",
                    fontFamily = activeBengaliFont.value,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                if (isAnalyzing) {
                    AnalyticsHabitAnalyzingSkeleton(isDark = isDark, isBn = isBn)
                } else if (analytics != null) {
                    val res = analytics!!
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AnalyticsCard(
                            title = if (isBn) "প্রধান বিষয়" else "Top Topic",
                            content = res.topic,
                            icon = Icons.Default.TrendingUp,
                            color = Color(0xFF0EA5E9),
                            isDark = isDark
                        )
                        res.riskLevel?.let {
                            val color = when (it.lowercase()) {
                                "high" -> Color.Red
                                "medium" -> Color(0xFFF59E0B)
                                else -> Color(0xFF10B981)
                            }
                            val displayRisk = when (it.lowercase()) {
                                "high" -> if (isBn) "**উচ্চ ঝুঁকি** (High)" else "**High Risk**"
                                "medium" -> if (isBn) "**মাঝারি ঝুঁকি** (Medium)" else "**Medium Risk**"
                                "low" -> if (isBn) "**কম ঝুঁকি** (Low)" else "**Low Risk**"
                                else -> "**$it**"
                            }
                            AnalyticsCard(
                                title = if (isBn) "ঝুঁকির মাত্রা" else "Vulnerability Risk",
                                content = displayRisk,
                                icon = Icons.Default.Warning,
                                color = color,
                                isDark = isDark
                            )
                        }
                        res.insights?.let {
                            AnalyticsCard(
                                title = if (isBn) "AI ইনসাইটস" else "AI Insights",
                                content = it,
                                icon = Icons.Default.Lightbulb,
                                color = Color(0xFF8B5CF6),
                                isDark = isDark
                            )
                        }
                        res.advice?.let {
                            AnalyticsCard(
                                title = if (isBn) "উপদেশ" else "Advice",
                                content = it,
                                icon = Icons.Default.Security,
                                color = Color(0xFF10B981),
                                isDark = isDark
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = { viewModel.analyzeHabits(history) },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                text = if (isBn) "আবার বিশ্লেষণ করুন" else "Analyze Again",
                                fontFamily = activeBengaliFont.value,
                                fontWeight = FontWeight.Bold,
                                color = SlatePrimary
                            )
                        }
                    }
                } else {
                    if (analysisError != null) {
                        Text(
                            text = analysisError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontFamily = activeBengaliFont.value,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Button(
                        onClick = { viewModel.analyzeHabits(history) },
                        colors = ButtonDefaults.buttonColors(containerColor = SlatePrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isBn) "আমার গুজব প্যাটার্ন বিশ্লেষণ করুন" else "Analyze My Rumor Patterns",
                            fontFamily = activeBengaliFont.value,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnalyticsCard(
    title: String,
    content: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isDark: Boolean
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFFFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(color.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    fontFamily = activeBengaliFont.value,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    style = TextStyle(
                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.None
                        )
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.height(8.dp))
            MarkdownText(
                text = content,
                fontToUse = activeBengaliFont.value,
                fontSize = 14,
                lineHeight = 22,
                textColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shimmerColors = if (isDark) {
        listOf(
            Color(0xFF1E293B),
            Color(0xFF334155),
            Color(0xFF1E293B)
        )
    } else {
        listOf(
            Color(0xFFE2E8F0),
            Color(0xFFF1F5F9),
            Color(0xFFE2E8F0)
        )
    }

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnim.value - 300f, y = translateAnim.value - 300f),
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    background(brush)
}

@Composable
fun HistorySkeletonItem(isDark: Boolean) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Indicator placeholder
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .shimmerEffect()
            )

            Spacer(Modifier.width(12.dp))

            // Info Column placeholder
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(15.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(11.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                    Box(
                        modifier = Modifier
                            .width(55.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Action button placeholders
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .shimmerEffect()
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .shimmerEffect()
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .shimmerEffect()
                )
            }
        }
    }
}

@Composable
fun AnalyticsSkeleton(isDark: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 2 Stat Cards skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(2) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .shimmerEffect()
                        )
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .shimmerEffect()
                        )
                    }
                }
            }
        }

        // Search Type Breakdown Card skeleton
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
                repeat(3) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerEffect()
                            )
                            Box(
                                modifier = Modifier
                                    .width(45.dp)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerEffect()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .shimmerEffect()
                        )
                    }
                }
            }
        }

        // AI Search Habit Card skeleton
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .shimmerEffect()
                    )
                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
                Spacer(Modifier.height(4.dp))
                repeat(2) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFFFFFFF)),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .shimmerEffect()
                                )
                                Box(
                                    modifier = Modifier
                                        .width(90.dp)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .shimmerEffect()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerEffect()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnalyticsHabitAnalyzingSkeleton(isDark: Boolean, isBn: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                color = SlatePrimary,
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (isBn) "গুজব প্যাটার্ন বিশ্লেষণ করা হচ্ছে..." else "Analyzing rumor patterns...",
                fontFamily = activeBengaliFont.value,
                fontSize = 13.sp,
                color = SlatePrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
        repeat(2) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFFFFFFF)),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .shimmerEffect()
                        )
                        Box(
                            modifier = Modifier
                                .width(90.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .shimmerEffect()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                }
            }
        }
    }
}
