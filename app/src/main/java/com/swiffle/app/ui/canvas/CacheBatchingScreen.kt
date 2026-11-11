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
fun CacheBatchingScreen(onBack: () -> Unit = {}) {
    var useLruCache by remember { mutableStateOf(true) }
    
    // Simulate LRU Cache for fully rendered tiles
    val maxCacheSize = 3
    val tileCache = remember { mutableStateListOf<Int>() } 
    
    var currentPage by remember { mutableIntStateOf(1) }
    var pageState by remember { mutableStateOf("HIGH_RES") } // MIPMAP, HIGH_RES
    var logMessages by remember { mutableStateOf(listOf("Engine Started. Rendered Page 1.")) }
    
    val scope = rememberCoroutineScope()
    
    fun log(msg: String) {
        logMessages = (logMessages + msg).takeLast(6)
    }

    fun jumpToPage(page: Int) {
        if (pageState == "MIPMAP") return // Currently rendering
        
        currentPage = page
        
        if (useLruCache && tileCache.contains(page)) {
            // Alg 7: Direct Cache Hit! Zero latency high-res!
            log("⚡ CACHE HIT (Page $page). Skipped GPU. Load time: 0ms.")
            pageState = "HIGH_RES"
            
            // Move to most recently used
            tileCache.remove(page)
            tileCache.add(page)
        } else {
            // Cache Miss. Fallback to Alg 6 (Mipmap Warming)
            log("❌ CACHE MISS (Page $page). Pulling 1:16 mipmap...")
            pageState = "MIPMAP"
            
            scope.launch {
                delay(600) // GPU rendering the high-res tile...
                log("✅ GPU Render Complete. Caching Page $page.")
                pageState = "HIGH_RES"
                
                // Add to LRU Cache
                if (useLruCache) {
                    if (tileCache.size >= maxCacheSize) {
                        val evicted = tileCache.removeAt(0)
                        log("🗑️ Cache full. Evicted Page $evicted.")
                    }
                    if (!tileCache.contains(page)) {
                        tileCache.add(page)
                    }
                }
            }
        }
    }
    
    // Initialize cache with page 1
    LaunchedEffect(Unit) {
        tileCache.add(1)
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
            Text("Alg 7: LRU Tile Cache", color = Color.White, fontSize = 20.sp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (useLruCache) "Mode: Active LRU Cache (Max: 3)" else "Mode: Cache Disabled", color = Color.LightGray)
            Switch(
                checked = useLruCache,
                onCheckedChange = { 
                    useLruCache = it 
                    if (!it) tileCache.clear()
                },
                modifier = Modifier.padding(start = 16.dp)
            )
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            (1..5).forEach { pageNum ->
                Button(
                    onClick = { jumpToPage(pageNum) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (currentPage == pageNum) Color(0xFFFC2828) else Color.DarkGray),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("P$pageNum")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Document Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.DarkGray)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = pageState, 
                animationSpec = tween(durationMillis = 300),
                label = "PageCrossfade"
            ) { state ->
                when (state) {
                    "MIPMAP" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White)
                                .blur(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text("PAGE $currentPage", fontSize = 48.sp, color = Color.LightGray, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(20.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(20.dp).background(Color.LightGray))
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(20.dp).background(Color.LightGray))
                            }
                        }
                    }
                    "HIGH_RES" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White)
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text("PAGE $currentPage", fontSize = 48.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(20.dp))
                                Text("High-Resolution Vector Graphics", color = Color(0xFF500000), fontSize = 20.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("This page is fully loaded in memory. If pulled from the LRU Cache, it required exactly 0ms of GPU compute to display on your screen.", color = Color.DarkGray, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
        
        // Console Log
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Color.Black)
                .padding(8.dp)
        ) {
            Text("CACHE ENGINE LOG:", color = Color.Green, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            logMessages.forEach { log ->
                Text(log, color = if (log.contains("HIT")) Color.Cyan else if (log.contains("MISS")) Color.Yellow else Color.LightGray, fontSize = 11.sp)
            }
        }
    }
}
