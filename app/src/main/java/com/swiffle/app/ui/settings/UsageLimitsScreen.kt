package com.swiffle.app.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiffle.app.ai.ModelUsageStats
import com.swiffle.app.ai.SwiffleAiConfig
import com.swiffle.app.theme.GoogleSans
import com.swiffle.app.theme.SwiffleMotion
import com.swiffle.app.theme.ThemeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat

/**
 * Live Usage, Rate Limits (RPM / TPM / RPD), and Quota Telemetry Screen.
 * Reads real token, request, sliding-window rate, and cooldown metrics from SwiffleAiConfig.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageLimitsSubpage(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences(SwiffleAiConfig.SYNC_PREFS_NAME, Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val numberFmt = remember { NumberFormat.getIntegerInstance() }

    // Persistent Automatic Recharge State
    var autoRechargeEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("auto_recharge_enabled", false))
    }
    var minBalance by remember {
        mutableStateOf(sharedPrefs.getString("auto_recharge_min_balance", "125") ?: "125")
    }
    var targetBalance by remember {
        mutableStateOf(sharedPrefs.getString("auto_recharge_target_balance", "250") ?: "250")
    }
    var monthlyLimit by remember {
        mutableStateOf(sharedPrefs.getString("auto_recharge_monthly_limit", "") ?: "")
    }

    var showRechargeSheet by remember { mutableStateOf(false) }
    var showTooltip by remember { mutableStateOf(false) }
    var isLoadingResets by remember { mutableStateOf(true) }

    var snapshot by remember { mutableStateOf(SwiffleAiConfig.getUsageRateSnapshot(context)) }

    // Live 1-second refresh so 60s RPM/TPM windows and any 429 cooldowns update live
    LaunchedEffect(Unit) {
        snapshot = SwiffleAiConfig.getUsageRateSnapshot(context)
        delay(200)
        isLoadingResets = false
        while (true) {
            delay(1000L)
            snapshot = SwiffleAiConfig.getUsageRateSnapshot(context)
        }
    }

    val rpmRatio = (snapshot.currentRpm.toFloat() / snapshot.rpmLimit.coerceAtLeast(1)).coerceIn(0f, 1f)
    val tpmRatio = (snapshot.currentTpm.toFloat() / snapshot.tpmLimit.coerceAtLeast(1)).coerceIn(0f, 1f)
    val rpdRatio = (snapshot.currentRpd.toFloat() / snapshot.rpdLimit.coerceAtLeast(1)).coerceIn(0f, 1f)
    val remainingTokenRatio = ((snapshot.dailyTokenQuota - snapshot.totalTokensToday).toFloat() /
        snapshot.dailyTokenQuota.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    val animatedRemainingProgress by animateFloatAsState(
        targetValue = remainingTokenRatio,
        animationSpec = tween(durationMillis = 700, easing = SwiffleMotion.EmphasizedDecelerate),
        label = "usage_progress_anim"
    )

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isDark = ThemeManager.isDarkThemeActive()
    val winUiCanvas = if (isDark) Color(0xFF000000) else Color(0xFFF3F3F3)
    val winUiCard = if (isDark) Color(0xFF0C0C0C) else Color(0xFFFFFFFF)
    val winUiBorder = if (isDark) Color(0x22FFFFFF) else Color(0x0F000000)
    val textPrimary = if (isDark) Color(0xFFF5F5F5) else Color(0xFF1A1A1A)
    val textMuted = if (isDark) Color(0xFF8A8A8A) else Color(0xFF6E6E6E)
    val accentBlue = Color(0xFF0078D4)
    val statusGreen = if (isDark) Color(0xFF56C288) else Color(0xFF107C41)
    val warnAmber = if (isDark) Color(0xFFF5B041) else Color(0xFFD97706)

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(winUiCanvas)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onBack,
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Transparent,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = textPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Usage and limits",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = textPrimary
                    )
                }
                HorizontalDivider(
                    thickness = 1.dp,
                    color = if (isDark) Color(0x1AFFFFFF) else Color(0x0E000000)
                )
            }
        },
        containerColor = winUiCanvas,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // ── SECTION 1: LIVE GEMINI RATE LIMITS (RPM / TPM / RPD) ──────
                Text(
                    text = "LIVE RATE LIMITS (${snapshot.activeModelDisplayName.uppercase()})",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    letterSpacing = 0.8.sp,
                    color = accentBlue,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = winUiCard,
                    border = BorderStroke(1.dp, winUiBorder),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("usage_live_rates_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = snapshot.activeProviderLabel,
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = textPrimary
                                )
                                Text(
                                    text = "${snapshot.activeModelId} • Key: ${snapshot.keySourceLabel}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    color = textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Surface(
                                shape = CircleShape,
                                color = (if (snapshot.cooldownRemainingSec > 0) warnAmber else statusGreen).copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = if (snapshot.cooldownRemainingSec > 0) {
                                        "Cooldown ${snapshot.cooldownRemainingSec}s"
                                    } else if (snapshot.hasGeminiKey) {
                                        "Active"
                                    } else {
                                        "Fallback"
                                    },
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = if (snapshot.cooldownRemainingSec > 0) warnAmber else statusGreen,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (!snapshot.lastRateLimitMessage.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = warnAmber.copy(alpha = 0.12f),
                                border = BorderStroke(0.8.dp, warnAmber.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = snapshot.lastRateLimitMessage.orEmpty(),
                                    fontFamily = GoogleSans,
                                    fontSize = 12.sp,
                                    color = warnAmber,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }

                        RateGaugeRow(
                            label = "Requests / Minute (RPM)",
                            valueText = "${numberFmt.format(snapshot.currentRpm)} / ${numberFmt.format(snapshot.rpmLimit)} RPM",
                            progress = rpmRatio,
                            barColor = accentBlue,
                            textPrimary = textPrimary,
                            textMuted = textMuted
                        )

                        RateGaugeRow(
                            label = "Tokens / Minute (TPM)",
                            valueText = "${numberFmt.format(snapshot.currentTpm)} / ${numberFmt.format(snapshot.tpmLimit)} TPM",
                            progress = tpmRatio,
                            barColor = statusGreen,
                            textPrimary = textPrimary,
                            textMuted = textMuted
                        )

                        RateGaugeRow(
                            label = "Requests / Day (RPD)",
                            valueText = "${numberFmt.format(snapshot.currentRpd)} / ${numberFmt.format(snapshot.rpdLimit)} RPD",
                            progress = rpdRatio,
                            barColor = Color(0xFF9B51E0),
                            textPrimary = textPrimary,
                            textMuted = textMuted
                        )

                        if (snapshot.lastLatencyMs > 0L) {
                            Text(
                                text = "Last response latency: ${snapshot.lastLatencyMs} ms",
                                fontFamily = GoogleSans,
                                fontSize = 12.sp,
                                color = textMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // ── SECTION 2: DAILY TOKEN USAGE BREAKDOWN ────────────────────
                Text(
                    text = "ACTUAL USAGE & TOKEN BREAKDOWN",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    letterSpacing = 0.8.sp,
                    color = accentBlue,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = winUiCard,
                    border = BorderStroke(1.dp, winUiBorder),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daily token budget",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${(remainingTokenRatio * 100).toInt()}% remaining",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.Normal,
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedRemainingProgress)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(accentBlue)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${numberFmt.format(snapshot.totalTokensToday)} / ${numberFmt.format(snapshot.dailyTokenQuota)} tokens",
                                fontFamily = GoogleSans,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = accentBlue
                            )
                            Text(
                                text = "${numberFmt.format(snapshot.queriesToday)} queries today",
                                fontFamily = GoogleSans,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Actual Input / Output / Reasoning Breakdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TokenStatChip(
                                label = "Input (Prompt)",
                                value = numberFmt.format(snapshot.promptTokensToday),
                                isDark = isDark,
                                textPrimary = textPrimary,
                                textMuted = textMuted,
                                modifier = Modifier.weight(1f)
                            )
                            TokenStatChip(
                                label = "Output",
                                value = numberFmt.format(snapshot.outputTokensToday),
                                isDark = isDark,
                                textPrimary = textPrimary,
                                textMuted = textMuted,
                                modifier = Modifier.weight(1f)
                            )
                            TokenStatChip(
                                label = "Reasoning",
                                value = numberFmt.format(snapshot.thoughtTokensToday),
                                isDark = isDark,
                                textPrimary = textPrimary,
                                textMuted = textMuted,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Resets automatically at midnight local time",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            IconButton(
                                onClick = { showTooltip = !showTooltip },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = "Usage limit reset info",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = showTooltip,
                            enter = fadeIn(tween(200)) + expandVertically(tween(220, easing = SwiffleMotion.EmphasizedDecelerate)),
                            exit = fadeOut(tween(180)) + shrinkVertically(tween(200, easing = SwiffleMotion.EmphasizedAccelerate))
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.inverseSurface,
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Token counts reflect exact server usageMetadata reported by Gemini Free Tier streams, combined with a 60-second sliding window for real-time RPM & TPM tracking.",
                                        fontFamily = GoogleSans,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        color = MaterialTheme.colorScheme.inverseOnSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { showTooltip = false },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Dismiss tooltip",
                                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // ── SECTION 3: FREE TIER PER-MODEL LIMITS ─────────────────────
                Text(
                    text = "FREE TIER MODELS • PER-MODEL QUOTAS",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    letterSpacing = 0.8.sp,
                    color = accentBlue,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = winUiCard,
                    border = BorderStroke(1.dp, winUiBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        snapshot.perModelStats.forEachIndexed { index, modelStat ->
                            PerModelQuotaRow(
                                stat = modelStat,
                                isActive = modelStat.modelId == snapshot.activeModelId,
                                numberFmt = numberFmt,
                                accentBlue = accentBlue,
                                warnAmber = warnAmber,
                                textPrimary = textPrimary,
                                textMuted = textMuted
                            )
                            if (index < snapshot.perModelStats.lastIndex) {
                                HorizontalDivider(thickness = 0.8.dp, color = winUiBorder)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── SECTION 4: USAGE LIMIT RESETS ─────────────────────────────
                Text(
                    text = "Usage limit resets",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = winUiCard,
                    border = BorderStroke(1.dp, winUiBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = isLoadingResets,
                        transitionSpec = {
                            fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                        },
                        label = "resets_loading_transition"
                    ) { loading ->
                        if (loading) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Loading live telemetry counters",
                                    fontFamily = GoogleSans,
                                    fontSize = 14.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.2.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Local Session Telemetry",
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Reset today's local RPM, TPM, RPD, and token counters.",
                                        fontFamily = GoogleSans,
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        SwiffleAiConfig.resetDailyUsageTelemetry(context)
                                        snapshot = SwiffleAiConfig.getUsageRateSnapshot(context)
                                        Toast.makeText(context, "Daily usage telemetry reset to 0", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text(
                                        text = "Reset",
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = accentBlue
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── SECTION 5: CREDITS & RECHARGE ─────────────────────────────
                Text(
                    text = "Credits",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Surface(
                    onClick = { showRechargeSheet = true },
                    shape = RoundedCornerShape(8.dp),
                    color = winUiCard,
                    border = BorderStroke(1.dp, winUiBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Automatic recharge",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (autoRechargeEnabled) "$targetBalance Credits" else "Off",
                                fontFamily = GoogleSans,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "Open recharge options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "Free Tier operates at $0.00 cost within published RPM, TPM, and RPD quotas.",
                    fontFamily = GoogleSans,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.padding(start = 6.dp, top = 8.dp)
                )

                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }

    if (showRechargeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRechargeSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            var tempMinBalance by remember { mutableStateOf(minBalance) }
            var tempTargetBalance by remember { mutableStateOf(targetBalance) }
            var tempMonthlyLimit by remember { mutableStateOf(monthlyLimit) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 24.dp)
            ) {
                Text(
                    text = "Set up automatic recharge",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(24.dp))

                RechargeInputField(
                    label = "Minimum balance",
                    value = tempMinBalance,
                    onValueChange = { tempMinBalance = it },
                    suffix = "Credits"
                )

                Spacer(modifier = Modifier.height(14.dp))

                RechargeInputField(
                    label = "Target balance",
                    value = tempTargetBalance,
                    onValueChange = { tempTargetBalance = it },
                    suffix = "Credits"
                )

                Spacer(modifier = Modifier.height(14.dp))

                RechargeInputField(
                    label = "Monthly recharge limit",
                    value = tempMonthlyLimit,
                    onValueChange = { tempMonthlyLimit = it },
                    suffix = null
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Optional. Leave blank to allow unlimited auto top-up purchases each month.",
                    fontFamily = GoogleSans,
                    fontSize = 12.sp,
                    lineHeight = 16.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        sharedPrefs.edit()
                            .putBoolean("auto_recharge_enabled", true)
                            .putString("auto_recharge_min_balance", tempMinBalance)
                            .putString("auto_recharge_target_balance", tempTargetBalance)
                            .putString("auto_recharge_monthly_limit", tempMonthlyLimit)
                            .apply()
                        minBalance = tempMinBalance
                        targetBalance = tempTargetBalance
                        monthlyLimit = tempMonthlyLimit
                        autoRechargeEnabled = true

                        coroutineScope.launch {
                            sheetState.hide()
                            showRechargeSheet = false
                            Toast.makeText(context, "Automatic recharge settings saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = "Save",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            sheetState.hide()
                            showRechargeSheet = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Cancel",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RateGaugeRow(
    label: String,
    valueText: String,
    progress: Float,
    barColor: Color,
    textPrimary: Color,
    textMuted: Color
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 450),
        label = "rate_gauge_$label"
    )
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontFamily = GoogleSans,
                fontSize = 12.5.sp,
                color = textMuted
            )
            Text(
                text = valueText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = textPrimary
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(textMuted.copy(alpha = 0.16f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun TokenStatChip(
    label: String,
    value: String,
    isDark: Boolean,
    textPrimary: Color,
    textMuted: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                fontFamily = GoogleSans,
                fontSize = 11.sp,
                color = textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = textPrimary
            )
        }
    }
}

@Composable
private fun PerModelQuotaRow(
    stat: ModelUsageStats,
    isActive: Boolean,
    numberFmt: NumberFormat,
    accentBlue: Color,
    warnAmber: Color,
    textPrimary: Color,
    textMuted: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stat.displayName,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = textPrimary
            )
            if (stat.cooldownRemainingSec > 0) {
                Text(
                    text = "Cooldown ${stat.cooldownRemainingSec}s",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = warnAmber
                )
            } else if (isActive) {
                Text(
                    text = "Active Tier",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = accentBlue
                )
            } else {
                Text(
                    text = stat.tierLabel,
                    fontFamily = GoogleSans,
                    fontSize = 11.5.sp,
                    color = textMuted
                )
            }
        }

        Text(
            text = "${stat.modelId} • ${stat.requestsLastMinute}/${stat.rpmLimit} RPM • ${numberFmt.format(stat.tokensLastMinute)}/${numberFmt.format(stat.tpmLimit)} TPM • ${stat.requestsToday}/${stat.rpdLimit} RPD",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = textMuted
        )

        Text(
            text = "Today: ${numberFmt.format(stat.tokensToday)} tokens across ${stat.requestsToday} requests",
            fontFamily = GoogleSans,
            fontSize = 11.5.sp,
            color = textMuted
        )
    }
}

@Composable
private fun RechargeInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suffix: String?
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Normal,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f)
                )

                if (suffix != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = suffix,
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
