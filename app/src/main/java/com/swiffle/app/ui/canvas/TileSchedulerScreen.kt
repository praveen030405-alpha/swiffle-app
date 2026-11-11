package com.swiffle.app.ui.canvas

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class TileType(val renderTimeMs: Long, val label: String, val color: Color) {
    TEXT(20L, "Text", Color.LightGray),
    BLANK(2L, "Blank", Color.White),
    VECTOR_HEAVY(300L, "CAD/Vector", Color(0xFF500000))
}

data class RenderTile(
    val id: Int,
    val type: TileType,
    val viewportDistance: Float, // V (Value is inversely proportional to distance)
    var isRendered: Boolean = false,
    var queuedOrder: Int = -1
)

@Composable
fun TileSchedulerScreen(onBack: () -> Unit = {}) {
    var useKnapsackScheduler by remember { mutableStateOf(true) }
    var tiles by remember { mutableStateOf<List<RenderTile>>(emptyList()) }
    var isRendering by remember { mutableStateOf(false) }
    var renderLog by remember { mutableStateOf(listOf<String>()) }
    val scope = rememberCoroutineScope()
    
    // Generate a simulated page of tiles
    fun generateTiles() {
        val newTiles = mutableListOf<RenderTile>()
        for (i in 0 until 12) { // 3x4 grid
            val distance = abs(i - 6).toFloat() // Simulate viewport is near the middle
            val type = when (i) {
                4, 5 -> TileType.VECTOR_HEAVY // A huge complex diagram in the middle
                2, 8 -> TileType.BLANK
                else -> TileType.TEXT
            }
            newTiles.add(RenderTile(id = i, type = type, viewportDistance = distance))
        }
        tiles = newTiles
        renderLog = listOf("Ready to render.")
    }

    LaunchedEffect(Unit) {
        generateTiles()
    }

    fun startRenderSim() {
        if (isRendering) return
        isRendering = true
        renderLog = emptyList()
        
        // Reset tiles
        tiles = tiles.map { it.copy(isRendered = false, queuedOrder = -1) }
        
        // Calculate GPU Queue Order
        val queue = if (useKnapsackScheduler) {
            // Alg 5: Sort by Value / Cost
            tiles.sortedByDescending { tile ->
                val value = 100f / (tile.viewportDistance + 1f) // Closer = higher value
                val cost = tile.type.renderTimeMs.toFloat()
                value / cost
            }
        } else {
            // Standard Engine: Sort purely by Distance to Viewport (Adobe/PDFium style)
            tiles.sortedBy { it.viewportDistance }
        }

        // Assign queue order for UI
        tiles = tiles.map { tile ->
            tile.copy(queuedOrder = queue.indexOfFirst { it.id == tile.id } + 1)
        }

        scope.launch {
            var timeElapsed = 0L
            val log = mutableListOf<String>()
            
            for (tileToRender in queue) {
                log.add("Rendering ${tileToRender.type.label} (Est: ${tileToRender.type.renderTimeMs}ms)...")
                renderLog = log.toList()
                
                // Simulate GPU Pipeline Block
                delay(tileToRender.type.renderTimeMs)
                timeElapsed += tileToRender.type.renderTimeMs
                
                // Mark as rendered
                tiles = tiles.map { 
                    if (it.id == tileToRender.id) it.copy(isRendered = true) else it 
                }
                
                log[log.lastIndex] = "Rendered ${tileToRender.type.label} in ${tileToRender.type.renderTimeMs}ms (Total: ${timeElapsed}ms)"
                renderLog = log.toList()
            }
            
            log.add("✅ Page Complete in ${timeElapsed}ms!")
            renderLog = log.toList()
            isRendering = false
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
            Text("Alg 5: V/C Tile Scheduler", color = Color.White, fontSize = 20.sp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (useKnapsackScheduler) "Mode: V/C Knapsack (Alg 5)" else "Mode: Distance-Only (Standard)", color = Color.LightGray)
            Switch(
                checked = useKnapsackScheduler,
                onCheckedChange = { useKnapsackScheduler = it; generateTiles() },
                modifier = Modifier.padding(start = 16.dp),
                enabled = !isRendering
            )
        }
        
        Button(
            onClick = { startRenderSim() },
            enabled = !isRendering,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFC2828))
        ) {
            Text("Simulate GPU Render Pipeline")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.weight(1f)) {
            // Visual Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tiles) { tile ->
                    val alpha by animateFloatAsState(
                        targetValue = if (tile.isRendered) 1f else 0.2f,
                        animationSpec = tween(300)
                    )
                    
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .border(1.dp, if (tile.queuedOrder > 0) Color.White else Color.DarkGray)
                            .background(tile.type.color.copy(alpha = alpha)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(tile.type.label, color = if (tile.type == TileType.VECTOR_HEAVY) Color.White else Color.Black, fontSize = 12.sp)
                            if (tile.queuedOrder > 0) {
                                Text("Q: #${tile.queuedOrder}", color = Color(0xFFFC2828), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
            
            // Console Log
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.Black)
                    .padding(8.dp)
            ) {
                Text("GPU PIPELINE LOG:", color = Color.Green, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                renderLog.forEach { log ->
                    Text(log, color = Color.LightGray, fontSize = 10.sp)
                }
            }
        }
    }
}
