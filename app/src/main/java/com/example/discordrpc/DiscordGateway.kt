package com.example.discordrpc

import android.util.Log

object DiscordGateway {
    // Native method declarations
    external fun initDiscord(clientId: Long)
    external fun shutdownDiscord()
    external fun startAuthorization()
    external fun handleOAuthCallback(code: String, redirectUri: String)
    external fun connect()
    external fun updateRichPresence(details: String, state: String, imageKey: String, type: Int)
    external fun updateRichPresenceWithTimestamps(details: String, state: String, imageKey: String, start: Long, end: Long, type: Int)
    external fun restoreSession(accessToken: String, refreshToken: String)

    var tokenSaver: ((String, String) -> Unit)? = null

    // Called from C++
    fun onTokenReceived(accessToken: String, refreshToken: String) {
        Log.i("DiscordGateway", "Token received in Java! Saving...")
        tokenSaver?.invoke(accessToken, refreshToken)
    }

    init {
        try {
            System.loadLibrary("discord")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("DiscordGateway", "❌ Failed to load native library", e)
        }
    }
}
