package com.swiffle.app.ui.canvas

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiffle.app.theme.GoogleSans
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfDetailsBottomSheet(
    visible: Boolean,
    pdfTitle: String,
    pdfUri: Uri,
    pageCount: Int,
    pdfPointsWidth: Float,
    pdfPointsHeight: Float,
    pageAspectRatio: Float,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sheetSurfaceColor = if (isDark) Color(0xFF0C0C0C) else Color(0xFFFFFFFF)
    val sheetBorderColor = if (isDark) Color(0x22FFFFFF) else Color(0x0F000000)
    val menuText = if (isDark) Color(0xFFF5F5F5) else Color(0xFF1A1A1A)
    val primaryAccent = Color(0xFF0078D4)
    val density = LocalDensity.current

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val cutoutStart = with(density) { WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) }
    val cutoutEnd = with(density) { WindowInsets.displayCutout.asPaddingValues().calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetSurfaceColor,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(
                        interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            coroutineScope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        }
                    )
                    .semantics {
                        role = androidx.compose.ui.semantics.Role.Button
                        contentDescription = "Sheet handle, tap to collapse"
                    },
                contentAlignment = Alignment.Center
            ) {
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        },
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 640.dp)
                .padding(
                    start = if (isLandscape) 20.dp + cutoutStart else 20.dp,
                    end = if (isLandscape) 20.dp + cutoutEnd else 20.dp,
                    top = 0.dp,
                    bottom = if (isLandscape) 18.dp else 32.dp
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row matching PdfJumpBottomSheet: Title + Subtitle + Brand Badge (NO X button)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = primaryAccent.copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "Document Details",
                                    tint = primaryAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Document Details",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.5.sp,
                                color = menuText
                            )
                            Text(
                                text = "File metadata, page geometry & layout specs",
                                fontFamily = GoogleSans,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Tonal M3 Document Tag Chip
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, sheetBorderColor)
                    ) {
                        Text(
                            text = "PDF",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            fontFamily = GoogleSans
                        )
                    }
                }

                // Hero Document Title Card (Dedicated card so long titles never crush labels)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
                        .border(1.dp, sheetBorderColor, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = primaryAccent.copy(alpha = 0.12f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Description,
                                        contentDescription = null,
                                        tint = primaryAccent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Document Title",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.5.sp,
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Clean full readable title container with subtle tonal background
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, sheetBorderColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = pdfTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = GoogleSans,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Document Technical Specifications Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
                        .border(1.dp, sheetBorderColor, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    M3DetailMetricRow(
                        icon = Icons.Filled.Layers,
                        label = "Total Pages",
                        value = "$pageCount pages",
                        accentColor = primaryAccent
                    )

                    HorizontalDivider(color = sheetBorderColor.copy(alpha = 0.5f))

                    M3DetailMetricRow(
                        icon = Icons.Filled.Straighten,
                        label = "Native Dimensions",
                        value = "${pdfPointsWidth.roundToInt()} × ${pdfPointsHeight.roundToInt()} pt",
                        accentColor = primaryAccent
                    )

                    HorizontalDivider(color = sheetBorderColor.copy(alpha = 0.5f))

                    M3DetailMetricRow(
                        icon = Icons.Filled.AspectRatio,
                        label = "Aspect Ratio",
                        value = String.format(java.util.Locale.US, "%.2f (Standard)", pageAspectRatio),
                        accentColor = primaryAccent
                    )

                    HorizontalDivider(color = sheetBorderColor.copy(alpha = 0.5f))

                    M3DetailMetricRow(
                        icon = Icons.Filled.Folder,
                        label = "File Path / URI",
                        value = pdfUri.path ?: pdfTitle,
                        accentColor = primaryAccent
                    )
                }

                Text(
                    text = "Swipe down or tap outside to dismiss",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 11.5.sp,
                    fontFamily = GoogleSans,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun M3DetailMetricRow(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label with minimum width and shrink protection
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(0.48f, fill = false)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.10f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.5.sp,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GoogleSans,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.52f, fill = false)
        )
    }
}
