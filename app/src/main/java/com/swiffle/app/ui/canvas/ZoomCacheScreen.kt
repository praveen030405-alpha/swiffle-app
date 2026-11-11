package com.swiffle.app.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

// Alg 2: Zoom-Invariant Curve Cache & Vector Mipmaps
data class CubicCurve(val p0: Offset, val p1: Offset, val p2: Offset, val p3: Offset, val color: Color) {
    val mUser: Float
    val bounds: Rect

    init {
        val v1x = p0.x - 2 * p1.x + p2.x
        val v1y = p0.y - 2 * p1.y + p2.y
        val v2x = p1.x - 2 * p2.x + p3.x
        val v2y = p1.y - 2 * p2.y + p3.y
        val m1 = sqrt(v1x * v1x + v1y * v1y)
        val m2 = sqrt(v2x * v2x + v2y * v2y)
        mUser = max(m1, m2)
        
        // Simple bounding box of control points (approximate)
        val minX = minOf(p0.x, p1.x, p2.x, p3.x)
        val minY = minOf(p0.y, p1.y, p2.y, p3.y)
        val maxX = maxOf(p0.x, p1.x, p2.x, p3.x)
        val maxY = maxOf(p0.y, p1.y, p2.y, p3.y)
        bounds = Rect(minX, minY, maxX, maxY)
    }
}

data class VectorNode(
    val curves: List<CubicCurve>,
    val bounds: Rect,
    val averageColor: Color
)

@Composable
fun ZoomCacheScreen(onBack: () -> Unit = {}) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var useVectorMipmaps by remember { mutableStateOf(true) }
    
    // Generate a complex cluster of curves (like a CAD drawing or map)
    val mapNode = remember {
        val curves = mutableListOf<CubicCurve>()
        val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Cyan, Color.Magenta, Color.Yellow)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        
        for (i in 0 until 100) {
            val cx = (Math.random() * 1000).toFloat()
            val cy = (Math.random() * 1000).toFloat()
            val size = 50f
            val c = CubicCurve(
                Offset(cx, cy),
                Offset(cx + size, cy - size),
                Offset(cx + size, cy + size),
                Offset(cx + size * 2, cy)
            , colors[i % colors.size])
            
            curves.add(c)
            minX = minOf(minX, c.bounds.left)
            minY = minOf(minY, c.bounds.top)
            maxX = maxOf(maxX, c.bounds.right)
            maxY = maxOf(maxY, c.bounds.bottom)
        }
        VectorNode(curves, Rect(minX, minY, maxX, maxY), Color.Gray)
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack, 
                    contentDescription = "Back", 
                    tint = Color.White
                )
            }
            Text("Alg 2: Zoom-Invariant & Mipmaps", color = Color.White, fontSize = 20.sp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enable Vector Mipmaps", color = Color.LightGray)
            Switch(
                checked = useVectorMipmaps,
                onCheckedChange = { useVectorMipmaps = it },
                modifier = Modifier.padding(start = 16.dp)
            )
        }
        
        Text("Zoom: ${"%.2f".format(scale)}x", color = Color.LightGray)
        Text("Pinch to zoom / Drag to pan", color = Color(0xFFFC2828), fontSize = 12.sp)

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier
            .fillMaxSize()
            .weight(1f)
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val oldScale = scale
                    scale = (scale * zoom).coerceIn(0.01f, 50f)
                    val actualZoom = scale / oldScale
                    offset = (offset - centroid) * actualZoom + centroid + pan
                }
            }
        ) {
            Canvas(modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                )
            ) {
                val tau = 300f // Threshold increased so it triggers easily when zoomed out
                
                // Check if node is too small to render individual curves
                val nodeScreenExtent = mapNode.bounds.width * scale
                if (useVectorMipmaps && nodeScreenExtent <= tau) {
                        // Draw aggregate mipmap (just a simple rect of the average color)
                        drawRect(
                            color = mapNode.averageColor,
                            topLeft = mapNode.bounds.topLeft,
                            size = mapNode.bounds.size
                        )
                    } else {
                        // Draw individual curves using Zoom-Invariant cached formula
                        val epsilon = 0.5f // Half a pixel error budget
                        
                        var totalSegmentsComputed = 0
                        
                        for (curve in mapNode.curves) {
                            // n(s) = ⌈ √( 0.75 · σ_max(A) · M_user / ε ) ⌉
                            // σ_max(A) is simply the scale factor
                            val rawSegments = sqrt(0.75f * scale * curve.mUser / epsilon)
                            val segments = max(1, ceil(rawSegments).toInt())
                            totalSegmentsComputed += segments
                            
                            val approxPath = Path().apply {
                                moveTo(curve.p0.x, curve.p0.y)
                                for (i in 1..segments) {
                                    val t = i.toFloat() / segments
                                    val u = 1f - t
                                    val x = u*u*u*curve.p0.x + 3*u*u*t*curve.p1.x + 3*u*t*t*curve.p2.x + t*t*t*curve.p3.x
                                    val y = u*u*u*curve.p0.y + 3*u*u*t*curve.p1.y + 3*u*t*t*curve.p2.y + t*t*t*curve.p3.y
                                    lineTo(x, y)
                                }
                            }
                            drawPath(approxPath, color = curve.color, style = Stroke(width = 2f / scale))
                        }
                    }
            }
        }
    }
}
