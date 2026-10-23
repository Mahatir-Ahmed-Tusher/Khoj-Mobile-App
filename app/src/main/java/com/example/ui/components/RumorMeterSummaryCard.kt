package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.getString
import com.example.ui.theme.SlatePrimary
import com.example.ui.viewmodel.RumorMeterUiState
import com.example.ui.viewmodel.RumorMeterViewModel
import com.example.utils.BengaliNumberUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Horizontally stretched Rumor Meter summary card designed for the Home Feed.
 * Conforms strictly to Indic typography rules and dark/light responsive themes.
 */
@Composable
fun RumorMeterSummaryCard(
    isBn: Boolean,
    onClick: () -> Unit,
    onSeeMoreClick: () -> Unit = onClick,
    viewModel: RumorMeterViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Extract current metrics
    val meterData = when (val s = uiState) {
        is RumorMeterUiState.Success -> s.meterData
        is RumorMeterUiState.Error -> s.fallbackData ?: com.example.data.model.RumorMeterData()
        else -> com.example.data.model.RumorMeterData()
    }

    val score = meterData.meter.score.coerceIn(0, 100)
    val velocity = meterData.meter.dailyVelocity

    // Threat level definitions
    val threatLevel = when {
        score > 75 -> "HIGH"
        score > 40 -> "MODERATE"
        else -> "NORMAL"
    }

    val statusBadgeText = when (threatLevel) {
        "HIGH" -> getString("rumor_meter_high", isBn)
        "MODERATE" -> getString("rumor_meter_moderate", isBn)
        else -> getString("rumor_meter_normal", isBn)
    }

    val statusSubtitle = when (threatLevel) {
        "HIGH" -> getString("rumor_meter_status_high", isBn)
        "MODERATE" -> getString("rumor_meter_status_moderate", isBn)
        else -> getString("rumor_meter_status_low", isBn)
    }

    val statusColor = when (threatLevel) {
        "HIGH" -> Color(0xFFEF4444)
        "MODERATE" -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    // Pulse animation for the live dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // ── Section Header Row ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_rumour_meter),
                    contentDescription = null,
                    tint = if (isDark) Color(0xFFF1F5F9) else Color(0xFF1E293B),
                    modifier = Modifier.size(20.dp)
                )

                Text(
                    text = getString("rumor_meter_title", isBn),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.None
                        ),
                        lineHeight = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Live green dot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = pulseAlpha))
                    )
                    Text(
                        text = getString("rumor_meter_live", isBn),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        color = Color(0xFF10B981)
                    )
                }
            }

            // "See All" button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onSeeMoreClick() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (isBn) "বিস্তারিত" else "More",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.None
                        )
                    ),
                    color = SlatePrimary
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = SlatePrimary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        // ── Horizontally Stretched Main Card ─────────────────────────────────
        // Dark theme: solid Color(0xFF0D0D0D) with border Color(0xFF333333)
        // Light theme: pure white #FFFFFF
        val cardBackground = if (isDark) {
            Color(0xFF0D0D0D)
        } else {
            Color(0xFFFFFFFF)
        }

        val cardBorderColor = if (isDark) {
            Color(0xFF333333)
        } else {
            Color(0xFFE2E8F0).copy(alpha = 0.8f)
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, cardBorderColor, RoundedCornerShape(18.dp))
                .clickable { onClick() },
            shape = RoundedCornerShape(18.dp),
            color = cardBackground,
            tonalElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // ── Left Column: Gauge Arc with Needle and Score ────────
                    Box(
                        modifier = Modifier
                            .size(width = 140.dp, height = 110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SpeedometerGaugeArc(
                            score = score,
                            isDark = isDark,
                            modifier = Modifier.matchParentSize()
                        )

                        // Center score number + "/100"
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(top = 18.dp)
                        ) {
                            Text(
                                text = BengaliNumberUtils.formatNumber(score, isBn),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 26.sp,
                                    platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.None
                                    ),
                                    lineHeight = 30.sp
                                ),
                                color = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E)
                            )

                            Text(
                                text = if (isBn) "/১০০" else "/100",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                    platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.None
                                    )
                                ),
                                color = if (isDark) Color.White.copy(alpha = 0.60f) else Color.Black.copy(alpha = 0.55f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // ── Right Column: Threat Pill, Velocity, Status Row ─────
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Threat Pill Badge
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(statusColor.copy(alpha = if (isDark) 0.15f else 0.12f))
                                .border(1.dp, statusColor.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = statusBadgeText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.None
                                    )
                                ),
                                color = if (isDark) statusColor else statusColor.copy(alpha = 0.95f)
                            )
                        }

                        // Daily Velocity Row
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = getString("rumor_meter_velocity_label", isBn),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    ),
                                    color = if (isDark) Color.White.copy(alpha = 0.70f) else Color(0xFF475569)
                                )

                                Text(
                                    text = getString("rumor_meter_velocity_period", isBn),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    ),
                                    color = if (isDark) Color(0xFF2DD4BF).copy(alpha = 0.90f) else Color(0xFF0F766E).copy(alpha = 0.85f)
                                )
                            }

                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = BengaliNumberUtils.formatNumber(velocity, isBn, decimals = 1),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 22.sp,
                                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        ),
                                        lineHeight = 26.sp
                                    ),
                                    color = if (isDark) Color.White else Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = getString("rumor_meter_per_day", isBn),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    ),
                                    color = if (isDark) Color.White.copy(alpha = 0.75f) else Color(0xFF334155),
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }

                        // Pulse Wave + Status Text + Forward Chevron
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                PulseWaveIndicator(
                                    tint = statusColor,
                                    modifier = Modifier.size(width = 16.dp, height = 10.dp)
                                )
                                Text(
                                    text = statusSubtitle,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(includeFontPadding = true),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    ),
                                    color = if (isDark) Color.White.copy(alpha = 0.70f) else Color(0xFF475569),
                                    maxLines = 1
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = if (isDark) Color.White.copy(alpha = 0.45f) else Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom Canvas drawing the 220-degree speedometer arc, tick marks,
 * and animated needle indicator.
 */
@Composable
fun SpeedometerGaugeArc(
    score: Int,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedScore by animateFloatAsState(
        targetValue = score.toFloat(),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "gauge_needle_anim"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val strokeWidth = 8.dp.toPx()
        val centerX = width / 2f
        val centerY = height * 0.70f
        val radius = (width.coerceAtMost(height * 1.5f) / 2f) - strokeWidth

        // Arc starts at 150 degrees and sweeps 240 degrees (from bottom-left to bottom-right)
        val startAngle = 150f
        val sweepAngle = 240f

        val arcTopLeft = Offset(centerX - radius, centerY - radius)
        val arcSize = Size(radius * 2, radius * 2)

        // 1. Draw subtle background track
        val trackColor = if (isDark) Color(0xFF333333) else Color(0xFFE2E8F0)
        drawArc(
            color = trackColor,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // 2. Draw colored gradient arc
        val gradientBrush = Brush.sweepGradient(
            0.0f to Color(0xFF10B981),  // Green
            0.3f to Color(0xFFFBBF24),  // Yellow
            0.5f to Color(0xFFF97316),  // Orange
            0.7f to Color(0xFFEF4444),  // Red
            1.0f to Color(0xFFEF4444),
            center = Offset(centerX, centerY)
        )

        drawArc(
            brush = gradientBrush,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // 3. Draw radial tick marks
        val tickCount = 5 // 0, 25, 50, 75, 100
        val tickColor = if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.30f)
        for (i in 0 until tickCount) {
            val fraction = i.toFloat() / (tickCount - 1)
            val angleDeg = startAngle + (fraction * sweepAngle)
            val angleRad = (angleDeg * PI / 180f).toFloat()

            val innerR = radius - strokeWidth - 2.dp.toPx()
            val outerR = radius - 1.dp.toPx()

            val p1 = Offset(
                x = centerX + innerR * cos(angleRad),
                y = centerY + innerR * sin(angleRad)
            )
            val p2 = Offset(
                x = centerX + outerR * cos(angleRad),
                y = centerY + outerR * sin(angleRad)
            )

            drawLine(
                color = tickColor,
                start = p1,
                end = p2,
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 4. Draw Needle
        val needleFraction = (animatedScore / 100f).coerceIn(0f, 1f)
        val needleAngleDeg = startAngle + (needleFraction * sweepAngle)
        val needleAngleRad = (needleAngleDeg * PI / 180f).toFloat()
        val needleLength = radius * 0.75f

        val needleTip = Offset(
            x = centerX + needleLength * cos(needleAngleRad),
            y = centerY + needleLength * sin(needleAngleRad)
        )

        // Needle perpendicular offsets for triangle base
        val baseWidth = 3.dp.toPx()
        val perpAngle = needleAngleRad + (PI / 2).toFloat()
        val baseLeft = Offset(
            x = centerX + baseWidth * cos(perpAngle),
            y = centerY + baseWidth * sin(perpAngle)
        )
        val baseRight = Offset(
            x = centerX - baseWidth * cos(perpAngle),
            y = centerY - baseWidth * sin(perpAngle)
        )

        val needlePath = Path().apply {
            moveTo(needleTip.x, needleTip.y)
            lineTo(baseLeft.x, baseLeft.y)
            lineTo(baseRight.x, baseRight.y)
            close()
        }

        val needleColor = if (isDark) Color(0xFF67E8F9) else Color(0xFF0284C7)
        drawPath(path = needlePath, color = needleColor)

        // Center hub circle
        drawCircle(
            color = needleColor,
            radius = 4.dp.toPx(),
            center = Offset(centerX, centerY)
        )
        drawCircle(
            color = if (isDark) Color(0xFF0D0D0D) else Color.White,
            radius = 2.dp.toPx(),
            center = Offset(centerX, centerY)
        )
    }
}

/**
 * Small heartbeat / ECG pulse wave line graphic.
 */
@Composable
fun PulseWaveIndicator(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        val path = Path().apply {
            moveTo(0f, midY)
            lineTo(w * 0.25f, midY)
            lineTo(w * 0.40f, 0f)
            lineTo(w * 0.60f, h)
            lineTo(w * 0.75f, midY)
            lineTo(w, midY)
        }

        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
