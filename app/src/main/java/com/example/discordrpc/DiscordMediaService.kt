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
import com.example.discordrpc.models.StatusDisplayTypes
import com.example.discordrpc.models.ActivityType
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
    
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    
    companion object {
        const val ACTION_STATUS_UPDATE = "com.example.discordrpc.STATUS_UPDATE"
        const val ACTION_REFRESH_SESSIONS = "com.example.discordrpc.REFRESH_SESSIONS"
        const val ACTION_STOP_SERVICE = "com.example.discordrpc.STOP_SERVICE"
        const val KEY_RPC_ENABLED = "rpc_enabled"
        const val EXTRA_STATUS = "status"
        const val EXTRA_DETAILS = "details"
        const val EXTRA_IMAGE = "image"
        const val EXTRA_START_TIME = "start_time"
        const val EXTRA_END_TIME = "end_time"
        const val EXTRA_STATE = "state"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_ACTIVITY_TYPE = "activity_type"
        const val ACTION_APPS_UPDATE = "com.example.discordrpc.APPS_UPDATE"
        const val EXTRA_APPS_LIST = "apps_list"
        
        var currentStatus: String? = null
        var currentDetails: String? = null
        var currentState: String? = null
        var currentAppName: String? = null
        var currentActivityType: Int = ActivityType.LISTENING.value
        var currentImage: String? = null
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i("DiscordMediaService", "Service Created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Initializing...", "Waiting for media sessions"))
        
        // Register refresh receiver
        val filter = android.content.IntentFilter(ACTION_REFRESH_SESSIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(refreshReceiver, filter)
        }
    }

    private val refreshReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_REFRESH_SESSIONS) {
                Log.i("DiscordMediaService", "Refresh requested via broadcast")
                val componentName = ComponentName(this@DiscordMediaService, DiscordMediaService::class.java)
                onActiveSessionsChanged(sessionManager?.getActiveSessions(componentName))
            }
        }
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

        val stopIntent = Intent(this, DiscordMediaService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Exit", stopPendingIntent)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.i("DiscordMediaService", "Stop service action received")
            unregisterCurrent()
            DiscordGateway.shutdownDiscord()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
            stopSelf()
            return START_NOT_STICKY
        } else if (intent?.action == ACTION_REFRESH_SESSIONS) {
            Log.i("DiscordMediaService", "Refreshing sessions request received")
            val componentName = ComponentName(this, DiscordMediaService::class.java)
            val controllers = sessionManager?.getActiveSessions(componentName)
            if (controllers != null) {
                onActiveSessionsChanged(controllers)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun updateNotification(
        title: String, 
        text: String, 
        image: String? = null, 
        start: Long = 0, 
        end: Long = 0,
        state: String? = null,
        appName: String? = null,
        activityType: Int = 2
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, text))
        
        // Update Static Cache
        currentStatus = title.replace("Discord RPC: ", "")
        currentDetails = text
        currentState = state
        currentAppName = appName
        currentActivityType = activityType
        currentImage = image
        
        // Also broadcast to MainActivity
        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATUS, currentStatus)
            putExtra(EXTRA_DETAILS, currentDetails)
            putExtra(EXTRA_STATE, currentState)
            putExtra(EXTRA_APP_NAME, currentAppName)
            putExtra(EXTRA_ACTIVITY_TYPE, currentActivityType)
            putExtra(EXTRA_IMAGE, currentImage)
            putExtra(EXTRA_START_TIME, start)
            putExtra(EXTRA_END_TIME, end)
        }
        sendBroadcast(intent)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i("DiscordMediaService", "Notification Listener Connected")
        
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
            Log.e("DiscordMediaService", "Missing notification access permission", e)
        }
    }

    private var currentController: MediaController? = null
    private var callback: MediaController.Callback? = null

    private fun onActiveSessionsChanged(controllers: List<MediaController>?) {
        Log.i("DiscordMediaService", "Active sessions changed: ${controllers?.size ?: 0} sessions")
        
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_RPC_ENABLED, true)) {
            Log.d("DiscordMediaService", "RPC is disabled, clearing activity and shutting down")
            unregisterCurrent()
            DiscordGateway.clearActivity()
            DiscordGateway.shutdownDiscord()
            return
        }

        val allowedApps = prefs.getStringSet(KEY_ALLOWED_APPS, emptySet()) ?: emptySet()
        Log.i("DiscordMediaService", "Found sessions: ${controllers?.map { it.packageName }}")

        val filteredControllers = controllers?.filter { allowedApps.contains(it.packageName) }

        if (filteredControllers.isNullOrEmpty()) {
            Log.i("DiscordMediaService", "No active media sessions from allowed apps")
            broadcastAppsList(controllers) // Broadcast all found controllers so UI can show them
            unregisterCurrent()
            DiscordGateway.updateRichPresence("Discord RPC", "Idle", "Waiting for media...", "", ActivityType.LISTENING.value, StatusDisplayTypes.STATE.value)
            updateNotification("Discord RPC: Idle", "Waiting for media playback", null, 0, 0, "Waiting for media playback", "Discord RPC", ActivityType.LISTENING.value)
            return
        }

        // Broadcast available apps to UI
        broadcastAppsList(controllers)

        // Prioritize a playing controller, or fallback to the most recent one (first usually)
        val selectedController = filteredControllers.find { 
            it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING 
        } ?: filteredControllers.first()
        
        // Check if we switched sessions
        if (currentController?.sessionToken != selectedController.sessionToken) {
            Log.i("DiscordMediaService", "Switched to session: ${selectedController.packageName}")
            unregisterCurrent()
            currentController = selectedController
            registerCallback(selectedController)
            updatePresenceFromController(selectedController)
        } else {
             // Same session, force one update to be safe.
             updatePresenceFromController(selectedController)
        }
    }
    
    private fun unregisterCurrent() {
        if (currentController != null && callback != null) {
            try {
                currentController?.unregisterCallback(callback!!)
            } catch (e: Exception) {
                // Ignore if already dead
                Log.w("DiscordMediaService", "Failed to unregister callback: ${e.message}")
            }
        }
        currentController = null
        callback = null
    }

    private fun registerCallback(controller: MediaController) {
        callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                Log.d("DiscordMediaService", "Metadata changed")
                updatePresenceFromController(controller)
            }

            override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
                Log.d("DiscordMediaService", "Playback state changed: ${state?.state}")
                updatePresenceFromController(controller)
            }
            
            override fun onSessionDestroyed() {
                 Log.d("DiscordMediaService", "Session destroyed")
                 unregisterCurrent()
                 DiscordGateway.updateRichPresence("Discord RPC", "Idle", "Waiting for media...", "", ActivityType.LISTENING.value, StatusDisplayTypes.STATE.value)
            }
        }
        controller.registerCallback(callback!!)
    }

    private fun updatePresenceFromController(controller: MediaController?) {
        if (controller == null) return
        
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_RPC_ENABLED, true)) {
            Log.d("DiscordMediaService", "RPC is disabled, clearing activity and shutting down")
            unregisterCurrent()
            DiscordGateway.clearActivity()
            DiscordGateway.shutdownDiscord()
            return
        }
        val metadata = controller.metadata ?: return
        
        val packageName = controller.packageName
        
        // Delegate parsing to modular system
        val parser = com.example.discordrpc.parsing.MetadataParserFactory.getParser(packageName)
        val parsed = parser.parse(metadata)
        
        val details = parsed.details
        val state = parsed.state
        val displayType = parsed.displayType.value
        
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val position = controller.playbackState?.position ?: 0L
        
        // Helper to get type
        val type = prefs.getInt("app_type_$packageName", ActivityType.LISTENING.value)
        
        // Handle Album Art
        var imageKey = "" // Default to no image
        val trackId = "$details|$state|$packageName"
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
                     serviceScope.launch(Dispatchers.IO) {
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
        
        var appName = "Unknown App"
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            appName = packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            Log.w("DiscordMediaService", "Could not get app label for $packageName")
        }

        val playbackState = controller.playbackState?.state
        if (duration > 0 && playbackState == android.media.session.PlaybackState.STATE_PLAYING) {
            val now = System.currentTimeMillis()
            val startTs = now - position
            val endTs = startTs + duration
            Log.d("DiscordMediaService", "Sending presence update with timestamps")
            DiscordGateway.updateRichPresenceWithTimestamps(appName, details, state, imageKey, startTs, endTs, type, displayType)
        } else {
            Log.d("DiscordMediaService", "Sending standard presence update")
            DiscordGateway.updateRichPresence(appName, details, state, imageKey, type, displayType)
        }
        
        val statusText = if (playbackState == android.media.session.PlaybackState.STATE_PLAYING) "Playing" else "Paused"
        
        val isPlaying = playbackState == android.media.session.PlaybackState.STATE_PLAYING
        val s = if (isPlaying && duration > 0) (System.currentTimeMillis() - (controller.playbackState?.position ?: 0)) else 0L
        val e = if (isPlaying && duration > 0) s + duration else 0L
        
        updateNotification("Discord RPC: $statusText", details, imageKey, s, e, state, appName, type)
    }

    private fun broadcastAppsList(controllers: List<MediaController>?) {
        val packages = ArrayList<String>()
        controllers?.forEach { packages.add(it.packageName) }
        
        val intent = Intent(ACTION_APPS_UPDATE).apply {
            setPackage(packageName)
            putStringArrayListExtra(EXTRA_APPS_LIST, packages)
        }
        sendBroadcast(intent)
        Log.d("DiscordMediaService", "Broadcasted apps list: ${packages.size} apps")
    }

    private val urlCache = mutableMapOf<String, String>()
    private val uploadingTracks = mutableSetOf<String>()

    // User provided helper
    private fun getCoverArt(metadata: MediaMetadata?): Bitmap? {
        if (metadata == null) return null
        return metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) 
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
    }
}
