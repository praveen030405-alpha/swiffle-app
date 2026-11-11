package com.swiffle.app.ui.convert

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swiffle.app.conversion.TableReinforcementAgent

/**
 * Windows 11 Fluent Reinforcement Learning Human Feedback (RLHF) Dialog.
 * 
 * Pops after the user finishes viewing or closes an extracted Excel workbook,
 * enabling lifelong closed-loop learning to reach 100% table extraction accuracy.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FluentConversionFeedbackDialog(
    onDismiss: () -> Unit,
    onSubmitFeedback: (stars: Int, tags: Set<String>) -> Unit
) {
    val context = LocalContext.current
    val isDark = com.swiffle.app.theme.ThemeManager.isDarkThemeActive()

    var stars by remember { mutableIntStateOf(5) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var submitted by remember { mutableStateOf(false) }

    val bgPrimary = if (isDark) Color(0xFF181818) else Color(0xFFFFFFFF)
    val strokeColor = if (isDark) Color(0x33FFFFFF) else Color(0x1A000000)
    val textPrimary = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1E1E1E)
    val textSecondary = if (isDark) Color(0xFFB0B0B0) else Color(0xFF6E6E6E)
    val brandGreen = Color(0xFF107C41)
    val starGold = Color(0xFFFFB900)

    val feedbackOptions = listOf(
        "COLUMNS_MERGED" to "Columns Merged",
        "COLUMNS_SPLIT" to "Columns Split",
        "ROWS_MISALIGNED" to "Rows Misaligned",
        "HEADERS_SPLIT" to "Headers Split",
        "PERFECT_100" to "100% Perfect Match"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, strokeColor, RoundedCornerShape(16.dp)),
            color = bgPrimary,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(brandGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = brandGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Teach Swiffle AI",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Text(
                                text = "Help the RL agent reach 100% accuracy",
                                fontSize = 12.sp,
                                color = textSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (!submitted) {
                    Text(
                        text = "How accurate was the table reconstruction?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 5-Star Interactive Rating
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 1..5) {
                            val isSelected = i <= stars
                            Icon(
                                imageVector = if (isSelected) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Star $i",
                                tint = if (isSelected) starGold else textSecondary.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { stars = i }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Optional Details:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textSecondary,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Multi-select tags
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        feedbackOptions.forEach { (key, label) ->
                            val isTagSelected = selectedTags.contains(key)
                            val tagBg = if (isTagSelected) brandGreen.copy(alpha = 0.2f) else if (isDark) Color(0xFF262626) else Color(0xFFF0F0F0)
                            val tagBorder = if (isTagSelected) brandGreen else strokeColor
                            val tagText = if (isTagSelected) brandGreen else textPrimary

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(tagBg)
                                    .border(1.dp, tagBorder, RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedTags = if (isTagSelected) {
                                            selectedTags - key
                                        } else {
                                            selectedTags + key
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tagText
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Submit Button
                    Button(
                        onClick = {
                            TableReinforcementAgent.applyHumanFeedback(
                                context = context,
                                stars = stars,
                                feedbackTags = selectedTags
                            )
                            submitted = true
                            onSubmitFeedback(stars, selectedTags)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = brandGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = "Submit & Train Agent",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    // Success Confirmation State
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = brandGreen,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Learned & Updated!",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "The RL policy weights have adapted to this document archetype for your next conversions.",
                            fontSize = 12.sp,
                            color = textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color(0xFF333333) else Color(0xFFE5E5E5),
                                contentColor = textPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(40.dp)
                        ) {
                            Text(text = "Done", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

