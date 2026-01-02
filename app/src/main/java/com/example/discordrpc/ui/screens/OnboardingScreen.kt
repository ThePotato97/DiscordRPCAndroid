package com.example.discordrpc.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onGrantPermissions: () -> Unit,
    onConnectDiscord: (String) -> Unit,
    onFinish: () -> Unit,
    initialClientId: String = "1435558259892293662",
    isPermissionGranted: Boolean = false,
    isAuthorized: Boolean = false
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    var clientId by remember { mutableStateOf(initialClientId) }

    // Auto-advance if permissions granted or authorized
    LaunchedEffect(isPermissionGranted) {
        if (isPermissionGranted && pagerState.currentPage == 0) {
            pagerState.animateScrollToPage(1)
        }
    }

    LaunchedEffect(isAuthorized) {
        if (isAuthorized && pagerState.currentPage == 1) {
            pagerState.animateScrollToPage(2)
        }
    }

    Scaffold(
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(3) { iteration ->
                    val color = if (pagerState.currentPage == iteration) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.outlineVariant
                    
                    val width = if (pagerState.currentPage == iteration) 24.dp else 8.dp
                    
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .width(width)
                            .height(8.dp)
                    )
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            userScrollEnabled = false // Control via buttons
        ) { page ->
            when (page) {
                0 -> OnboardingStep(
                    icon = Icons.Default.Info,
                    title = "Give us a Hand",
                    description = "We need Notification access to detect what music or videos you are playing.",
                    buttonText = if (isPermissionGranted) "Permission Granted" else "Grant Permissions",
                    buttonEnabled = !isPermissionGranted,
                    onButtonClick = onGrantPermissions,
                    color = MaterialTheme.colorScheme.tertiary
                )
                1 -> OnboardingStep(
                    icon = Icons.Default.Link,
                    title = "Setup Discord",
                    description = "Enter your Discord Application ID to start.",
                    customContent = {
                        OutlinedTextField(
                            value = clientId,
                            onValueChange = { clientId = it },
                            label = { Text("Client ID") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                    },
                    buttonText = "Authorize with Discord",
                    onButtonClick = { onConnectDiscord(clientId) },
                    color = MaterialTheme.colorScheme.primary
                )
                2 -> OnboardingStep(
                    icon = Icons.Default.CheckCircle,
                    title = "You're All Set!",
                    description = "Discord RPC is ready to go. You can now choose which apps to track.",
                    buttonText = "Let's Go!",
                    onButtonClick = onFinish,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun OnboardingStep(
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    color: androidx.compose.ui.graphics.Color,
    buttonEnabled: Boolean = true,
    customContent: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = color
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        customContent?.let {
            Spacer(modifier = Modifier.height(24.dp))
            it()
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onButtonClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = buttonEnabled,
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(buttonText)
        }
    }
}
