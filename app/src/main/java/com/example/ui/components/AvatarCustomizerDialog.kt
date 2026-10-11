package com.example.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.example.R
import com.example.ui.theme.AnekBanglaFamily
import com.example.ui.theme.SlatePrimary
import java.net.URLEncoder
import java.util.UUID

val AVAILABLE_DICEBEAR_STYLES = listOf(
    "lorelei" to "Lorelei",
    "pixel-art" to "Pixel Art",
    "bottts" to "Bottts",
    "adventurer" to "Adventurer",
    "avataaars" to "Avataaars",
    "fun-emoji" to "Fun Emoji",
    "big-smile" to "Big Smile",
    "micah" to "Micah",
    "personas" to "Personas",
    "shapes" to "Shapes",
    "croodles" to "Croodles",
    "identicon" to "Identicon"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarCustomizerDialog(
    isBn: Boolean,
    currentAvatarUrl: String?,
    hasCustomAvatar: Boolean,
    userEmail: String?,
    onDismiss: () -> Unit,
    onFinalizeAvatar: (avatarUrl: String) -> Unit,
    onDeleteAvatar: () -> Unit
) {
    val context = LocalContext.current
    var selectedStyle by remember { mutableStateOf("lorelei") }
    var seed by remember { mutableStateOf((userEmail?.substringBefore("@") ?: "user") + "-" + UUID.randomUUID().toString().take(5)) }
    var flip by remember { mutableStateOf(false) }

    // Helper to generate URL
    fun generateDiceBearUrl(styleName: String, seedVal: String, isFlipped: Boolean): String {
        val encodedSeed = try {
            URLEncoder.encode(seedVal.ifBlank { "user" }, "UTF-8")
        } catch (e: Exception) {
            "user"
        }
        val flipParam = if (isFlipped) "&flip=horizontal" else ""
        return "https://api.dicebear.com/10.x/$styleName/svg?seed=$encodedSeed$flipParam"
    }

    var currentPreviewUrl by remember {
        mutableStateOf(
            if (!currentAvatarUrl.isNullOrBlank() && currentAvatarUrl.contains("dicebear.com")) {
                currentAvatarUrl
            } else {
                generateDiceBearUrl(selectedStyle, seed, flip)
            }
        )
    }

    // Function to shuffle seed
    fun shuffleSeed() {
        seed = UUID.randomUUID().toString().take(7)
        currentPreviewUrl = generateDiceBearUrl(selectedStyle, seed, flip)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = SlatePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isBn) "অবতার কাস্টমাইজ করুন" else "Avatar Customizer",
                        fontFamily = AnekBanglaFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Preview Box
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(3.dp, SlatePrimary.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(currentPreviewUrl)
                            .decoderFactory(SvgDecoder.Factory())
                            .crossfade(true)
                            .placeholder(R.drawable.khoj_logo)
                            .error(R.drawable.khoj_logo)
                            .build(),
                        contentDescription = "Avatar Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Shuffle Controls Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { shuffleSeed() },
                        colors = ButtonDefaults.buttonColors(containerColor = SlatePrimary),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle Avatar",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isBn) "শাফেল করুন 🎲" else "Shuffle 🎲",
                            fontFamily = AnekBanglaFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    FilterChip(
                        selected = flip,
                        onClick = {
                            flip = !flip
                            currentPreviewUrl = generateDiceBearUrl(selectedStyle, seed, flip)
                        },
                        label = {
                            Text(
                                if (isBn) "ফ্লিপ" else "Flip",
                                fontFamily = AnekBanglaFamily,
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Style Selector Header
                Text(
                    text = if (isBn) "স্টাইল নির্বাচন করুন:" else "Select Style:",
                    fontFamily = AnekBanglaFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable Style Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AVAILABLE_DICEBEAR_STYLES) { (styleKey, styleLabel) ->
                        val isSelected = selectedStyle == styleKey
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedStyle = styleKey
                                currentPreviewUrl = generateDiceBearUrl(selectedStyle, seed, flip)
                            },
                            label = {
                                Text(
                                    text = styleLabel,
                                    fontFamily = AnekBanglaFamily,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SlatePrimary.copy(alpha = 0.2f),
                                selectedLabelColor = SlatePrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom Seed Input
                OutlinedTextField(
                    value = seed,
                    onValueChange = {
                        seed = it
                        currentPreviewUrl = generateDiceBearUrl(selectedStyle, seed, flip)
                    },
                    label = { Text(if (isBn) "কাস্টম সিড" else "Custom Seed", fontFamily = AnekBanglaFamily) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onFinalizeAvatar(currentPreviewUrl)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SlatePrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    if (isBn) "অবতার সেট করুন" else "Finalize Avatar",
                    fontFamily = AnekBanglaFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // If custom avatar is set, show Delete / Reset Option
                if (hasCustomAvatar) {
                    TextButton(
                        onClick = onDeleteAvatar,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Avatar",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (isBn) "মুছে ফেলুন" else "Delete Avatar",
                            fontFamily = AnekBanglaFamily,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                TextButton(onClick = onDismiss) {
                    Text(if (isBn) "বাতিল" else "Cancel", fontFamily = AnekBanglaFamily)
                }
            }
        }
    )
}
