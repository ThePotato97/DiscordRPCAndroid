package com.example.discordrpc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.util.Log
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DiscordMediaService : NotificationListenerService() {

    private var sessionManager: MediaSessionManager? = null
    private val PREFS_NAME = "discord_rpc_prefs"
    private val KEY_ALLOWED_APPS = "allowed_apps"
    private val CHANNEL_ID = "DiscordRPCStatus"
    private val NOTIFICATION_ID = 1
    
    companion object {
        const val ACTION_STATUS_UPDATE = "com.example.discordrpc.STATUS_UPDATE"
        const val EXTRA_STATUS = "status"
        const val EXTRA_DETAILS = "details"
        
        var currentStatus: String? = null
        var currentDetails: String? = null
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i("DiscordMediaService", "🚀 Service Created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Initializing...", "Waiting for media sessions"))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Discord RPC Status"
            val descriptionText = "Shows current media being mirrored to Discord"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, text))
        
        // Update Static Cache
        currentStatus = title.replace("Discord RPC: ", "")
        currentDetails = text
        
        // Also broadcast to MainActivity
        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATUS, currentStatus)
            putExtra(EXTRA_DETAILS, currentDetails)
        }
        sendBroadcast(intent)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i("DiscordMediaService", "✅ Notification Listener Connected!")
        
        sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        
        // Register for session updates
        val componentName = ComponentName(this, DiscordMediaService::class.java)
        try {
            sessionManager?.addOnActiveSessionsChangedListener(
                { controllers -> onActiveSessionsChanged(controllers) },
                componentName
            )
            // Trigger initial check
            val controllers = sessionManager?.getActiveSessions(componentName)
            if (controllers != null) {
                onActiveSessionsChanged(controllers)
            }
        } catch (e: SecurityException) {
            Log.e("DiscordMediaService", "❌ Missing notification access permission!", e)
        }
    }

    private var currentController: MediaController? = null
    private var callback: MediaController.Callback? = null

    private fun onActiveSessionsChanged(controllers: List<MediaController>?) {
        Log.i("DiscordMediaService", "📢 onActiveSessionsChanged: ${controllers?.size ?: 0} sessions total")
        
        val allowedApps = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_ALLOWED_APPS, emptySet()) ?: emptySet()

        val filteredControllers = controllers?.filter { allowedApps.contains(it.packageName) }

        if (filteredControllers.isNullOrEmpty()) {
            Log.i("DiscordMediaService", "ℹ️ No active media sessions from allowed apps")
            unregisterCurrent()
            DiscordGateway.updateRichPresence("Idle", "Waiting for media...", "android", 2)
            updateNotification("Discord RPC: Idle", "Waiting for media playback")
            return
        }

        // Prioritize a playing controller, or fallback to the most recent one (first usually)
        val selectedController = filteredControllers.find { 
            it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING 
        } ?: filteredControllers.first()
        
        // Check if we switched sessions
        if (currentController?.sessionToken != selectedController.sessionToken) {
            Log.i("DiscordMediaService", "✨ Switched to new session: ${selectedController.packageName}")
            unregisterCurrent()
            currentController = selectedController
            registerCallback(selectedController)
            updatePresenceFromController(selectedController)
        } else {
             // Same session, maybe state changed? Check manually just in case
             // But callback handles metadata changes. We don't need to force update here unless needed.
             // We can force one update to be safe.
             updatePresenceFromController(selectedController)
        }
    }
    
    private fun unregisterCurrent() {
        if (currentController != null && callback != null) {
            try {
                currentController?.unregisterCallback(callback!!)
            } catch (e: Exception) {
                // Ignore if already dead
            }
        }
        currentController = null
        callback = null
    }

    private fun registerCallback(controller: MediaController) {
        callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                Log.d("DiscordMediaService", "Callback: Metadata Changed")
                updatePresenceFromController(controller)
            }

            override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
                Log.d("DiscordMediaService", "Callback: State Changed to ${state?.state}")
                updatePresenceFromController(controller)
            }
            
            override fun onSessionDestroyed() {
                 Log.d("DiscordMediaService", "Callback: Session Destroyed")
                 unregisterCurrent()
                 DiscordGateway.updateRichPresence("Idle", "Waiting for media...", "android", 2)
            }
        }
        controller.registerCallback(callback!!)
    }

    private fun updatePresenceFromController(controller: MediaController?) {
        if (controller == null) return
        val metadata = controller.metadata
        
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Title"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist"
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = controller.playbackState?.position ?: 0L
        val packageName = controller.packageName
        
        Log.i("DiscordMediaService", "🎵 Now Playing: $title by $artist ($packageName) [Pos: $position, Dur: $duration]")
        
        val prefs = getSharedPreferences("discord_rpc_prefs", MODE_PRIVATE)
        val type = prefs.getInt("app_type_$packageName", 2) // Default to Listening (2)
        
        // Handle Album Art
        var imageKey = "" // Default to no image
        val trackId = "$title|$artist|$packageName"
        val cachedUrl = urlCache[trackId]
        
        if (cachedUrl != null) {
            imageKey = cachedUrl
        } else {
             // Not in cache, try to get bitmap and upload
             val bitmap = getCoverArt(metadata)
             if (bitmap != null) {
                 if (!uploadingTracks.contains(trackId)) {
                     uploadingTracks.add(trackId)
                     // Launch upload
                     CoroutineScope(Dispatchers.IO).launch {
                         val url = ImageUploader(this@DiscordMediaService).uploadImage(bitmap)
                         uploadingTracks.remove(trackId) 
                         if (url != null) {
                             urlCache[trackId] = url
                             // Force update with new URL
                             withContext(Dispatchers.Main) {
                                 // Just re-trigger update, it will pick up the cached URL
                                 updatePresenceFromController(controller)
                             }
                         }
                     }
                 }
             }
        }
        
        val state = controller.playbackState?.state
        if (duration > 0 && state == android.media.session.PlaybackState.STATE_PLAYING) {
            val now = System.currentTimeMillis()
            val startTs = now - position
            val endTs = startTs + duration
            Log.d("DiscordMediaService", "Sending RPC Update with Timestamps: $title - $artist (Type: $type, Key: $imageKey)")
            DiscordGateway.updateRichPresenceWithTimestamps(title, artist, imageKey, startTs, endTs, type)
        } else {
            Log.d("DiscordMediaService", "Sending Standard RPC Update: $title - $artist (Type: $type, Key: $imageKey)")
            DiscordGateway.updateRichPresence(title, artist, imageKey, type)
        }
        
        val statusText = if (state == android.media.session.PlaybackState.STATE_PLAYING) "Playing" else "Paused"
        updateNotification("Discord RPC: $statusText", "$title - $artist")
    }

    private val urlCache = mutableMapOf<String, String>()
    private val uploadingTracks = mutableSetOf<String>()

    // User provided helper
    private fun getCoverArt(metadata: MediaMetadata?): Bitmap? {
        if (metadata == null) return null
        return if (metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) != null) metadata.getBitmap(
            MediaMetadata.METADATA_KEY_ALBUM_ART
        )
        else metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
    }
}

