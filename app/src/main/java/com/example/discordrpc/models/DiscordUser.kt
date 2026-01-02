package com.example.discordrpc.models

data class DiscordUser(
    val username: String,
    val discriminator: String,
    val userId: Long,
    val avatarHash: String?
) {
    fun getAvatarUrl(): String {
        return if (avatarHash != null) {
            "https://cdn.discordapp.com/avatars/$userId/$avatarHash.png"
        } else {
            "https://cdn.discordapp.com/embed/avatars/${(userId shr 22) % 6}.png"
        }
    }
}
