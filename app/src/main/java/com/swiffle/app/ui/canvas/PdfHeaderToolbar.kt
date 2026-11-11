package com.swiffle.app.ui.canvas

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiffle.app.theme.GoogleSans

/**
 * High-Class Native Material 4 / Material 3 Pro Top App Bar for PDF Immersion Canvas:
 * - Authentic edge-to-edge system architecture spanning the full status bar width
 * - High-surface container (surfaceContainer) with subtle translucent glassmorphic backdrop
 * - Subtitle status metadata row with micro-pill badge
 * - Standard 48dp touch targets with native Material ripple feedback
 * - Direct 1-tap Share, Neural Voice, Search, and anchored M3 Pro Dropdown Menu
 * - Seamless physical slide-in / slide-out choreography
 */
@Composable
fun PdfHeaderToolbar(
    pdfTitle: String,
    pdfUri: Uri,
    visible: Boolean,
    isDark: Boolean,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenGodVoice: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenToolkit: () -> Unit,
    onOpenDetails: () -> Unit,
    onSaveToDevice: () -> Unit = {},
    isSmartCropEnabled: Boolean = true,
    onToggleSmartCrop: () -> Unit = {},
    onOpenAskSwiffle: () -> Unit = {},
    isEditMode: Boolean = false,
    hasUnsavedEdits: Boolean = false,
    onToggleEditMode: () -> Unit = {},
    onSaveEditedPdf: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showOverflow by remember { mutableStateOf(false) }

    // Windows 11 Fluent WinUI 3 CommandBar Tokens
    val containerBg = if (isDark) Color(0xFF000000) else Color(0xFFF9F9F9)
    val textPrimary = if (isDark) Color(0xFFF5F5F5) else Color(0xFF1A1A1A)
    val textMuted = if (isDark) Color(0xFF8A8A8A) else Color(0xFF6E6E6E)
    val borderSpecular = if (isDark) Color(0x22FFFFFF) else Color(0x0F000000)
    val fluentCobalt = Color(0xFF0078D4)
    val brandAccent = fluentCobalt

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)) + slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(180)
        ),
        modifier = modifier
    ) {
        val density = LocalDensity.current
        val statusBarTop: Dp = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding() }
        val cutoutStart: Dp = with(density) { WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(LayoutDirection.Ltr) }
        val cutoutEnd: Dp = with(density) { WindowInsets.displayCutout.asPaddingValues().calculateEndPadding(LayoutDirection.Ltr) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    spotColor = Color.Black.copy(alpha = 0.15f),
                    ambientColor = Color.Black.copy(alpha = 0.08f)
                ),
            color = containerBg,
            tonalElevation = 3.dp,
            border = BorderStroke(0.75.dp, borderSpecular)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusBarTop, start = cutoutStart, end = cutoutEnd)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Native Material Back Navigation Icon
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Clean & Minimal Title (Zero Clutter)
                    Text(
                        text = pdfTitle,
                        color = textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSans,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Action: Save Edited PDF (When in Realistic Edit Mode)
                    if (isEditMode) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0078D4),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSaveEditedPdf() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Save PDF",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Save",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Action 1: Search in Document
                    IconButton(
                        onClick = onOpenSearch,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search Document",
                            tint = textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Action 2: Direct Share PDF
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, pdfUri)
                                clipData = ClipData.newRawUri("PDF", pdfUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share PDF").apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            })
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share PDF",
                            tint = textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Action 3: Anchored 3-Dot More Menu
                    Box {
                        IconButton(
                            onClick = { showOverflow = true },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More Options",
                                tint = textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Windows 11 Fluent WinUI 3 MenuFlyout
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false },
                            shape = RoundedCornerShape(8.dp),
                            containerColor = if (isDark) Color(0xFF141414) else Color(0xFFFFFFFF),
                            shadowElevation = 8.dp,
                            border = BorderStroke(1.dp, if (isDark) Color(0x33FFFFFF) else Color(0x18000000)),
                            modifier = Modifier.widthIn(min = 220.dp, max = 280.dp)
                        ) {
                            // Swiffle AI Assistant
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Swiffle Intelligence (AI)",
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        fontSize = 14.5.sp
                                    )
                                },
                                onClick = {
                                    showOverflow = false
                                    onOpenAskSwiffle()
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(id = com.swiffle.app.R.drawable.ic_swiffle_logo),
                                        contentDescription = "Swiffle AI",
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                modifier = Modifier.height(48.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = borderSpecular.copy(alpha = 0.5f)
                            )

                            // Neural Voice (TTS)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Neural Voice (TTS)",
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.Medium,
                                        color = textPrimary,
                                        fontSize = 14.5.sp
                                    )
                                },
                                onClick = {
                                    showOverflow = false
                                    onOpenGodVoice()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.GraphicEq,
                                        contentDescription = null,
                                        tint = brandAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                modifier = Modifier.height(48.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Bookmarks & Outline",
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.Medium,
                                        color = textPrimary,
                                        fontSize = 14.5.sp
                                    )
                                },
                                onClick = {
                                    showOverflow = false
                                    onOpenBookmarks()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Bookmarks,
                                        contentDescription = null,
                                        tint = brandAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                modifier = Modifier.height(48.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Reading Tools & Ink",
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.Medium,
                                        color = textPrimary,
                                        fontSize = 14.5.sp
                                    )
                                },
                                onClick = {
                                    showOverflow = false
                                    onOpenToolkit()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Build,
                                        contentDescription = null,
                                        tint = brandAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                modifier = Modifier.height(48.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Auto-Crop Margins",
                                            fontFamily = GoogleSans,
                                            fontWeight = FontWeight.Medium,
                                            color = textPrimary,
                                            fontSize = 14.5.sp
                                        )
                                        Switch(
                                            checked = isSmartCropEnabled,
                                            onCheckedChange = {
                                                onToggleSmartCrop()
                                                showOverflow = false
                                            },
                                            modifier = Modifier.scale(0.8f)
                                        )
                                    }
                                },
                                onClick = {
                                    onToggleSmartCrop()
                                    showOverflow = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Crop,
                                        contentDescription = null,
                                        tint = brandAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                modifier = Modifier.height(48.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp),
                                thickness = 1.dp
                            )

                            // Edit PDF Option
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isEditMode) "Exit PDF Editor" else "Edit PDF",
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isEditMode) brandAccent else textPrimary,
                                        fontSize = 14.5.sp
                                    )
                                },
                                onClick = {
                                    showOverflow = false
                                    onToggleEditMode()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = null,
                                        tint = brandAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                modifier = Modifier.height(48.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            )

                            // Save to Device Option
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Save to Device",
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.Medium,
                                        color = textPrimary,
                                        fontSize = 14.5.sp
                                    )
                                },
                                onClick = {
                                    showOverflow = false
                                    onSaveToDevice()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.SaveAlt,
                                        contentDescription = "Save to Device",
                                        tint = brandAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                modifier = Modifier.height(48.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Document Details",
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.Medium,
                                        color = textPrimary,
                                        fontSize = 14.5.sp
                                    )
                                },
                                onClick = {
                                    showOverflow = false
                                    onOpenDetails()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = textMuted,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                modifier = Modifier.height(48.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
