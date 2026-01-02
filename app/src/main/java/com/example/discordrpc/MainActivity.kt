package com.example.discordrpc

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.discord.socialsdk.DiscordSocialSdkInit
import com.example.discordrpc.ui.screens.AppItem
import com.example.discordrpc.models.ActivityType
import com.example.discordrpc.ui.screens.MainScreen
import com.example.discordrpc.ui.theme.DiscordRPCTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    
    private lateinit var prefs: android.content.SharedPreferences
    
    // Data
    private val PREFS_NAME = "discord_rpc_prefs"
    private val KEY_ALLOWED_APPS = "allowed_apps"


    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val onboardingCompleted = prefs.getBoolean("onboarding_completed", false)

        if (!onboardingCompleted) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        // Init Discord
        DiscordSocialSdkInit.setEngineActivity(this)
        val clientId = prefs.getString("global_client_id", "1435558259892293662")?.toLongOrNull() ?: 1435558259892293662L
        DiscordGateway.initDiscord(clientId)
        
        // Handle Token Persistence
        DiscordGateway.tokenSaver = { access, refresh ->
            Log.i("MainActivity", "Saving auth tokens")
            prefs.edit()
                .putString("auth_access_token", access)
                .putString("auth_refresh_token", refresh)
                .apply()
        }
        
        // Restore Session if exists
        val savedAccess = prefs.getString("auth_access_token", null)
        val savedRefresh = prefs.getString("auth_refresh_token", null)
        
        if (savedAccess != null && savedRefresh != null) {
            DiscordGateway.restoreSession(savedAccess, savedRefresh)
        }
        
        // Set Compose content
        setContent {
            DiscordRPCTheme {
                val apps = remember { mutableStateListOf<com.example.discordrpc.ui.screens.AppItem>() }
                var isLoading by remember { mutableStateOf(true) }
                var statusText by remember { mutableStateOf(DiscordMediaService.currentStatus ?: "Connected to Discord") }
                var detailsText by remember { mutableStateOf(DiscordMediaService.currentDetails ?: "Waiting for service...") }
                var stateText by remember { mutableStateOf(DiscordMediaService.currentState ?: "") }
                var appNameText by remember { mutableStateOf(DiscordMediaService.currentAppName ?: "Discord RPC") }
                var activityTypeText by remember { mutableIntStateOf(ActivityType.LISTENING.value) }
                var imageKey by remember { mutableStateOf(DiscordMediaService.currentImage) }
                var startTime by remember { mutableStateOf(0L) }
                var endTime by remember { mutableIntStateOf(0).let { mutableStateOf(0L) } }
                
                // RPC State
                var isRpcEnabled by remember { 
                    mutableStateOf(prefs.getBoolean(DiscordMediaService.KEY_RPC_ENABLED, true)) 
                }
                
                // User State
                var currentUser by remember { mutableStateOf(DiscordGateway.currentUser) }
                
                // Stream apps in
                LaunchedEffect(Unit) {
                    sendBroadcast(Intent(DiscordMediaService.ACTION_REFRESH_SESSIONS))
                    
                    val pm = packageManager
                    val allowedApps = getAllowedApps()

                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            // 1. Fast textual fetch & sort
                            val installed = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                            
                            // Popular media apps to prioritize
                            val priorityMediaPackages = setOf(
                                "com.google.android.youtube",
                                "com.spotify.music",
                                "tv.twitch.android.app",
                                "com.netflix.mediaclient",
                                "com.disney.disneyplus",
                                "com.hulu.plus",
                                "com.amazon.avod.thirdpartyclient",
                                "com.google.android.apps.youtube.music",
                                "com.soundcloud.android",
                                "deezer.android.app",
                                "com.pandora.android",
                                "com.tidal.android",
                                "com.stremio.one",
                                "app.revanced.android.youtube",
                                "app.revanced.android.youtube.music",
                                "app.revanced.android.twitch"
                            )

                            fun getAppPriority(app: android.content.pm.ApplicationInfo): Int {
                                // 1. Priority Media Apps (Top)
                                if (priorityMediaPackages.contains(app.packageName)) return 0
                                
                                // Check Category (API 26+)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    if (app.category == android.content.pm.ApplicationInfo.CATEGORY_AUDIO ||
                                        app.category == android.content.pm.ApplicationInfo.CATEGORY_VIDEO) {
                                        return 1
                                    }
                                }
                                
                                // 2. User Apps (Middle)
                                if ((app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) return 2
                                
                                // 3. System Apps (Bottom)
                                return 3
                            }

                            val sortedPackages = installed
                                .map { it to pm.getApplicationLabel(it).toString() }
                                .sortedWith(
                                    compareBy<Pair<android.content.pm.ApplicationInfo, String>> { (app, _) -> 
                                        getAppPriority(app) 
                                    }.thenBy { (_, label) -> label }
                                )

                            // Initial fetch done, hide big spinner so streaming is visible
                            withContext(kotlinx.coroutines.Dispatchers.Main) { 
                                isLoading = false 
                            }

                            // 2. Stream loads
                            val batch = mutableListOf<com.example.discordrpc.ui.screens.AppItem>()
                            
                            sortedPackages.forEachIndexed { index, (appInfo, label) ->
                                try {
                                    val item = com.example.discordrpc.ui.screens.AppItem(
                                        name = label,
                                        packageName = appInfo.packageName,
                                        icon = pm.getApplicationIcon(appInfo),
                                        isEnabled = allowedApps.contains(appInfo.packageName),
                                        activityType = prefs.getInt("app_type_${appInfo.packageName}", 2)
                                    )
                                    batch.add(item)

                                    // Emit every 5 items or at the end
                                    if (batch.size >= 5 || index == sortedPackages.lastIndex) {
                                        val chunk = batch.toList()
                                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            apps.addAll(chunk)
                                        }
                                        batch.clear()
                                    }
                                } catch (e: Exception) {
                                    // Skip failed apps
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error streaming apps", e)
                            withContext(kotlinx.coroutines.Dispatchers.Main) { isLoading = false }
                        }
                    }
                }

                // User Callback
                DisposableEffect(Unit) {
                    DiscordGateway.startUserCallback = { name, disc, id, avatar ->
                        Log.i("MainActivity", "✅ Received user update in UI: $name (Thread: ${Thread.currentThread().name})")
                        runOnUiThread {
                            val newUser = com.example.discordrpc.models.DiscordUser(name, disc, id, avatar)
                            currentUser = newUser
                        }
                    }
                    
                    // NOW trigger connection - callback is ready
                    DiscordGateway.connect()
                    
                    onDispose { DiscordGateway.startUserCallback = null }
                }

                // Stream apps in
// ... (lines 92-185 preserved automatically by context match if I skip them, but simpler to just focus on the Receiver part)

                // Observe broadcast for status updates
                DisposableEffect(Unit) {
                    val receiver = object : android.content.BroadcastReceiver() {
                        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                            if (intent.action == DiscordMediaService.ACTION_STATUS_UPDATE) {
                                statusText = intent.getStringExtra(DiscordMediaService.EXTRA_STATUS) ?: statusText
                                detailsText = intent.getStringExtra(DiscordMediaService.EXTRA_DETAILS) ?: detailsText
                                stateText = intent.getStringExtra(DiscordMediaService.EXTRA_STATE) ?: ""
                                appNameText = intent.getStringExtra(DiscordMediaService.EXTRA_APP_NAME) ?: "Discord RPC"
                                activityTypeText = intent.getIntExtra(DiscordMediaService.EXTRA_ACTIVITY_TYPE, ActivityType.LISTENING.value)
                                imageKey = intent.getStringExtra(DiscordMediaService.EXTRA_IMAGE)
                                startTime = intent.getLongExtra(DiscordMediaService.EXTRA_START_TIME, 0L)
                                endTime = intent.getLongExtra(DiscordMediaService.EXTRA_END_TIME, 0L)
                            }
                        }
                    }

                    val filter = android.content.IntentFilter().apply {
                        addAction(DiscordMediaService.ACTION_STATUS_UPDATE)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
                    } else {
                        registerReceiver(receiver, filter)
                    }
                    onDispose { unregisterReceiver(receiver) }
                }

                MainScreen(
                    isRpcEnabled = isRpcEnabled,
                    onRpcToggle = { enabled ->
                        Log.i("MainActivity", "Global RPC Toggled: $enabled")
                        isRpcEnabled = enabled
                        prefs.edit().putBoolean(DiscordMediaService.KEY_RPC_ENABLED, enabled).apply()
                        
                        if (enabled) {
                            // Re-initialize Discord SDK since it was shut down
                            val clientId = prefs.getString("global_client_id", "1435558259892293662")?.toLongOrNull() ?: 1435558259892293662L
                            DiscordGateway.initDiscord(clientId)
                            
                            val savedAccess = prefs.getString("auth_access_token", null)
                            val savedRefresh = prefs.getString("auth_refresh_token", null)
                            if (savedAccess != null && savedRefresh != null) {
                                DiscordGateway.restoreSession(savedAccess, savedRefresh)
                            }
                            DiscordGateway.connect()
                        }

                        // Notify service to refresh (it reads prefs on next update)
                        sendBroadcast(Intent(DiscordMediaService.ACTION_REFRESH_SESSIONS))
                    },
                    status = statusText,
                    details = detailsText,
                    state = stateText,
                    appName = appNameText,
                    activityType = activityTypeText,
                    image = imageKey,
                    start = startTime,
                    end = endTime,
                    user = currentUser,
                    apps = apps,
                    isLoading = isLoading,
                    onAppToggled = { packageName, enabled ->
                        updateAppSelection(packageName, enabled)
                        // Trigger service refresh
                        sendBroadcast(Intent(DiscordMediaService.ACTION_REFRESH_SESSIONS))
                        // Refresh the item in the list
                        val index = apps.indexOfFirst { it.packageName == packageName }
                        if (index != -1) {
                            apps[index] = apps[index].copy(isEnabled = enabled)
                        }
                    },
                    onActivityTypeChanged = { packageName, type ->
                        updateActivityType(packageName, type)
                        // Trigger service refresh
                        sendBroadcast(Intent(DiscordMediaService.ACTION_REFRESH_SESSIONS))
                        // Refresh the item in the list
                        val index = apps.indexOfFirst { it.packageName == packageName }
                        if (index != -1) {
                            apps[index] = apps[index].copy(activityType = type)
                        }
                    },
                    onLogout = {
                        prefs.edit().clear().apply()
                        DiscordGateway.shutdownDiscord()
                        startActivity(Intent(this@MainActivity, OnboardingActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
    
    
    private fun updateAppSelection(packageName: String, enabled: Boolean) {
        val allowedApps = getAllowedApps().toMutableSet()
        if (enabled) {
            allowedApps.add(packageName)
        } else {
            allowedApps.remove(packageName)
        }
        saveAllowedApps(allowedApps)
    }
    
    private fun updateActivityType(packageName: String, type: Int) {
        prefs.edit().putInt("app_type_$packageName", type).apply()
    }
    
    private fun getAllowedApps(): Set<String> {
        return prefs.getStringSet(KEY_ALLOWED_APPS, emptySet()) ?: emptySet()
    }
    
    private fun saveAllowedApps(apps: Set<String>) {
        prefs.edit().putStringSet(KEY_ALLOWED_APPS, apps).apply()
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(pkgName)
    }
}
