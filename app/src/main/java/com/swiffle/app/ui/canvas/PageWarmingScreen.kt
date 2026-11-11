package com.swiffle.app.ui.canvas

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PageWarmingScreen(onBack: () -> Unit = {}) {
    var usePageWarming by remember { mutableStateOf(true) }
    var pageState by remember { mutableStateOf("NOT_LOADED") } // NOT_LOADED, MIPMAP, HIGH_RES
    var pageNumber by remember { mutableIntStateOf(1) }
    
    val scope = rememberCoroutineScope()
    
    fun jumpToRandomPage() {
        if (pageState == "LOADING") return
        pageNumber = (2..999).random()
        
        scope.launch {
            if (usePageWarming) {
                // Alg 6: Show pre-rendered 1:16 mipmap instantly (0ms)
                pageState = "MIPMAP"
                delay(600) // Simulate GPU rendering high-res tiles in background
                pageState = "HIGH_RES"
            } else {
                // Standard engine: White screen until GPU finishes
                pageState = "NOT_LOADED"
                delay(600) // Simulate GPU rendering high-res tiles in background
                pageState = "HIGH_RES"
            }
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
            Text("Alg 6: Page Warming", color = Color.White, fontSize = 20.sp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (usePageWarming) "Mode: 1:16 Mipmap (Alg 6)" else "Mode: White Screen (Standard)", color = Color.LightGray)
            Switch(
                checked = usePageWarming,
                onCheckedChange = { usePageWarming = it },
                modifier = Modifier.padding(start = 16.dp)
            )
        }
        
        Button(
            onClick = { jumpToRandomPage() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFC2828)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Jump to Random Page")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Document Viewport
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .background(Color.DarkGray)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = pageState, 
                animationSpec = tween(durationMillis = 300),
                label = "PageCrossfade"
            ) { state ->
                when (state) {
                    "NOT_LOADED" -> {
                        // Standard Engine: Blank white page
                        Box(modifier = Modifier.fillMaxSize().background(Color.White))
                    }
                    "MIPMAP" -> {
                        // Alg 6: Pre-rendered thumbnail stretched and blurred
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White)
                                .blur(12.dp) // Simulate low-res upscaling
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text("PAGE $pageNumber", fontSize = 48.sp, color = Color.LightGray, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(20.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(20.dp).background(Color.LightGray))
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(20.dp).background(Color.LightGray))
                                Spacer(modifier = Modifier.height(40.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(Color.Gray.copy(alpha = 0.3f)))
                            }
                        }
                    }
                    "HIGH_RES" -> {
                        // Fully rendered high-resolution page
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White)
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text("PAGE $pageNumber", fontSize = 48.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(20.dp))
                                Text("This is crisp, high-resolution text rendered perfectly by the GPU after the psychological illusion of the mipmap bought us exactly 600ms of compute time.", color = Color.Black, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(40.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(Color(0xFF500000)), contentAlignment = Alignment.Center) {
                                    Text("High-Res CAD Diagram", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
