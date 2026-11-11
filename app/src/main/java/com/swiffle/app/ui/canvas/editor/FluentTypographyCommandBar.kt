package com.swiffle.app.ui.canvas.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Windows 11 Fluent Typography & Realism Command Bar:
 * Provides precision controls for:
 * 1. "Auto-Match Source Font & Weight"
 * 2. Universal font families (Serif, Sans, Mono, Signatures)
 * 3. 0.1 pt resolution font size stepper & slider
 * 4. 100 to 900 font thickness (weight)
 * 5. Optical realism tuning (edge softening, paper micro-grain, ink dilation)
 * 6. Pigment / ink color palette with natural printed tones
 */
@Composable
fun FluentTypographyCommandBar(
    editableObject: EditableObject,
    onTextChanged: (String) -> Unit = {},
    onTypographyChanged: (TypographyConfig) -> Unit,
    onRealismChanged: (RealismConfig) -> Unit,
    onAutoMatchRequested: () -> Unit,
    onDuplicateRequested: () -> Unit,
    onDeleteRequested: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(CommandBarTab.TYPOGRAPHY) }
    var selectedCategory by remember { mutableStateOf<UniversalFontCatalog.FontCategory?>(null) }

    val typography = editableObject.typography
    val realism = editableObject.realism

    // Fluent Acrylic / Card container
    Card(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .shadow(16.dp, RoundedCornerShape(16.dp), spotColor = Color(0x33000000))
            .border(1.dp, Color(0x1F000000), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xF7FAFAFA) // Windows 11 Mica / Acrylic light surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Prominent Text Editing Input Box (When editing text element)
            if (editableObject.type == EditObjectType.TEXT) {
                var currentInputText by remember(editableObject.id, editableObject.text) {
                    mutableStateOf(editableObject.text)
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, Color(0xFF0078D4)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = currentInputText,
                            onValueChange = { newText ->
                                currentInputText = newText
                                editableObject.text = newText
                                editableObject.isMovedOrModified = true
                                onTextChanged(newText)
                            },
                            textStyle = TextStyle(
                                fontFamily = typography.fontFamily,
                                fontWeight = typography.fontWeight,
                                fontStyle = if (typography.isItalic) FontStyle.Italic else FontStyle.Normal,
                                fontSize = 15.sp,
                                color = typography.textColor
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        if (currentInputText.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    currentInputText = ""
                                    editableObject.text = ""
                                    editableObject.isMovedOrModified = true
                                    onTextChanged("")
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Header Row: Tab toggles & Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Tab pills
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TabPill(
                        label = "Typography",
                        isSelected = activeTab == CommandBarTab.TYPOGRAPHY,
                        onClick = { activeTab = CommandBarTab.TYPOGRAPHY }
                    )
                    TabPill(
                        label = "Realism & Ink",
                        isSelected = activeTab == CommandBarTab.REALISM,
                        onClick = { activeTab = CommandBarTab.REALISM }
                    )
                }

                // Right: Action buttons (Match Source, Duplicate, Delete, Done)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Auto-Match Source Font Button
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEBF3FC),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onAutoMatchRequested() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Auto Match Source Font",
                                tint = Color(0xFF0078D4),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Match Source",
                                color = Color(0xFF0078D4),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Duplicate
                    IconButton(
                        onClick = onDuplicateRequested,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Duplicate",
                            tint = Color(0xFF424242),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Delete
                    IconButton(
                        onClick = onDeleteRequested,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFD83B01),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Done / Dismiss
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFF0078D4),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Done",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (activeTab) {
                CommandBarTab.TYPOGRAPHY -> {
                    TypographyPanel(
                        typography = typography,
                        selectedCategory = selectedCategory,
                        onCategorySelected = { selectedCategory = it },
                        onTypographyChanged = onTypographyChanged
                    )
                }
                CommandBarTab.REALISM -> {
                    RealismPanel(
                        realism = realism,
                        onRealismChanged = onRealismChanged
                    )
                }
            }
        }
    }
}

private enum class CommandBarTab {
    TYPOGRAPHY,
    REALISM
}

@Composable
private fun TabPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0xFF0078D4) else Color(0xFFE8E8E8),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color(0xFF424242),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

/**
 * Typographic Controls:
 * - Font Categories (All, Serif, Sans, Mono, Handwriting)
 * - Horizontal Font Carousel with real previews
 * - Font Size Stepper + Slider (0.1 pt resolution)
 * - Font Thickness Slider (100 to 900)
 * - Ink Pigment Swatches
 */
@Composable
private fun TypographyPanel(
    typography: TypographyConfig,
    selectedCategory: UniversalFontCatalog.FontCategory?,
    onCategorySelected: (UniversalFontCatalog.FontCategory?) -> Unit,
    onTypographyChanged: (TypographyConfig) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. Category Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategorySelected(null) },
                label = { Text("All Fonts (${UniversalFontCatalog.allFonts.size})", fontSize = 11.sp) },
                shape = RoundedCornerShape(8.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF0078D4),
                    selectedLabelColor = Color.White
                )
            )
            UniversalFontCatalog.FontCategory.values().forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { onCategorySelected(cat) },
                    label = {
                        Text(
                            when (cat) {
                                UniversalFontCatalog.FontCategory.SERIF -> "Serif"
                                UniversalFontCatalog.FontCategory.SANS_SERIF -> "Sans-Serif"
                                UniversalFontCatalog.FontCategory.MONOSPACE -> "Monospace"
                                UniversalFontCatalog.FontCategory.HANDWRITING -> "Handwriting"
                                UniversalFontCatalog.FontCategory.SYSTEM -> "System"
                            },
                            fontSize = 11.sp
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0078D4),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Font Family Picker Carousel
        val filteredFonts = remember(selectedCategory) {
            if (selectedCategory == null) UniversalFontCatalog.allFonts
            else UniversalFontCatalog.allFonts.filter { it.category == selectedCategory }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filteredFonts.forEach { fontEntry ->
                val isSelected = fontEntry.displayName == typography.fontName
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) Color(0xFFEBF3FC) else Color.White,
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 1.5.dp else 0.5.dp,
                        color = if (isSelected) Color(0xFF0078D4) else Color(0xFFD6D6D6)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onTypographyChanged(
                                typography.copy(
                                    fontName = fontEntry.displayName,
                                    fontFamily = fontEntry.composeFontFamily,
                                    isSourceMatched = false
                                )
                            )
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Ag",
                            fontFamily = fontEntry.composeFontFamily,
                            fontWeight = typography.fontWeight,
                            fontSize = 16.sp,
                            color = if (isSelected) Color(0xFF0078D4) else Color(0xFF212121)
                        )
                        Text(
                            text = fontEntry.displayName,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isSelected) Color(0xFF0078D4) else Color(0xFF616161)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Font Size & Thickness Steppers/Sliders Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Font Size Stepper (0.1 pt resolution)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Size: ${String.format("%.1f", typography.fontSizePt)} pt",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF333333)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val newSize = (typography.fontSizePt - 0.5f).coerceAtLeast(4.0f)
                                onTypographyChanged(typography.copy(fontSizePt = newSize))
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "-0.5pt", modifier = Modifier.size(12.dp))
                        }
                        IconButton(
                            onClick = {
                                val newSize = (typography.fontSizePt + 0.5f).coerceAtMost(120.0f)
                                onTypographyChanged(typography.copy(fontSizePt = newSize))
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "+0.5pt", modifier = Modifier.size(12.dp))
                        }
                    }
                }
                Slider(
                    value = typography.fontSizePt,
                    onValueChange = { newSize ->
                        // 0.1 pt snapping
                        val rounded = (newSize * 10).roundToInt() / 10.0f
                        onTypographyChanged(typography.copy(fontSizePt = rounded))
                    },
                    valueRange = 4.0f..72.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF0078D4),
                        activeTrackColor = Color(0xFF0078D4)
                    )
                )
            }

            // Thickness / Weight Slider (100 to 900)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Weight: ${typography.fontWeightValue} (${getWeightLabel(typography.fontWeightValue)})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF333333)
                    )
                }
                Slider(
                    value = typography.fontWeightValue.toFloat(),
                    onValueChange = { newWeight ->
                        val snapped = ((newWeight / 50).roundToInt() * 50).coerceIn(100, 900)
                        onTypographyChanged(
                            typography.copy(
                                fontWeightValue = snapped,
                                fontWeight = UniversalFontCatalog.resolveFontWeight(snapped)
                            )
                        )
                    },
                    valueRange = 100f..900f,
                    steps = 15,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF0078D4),
                        activeTrackColor = Color(0xFF0078D4)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Styles (Bold toggle, Italic toggle) and Ink Color Palette
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Style Toggles
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Bold toggle
                val isBold = typography.fontWeightValue >= 700
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isBold) Color(0xFF0078D4) else Color(0xFFE8E8E8),
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            val newWeightVal = if (isBold) 400 else 700
                            onTypographyChanged(
                                typography.copy(
                                    fontWeightValue = newWeightVal,
                                    fontWeight = UniversalFontCatalog.resolveFontWeight(newWeightVal)
                                )
                            )
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FormatBold,
                            contentDescription = "Bold",
                            tint = if (isBold) Color.White else Color(0xFF424242),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Italic toggle
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (typography.isItalic) Color(0xFF0078D4) else Color(0xFFE8E8E8),
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            onTypographyChanged(typography.copy(isItalic = !typography.isItalic))
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FormatItalic,
                            contentDescription = "Italic",
                            tint = if (typography.isItalic) Color.White else Color(0xFF424242),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Natural Print Ink Palette
            val inkColors = listOf(
                Color(0xFF1E1E1E) to "Charcoal Ink",
                Color(0xFF000000) to "Black Ink",
                Color(0xFF0A2540) to "Document Navy",
                Color(0xFF4A3525) to "Vintage Sepia",
                Color(0xFF8B0000) to "Red Stamp",
                Color(0xFF005FB8) to "Fluent Blue"
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                inkColors.forEach { (color, label) ->
                    val isColorSelected = typography.textColor == color
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .shadow(if (isColorSelected) 3.dp else 1.dp, CircleShape)
                            .background(color, CircleShape)
                            .border(
                                width = if (isColorSelected) 2.dp else 1.dp,
                                color = if (isColorSelected) Color(0xFF0078D4) else Color(0x33FFFFFF),
                                shape = CircleShape
                            )
                            .clip(CircleShape)
                            .clickable {
                                onTypographyChanged(typography.copy(textColor = color))
                            }
                    )
                }
            }
        }
    }
}

/**
 * Realism Controls:
 * - Edge Softening (Anti-aliasing sub-pixel point spread)
 * - Paper Micro-Grain Noise Injection
 * - Ink Dilation (-0.5px to +1.0px)
 */
@Composable
private fun RealismPanel(
    realism: RealismConfig,
    onRealismChanged: (RealismConfig) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Optical Realism Tuning",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0078D4)
        )
        Text(
            text = "Applies sub-pixel print anti-aliasing and paper fiber grain so replacement text optically matches scanned documents.",
            fontSize = 10.sp,
            color = Color(0xFF666666)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 1. Edge Softening Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Edge Softening (PSF Blur): ${String.format("%.2f", realism.edgeSofteningPx)} px",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = realism.edgeSofteningPx,
            onValueChange = { onRealismChanged(realism.copy(edgeSofteningPx = it)) },
            valueRange = 0.0f..1.5f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF0078D4),
                activeTrackColor = Color(0xFF0078D4)
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Paper Grain Intensity
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Paper Micro-Grain: ${(realism.paperGrainIntensity * 100).roundToInt()}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = realism.paperGrainIntensity,
            onValueChange = { onRealismChanged(realism.copy(paperGrainIntensity = it)) },
            valueRange = 0.0f..0.35f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF0078D4),
                activeTrackColor = Color(0xFF0078D4)
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Ink Bleed / Dilation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ink Bleed / Dilation: ${String.format("%+.2f", realism.inkDilationPx)} px",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = realism.inkDilationPx,
            onValueChange = { onRealismChanged(realism.copy(inkDilationPx = it)) },
            valueRange = -0.5f..1.0f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF0078D4),
                activeTrackColor = Color(0xFF0078D4)
            )
        )
    }
}

private fun getWeightLabel(weight: Int): String {
    return when {
        weight <= 150 -> "Thin"
        weight <= 250 -> "ExtraLight"
        weight <= 350 -> "Light"
        weight <= 450 -> "Regular"
        weight <= 550 -> "Medium"
        weight <= 650 -> "SemiBold"
        weight <= 750 -> "Bold"
        weight <= 850 -> "ExtraBold"
        else -> "Black"
    }
}

