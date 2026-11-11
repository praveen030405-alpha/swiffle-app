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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.sqrt

// Prototype: Alg 1 - Error-Budget Allocator & Minimax Curve Flattening
@Composable
fun CurveFidelityScreen(onBack: () -> Unit = {}) {
    var totalSegments by remember { mutableIntStateOf(10) }
    var useMinimax by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack, 
                    contentDescription = "Back", 
                    tint = Color.White
                )
            }
            Text("Alg 1: Error-Budget Curve Flattening", color = Color.White, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Segments (Compute Budget): $totalSegments", color = Color.LightGray)
            Slider(
                value = totalSegments.toFloat(),
                onValueChange = { totalSegments = it.toInt() },
                valueRange = 2f..50f,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Minimax Placement (Halve Error)", color = Color.LightGray)
            Switch(
                checked = useMinimax,
                onCheckedChange = { useMinimax = it },
                modifier = Modifier.padding(start = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Canvas(modifier = Modifier.fillMaxSize().weight(1f)) {
            val p0 = Offset(100f, size.height - 100f)
            val p1 = Offset(size.width * 0.2f, 100f)
            val p2 = Offset(size.width * 0.8f, 100f)
            val p3 = Offset(size.width - 100f, size.height - 100f)

            // Draw Ground Truth (High-res native path)
            val truthPath = Path().apply {
                moveTo(p0.x, p0.y)
                cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
            }
            drawPath(truthPath, color = Color(0xFF333333), style = Stroke(width = 8f))

            // Flattening via Wang's Formula & Lagrange Budget
            // M = max(|P0 - 2P1 + P2|, |P1 - 2P2 + P3|)
            val v1x = p0.x - 2 * p1.x + p2.x
            val v1y = p0.y - 2 * p1.y + p2.y
            val v2x = p1.x - 2 * p2.x + p3.x
            val v2y = p1.y - 2 * p2.y + p3.y
            val m1 = sqrt(v1x * v1x + v1y * v1y)
            val m2 = sqrt(v2x * v2x + v2y * v2y)
            val M = max(m1, m2)

            // Max deviation E = sqrt(3)/4 * M * h^2
            // If we use Minimax, we push the segment outward by E/2, halving the absolute error!
            
            val segments = mutableListOf<Offset>()
            for (i in 0..totalSegments) {
                val t = i.toFloat() / totalSegments
                val u = 1f - t
                
                // standard cubic bezier
                val x = u*u*u*p0.x + 3*u*u*t*p1.x + 3*u*t*t*p2.x + t*t*t*p3.x
                val y = u*u*u*p0.y + 3*u*u*t*p1.y + 3*u*t*t*p2.y + t*t*t*p3.y
                var point = Offset(x, y)

                if (useMinimax && i > 0 && i < totalSegments) {
                    // Push point outward along normal by half max deviation
                    val dt = 1f / totalSegments
                    val E = (sqrt(3.0) / 4.0) * M * (dt * dt)
                    val offsetMagnitude = (E / 2f).toFloat()
                    
                    // compute tangent
                    val tx = 3*u*u*(p1.x-p0.x) + 6*u*t*(p2.x-p1.x) + 3*t*t*(p3.x-p2.x)
                    val ty = 3*u*u*(p1.y-p0.y) + 6*u*t*(p2.y-p1.y) + 3*t*t*(p3.y-p2.y)
                    val tLen = sqrt(tx*tx + ty*ty)
                    
                    // normal is (-ty, tx)
                    val nx = -ty / tLen
                    val ny = tx / tLen
                    
                    point = Offset(x + nx * offsetMagnitude, y + ny * offsetMagnitude)
                }

                segments.add(point)
            }

            val approxPath = Path().apply {
                moveTo(segments[0].x, segments[0].y)
                for (i in 1 until segments.size) {
                    lineTo(segments[i].x, segments[i].y)
                }
            }
            drawPath(approxPath, color = Color(0xFFFC2828), style = Stroke(width = 4f))
            
            // Draw segment points
            for (pt in segments) {
                drawCircle(color = Color.White, radius = 6f, center = pt)
            }
        }
    }
}
