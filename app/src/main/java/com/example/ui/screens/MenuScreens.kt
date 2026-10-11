package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.*
import com.example.ui.viewmodel.FactCheckViewModel

// ----------------------------------------------------
// UTILS & SHARED COMPOSABLES
// ----------------------------------------------------

@Composable
fun ScreenHeader(
    titleBn: String,
    titleEn: String,
    subtitleBn: String = "",
    subtitleEn: String = "",
    isBn: Boolean,
    fontToUse: FontFamily = FontFamily.Default,
    showGearIcon: Boolean = false,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2A89D1))
            .statusBarsPadding()
            .padding(top = 10.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isBn) titleBn else titleEn,
                        fontFamily = fontToUse,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White
                    )
                    if (showGearIcon) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                val subtitle = if (isBn) subtitleBn else subtitleEn
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        fontFamily = fontToUse,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// 1. ABOUT SCREEN
// ----------------------------------------------------

@Composable
fun AboutScreen(
    viewModel: FactCheckViewModel,
    onBack: () -> Unit
) {
    val renderingLanguage by viewModel.renderingLanguage.collectAsStateWithLifecycle()
    val isBn = renderingLanguage == "bn"

    val teamList = listOf(
        TeamMember(
            nameBn = "মাহাথির আহমেদ তুষার",
            nameEn = "Mahathir Ahmed Tusher",
            roleBn = "প্রতিষ্ঠাতা ও লিড ডেভেলপার",
            roleEn = "Founder & Lead Developer",
            initials = "MT",
            color = Color(0xFF0284C7)
        ),
        TeamMember(
            nameBn = "সাগর চন্দ্র দে",
            nameEn = "Sagar Chandra Dey",
            roleBn = "ডেটাবেজ ব্যবস্থাপক",
            roleEn = "Database Manager",
            initials = "SD",
            color = Color(0xFF0F766E)
        ),
        TeamMember(
            nameBn = "তানিয়া চৈতি",
            nameEn = "Tania Choity",
            roleBn = "লিড রিসার্চার (Data Architecture)",
            roleEn = "Lead Researcher (Data Architecture)",
            initials = "TC",
            color = Color(0xFFB45309)
        ),
        TeamMember(
            nameBn = "আকিফ বিন সাঈদ",
            nameEn = "Akif Bin Sayeed",
            roleBn = "ফিচার ডেভেলপার (মুক্তিযুদ্ধ কর্নার)",
            roleEn = "Feature Developer (Liberation War)",
            initials = "AS",
            color = Color(0xFF4338CA)
        ),
        TeamMember(
            nameBn = "ইঞ্জিঃ আবু কাউছার",
            nameEn = "Engr. Abu Kawsar",
            roleBn = "সিস্টেম ইঞ্জিনিয়ার ও টিম সদস্য",
            roleEn = "Systems Engineer & Team Member",
            initials = "AK",
            color = Color(0xFF6D28D9)
        )
    )

    val bengaliFontPreference by viewModel.bengaliFontPreference.collectAsStateWithLifecycle()
    val fontToUse = if (isBn) {
        if (bengaliFontPreference == "anek") AnekBanglaFamily else TiroBanglaFamily
    } else {
        FontFamily.Default
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(
            titleBn = "খোঁজ সম্পর্কে",
            titleEn = "About Khoj",
            subtitleBn = "AI-চালিত ফ্যাক্টচেকিং প্ল্যাটফর্ম",
            subtitleEn = "AI-Driven Fact-Checking Platform",
            isBn = isBn,
            fontToUse = fontToUse,
            onBack = onBack
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Mission statement
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "আমাদের লক্ষ্য ও দৃষ্টিভঙ্গি" else "Our Mission & Vision",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SlatePrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = if (isBn) {
                            "আমাদের চারপাশে প্রতিদিন অসংখ্য খবর, দাবি আর তথ্য ঘুরে বেড়ায়। এর ভিড়ে কোনটা সত্য আর কোনটা মিথ্যা, তা বুঝে ওঠা আজ অনেক কঠিন। ভুল তথ্যের এই স্রোত শুধু বিভ্রান্তিই ছড়ায় না, সমাজে অবিশ্বাস আর ভয়ের দেয়ালও তৈরি করে। খোঁজ জন্ম নিয়েছে সেই অন্ধকার কাটাতে। খোঁজ মানে শুধু তথ্য খুঁজে বের করা নয়—খোঁজ মানে সত্যের সন্ধান, নির্ভরযোগ্যতার প্রতিশ্রুতি।"
                        } else {
                            "Every day, countless news stories, claims, and pieces of information circulate around us. Amidst this flood, discerning truth from falsehood has become incredibly difficult. This wave of misinformation not only breeds confusion but also builds walls of distrust and fear within society. Khoj was born to pierce through this darkness. Khoj is not just about finding information—it is a quest for truth, a commitment to reliability."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isBn) {
                            "আমরা বিশ্বাস করি, সত্য হলো আলোর মতো; একবার তা উন্মোচিত হলে মিথ্যার ছায়া মিলিয়ে যায়। খোঁজের প্রতিটি যাচাই হলো আমাদের সম্মিলিত আস্থা রক্ষার প্রচেষ্টা। আমরা চাই, বাংলাভাষী মানুষেরা যেন নির্ভরযোগ্য তথ্যের ভিত্তিতে সিদ্ধান্ত নিতে পারে, প্রশ্ন তুলতে পারে, এবং একসাথে একটি সচেতন সমাজ গড়তে পারে।"
                        } else {
                            "We believe that truth is like light; once unveiled, the shadows of falsehood dissolve. Every verification by Khoj is an effort to protect our collective trust. We want Bengali-speaking people to make decisions based on reliable data, ask questions, and build a conscious society together."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // The Creation of Khoj
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "খোঁজের সৃষ্টি" else "The Creation of Khoj",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SlatePrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = if (isBn) {
                            "আমরা দেখেছি কীভাবে ভুল তথ্য আমাদের সমাজে বিভ্রান্তি ছড়াচ্ছে, কীভাবে মানুষ বিভ্রান্ত হচ্ছে। তাই আমরা একসাথে কাজ শুরু করেছি। আমাদের লক্ষ্য ছিল একটি প্ল্যাটফর্ম তৈরি করা যা AI প্রযুক্তি ব্যবহার করে যেকোনো দাবি বা তথ্যের সত্যতা যাচাই করতে পারবে। আমরা বিশ্বাস করি যে প্রযুক্তি মানুষের কল্যাণে ব্যবহার করা উচিত.\n\nখোঁজ আজ শুধু একটি ওয়েবসাইট নয়—এটি একটি আন্দোলন। একটি আন্দোলন সত্যের পক্ষে, নির্ভরযোগ্যতার পক্ষে, এবং বাংলাভাষী মানুষের জ্ঞানের অধিকারের পক্ষে।"
                        } else {
                            "We saw how misinformation was causing confusion in our society and how people were being misled. So, we started working together. Our goal was to create a platform using AI technology to verify the truth of any claim or information. We believe technology should serve humanity.\n\nToday, Khoj is not just a website—it is a movement. A movement for truth, reliability, and the right to knowledge for Bengali speakers."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Team section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isBn) "আমাদের টিম সদস্য" else "Our Team Members",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                teamList.forEach { member ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(member.color, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = member.initials,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = if (isBn) member.nameBn else member.nameEn,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isBn) member.roleBn else member.roleEn,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Promises
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "আমাদের প্রতিশ্রুতিসমূহ" else "Our Promises",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SlatePrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val promises = if (isBn) {
                        listOf(
                            "আমরা শুধুমাত্র বিশ্বাসযোগ্য উৎস থেকে তথ্য সংগ্রহ করব",
                            "আমাদের বিশ্লেষণ হবে নিরপেক্ষ এবং যুক্তি-ভিত্তিক",
                            "আমরা সবসময় আমাদের উৎস উল্লেখ করব",
                            "আমরা ভুল হলে তা স্বীকার করব এবং সংশোধন করব",
                            "আমরা সবসময় বাংলাভাষী মানুষের কল্যাণে কাজ করব"
                        )
                    } else {
                        listOf(
                            "We will only gather information from trustworthy sources.",
                            "Our analysis will be impartial and logic-based.",
                            "We will always cite our sources.",
                            "We will admit and correct our mistakes if we are wrong.",
                            "We will always work for the well-being of Bengali-speaking people."
                        )
                    }

                    promises.forEachIndexed { idx, promise ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ColorTrue,
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = promise,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isBn) {
                            "\"আমরা প্রতিশ্রুতি দিচ্ছি যে খোঁজ সবসময় সত্যের পক্ষে থাকবে। আমাদের প্রতিটি যাচাই আমাদের এই আস্থার পরিচয়বাহী। আমাদের এই প্রতিশ্রুতি শুধু কথার কথা নয়—এটি আমাদের প্রতিটি কাজের ভিত্তি।\""
                        } else {
                            "\"We promise that Khoj will always stand for the truth. Every verification we do represents this trust. This promise is not just words—it is the foundation of everything we do.\""
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        textAlign = TextAlign.Center,
                        color = SlateSecondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Key features
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "আমাদের বৈশিষ্ট্যসমূহ" else "Our Core Features",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SlatePrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    FeatureItem(
                        icon = Icons.Default.FlashOn,
                        titleBn = "তাৎক্ষণিক যাচাই",
                        titleEn = "Instant Verification",
                        descBn = "আমাদের এআই মডেল মুহূর্তের মধ্যে যেকোনো দাবি বিশ্লেষণ করে তার সত্যতা যাচাই করতে সক্ষম।",
                        descEn = "Our AI model is capable of analyzing and verifying any claim in seconds.",
                        isBn = isBn
                    )

                    FeatureItem(
                        icon = Icons.Default.VerifiedUser,
                        titleBn = "নির্ভরযোগ্য উৎস",
                        titleEn = "Reliable Sources",
                        descBn = "আমরা তথ্যের সত্যতা নিশ্চিত করতে নির্ভরযোগ্য নিউজ পোর্টাল এবং সোর্স ব্যবহার করি।",
                        descEn = "We use credible news portals and verified sources to ensure accuracy.",
                        isBn = isBn
                    )

                    FeatureItem(
                        icon = Icons.Default.Devices,
                        titleBn = "সহজ ব্যবহার",
                        titleEn = "Easy to Use",
                        descBn = "সাধারণ ব্যবহারকারীদের কথা মাথায় রেখে আমাদের প্ল্যাটফর্মটি অত্যন্ত সহজভাবে ডিজাইন করা হয়েছে।",
                        descEn = "Designed with everyday users in mind to keep the platform simple and accessible.",
                        isBn = isBn
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Partner Logos ("অর্থায়নে") Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "অর্থায়নে",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 9.5.sp,
                        letterSpacing = 0.3.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.undp_logo),
                        contentDescription = "UNDP",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(20.dp)
                    )
                    Image(
                        painter = painterResource(id = R.drawable.ict_division_logo),
                        contentDescription = "ICT Division",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(20.dp)
                    )
                    Image(
                        painter = painterResource(id = R.drawable.bcc_logo),
                        contentDescription = "BCC",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun FeatureItem(
    icon: ImageVector,
    titleBn: String,
    titleEn: String,
    descBn: String,
    descEn: String,
    isBn: Boolean
) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(SlatePrimary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SlatePrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = if (isBn) titleBn else titleEn,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isBn) descBn else descEn,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

data class TeamMember(
    val nameBn: String,
    val nameEn: String,
    val roleBn: String,
    val roleEn: String,
    val initials: String,
    val color: Color
)

// ----------------------------------------------------
// 2. FAQ SCREEN
// ----------------------------------------------------

@Composable
fun FaqScreen(
    viewModel: FactCheckViewModel,
    onBack: () -> Unit,
    onNavigateToScreen: (AppScreen) -> Unit
) {
    val renderingLanguage by viewModel.renderingLanguage.collectAsStateWithLifecycle()
    val isBn = renderingLanguage == "bn"

    val faqs = listOf(
        FaqItemData(
            qBn = "খোঁজ কী এবং এটি কীভাবে কাজ করে?",
            qEn = "What is Khoj and how does it work?",
            aBn = "খোঁজ একটি কৃত্রিম বুদ্ধিমত্তা-ভিত্তিক ফ্যাক্টচেকিং প্ল্যাটফর্ম। এটি আপনার দেওয়া যেকোনো দাবি, ছবি বা লেখার সত্যতা যাচাই করে নির্ভুল তথ্য প্রদান করে। আমাদের AI মডেল বিভিন্ন উৎস থেকে তথ্য সংগ্রহ করে বিশ্লেষণ করে ফলাফল দেয়।",
            aEn = "Khoj is an artificial intelligence-based fact-checking platform. It verifies the truth of any claim, image, or text you provide and delivers accurate information. Our AI model collects information from various trusted sources, analyzes it, and produces the result."
        ),
        FaqItemData(
            qBn = "ফ্যাক্টচেকিং কতটা নির্ভুল?",
            qEn = "How accurate is the fact-checking?",
            aBn = "আমাদের AI মডেল উচ্চ নির্ভুলতা নিশ্চিত করার জন্য ডিজাইন করা হয়েছে। তবে কোন ফ্যাক্টচেকিং সিস্টেমই ১০০% নির্ভুল নয়।",
            aEn = "Our AI model is designed to ensure high accuracy. However, no fact-checking system is 100% accurate."
        ),
        FaqItemData(
            qBn = "খোঁজ কি বাংলা ছাড়া অন্য কোনো ভাষা সাপোর্ট করে?",
            qEn = "Does Khoj support languages other than Bengali?",
            aBn = "আমাদের সিস্টেম বাংলা এবং ইংরেজি উভয় ভাষায় কাজ করে।",
            aEn = "Our system works in both Bengali and English."
        ),
        FaqItemData(
            qBn = "খোঁজের কোনো ওয়েবসাইট আছে কি?",
            qEn = "Does Khoj have a website?",
            aBn = "হ্যাঁ, খোঁজের যাত্রা শুরু হয়েছিলো ওয়েবসাইটের মাধ্যমেই। এবং আপনি আপনার ডেস্কটপ, মোবাইল সহ নানান ভাবেই সে ওয়েবসাইটে গিয়ে খোঁজ এর একই ফিচার সমূহ ব্যবহার করতে পারবেনঃ https://khoj-ai.bd/",
            aEn = "Yes, Khoj's journey started through its website. You can access the same features of Khoj from your desktop, mobile, or any device at: https://khoj-ai.bd/",
            websiteUrl = "https://khoj-ai.bd/"
        ),
        FaqItemData(
            qBn = "খোঁজ কি কোনো প্রতিষ্ঠানের সাথে যুক্ত বা আর্থিক সহায়তা পায়?",
            qEn = "Is Khoj affiliated with any institution or does it receive financial support?",
            aBn = "খোঁজ সম্প্রতি 'সিভিক টেকনোলজি রিভিউ প্যানেল' (Civic Technology Review Panel) কর্তৃক সফলভাবে মূল্যায়িত হয়ে একটি বিশেষ সিভিক টেক (Civic Tech) অনুদান (Grant) অর্জন করেছে। আমাদের অনুদান প্রদানকারী প্রতিষ্ঠান হচ্ছে যৌথভাবে জাতিসংঘ উন্নয়ন কর্মসূচি (UNDP), বাংলাদেশ কম্পিউটার কাউন্সিল (BCC) এবং তথ্য ও যোগাযোগ প্রযুক্তি বিভাগ (ICT Division)। বিস্তারিত জানতে অর্থায়ন ও স্বচ্ছতা পেইজটি দেখুন।",
            aEn = "Khoj was recently successfully evaluated by the 'Civic Technology Review Panel' and has received a special Civic Tech Grant. Our funding partners are jointly the United Nations Development Programme (UNDP), Bangladesh Computer Council (BCC), and the Information and Communication Technology Division (ICT Division). For details, check the Funding & Transparency page.",
            targetScreen = AppScreen.FUNDING_TRANSPARENCY,
            buttonTextBn = "অর্থায়ন ও স্বচ্ছতা পেজে যান",
            buttonTextEn = "Go to Funding & Transparency Screen"
        ),
        FaqItemData(
            qBn = "খোঁজের কী API সার্ভিস আছে?",
            qEn = "Does Khoj have an API service?",
            aBn = "হ্যাঁ, আমাদের একটি শক্তিশালী API সার্ভিস রয়েছে যা যেকোনো তৃতীয় পক্ষের অ্যাপে ব্যবহার করা সম্ভব। বিস্তারিত জানতে এবং ডকুমেন্টেশন দেখতে আমাদের API সেকশনে যান।",
            aEn = "Yes, we have a robust API service that can be integrated into any third-party app. To learn more and see documentation, visit our API section.",
            websiteUrl = "https://khoj-ai.bd/api-docs",
            buttonTextBn = "API ডকুমেন্টেশন দেখুন",
            buttonTextEn = "View API Documentation"
        ),
        FaqItemData(
            qBn = "মুক্তিযুদ্ধ কর্নার কী?",
            qEn = "What is the Liberation War Corner?",
            aBn = "মুক্তিযুদ্ধ কর্নার বাংলাদেশের মুক্তিযুদ্ধ সম্পর্কিত তথ্য এবং ফ্যাক্টচেকের জন্য একটি বিশেষ বিভাগ। এটি মুক্তিযুদ্ধের ইতিহাস সম্পর্কিত ভুয়া তথ্য চিহ্নিত করে সত্য তথ্য প্রদান করে।",
            aEn = "The Liberation War Corner is a dedicated section for verifying information and claims regarding the Bangladesh Liberation War. It identifies fake news and historical inaccuracies to present the absolute truth."
        ),
        FaqItemData(
            qBn = "ই-গ্রন্থ সম্ভার কী?",
            qEn = "What is the E-Library?",
            aBn = "ই-গ্রন্থ সম্ভার একটি ডিজিটাল লাইব্রেরি যেখানে নির্ভরযোগ্য উৎস থেকে সংগ্রহ করা বই, গবেষণাপত্র এবং নথি পাওয়া যায়। এটি গবেষণা এবং তথ্য যাচাইয়ের জন্য একটি মূল্যবান সম্পদ।",
            aEn = "The E-Library is a digital library consisting of books, research papers, and documents compiled from reliable sources. It is a valuable asset for research and verification. Visit the E-Library page for details.",
            targetScreen = AppScreen.E_LIBRARY,
            buttonTextBn = "ই-গ্রন্থ সম্ভার পেজে যান",
            buttonTextEn = "Go to E-Library Screen"
        ),
        FaqItemData(
            qBn = "আমার ব্যক্তিগত তথ্য কীভাবে সুরক্ষিত?",
            qEn = "How is my personal information secured?",
            aBn = "আমরা আপনার ব্যক্তিগত তথ্য সুরক্ষার জন্য সর্বোচ্চ ব্যবস্থা গ্রহণ করি। আপনার ফ্যাক্টচেক অনুরোধগুলি এনক্রিপ্টেড হয় এবং আমরা আপনার ব্যক্তিগত তথ্য তৃতীয় পক্ষের সাথে শেয়ার করি না। বিস্তারিত জানতে আমাদের গোপনীয়তা নীতি দেখুন।",
            aEn = "We prioritize the security of your private data. Your fact-check requests are encrypted, and we do not share your personal information with third parties. Read our Privacy Policy to learn more.",
            websiteUrl = "https://khoj-ai.bd/privacy-policy",
            buttonTextBn = "গোপনীয়তা নীতি দেখুন",
            buttonTextEn = "View Privacy Policy"
        ),
        FaqItemData(
            qBn = "খোঁজের পেছনের টিমে কারা রয়েছেন?",
            qEn = "Who is in the team behind Khoj?",
            aBn = "খোঁজ একটি দলগত প্রচেষ্টা। আমাদের টিমে রয়েছেন মাহাথির আহমেদ তুষার, সাগর চন্দ্র দে, তানিয়া চৈতি, আকিফ বিন সাঈদ এবং ইঞ্জিঃ আবু কাউছার। আমরা সবাই বাংলাদেশের তথ্য সাক্ষরতা বৃদ্ধির লক্ষ্যে কাজ করছি।",
            aEn = "Khoj is a collaborative effort. Our team consists of Mahathir Ahmed Tusher, Sagar Chandra Dey, Tania Choity, Akif Bin Sayeed, and Engr. Abu Kawsar. Together, we are working to elevate digital and information literacy in Bangladesh."
        )
    )

    val bengaliFontPreference by viewModel.bengaliFontPreference.collectAsStateWithLifecycle()
    val fontToUse = if (isBn) {
        if (bengaliFontPreference == "anek") AnekBanglaFamily else TiroBanglaFamily
    } else {
        FontFamily.Default
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(
            titleBn = "সচরাচর জিজ্ঞাসা (FAQ)",
            titleEn = "Frequently Asked Questions",
            subtitleBn = "খোঁজ সম্পর্কে সাধারণ প্রশ্ন ও উত্তর",
            subtitleEn = "Common Questions about Khoj",
            isBn = isBn,
            fontToUse = fontToUse,
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = SlatePrimary.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LiveHelp,
                            contentDescription = null,
                            tint = SlatePrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = if (isBn) "আমাদের প্ল্যাটফর্মটি নির্ভুল তথ্য সরবরাহে আপনার বিশ্বস্ত সঙ্গী।" else "Our platform is your trusted companion in providing accurate information.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = SlatePrimary
                        )
                    }
                }
            }

            items(faqs) { faq ->
                FaqExpandableCard(
                    faq = faq,
                    isBn = isBn,
                    onNavigateToScreen = onNavigateToScreen
                )
            }
        }
    }
}

data class FaqItemData(
    val qBn: String,
    val qEn: String,
    val aBn: String,
    val aEn: String,
    val targetScreen: AppScreen? = null,
    val websiteUrl: String? = null,
    val buttonTextBn: String? = null,
    val buttonTextEn: String? = null
)

@Composable
fun FaqExpandableCard(
    faq: FaqItemData,
    isBn: Boolean,
    onNavigateToScreen: (AppScreen) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (expanded) 1.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isBn) faq.qBn else faq.qEn,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (expanded) SlatePrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = if (expanded) SlatePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = if (isBn) faq.aBn else faq.aEn,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (faq.targetScreen != null || faq.websiteUrl != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (faq.targetScreen != null) {
                                    onNavigateToScreen(faq.targetScreen)
                                } else if (faq.websiteUrl != null) {
                                    uriHandler.openUri(faq.websiteUrl)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SlatePrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (faq.targetScreen != null) Icons.Default.ArrowForward else Icons.Default.Launch,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBn) (faq.buttonTextBn ?: "দেখুন") else (faq.buttonTextEn ?: "View"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// 3. WRITE US SCREEN
// ----------------------------------------------------

@Composable
fun WriteUsScreen(
    viewModel: FactCheckViewModel,
    onBack: () -> Unit
) {
    val renderingLanguage by viewModel.renderingLanguage.collectAsStateWithLifecycle()
    val isBn = renderingLanguage == "bn"
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val bengaliFontPreference by viewModel.bengaliFontPreference.collectAsStateWithLifecycle()
    val fontToUse = if (isBn) {
        if (bengaliFontPreference == "anek") AnekBanglaFamily else TiroBanglaFamily
    } else {
        FontFamily.Default
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(
            titleBn = "কীভাবে লিখবেন",
            titleEn = "How to Write for Us",
            subtitleBn = "খোঁজ ফ্যাক্টচেকিং গাইড",
            subtitleEn = "Khoj Fact-Checking Guide",
            isBn = isBn,
            fontToUse = fontToUse,
            onBack = onBack
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Intro Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "শুরুর কথা" else "Introduction",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SlatePrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = if (isBn) {
                            "ফ্যাক্টচেকিং একটি গুরুত্বপূর্ণ কাজ যা সমাজে সত্যের প্রচার এবং অসত্য তথ্যের বিরুদ্ধে লড়াই করে। আমাদের সাথে যোগ দিয়ে আপনি এই মহৎ কাজে অংশগ্রহণ করতে পারেন।"
                        } else {
                            "Fact-checking is a critical task that promotes truth in society and fights against false information. By joining us, you can participate in this noble work."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Why is it important
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "কেন ফ্যাক্টচেকিং গুরুত্বপূর্ণ?" else "Why is Fact-Checking Important?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SlatePrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val importanceList = if (isBn) {
                        listOf(
                            "অসত্য তথ্যের বিস্তার রোধ করে",
                            "জনগণকে সঠিক তথ্য প্রদান করে",
                            "গণতন্ত্রকে শক্তিশালী করে",
                            "সামাজিক বিভ্রান্তি কমায়"
                        )
                    } else {
                        listOf(
                            "Prevents the spread of misinformation",
                            "Empowers people with accurate facts",
                            "Strengthens democracy",
                            "Minimizes social confusion"
                        )
                    }

                    importanceList.forEach { item ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = SlatePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Topic Choices
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "ভালো বিষয় নির্বাচনের জন্য" else "Good Topics for Selection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ColorTrue,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val goodTopics = if (isBn) {
                        listOf(
                            "ভাইরাল হওয়া পোস্ট",
                            "রাজনৈতিক দাবি",
                            "স্বাস্থ্য সম্পর্কিত তথ্য",
                            "অর্থনৈতিক দাবি",
                            "বিজ্ঞান সম্পর্কিত তথ্য"
                        )
                    } else {
                        listOf(
                            "Viral social media posts",
                            "Political statements and claims",
                            "Health and medical info",
                            "Economic claims",
                            "Scientific information"
                        )
                    }

                    goodTopics.forEach { topic ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = null,
                                tint = ColorTrue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = topic,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    Text(
                        text = if (isBn) "এড়িয়ে চলুন (Topics to Avoid)" else "Topics to Avoid",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ColorFalse,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val avoidTopics = if (isBn) {
                        listOf(
                            "ব্যক্তিগত মতামত",
                            "ভবিষ্যদ্বাণী",
                            "অতিমাত্রায় জটিল বিষয়",
                            "সংবেদনশীল ব্যক্তিগত তথ্য"
                        )
                    } else {
                        listOf(
                            "Personal opinions and biases",
                            "Future predictions",
                            "Overly complex or subjective matters",
                            "Sensitive personal data"
                        )
                    }

                    avoidTopics.forEach { topic ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.RemoveCircle,
                                contentDescription = null,
                                tint = ColorFalse,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = topic,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Steps of Research
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "গবেষণা ও যাচাইয়ের ধাপসমূহ" else "Steps of Research & Verification",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SlatePrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    ResearchStepItem(
                        step = "1",
                        titleBn = "প্রাথমিক অনুসন্ধান",
                        titleEn = "Initial Investigation",
                        descBn = "বিষয়টি সম্পর্কে মৌলিক তথ্য সংগ্রহ করুন এবং কী ধরনের প্রমাণ প্রয়োজন তা নির্ধারণ করুন।",
                        descEn = "Gather basic details about the subject and decide what kind of evidence is needed.",
                        isBn = isBn
                    )

                    ResearchStepItem(
                        step = "2",
                        titleBn = "উৎস অনুসন্ধান",
                        titleEn = "Source Finding",
                        descBn = "নির্ভরযোগ্য উৎস যেমন সরকারি প্রতিষ্ঠান, স্বীকৃত সংস্থা এবং বিশেষজ্ঞদের মতামত খুঁজে বের করুন। (উদাহরণ: বাংলাদেশ স্বাস্থ্য অধিদপ্তর, বিশ্ব স্বাস্থ্য সংস্থা, চিকিৎসা বিশেষজ্ঞদের সাক্ষাৎকার)।",
                        descEn = "Search for authoritative sources, such as government entities, recognized agencies, and expert opinions (e.g. Bangladesh DGHS, WHO, expert interviews).",
                        isBn = isBn
                    )

                    ResearchStepItem(
                        step = "3",
                        titleBn = "তথ্য যাচাই",
                        titleEn = "Information Verification",
                        descBn = "সংগৃহীত তথ্যের সত্যতা যাচাই করুন এবং বিভিন্ন উৎসের মধ্যে তুলনা করুন।",
                        descEn = "Validate the authenticity of collected details and perform cross-referencing across sources.",
                        isBn = isBn
                    )
                }
            }

            // Structure of Report
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "রিপোর্টের গঠন" else "Structuring a Report",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SlatePrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val structureItems = if (isBn) {
                        listOf(
                            "শিরোনাম" to "সংক্ষিপ্ত এবং আকর্ষণীয় শিরোনাম যা পাঠকের মনোযোগ আকর্ষণ করবে",
                            "দাবি/তথ্য" to "যে দাবি বা তথ্য যাচাই করা হচ্ছে সেটি স্পষ্টভাবে উল্লেখ করুন",
                            "যাচাই প্রক্রিয়া" to "কীভাবে যাচাই করা হয়েছে তার বিস্তারিত বর্ণনা",
                            "ফলাফল" to "যাচাইয়ের চূড়ান্ত ফলাফল (সত্য/মিথ্যা/ভ্রান্তিমূলক)",
                            "উৎসসমূহ" to "ব্যবহৃত তথ্যসূত্রের তালিকা"
                        )
                    } else {
                        listOf(
                            "Title" to "Concise and compelling headline to grab the reader's attention.",
                            "Claim/Fact" to "Clearly state the specific claim or statement under verification.",
                            "Verification Process" to "Describe in detail how the claim was investigated and verified.",
                            "Verdict" to "The final outcome of the verification (True / False / Misleading).",
                            "Sources" to "A list of references and evidence used."
                        )
                    }

                    structureItems.forEach { (label, desc) ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = "• $label",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = SlatePrimary
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Successful Tips & Call To Action
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlatePrimary.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlatePrimary.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = SlatePrimary,
                        modifier = Modifier.size(36.dp)
                    )

                    Text(
                        text = if (isBn) "সফল ফ্যাক্টচেকিংয়ের টিপস:" else "Pro Tips for Success:",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = if (isBn) {
                            "সময়ের সাথে আপডেট রাখুন। নতুন তথ্য পাওয়া গেলে রিপোর্ট আপডেট করুন।"
                        } else {
                            "Keep your content updated over time. If new facts emerge, update the report accordingly."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = if (isBn) "এখনই শুরু করুন!" else "Start Today!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SlatePrimary
                    )

                    Text(
                        text = if (isBn) {
                            "আপনার প্রথম ফ্যাক্টচেকিং রিপোর্ট লিখে আমাদের ইমেইলে পাঠিয়ে দিন। আমাদের টিম এটি পর্যালোচনা করবে।"
                        } else {
                            "Write your first fact-checking report and email it to us. Our team will review it."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:fact@khoj-ai.bd")
                                    putExtra(Intent.EXTRA_SUBJECT, "Fact-Check Report Submission")
                                }
                                try {
                                    context.startActivity(emailIntent)
                                } catch (e: Exception) {
                                    // Fallback: copy to clipboard
                                    clipboardManager.setText(AnnotatedString("fact@khoj-ai.bd"))
                                    Toast.makeText(context, if (isBn) "ইমেইল কপি করা হয়েছে: fact@khoj-ai.bd" else "Email copied to clipboard: fact@khoj-ai.bd", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SlatePrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.MailOutline, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBn) "ইমেইল পাঠান" else "Send Email",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ResearchStepItem(
    step: String,
    titleBn: String,
    titleEn: String,
    descBn: String,
    descEn: String,
    isBn: Boolean
) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(SlatePrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = if (isBn) titleBn else titleEn,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isBn) descBn else descEn,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ----------------------------------------------------
// 4. FUNDING & TRANSPARENCY SCREEN
// ----------------------------------------------------

@Composable
fun FundingTransparencyScreen(
    viewModel: FactCheckViewModel,
    onBack: () -> Unit,
    onNavigateToScreen: (AppScreen) -> Unit
) {
    val renderingLanguage by viewModel.renderingLanguage.collectAsStateWithLifecycle()
    val isBn = renderingLanguage == "bn"
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val bengaliFontPreference by viewModel.bengaliFontPreference.collectAsStateWithLifecycle()
    val fontToUse = if (isBn) {
        if (bengaliFontPreference == "anek") AnekBanglaFamily else TiroBanglaFamily
    } else {
        FontFamily.Default
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(
            titleBn = "অর্থায়ন ও স্বচ্ছতা",
            titleEn = "Funding & Transparency",
            subtitleBn = "আমাদের অর্থায়ন ও কাজের স্বচ্ছতা নীতি",
            subtitleEn = "Our Funding and Transparency Guidelines",
            isBn = isBn,
            fontToUse = fontToUse,
            onBack = onBack
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Intro Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "আমাদের প্রতিশ্রুতি" else "Our Commitment",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SlatePrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = if (isBn) {
                            "খোঁজ (Khoj) বিশ্বাস করে যে, সঠিক ও বস্তুনিষ্ঠ তথ্য পাওয়া প্রতিটি মানুষের মৌলিক অধিকার। আমাদের কাজের স্বচ্ছতা ও জবাবদিহিতা নিশ্চিত করতে আমরা আমাদের অর্থায়নের উৎস সম্পর্কে সম্পূর্ণ উন্মুক্ত।\n\nইন্টারন্যাশনাল ফ্যাক্ট-চেকিং নেটওয়ার্ক (IFCN)-এর স্বচ্ছতার নীতিমালা অনুসরণ করে আমাদের অর্থায়নের বিস্তারিত তথ্য নিচে প্রকাশ করা হলো:"
                        } else {
                            "Khoj believes that obtaining accurate and objective information is a fundamental human right. To ensure the transparency and accountability of our operations, we are entirely open about our sources of funding.\n\nIn alignment with the transparency principles of the International Fact-Checking Network (IFCN), the details of our funding are declared below:"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Current Funding Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "আমাদের বর্তমান অর্থায়ন (Our Funding)" else "Our Current Funding",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SlatePrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        text = if (isBn) {
                            "খোঁজ সম্প্রতি 'সিভিক টেকনোলজি রিভিউ প্যানেল' (Civic Technology Review Panel) কর্তৃক সফলভাবে মূল্যায়িত হয়ে একটি বিশেষ সিভিক টেক (Civic Tech) অনুদান (Grant) অর্জন করেছে। এই অনুদান আমাদের প্রযুক্তিগত উদ্ভাবন এবং ডেডিকেশনের একটি বড় স্বীকৃতি।"
                        } else {
                            "Khoj was recently successfully evaluated by the 'Civic Technology Review Panel' and received a special Civic Tech Grant. This grant is a significant recognition of our technological innovation and dedication."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Funding details callout
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SlatePrimary.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FundingDetailItem(
                                labelBn = "অর্থায়নকারী অংশীদার",
                                labelEn = "Funding Partners",
                                valBn = "জাতিসংঘ উন্নয়ন কর্মসূচি (UNDP), বাংলাদেশ কম্পিউটার কাউন্সিল (BCC) এবং তথ্য ও যোগাযোগ প্রযুক্তি বিভাগ (ICT Division)।",
                                valEn = "United Nations Development Programme (UNDP), Bangladesh Computer Council (BCC), and Information and Communication Technology Division (ICT Division).",
                                isBn = isBn
                            )

                            FundingDetailItem(
                                labelBn = "অনুদানের পরিমাণ",
                                labelEn = "Grant Amount",
                                valBn = "১,৫০,০০০ টাকা (BDT 150,000)",
                                valEn = "BDT 150,000",
                                isBn = isBn
                            )

                            FundingDetailItem(
                                labelBn = "অনুদানের উদ্দেশ্য",
                                labelEn = "Grant Purpose",
                                valBn = "খোঁজের প্রযুক্তিগত উন্নয়ন ত্বরান্বিত করা এবং প্ল্যাটফর্মটিকে অন্তত আগামী এক বছরের জন্য জনসাধারণের জন্য কার্যকর এবং নিরবচ্ছিন্নভাবে চালু রাখা। এটি একটি দীর্ঘমেয়াদী অংশীদারিত্বের সূচনা যা সমাজে ইতিবাচক প্রভাব ফেলতে সাহায্য করবে।",
                                valEn = "To accelerate the technological development of Khoj and keep the platform effective and seamless for the general public for at least the coming year. It marks the beginning of a long-term partnership helping drive positive social impact.",
                                isBn = isBn
                            )
                        }
                    }
                }
            }

            // Editorial Independence Callout
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Gavel,
                            contentDescription = null,
                            tint = ColorTrue
                        )
                        Text(
                            text = if (isBn) "সম্পাদকীয় স্বাধীনতা (Editorial Independence)" else "Editorial Independence",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ColorTrue
                        )
                    }

                    Text(
                        text = if (isBn) {
                            "স্বচ্ছতা এবং নিরপেক্ষতা আমাদের ফ্যাক্ট-চেকিং প্রক্রিয়ার মূল ভিত্তি।\n\n• আমরা স্পষ্টভাবে জানাতে চাই যে, আমাদের অর্থায়নকারী প্রতিষ্ঠানসমূহ (UNDP, BCC, বা ICT Division) খোঁজের কোনো সম্পাদকীয় সিদ্ধান্তে (Editorial Decisions) হস্তক্ষেপ করে না।\n• আমরা কোন দাবিটি (Claim) ফ্যাক্ট-চেক করব তা আমরা স্বাধীনভাবে নির্বাচন করি。\n• আমাদের ফ্যাক্ট-চেক করার পদ্ধতি এবং চূড়ান্ত রায় (সত্য, অসত্য, নাকি অনিশ্চিত) সম্পূর্ণ স্বাধীনভাবে আমাদের নিজস্ব রিসার্চ টিম এবং এআই (AI) মডেল দ্বারা নির্ধারিত হয়।\n• আমাদের দাতা বা অংশীদারদের আমাদের কন্টেন্ট, ফ্যাক্ট-চেকিং প্রক্রিয়া বা পলিসির ওপর কোনো প্রকার প্রভাব খাটানোর সুযোগ নেই।"
                        } else {
                            "Transparency and impartiality are the cornerstones of our fact-checking process.\n\n• We explicitly state that our funding organizations (UNDP, BCC, or ICT Division) do not interfere with Khoj's editorial decisions.\n• We independently select which claims to fact-check.\n• Our fact-checking methodology and final verdicts (True, False, or Uncertain) are determined entirely independently by our research team and AI models.\n• Our donors or partners do not have any influence over our content, processes, or policies."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Sustainability Commitment
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "আমাদের ভবিষ্যৎ টেকসই পরিকল্পনা" else "Our Sustainability Commitment",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SlatePrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = if (isBn) {
                            "বর্তমানে খোঁজ একটি অনুদান-সমর্থিত প্রজেক্ট হিসেবে পরিচালিত হলেও, ভবিষ্যতে প্ল্যাটফর্মটিকে টেকসই করার জন্য আমাদের একটি শক্তিশালী API সার্ভিস রয়েছে। থার্ড-পার্টি প্ল্যাটফর্মগুলো আমাদের API সাবস্ক্রাইব করে ব্যবহার করতে পারবে, যা আমাদের অপারেশনাল খরচ মেটাতে সাহায্য করবে। বিস্তারিত জানতে আমাদের FAQ সেকশন দেখতে পারেন।"
                        } else {
                            "Although Khoj is currently operating as a grant-supported project, we maintain a robust API service to ensure the platform's long-term sustainability. Third-party platforms can subscribe to our API, helping cover our ongoing operational costs. For details, you may check our FAQ Section."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { onNavigateToScreen(AppScreen.FAQ) },
                        colors = ButtonDefaults.buttonColors(containerColor = SlatePrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBn) "সচরাচর জিজ্ঞাসা (FAQ) দেখুন" else "View FAQ Section",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Footer Contact Callout
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateSecondary.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isBn) {
                            "তথ্য যাচাই এবং আমাদের পলিসি সম্পর্কে আরও জানতে চাইলে আমাদের সাথে যোগাযোগ করুন:"
                        } else {
                            "To learn more about our fact-checking and policies, feel free to contact us:"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "fact@khoj-ai.bd",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = SlatePrimary
                        ),
                        modifier = Modifier
                            .clickable {
                                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:fact@khoj-ai.bd")
                                }
                                try {
                                    context.startActivity(emailIntent)
                                } catch (e: Exception) {
                                    clipboardManager.setText(AnnotatedString("fact@khoj-ai.bd"))
                                    Toast.makeText(context, if (isBn) "ইমেইল কপি করা হয়েছে: fact@khoj-ai.bd" else "Email copied to clipboard: fact@khoj-ai.bd", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun FundingDetailItem(
    labelBn: String,
    labelEn: String,
    valBn: String,
    valEn: String,
    isBn: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (isBn) labelBn else labelEn,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = SlatePrimary
        )
        Text(
            text = if (isBn) valBn else valEn,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ----------------------------------------------------
// 5. E-LIBRARY SCREEN
// ----------------------------------------------------

@Composable
fun ELibraryScreen(
    viewModel: FactCheckViewModel,
    onBack: () -> Unit
) {
    val renderingLanguage by viewModel.renderingLanguage.collectAsStateWithLifecycle()
    val isBn = renderingLanguage == "bn"
    val uriHandler = LocalUriHandler.current

    val allBooks = listOf(
        BookData(
            titleBn = "ফ্যাক্ট চেকিং ও ভেরিফিকেশন হ্যান্ডবুক",
            titleEn = "Fact Checking & Verification Handbook",
            authorBn = "কদরুদ্দীন শিশির",
            authorEn = "Kadiruddin Shishir",
            descBn = "বাংলাদেশে ফ্যাক্ট-চেকিং বিষয়ে প্রথম পূর্ণাঙ্গ গ্রন্থ হিসেবে কদরুদ্দীন শিশিরের এই বই নিঃসন্দেহে একটি মাইলফলক।",
            descEn = "As the first comprehensive book on fact-checking in Bangladesh, this book by Kadiruddin Shishir is undoubtedly a milestone.",
            link = "https://drive.google.com",
            isRecommend = false
        ),
        BookData(
            titleBn = "অলৌকিক নয়, লৌকিক — প্রথম খণ্ড",
            titleEn = "Oloukik Noy, Loukik (Part 1)",
            authorBn = "প্রবীর ঘোষ",
            authorEn = "Prabir Ghosh",
            descBn = "প্রবীর ঘোষের এই বই বাংলায় যুক্তিবাদী আন্দোলনের মাইলফলক। এখানে তিনি সরাসরি নানা অলৌকিক ঘটনা বৈজ্ঞানিক দৃষ্টিতে পরীক্ষা করেছেন।",
            descEn = "Prabir Ghosh's masterpiece is a milestone in the Bengali rationalist movement, directly testing supernatural claims from a scientific perspective.",
            link = "https://drive.google.com",
            isRecommend = false
        ),
        BookData(
            titleBn = "অলৌকিক নয়, লৌকিক — দ্বিতীয় খণ্ড",
            titleEn = "Oloukik Noy, Loukik (Part 2)",
            authorBn = "প্রবীর ঘোষ",
            authorEn = "Prabir Ghosh",
            descBn = "প্রথম খণ্ড পাঠকদের দৃষ্টি খুলে দিয়েছিল, কিন্তু দ্বিতীয় খণ্ডে লেখক আরও সুসংবদ্ধভাবে বিভিন্ন কেস স্টাডি উপস্থাপন করেন।",
            descEn = "While the first part opened readers' eyes, the second volume presents case studies in an even more structured manner.",
            link = "https://drive.google.com",
            isRecommend = false
        ),
        BookData(
            titleBn = "ত্রিশ লক্ষ শহিদ বাহুল্য নাকি বাস্তবতা",
            titleEn = "Thirty Lakh Martyrs: Hyperbole or Reality",
            authorBn = "আরিফ রহমান",
            authorEn = "Arif Rahman",
            descBn = "বাঙালি জাতির সবচেয়ে বড় গর্বের ফসল মুক্তিযুদ্ধ আর এই মুক্তিযুদ্ধের ইতিহাসে সবচেয়ে বেশি বিতর্ক সম্ভবত শহিদের সংখ্যা নিয়ে। এই বইটিতে প্রচুর রেফারেন্স সংকলন করা হয়েছে।",
            descEn = "The Liberation War is the proudest achievement of the Bengali nation, yet its history holds disputes, especially regarding the number of martyrs. This book compiles extensive references and evidence.",
            link = "https://drive.google.com",
            isRecommend = false
        ),
        BookData(
            titleBn = "Calling Bullshit: The Art of Skepticism in a Data-Driven World",
            titleEn = "Calling Bullshit: The Art of Skepticism in a Data-Driven World",
            authorBn = "Carl T. Bergstrom & Jevin D. West",
            authorEn = "Carl T. Bergstrom & Jevin D. West",
            descBn = "আজকের পৃথিবীতে সবচেয়ে বড় সমস্যা হচ্ছে ডেটা ও গ্রাফকে ব্যবহার করে মানুষকে ভুল পথে চালানো। এই বই শেখায়, কিভাবে \"অর্থবহ দেখালেও মিথ্যা\" ডেটা চিহ্নিত করা যায়।",
            descEn = "One of the biggest challenges today is the manipulation of data and graphs. This book teaches how to identify \"meaningful yet false\" data.",
            link = "https://drive.google.com",
            isRecommend = false
        ),
        BookData(
            titleBn = "May Contain Lies",
            titleEn = "May Contain Lies",
            authorBn = "Alex Edmans",
            authorEn = "Alex Edmans",
            descBn = "Edmans গবেষণায় দেখিয়েছেন—অনেক সময় বৈজ্ঞানিক গবেষণা বা সংবাদ নিরপেক্ষভাবে উপস্থাপন করা হয় না।",
            descEn = "Edmans shows how scientific research or news is often presented with bias and lacks neutrality.",
            link = "https://drive.google.com",
            isRecommend = false
        ),
        BookData(
            titleBn = "The Death of Expertise",
            titleEn = "The Death of Expertise",
            authorBn = "Tom Nichols",
            authorEn = "Tom Nichols",
            descBn = "Tom Nichols দেখিয়েছেন, আধুনিক সমাজে বিশেষজ্ঞদের মতামতকে সাধারণ মানুষ প্রায়ই উপেক্ষা করে। ইন্টারনেট যুগে সবাই মনে করে তারা সব জানে।",
            descEn = "Nichols shows how modern society often dismisses expert consensus, especially in the internet era where everyone feels they are experts.",
            link = "https://drive.google.com",
            isRecommend = false
        ),
        BookData(
            titleBn = "The Misinformation Age: How False Beliefs Spread",
            titleEn = "The Misinformation Age: How False Beliefs Spread",
            authorBn = "Cailin O 'Connor & James Owen Weatherall",
            authorEn = "Cailin O 'Connor & James Owen Weatherall",
            descBn = "এই বইটি তত্ত্বভিত্তিক—তবে অত্যন্ত কার্যকর। লেখকদ্বয় দেখিয়েছেন, অসত্য বিশ্বাস কিভাবে সমাজে নেটওয়ার্কের মাধ্যমে ছড়িয়ে পড়ে।",
            descEn = "A highly theoretical yet practical book showing how false beliefs spread through social networks.",
            link = "https://drive.google.com",
            isRecommend = false
        ),
        BookData(
            titleBn = "The Half-Life of Facts",
            titleEn = "The Half-Life of Facts",
            authorBn = "Samuel Arbesman",
            authorEn = "Samuel Arbesman",
            descBn = "তথ্য স্থায়ী নয়। বিজ্ঞানের অনেক সত্য সময়ের সাথে বদলে যায়। Arbesman এই বইয়ে ব্যাখ্যা করেছেন যে তথ্যেরও একধরনের \"আধা-জীবন\" আছে।",
            descEn = "Facts are not static. Much of what is considered scientific truth changes over time. Arbesman explains the \"half-life\" of facts.",
            link = "https://drive.google.com",
            isRecommend = false
        ),
        BookData(
            titleBn = "Trust Me, I 'm Lying: Confessions of a Media Manipulator",
            titleEn = "Trust Me, I 'm Lying: Confessions of a Media Manipulator",
            authorBn = "Ryan Holiday",
            authorEn = "Ryan Holiday",
            descBn = "এই বইটি একেবারে ভেতরের দিক থেকে লেখা। লেখক নিজেই মিডিয়া ম্যানিপুলেটর হিসেবে কাজ করেছেন—তার স্বীকারোক্তি এই বই।",
            descEn = "Written from an insider's perspective, this contains confessions from Ryan Holiday on how the media can be easily manipulated.",
            link = "https://drive.google.com",
            isRecommend = false
        ),
        // Purchases
        BookData(
            titleBn = "ভূত এক্সপোজড – বিজ্ঞানের চোখে ভূত",
            titleEn = "Ghost Exposed: Ghosts through the Eyes of Science",
            authorBn = "ব্যাঙের ছাতার বিজ্ঞান গ্রুপের এক দল লেখক",
            authorEn = "Authors of Bangershatar Biggan",
            descBn = "ভূত, জ্বিন, কালো জাদু, টেলিপ্যাথি কিংবা টেলিকাইনেসিস—এসবের পক্ষে আজও একটিও বৈজ্ঞানিক প্রমাণ নেই। এই বইয়ে ভৌতিক ঘটনার বিজ্ঞানভিত্তিক ব্যাখ্যা দেওয়া হয়েছে।",
            descEn = "There is still not a single shred of scientific evidence in favor of ghosts, djinns, black magic, telepathy, or telekinesis. This book offers scientific explanations of paranormal phenomena.",
            link = "https://www.rokomari.com",
            isRecommend = true
        ),
        BookData(
            titleBn = "অ-মীমাংসিত রহস্য",
            titleEn = "Unsolved Mysteries",
            authorBn = "জহিরুল ইসলাম",
            authorEn = "Zahirul Islam",
            descBn = "এখানে রহস্যময় কয়েকটি ঘটনা উপস্থাপন করা হয়েছে, এবং সেগুলাের বৈজ্ঞানিক ব্যাখ্যা দেওয়ার চেষ্টা করা হয়েছে। ১৭টি ভিন্ন ভিন্ন রহস্যের ব্যাখ্যা আছে এখানে।",
            descEn = "This book presents several mysterious events and attempts to provide logical, scientific explanations for them, covering 17 distinct mysteries.",
            link = "https://www.rokomari.com",
            isRecommend = true
        )
    )

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0: Free books, 1: Recommended

    val filteredBooks = allBooks.filter { book ->
        val matchesSearch = (if (isBn) book.titleBn else book.titleEn).contains(searchQuery, ignoreCase = true) ||
                (if (isBn) book.authorBn else book.authorEn).contains(searchQuery, ignoreCase = true)
        val matchesTab = if (selectedTab == 0) !book.isRecommend else book.isRecommend
        matchesSearch && matchesTab
    }

    val bengaliFontPreference by viewModel.bengaliFontPreference.collectAsStateWithLifecycle()
    val fontToUse = if (isBn) {
        if (bengaliFontPreference == "anek") AnekBanglaFamily else TiroBanglaFamily
    } else {
        FontFamily.Default
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(
            titleBn = "ই-গ্রন্থ সম্ভার",
            titleEn = "E-Library",
            subtitleBn = "ফ্যাক্ট চেকিং ও যুক্তিবাদী সাহিত্যের বিশাল সম্ভার",
            subtitleEn = "Repository of Fact-Checking & Rationalist Literature",
            isBn = isBn,
            fontToUse = fontToUse,
            onBack = onBack
        )

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(if (isBn) "বই বা লেখকের নাম দিয়ে খুঁজুন..." else "Search by book or author...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SlatePrimary,
                cursorColor = SlatePrimary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = SlatePrimary,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(if (isBn) "বিনামূল্যে ডাউনলোড করুন" else "Free Downloads", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(if (isBn) "প্রস্তাবিত সংগ্ৰহ" else "Recommended Purchases", fontWeight = FontWeight.Bold) }
            )
        }

        if (filteredBooks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = if (isBn) "কোনো বই পাওয়া যায়নি!" else "No books found!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredBooks) { book ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Custom colorful book thumbnail container
                            Box(
                                modifier = Modifier
                                    .size(width = 72.dp, height = 100.dp)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(SlatePrimary, SlateSecondary)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Book,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (book.isRecommend) "BUY" else "PDF",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isBn) book.titleBn else book.titleEn,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isBn) "লেখক: ${book.authorBn}" else "Author: ${book.authorEn}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = SlatePrimary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                Text(
                                    text = if (isBn) book.descBn else book.descEn,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = { uriHandler.openUri(book.link) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (book.isRecommend) Color(0xFFF59E0B) else SlatePrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Icon(
                                        imageVector = if (book.isRecommend) Icons.Default.ShoppingCart else Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (book.isRecommend) {
                                            if (isBn) "রকমারি থেকে কিনুন" else "Buy on Rokomari"
                                        } else {
                                            if (isBn) "ডাউনলোড লিঙ্ক" else "Download Link"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
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

data class BookData(
    val titleBn: String,
    val titleEn: String,
    val authorBn: String,
    val authorEn: String,
    val descBn: String,
    val descEn: String,
    val link: String,
    val isRecommend: Boolean
)
