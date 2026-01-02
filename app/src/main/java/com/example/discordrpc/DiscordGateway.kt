package com.thepotato.discordrpc

import android.util.Log

object DiscordGateway {
    // Native method declarations
    external fun initDiscord(clientId: Long)
    external fun shutdownDiscord()
    external fun startAuthorization()
    external fun handleOAuthCallback(code: String, redirectUri: String)
    external fun connect()
    external fun updateRichPresence(appName: String, details: String, state: String, imageKey: String, type: Int, statusDisplayType: Int)
    external fun updateRichPresenceWithTimestamps(appName: String, details: String, state: String, imageKey: String, start: Long, end: Long, type: Int, statusDisplayType: Int)
    external fun clearActivity()
    external fun restoreSession(accessToken: String, refreshToken: String)
    external fun requestUserUpdate()

    var tokenSaver: ((String, String) -> Unit)? = null
    var startUserCallback: ((String, String, Long, String?) -> Unit)? = null
    var currentUser: com.thepotato.discordrpc.models.DiscordUser? = null

    // Called from C++
    fun onTokenReceived(accessToken: String, refreshToken: String) {
        Log.i("DiscordGateway", "Token received in Java! Saving...")
        tokenSaver?.invoke(accessToken, refreshToken)
    }

    fun onCurrentUserUpdate(username: String, discriminator: String, currentUserId: Long, avatarHash: String) {
        Log.i("DiscordGateway", "User Update: $username#$discriminator ($currentUserId)")
        currentUser = com.thepotato.discordrpc.models.DiscordUser(username, discriminator, currentUserId, avatarHash)
        startUserCallback?.invoke(username, discriminator, currentUserId, avatarHash)
    }

    init {
        try {
            System.loadLibrary("discord")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("DiscordGateway", "❌ Failed to load native library", e)
        }
    }
}
