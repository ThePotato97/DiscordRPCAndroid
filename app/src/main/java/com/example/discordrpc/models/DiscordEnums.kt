package com.example.discordrpc.models

enum class StatusDisplayTypes(val value: Int) {
    NAME(0),
    STATE(1),
    DETAILS(2)
}

enum class ActivityType(val value: Int) {
    PLAYING(0),
    STREAMING(1),
    LISTENING(2),
    WATCHING(3),
    CUSTOM(4),
    COMPETING(5);

    companion object {
        fun fromInt(value: Int) = values().firstOrNull { it.value == value } ?: PLAYING
    }
}
