package com.swiffle.app.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

// Approximation of the Error Function (erf)
fun erf(x: Float): Float {
    val sign = if (x < 0) -1f else 1f
    val ax = abs(x)
    
    val p = 0.3275911f
    val a1 = 0.254829592f
    val a2 = -0.284496736f
    val a3 = 1.421413741f
    val a4 = -1.453152027f
    val a5 = 1.061405429f
    
    val t = 1f / (1f + p * ax)
    val y = 1f - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * exp(-ax * ax)
    
    return sign * y
}

// Distance from a point (px, py) to a line passing through origin with angle 'angleRad'
fun distanceToLine(px: Float, py: Float, angleRad: Float): Float {
    val nx = -sin(angleRad)
    val ny = cos(angleRad)
    return px * nx + py * ny
}

@Composable
fun GaussianEdgeScreen(onBack: () -> Unit = {}) {
    var angleDeg by remember { mutableFloatStateOf(30f) }
    var useGaussian by remember { mutableStateOf(true) }
    var strokeWidth by remember { mutableFloatStateOf(1.5f) }
    
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack, 
                    contentDescription = "Back", 
                    tint = Color.White
                )
            }
            Text("Alg 3: Gaussian Edge Coverage", color = Color.White, fontSize = 20.sp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Line Angle: ${angleDeg.toInt()}°", color = Color.LightGray)
        Slider(
            value = angleDeg,
            onValueChange = { angleDeg = it },
            valueRange = 0f..90f,
            colors = SliderDefaults.colors(thumbColor = Color(0xFFFC2828), activeTrackColor = Color(0xFFFC2828))
        )
        
        Text("Line Thickness: ${strokeWidth} pixels", color = Color.LightGray)
        Slider(
            value = strokeWidth,
            onValueChange = { strokeWidth = it },
            valueRange = 0.5f..5f,
            colors = SliderDefaults.colors(thumbColor = Color(0xFFFC2828), activeTrackColor = Color(0xFFFC2828))
        )
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (useGaussian) "Mode: Gaussian (erf)" else "Mode: Box MSAA (4x)", color = Color.LightGray)
            Switch(
                checked = useGaussian,
                onCheckedChange = { useGaussian = it },
                modifier = Modifier.padding(start = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxSize().weight(1f).background(Color.Black), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(300.dp)) {
                val gridSize = 15
                val cellSize = size.width / gridSize
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                
                val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
                val sigma = 0.5f // Standard deviation for the pixel Gaussian
                
                for (row in 0 until gridSize) {
                    for (col in 0 until gridSize) {
                        // Pixel coordinates in "pixel" units relative to center
                        val px = (col - gridSize / 2f) + 0.5f
                        val py = (row - gridSize / 2f) + 0.5f
                        
                        val d = distanceToLine(px, py, angleRad)
                        
                        val coverage = if (useGaussian) {
                            // Coverage(d) = 0.5 * (1 + erf(d / (sigma * sqrt(2))))
                            // For a stroke width w, coverage is Coverage(d + w/2) - Coverage(d - w/2)
                            val d1 = d + strokeWidth / 2f
                            val d2 = d - strokeWidth / 2f
                            val cov1 = 0.5f * (1f + erf(d1 / (sigma * sqrt(2f))))
                            val cov2 = 0.5f * (1f + erf(d2 / (sigma * sqrt(2f))))
                            abs(cov1 - cov2).coerceIn(0f, 1f)
                        } else {
                            // 4x MSAA Approximation (Box)
                            var hits = 0
                            val offsets = listOf(Offset(-0.25f, -0.25f), Offset(0.25f, -0.25f), Offset(-0.25f, 0.25f), Offset(0.25f, 0.25f))
                            for (off in offsets) {
                                val sampleD = distanceToLine(px + off.x, py + off.y, angleRad)
                                if (abs(sampleD) <= strokeWidth / 2f) hits++
                            }
                            hits / 4f
                        }
                        
                        // Draw the giant pixel
                        drawRect(
                            color = Color(coverage, 0f, 0f, 1f), // Red with intensity = coverage
                            topLeft = Offset(col * cellSize, row * cellSize),
                            size = androidx.compose.ui.geometry.Size(cellSize - 1f, cellSize - 1f) // 1f gap for grid
                        )
                    }
                }
                
                // Draw the mathematical center line
                val lineLength = size.width
                val dx = cos(angleRad) * lineLength
                val dy = sin(angleRad) * lineLength
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(centerX - dx, centerY - dy),
                    end = Offset(centerX + dx, centerY + dy),
                    strokeWidth = 2f
                )
            }
        }
    }
}
