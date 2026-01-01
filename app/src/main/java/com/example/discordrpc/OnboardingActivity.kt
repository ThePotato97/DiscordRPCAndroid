package com.example.discordrpc

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import com.discord.socialsdk.DiscordSocialSdkInit
import com.google.android.material.textfield.TextInputEditText

class OnboardingActivity : AppCompatActivity() {

    private lateinit var flipper: ViewFlipper
    private lateinit var dot1: View
    private lateinit var dot2: View
    private lateinit var dot3: View
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var inputClientId: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        prefs = getSharedPreferences("discord_rpc_prefs", MODE_PRIVATE)

        flipper = findViewById(R.id.onboarding_flipper)
        dot1 = findViewById(R.id.dot1)
        dot2 = findViewById(R.id.dot2)
        dot3 = findViewById(R.id.dot3)
        inputClientId = findViewById(R.id.input_client_id)

        // Pre-fill Client ID if exists
        val savedClientId = prefs.getString("global_client_id", "1435558259892293662")
        inputClientId.setText(savedClientId)

        // Step 1: Permissions
        findViewById<Button>(R.id.btn_grant_permissions).setOnClickListener {
            requestEssentialPermissions()
        }

        // Step 2: Connect (Auth + Client ID)
        findViewById<Button>(R.id.btn_connect_discord).setOnClickListener {
            val clientIdStr = inputClientId.text.toString()
            val clientId = clientIdStr.toLongOrNull()

            if (clientId != null) {
                // Save Client ID
                prefs.edit().putString("global_client_id", clientIdStr).apply()
                
                // Init & Start Auth
                DiscordGateway.initDiscord(clientId)
                DiscordSocialSdkInit.setEngineActivity(this)
                DiscordGateway.startAuthorization()
            } else {
                inputClientId.error = "Invalid Client ID"
            }
        }

        // Step 3: Finish
        findViewById<Button>(R.id.btn_finish_onboarding).setOnClickListener {
            prefs.edit().putBoolean("onboarding_completed", true).apply()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        handleIntent(intent)
        updateStepUI()
    }

    private fun requestEssentialPermissions() {
        // Post Notifications for Android 13+
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        
        // Always try to open listener settings
        if (!isNotificationServiceEnabled()) {
            Toast.makeText(this, "Please enable the Discord RPC listener", Toast.LENGTH_LONG).show()
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        } else {
            // Already enabled! Skip to next
            flipper.showNext()
            updateStepUI()
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
                
                // If we are in Step 2 (Auth), advance to Step 3
                if (flipper.displayedChild == 1) {
                    flipper.showNext()
                    updateStepUI()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // If we are in Step 1 (Permissions) and permission was granted, auto-advance
        if (flipper.displayedChild == 0 && isNotificationServiceEnabled()) {
            flipper.showNext()
            updateStepUI()
        }
    }

    private fun updateStepUI() {
        val current = flipper.displayedChild
        dot1.alpha = if (current == 0) 1.0f else 0.3f
        dot2.alpha = if (current == 1) 1.0f else 0.3f
        dot3.alpha = if (current == 2) 1.0f else 0.3f
    }
}
