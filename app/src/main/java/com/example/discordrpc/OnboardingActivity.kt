package com.example.discordrpc

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.discord.socialsdk.DiscordSocialSdkInit
import com.example.discordrpc.ui.screens.OnboardingScreen
import com.example.discordrpc.ui.theme.DiscordRPCTheme

class OnboardingActivity : ComponentActivity() {

    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("discord_rpc_prefs", MODE_PRIVATE)
        
        // Ensure tokens are saved when received
        DiscordGateway.tokenSaver = { access, refresh ->
            prefs.edit()
                .putString("auth_access_token", access)
                .putString("auth_refresh_token", refresh)
                .apply()
        }

        setContent {
            DiscordRPCTheme {
                var isPermissionGranted by remember { mutableStateOf(isNotificationServiceEnabled()) }
                var isAuthorized by remember { mutableStateOf(prefs.getBoolean("is_authorized", false)) }
                val initialClientId = prefs.getString("global_client_id", "1435558259892293662") ?: "1435558259892293662"

                // Check permissions periodically on resume
                LaunchedEffect(Unit) {
                    // This is handled by onResume updating the state if needed
                }

                OnboardingScreen(
                    onGrantPermissions = { requestEssentialPermissions() },
                    onConnectDiscord = { clientIdStr ->
                        val clientId = clientIdStr.toLongOrNull()
                        if (clientId != null) {
                            prefs.edit().putString("global_client_id", clientIdStr).apply()
                            DiscordGateway.initDiscord(clientId)
                            DiscordSocialSdkInit.setEngineActivity(this)
                            DiscordGateway.startAuthorization()
                        } else {
                            Toast.makeText(this, "Invalid Client ID", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFinish = {
                        prefs.edit().putBoolean("onboarding_completed", true).apply()
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    initialClientId = initialClientId,
                    isPermissionGranted = isPermissionGranted,
                    isAuthorized = isAuthorized
                )
                
                // Track state changes from within the activity lifecycle
                DisposableEffect(Unit) {
                    val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
                        override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                            isPermissionGranted = isNotificationServiceEnabled()
                            isAuthorized = prefs.getBoolean("is_authorized", false)
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
            }
        }

        handleIntent(intent)
    }

    private fun requestEssentialPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        
        if (!isNotificationServiceEnabled()) {
            Toast.makeText(this, "Please enable the Discord RPC listener", Toast.LENGTH_LONG).show()
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(pkgName)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null && data.scheme == "discordrpc") {
            val code = data.getQueryParameter("code")
            if (code != null) {
                DiscordGateway.handleOAuthCallback(code, data.toString())
                prefs.edit().putBoolean("is_authorized", true).apply()
                Toast.makeText(this, "Linked Successfully!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
