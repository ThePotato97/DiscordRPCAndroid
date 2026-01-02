package com.example.discordrpc.ui.screens

import com.frosch2010.fuzzywuzzy_kotlin.Ratio
import com.frosch2010.fuzzywuzzy_kotlin.diffutils.DiffUtils
import android.graphics.drawable.Drawable
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.discordrpc.ui.components.AppCard
import com.example.discordrpc.ui.components.StatusCard
import com.frosch2010.fuzzywuzzy_kotlin.FuzzySearch

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: Drawable?,
    val isEnabled: Boolean,
    val activityType: Int
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(
    status: String,
    details: String,
    image: String? = null,
    start: Long = 0,
    end: Long = 0,
    user: com.example.discordrpc.models.DiscordUser? = null,
    apps: List<AppItem>,
    isLoading: Boolean = false,
    onAppToggled: (String, Boolean) -> Unit,
    onActivityTypeChanged: (String, Int) -> Unit,
    onLogout: () -> Unit
) {
    android.util.Log.i("MainScreen", "Recomposing with user: ${user?.username ?: "NULL"}")
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val filteredApps by remember(searchQuery) {
        derivedStateOf {
            val query = searchQuery.trim()
            if (query.isBlank()) {
                apps
            } else {
                // Use a cutoff to hide irrelevant matches
                apps.asSequence()
                    .map { app ->
                        val target = "${app.name} ${app.packageName}"
                        val score = FuzzySearch.partialRatio(query, target)
                        app to score
                    }
                    .filter { it.second >= 50 }
                    .sortedByDescending { it.second }
                    .map { it.first }
                    .take(10)
                    .toList()
            }
        }
    }

    val displayList by remember(searchQuery) {
        derivedStateOf {
            if (searchQuery.isNotBlank()) {
                filteredApps
            } else {
                apps
            }
        }
    }


    // Scroll to top when search results change
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            listState.scrollToItem(0)
            searchListState.scrollToItem(0)
        }
    }

    val enabledApps by remember(apps) {
        derivedStateOf { apps.filter { it.isEnabled } }
    }

    val jumpToApp: (AppItem) -> Unit = { app ->
        val targetPackageName = app.packageName
        searchActive = false
        searchQuery = "" // Clear search so list restores before scroll
        coroutineScope.launch {
            val index = if (app.isEnabled) {
                1 + enabledApps.indexOfFirst { it.packageName == targetPackageName }
            } else {
                val offset = if (enabledApps.isNotEmpty()) (enabledApps.size + 3) else 1
                offset + apps.indexOfFirst { it.packageName == targetPackageName }
            }
            if (index >= 0) {
                listState.animateScrollToItem(index)
            }
        }
    }

    val searchBarPadding by animateDpAsState(
        targetValue = if (searchActive) 0.dp else 16.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "searchBarPadding"
    )

    val searchBarYOffset by animateDpAsState(
        targetValue = if (searchActive) 0.dp else 140.dp, // Offset to sit below StatusCard
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "searchBarYOffset"
    )
    
    // Handle back button when search is active
    androidx.activity.compose.BackHandler(enabled = searchActive) {
        searchActive = false
        searchQuery = ""
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("Discord RPC") },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Filled.Logout, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Dashboard Content (Fades slightly when search is active)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        alpha = animateFloatAsState(
                            if (searchActive) 0.5f else 1f,
                            label = "dashboardAlpha"
                        ).value
                    )
            ) {
                StatusCard(
                    status = status,
                    details = details
                )

                // Gap for the SearchBar integrated position
                Spacer(modifier = Modifier.height(88.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.width(48.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        // Active Apps Section
                        if (enabledApps.isNotEmpty() && searchQuery.isBlank()) {
                            item {
                                Text(
                                    text = "Active Apps",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                                )
                            }

                            items(enabledApps, key = { "section_active_${it.packageName}" }) { app ->
                                Box(modifier = Modifier.animateItem()) {
                                    AppCard(
                                        appName = app.name,
                                        packageName = app.packageName,
                                        icon = app.icon,
                                        isChecked = app.isEnabled,
                                        onCheckedChange = { enabled ->
                                            onAppToggled(app.packageName, enabled)
                                        },
                                        activityType = app.activityType,
                                        onActivityTypeChange = { type ->
                                            onActivityTypeChanged(app.packageName, type)
                                        }
                                    )
                                }
                            }

                            item {
                                HorizontalDivider(
                                    modifier = Modifier.padding(
                                        horizontal = 24.dp,
                                        vertical = 16.dp
                                    ),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }

                        // All Applications Header
                        item {
                            val headerText =
                                if (searchQuery.isNotBlank()) "Search Results" else "All Applications"
                            Text(
                                text = headerText,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                            )
                        }

                        items(displayList, key = { "section_all_${it.packageName}" }) { app ->
                            Box(modifier = Modifier.animateItem()) {
                                AppCard(
                                    appName = app.name,
                                    packageName = app.packageName,
                                    icon = app.icon,
                                    isChecked = app.isEnabled,
                                    onCheckedChange = { enabled ->
                                        onAppToggled(app.packageName, enabled)
                                    },
                                    activityType = app.activityType,
                                    onActivityTypeChange = { type ->
                                        onActivityTypeChanged(app.packageName, type)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // The Floating Expressive Search Bar (Top Layer)
            Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = searchBarPadding)
                        .offset(y = searchBarYOffset)
                        .animateContentSize()
                ) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = { searchActive = false },
                        active = searchActive,
                        onActiveChange = { searchActive = it },
                        placeholder = {
                            Text(
                                "Search apps...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty() || searchActive) {
                                IconButton(onClick = {
                                    if (searchQuery.isNotEmpty()) searchQuery = ""
                                    else searchActive = false
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        colors = SearchBarDefaults.colors(
                            containerColor = if (searchActive) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            inputFieldColors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                errorContainerColor = Color.Transparent
                            )
                        ),
                        shape = if (searchActive) RectangleShape else MaterialTheme.shapes.extraLarge
                    ) {
                        LazyColumn(
                            state = searchListState,
                            contentPadding = PaddingValues(
                                top = 16.dp,
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 100.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                Card(
                                    onClick = { jumpToApp(app) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ),
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        app.icon?.let {
                                            Image(
                                                bitmap = it.toBitmap().asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column {
                                            Text(
                                                text = app.name,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = app.packageName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

