package com.swiffle.app.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiffle.app.theme.GoogleSans
import com.swiffle.app.theme.SwiffleMotion
import com.swiffle.app.theme.ThemeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  USAGE AND LIMITS — MATERIAL 3 EXPRESSIVE UI SPECIFICATION
 * ══════════════════════════════════════════════════════════════════════════════
 *
 * Implements the official Usage & Limits management experience modeled after the
 * ChatGPT / OpenAI mobile architecture.
 *
 * Sections:
 *  1. Agentic usage limit: Linear gauge, remaining quota %, reset countdown, tooltip info.
 *  2. Usage limit resets: Info header, dynamic query state (loading spinner -> availability).
 *  3. Credits & Automatic Recharge: Current status, bottom sheet config for minimum/target limits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageLimitsSubpage(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

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

    // Interactive UI State
    var showRechargeSheet by remember { mutableStateOf(false) }
    var showTooltip by remember { mutableStateOf(false) }
    var isLoadingResets by remember { mutableStateOf(true) }

    // Live Token & Quota Metrics from Swiffle AI (real counters, zero fake defaults)
    val usagePrefs = remember { context.getSharedPreferences("swiffle_usage_prefs", Context.MODE_PRIVATE) }
    var tokensToday by remember { mutableIntStateOf(usagePrefs.getInt("real_tokens_used_today", 0)) }
    var queriesToday by remember { mutableIntStateOf(usagePrefs.getInt("real_queries_executed_today", 0)) }
    val dailyQuota = 100000 // 100k daily tier token allocation

    // Real-time remaining percentage
    val remainingPercentage = ((dailyQuota - tokensToday).toFloat() / dailyQuota.toFloat()).coerceIn(0.05f, 1.0f)
    val animatedProgress by animateFloatAsState(
        targetValue = remainingPercentage,
        animationSpec = tween(durationMillis = 900, easing = SwiffleMotion.EmphasizedDecelerate),
        label = "usage_progress_anim"
    )

    // Load real usage telemetry
    LaunchedEffect(Unit) {
        delay(250)
        tokensToday = usagePrefs.getInt("real_tokens_used_today", 0)
        queriesToday = usagePrefs.getInt("real_queries_executed_today", 0)
        isLoadingResets = false
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isDark = ThemeManager.isDarkThemeActive()
    val winUiCanvas = if (isDark) Color(0xFF000000) else Color(0xFFF3F3F3)
    val winUiCard = if (isDark) Color(0xFF0C0C0C) else Color(0xFFFFFFFF)
    val winUiBorder = if (isDark) Color(0x22FFFFFF) else Color(0x0F000000)
    val textPrimary = if (isDark) Color(0xFFF5F5F5) else Color(0xFF1A1A1A)
    val textMuted = if (isDark) Color(0xFF8A8A8A) else Color(0xFF6E6E6E)

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
                        modifier = Modifier.size(34.dp)
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
                    Spacer(modifier = Modifier.width(8.dp))
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

            // ── SECTION 1: AGENTIC USAGE LIMIT ────────────────────────────────
            Text(
                text = "AGENTIC USAGE LIMIT",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                letterSpacing = 0.8.sp,
                color = Color(0xFF0078D4),
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
                    // Header Row: Usage limit & Remaining %
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Usage limit",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${(remainingPercentage * 100).toInt()}% remaining",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Expressive Linear Progress Indicator
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Token Count Metrics Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "%,d / %,d tokens".format(tokensToday, dailyQuota),
                            fontFamily = GoogleSans,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$queriesToday queries executed",
                            fontFamily = GoogleSans,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Reset Date Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Resets daily at midnight local time",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Subtle Info icon
                        IconButton(
                            onClick = { showTooltip = !showTooltip },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "Usage limit reset info",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // Informational Tooltip Popover
                    AnimatedVisibility(
                        visible = showTooltip,
                        enter = fadeIn(tween(200)) + expandVertically(tween(220, easing = SwiffleMotion.EmphasizedDecelerate)),
                        exit = fadeOut(tween(180)) + shrinkVertically(tween(200, easing = SwiffleMotion.EmphasizedAccelerate))
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.inverseSurface,
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Usage telemetry tracks real tokens and queries executed across Groq, OpenRouter, and RAG sessions.",
                                    fontFamily = GoogleSans,
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { showTooltip = false },
                                    modifier = Modifier.size(24.dp)
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

            Spacer(modifier = Modifier.height(28.dp))

            // ── SECTION 2: USAGE LIMIT RESETS ─────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            ) {
                Text(
                    text = "Usage limit resets",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { showTooltip = !showTooltip },
                    modifier = Modifier.size(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

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
                                text = "Loading usage limit resets",
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
                                    text = "Reset today's local token and query counters.",
                                    fontFamily = GoogleSans,
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = {
                                    usagePrefs.edit()
                                        .putInt("real_tokens_used_today", 0)
                                        .putInt("real_queries_executed_today", 0)
                                        .putInt("tokens_used_today", 0)
                                        .putInt("queries_executed_today", 0)
                                        .apply()
                                    tokensToday = 0
                                    queriesToday = 0
                                    Toast.makeText(context, "Daily usage telemetry reset to 0", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text(
                                    text = "Reset",
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = Color(0xFF0078D4)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── SECTION 3: CREDITS & RECHARGE ─────────────────────────────────
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

            // Caption under Credits card
            Text(
                text = "Credits extend usage beyond your plan limits.",
                fontFamily = GoogleSans,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.padding(start = 6.dp, top = 8.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }

    // ── MODAL BOTTOM SHEET: SET UP AUTOMATIC RECHARGE ─────────────────────────
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

                // Field 1: Minimum balance
                RechargeInputField(
                    label = "Minimum balance",
                    value = tempMinBalance,
                    onValueChange = { tempMinBalance = it },
                    suffix = "Credits"
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Field 2: Target balance
                RechargeInputField(
                    label = "Target balance",
                    value = tempTargetBalance,
                    onValueChange = { tempTargetBalance = it },
                    suffix = "Credits"
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Field 3: Monthly recharge limit
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

                // Primary Action Button: Save
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
                        containerColor = Color(0xFF0078D4),
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

                // Secondary Action Button: Cancel
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

/**
 * Material 3 Expressive Container Input Field for Automatic Recharge Sheet
 */
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
