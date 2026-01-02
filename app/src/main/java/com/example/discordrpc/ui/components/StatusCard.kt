package com.example.discordrpc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.discordrpc.models.DiscordUser
import com.example.discordrpc.models.ActivityType
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@Composable
fun StatusCard(
    status: String,
    details: String,
    state: String? = null,
    appName: String? = null,
    activityType: Int = ActivityType.LISTENING.value,
    image: String? = null,
    start: Long = 0,
    end: Long = 0,
    user: DiscordUser? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2B2D31) // Discord Dark Theme Color
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Activity Header
            val type = ActivityType.fromInt(activityType)
            val headerText = when (type) {
                ActivityType.PLAYING -> "PLAYING"
                ActivityType.STREAMING -> "STREAMING"
                ActivityType.LISTENING -> "LISTENING"
                ActivityType.WATCHING -> "WATCHING"
                else -> "PLAYING"
            }
            
            Text(
                text = headerText,
                color = Color(0xFFB5BAC1),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Large Image
                if (!image.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(image)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Presence Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1F22))
                    )
                } else {
                     Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF5865F2)), // Blurple
                        contentAlignment = Alignment.Center
                    ) {
                        Text("RP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    // Line 1: App Name / Title (Bold)
                    Text(
                        text = appName ?: "Discord RPC",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    
                    // Line 2: Details
                    if (!details.isNullOrEmpty()) {
                        Text(
                            text = details,
                            color = Color(0xFFDBDEE1),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                    }
                    
                    // Line 3: State
                    if (!state.isNullOrEmpty()) {
                        Text(
                            text = state, 
                            color = Color(0xFFDBDEE1),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                    }
                    
                    // Timer
                    if (start > 0 || end > 0) {
                        PresenceTimer(start, end)
                    }
                }
            }
        }
    }
}

@Composable
fun PresenceTimer(start: Long, end: Long) {
    var timeText by remember { mutableStateOf("") }
    
    LaunchedEffect(start, end) {
        if (end > 0) {
            // Time Left
            while (true) {
                val now = System.currentTimeMillis()
                val left = end - now
                if (left < 0) {
                    timeText = "00:00"
                    break
                }
                timeText = formatTime(left) + " left"
                delay(1000)
            }
        } else if (start > 0) {
            // Elapsed
            while (true) {
                val now = System.currentTimeMillis()
                val elapsed = now - start
                timeText = formatTime(elapsed) + " elapsed"
                delay(1000)
            }
        }
    }
    
    Text(
        text = timeText,
        color = Color(0xFFB5BAC1),
        style = MaterialTheme.typography.bodySmall
    )
}

fun formatTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60))
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
