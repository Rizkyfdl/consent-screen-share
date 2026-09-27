package com.example.observer

import android.content.Context

data class RelaySettings(
    val serverUrl: String,
    val room: String,
    val token: String
)

object SettingsStore {
    private const val PREFS_NAME = "relay_connection_settings"
    private const val SERVER_URL = "server_url"
    private const val ROOM = "room"
    private const val TOKEN = "token"

    fun load(context: Context): RelaySettings {
        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        return RelaySettings(
            serverUrl = prefs.getString(
                SERVER_URL,
                "ws://10.0.2.2:8080"
            ).orEmpty(),
            room = prefs.getString(ROOM, "room-demo").orEmpty(),
            token = prefs.getString(TOKEN, "").orEmpty()
        )
    }

    fun save(context: Context, settings: RelaySettings) {
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(SERVER_URL, settings.serverUrl)
            .putString(ROOM, settings.room)
            .putString(TOKEN, settings.token)
            .apply()
    }
}