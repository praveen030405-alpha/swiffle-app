package com.swiffle.app.ui.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class TouchPoint(val t: Long, val y: Float)

@Composable
fun PredictiveViewportScreen(onBack: () -> Unit = {}) {
    var scrollY by remember { mutableFloatStateOf(0f) }
    var predictedScrollY by remember { mutableFloatStateOf(0f) }
    var touchHistory by remember { mutableStateOf(listOf<TouchPoint>()) }
    var predictionTimeMs by remember { mutableFloatStateOf(200f) } // Predict 200ms into the future
    
    // Smooth trailing effect for the camera
    var actualCameraY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            actualCameraY += (scrollY - actualCameraY) * 0.3f
            delay(16)
        }
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
            Text("Alg 4: Predictive Viewport", color = Color.White, fontSize = 20.sp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Prediction Horizon: ${predictionTimeMs.toInt()} ms", color = Color.LightGray)
        Slider(
            value = predictionTimeMs,
            onValueChange = { predictionTimeMs = it },
            valueRange = 0f..500f,
            colors = SliderDefaults.colors(thumbColor = Color(0xFFFC2828), activeTrackColor = Color(0xFFFC2828))
        )
        Text("Drag up/down in the gray area.", color = Color.LightGray, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .background(Color.DarkGray)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            touchHistory = listOf(TouchPoint(System.currentTimeMillis(), scrollY))
                        },
                        onDragEnd = {
                            // Fling decay simulation could go here, but we focus on drag prediction
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        scrollY -= dragAmount.y // Scroll down moves viewport down (positive Y)
                        
                        val now = System.currentTimeMillis()
                        val newHistory = (touchHistory + TouchPoint(now, scrollY)).takeLast(3)
                        touchHistory = newHistory
                        
                        // Closed-form algebraic prediction (Taylor expansion / quadratic interpolation)
                        if (newHistory.size >= 3) {
                            val p1 = newHistory[0]
                            val p2 = newHistory[1]
                            val p3 = newHistory[2]
                            
                            val dt1 = (p2.t - p1.t).toFloat()
                            val dt2 = (p3.t - p2.t).toFloat()
                            
                            if (dt1 > 0 && dt2 > 0) {
                                val v1 = (p2.y - p1.y) / dt1
                                val v2 = (p3.y - p2.y) / dt2
                                
                                val a = (v2 - v1) / ((dt1 + dt2) / 2f)
                                
                                // Predict position at t = now + predictionTimeMs
                                val tFuture = predictionTimeMs
                                predictedScrollY = p3.y + v2 * tFuture + 0.5f * a * tFuture * tFuture
                            }
                        } else {
                            predictedScrollY = scrollY
                        }
                    }
                }
        ) {
            // Document background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3000.dp) // Very tall document
                    .offset(y = (-actualCameraY).dp)
                    .background(Color.White)
            ) {
                // Draw some text/lines to simulate a document
                for (i in 0..100) {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 30.dp).offset(y = (i * 100).dp).height(20.dp).background(Color.LightGray))
                }
                
                // Real Viewport Box (What the user currently sees)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = actualCameraY.dp)
                        .height(400.dp) // Arbitrary viewport height
                        .background(Color.Blue.copy(alpha = 0.2f))
                ) {
                    Text("Current Viewport", color = Color.Blue, modifier = Modifier.padding(8.dp))
                }

                // Predicted Viewport Box (What we should render NOW)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = predictedScrollY.dp)
                        .height(400.dp)
                        .background(Color(0xFFFC2828).copy(alpha = 0.4f))
                ) {
                    Text("Ghost: Predicted +${predictionTimeMs.toInt()}ms", color = Color(0xFF500000), modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}
