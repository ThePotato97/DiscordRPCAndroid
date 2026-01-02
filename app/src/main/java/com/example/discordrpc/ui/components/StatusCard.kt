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
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@Composable
fun StatusCard(
    status: String,
    details: String,
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
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Rich Presence Preview
            Text(
                text = "PLAYING A GAME",
                color = Color(0xFFB5BAC1),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
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
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1F22))
                    )
                } else {
                     Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF5865F2)), // Blurple
                        contentAlignment = Alignment.Center
                    ) {
                        Text("RP", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    // App Name (Header)
                    val appName = if (status.startsWith("Playing")) details.split(":").firstOrNull() ?: "App" else "Android App"
                    // Extract AppName from Details if formatted as "AppName: Details"
                    // Wait, My Service sends "Discord RPC: Playing" as Title(Status) and "AppName: Details" as Text(Details).
                    // status = "Playing" (from Activity)
                    // details = "AppName: Details" (from Notification Text)
                    // This data flow is a bit messy. Let's parse it best effort.
                    
                    Text(
                        text = "Discord RPC", // Activity Name override
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Details (Top Line)
                    // The 'details' param passed in contains "AppName: Title".
                    // I should probably pass raw lines if I want perfect preview.
                    // But for now, let's just show what we have.
                    Text(
                        text = details,
                        color = Color(0xFFDBDEE1),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    
                    // State (Bottom Line)
                    // We aren't passing state specifically to MainScreen (only Details/Status).
                    // Wait, I updated MainScreen to take `details` (notification text).
                    // In `DiscordMediaService`, notification text is "AppName: Details".
                    // And I broadcast `currentDetails` which is "AppName: Details".
                    // I need to separate them if I want a true preview.
                    // But for now, I'll render what I have.
                    
                    Text(
                        text = status, 
                        color = Color(0xFFB5BAC1),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    
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
