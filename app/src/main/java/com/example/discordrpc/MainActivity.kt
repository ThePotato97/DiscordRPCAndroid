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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.discord.socialsdk.DiscordSocialSdkInit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    
    private lateinit var prefs: android.content.SharedPreferences
    private var isReceiverRegistered = false
    
    // UI Components
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchView: com.google.android.material.search.SearchView
    private lateinit var searchBar: com.google.android.material.search.SearchBar
    private lateinit var searchResultsRecyclerView: RecyclerView

    // Adapters
    private lateinit var headerAdapter: HeaderAdapter
    private lateinit var appAdapter: AppAdapter
    private lateinit var searchAdapter: AppAdapter
    private lateinit var concatAdapter: ConcatAdapter
    
    // Data
    private var fullAppList: List<AppItem> = emptyList()
    private val PREFS_NAME = "discord_rpc_prefs"
    private val KEY_ALLOWED_APPS = "allowed_apps"

    // Initial Status placeholders
    private var currentStatusText = "Connected to Discord"
    private var currentDetailsText = "Waiting for service..."

    private val statusReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            val status = intent.getStringExtra(DiscordMediaService.EXTRA_STATUS)
            val details = intent.getStringExtra(DiscordMediaService.EXTRA_DETAILS)
            if (status != null) currentStatusText = status
            if (details != null) currentDetailsText = details
            
            // Notify header to update if initialized
            if (::headerAdapter.isInitialized) {
                headerAdapter.updateStatus(currentStatusText, currentDetailsText)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val onboardingCompleted = prefs.getBoolean("onboarding_completed", false)

        if (!onboardingCompleted) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.topAppBar))

        // Init Views
        recyclerView = findViewById(R.id.apps_recycler_view)
        searchBar = findViewById(R.id.search_bar)
        searchView = findViewById(R.id.search_view)
        searchResultsRecyclerView = findViewById(R.id.search_results_recycler_view)

        setupAdapters()
        setupSearch()
        
        // Register Receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, android.content.IntentFilter(DiscordMediaService.ACTION_STATUS_UPDATE), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, android.content.IntentFilter(DiscordMediaService.ACTION_STATUS_UPDATE))
        }
        isReceiverRegistered = true

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
        
        DiscordGateway.connect()
        
        loadApps()
    }

    private fun setupAdapters() {
        // Use cached status if available
        if (DiscordMediaService.currentStatus != null) {
            currentStatusText = DiscordMediaService.currentStatus!!
            currentDetailsText = DiscordMediaService.currentDetails ?: ""
        }
        
        // 1. Header Adapter (Status Card)
        headerAdapter = HeaderAdapter(currentStatusText, currentDetailsText)
        
        // 2. App Adapter (Main List)
        val onCheckedChange: (AppItem, Boolean) -> Unit = { app, isChecked ->
            updateAppSelection(app, isChecked)
        }
        appAdapter = AppAdapter(mutableListOf(), prefs, onCheckedChange)
        
        // Combine them
        concatAdapter = ConcatAdapter(headerAdapter, appAdapter)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = concatAdapter
        
        // 3. Search Adapter (Separate List)
        searchAdapter = AppAdapter(mutableListOf(), prefs, onCheckedChange)
        searchResultsRecyclerView.layoutManager = LinearLayoutManager(this)
        searchResultsRecyclerView.adapter = searchAdapter
    }
    
    private fun setupSearch() {
        searchView.setupWithSearchBar(searchBar)
        searchView.editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (searchView.isShowing) {
                    searchView.hide()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun updateAppSelection(app: AppItem, isChecked: Boolean) {
        val currentAllowed = prefs.getStringSet(KEY_ALLOWED_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (isChecked) currentAllowed.add(app.packageName) else currentAllowed.remove(app.packageName)
        prefs.edit().putStringSet(KEY_ALLOWED_APPS, currentAllowed).apply()
        
        // Sync state in full list
        fullAppList.find { it.packageName == app.packageName }?.isSelected = isChecked
        
        // Re-sort and update main list if we are not searching
        if (!searchView.isShowing) {
             val sorted = fullAppList.sortedWith(compareByDescending<AppItem> { it.isSelected }.thenBy { it.name })
             appAdapter.updateList(sorted)
        }
    }

    private fun filterApps(query: String) {
        val filtered = if (query.isEmpty()) {
            emptyList()
        } else {
            fullAppList.filter { it.name.contains(query, ignoreCase = true) }
        }
        searchAdapter.updateList(filtered)
    }

    private fun loadApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val allowedApps = prefs.getStringSet(KEY_ALLOWED_APPS, emptySet()) ?: emptySet()

            val appList = packages
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { appInfo ->
                    AppItem(
                        name = appInfo.loadLabel(pm).toString(),
                        packageName = appInfo.packageName,
                        icon = appInfo.loadIcon(pm),
                        isSelected = allowedApps.contains(appInfo.packageName)
                    )
                }
                // Sort: Selected first, then Alphabetical
                .sortedWith(compareByDescending<AppItem> { it.isSelected }.thenBy { it.name })

            withContext(Dispatchers.Main) {
                fullAppList = appList
                appAdapter.updateList(fullAppList)
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        if (!isNotificationServiceEnabled()) {
             startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                performLogout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun performLogout() {
        prefs.edit().putBoolean("is_authorized", false).putBoolean("onboarding_completed", false).apply()
        DiscordGateway.shutdownDiscord()
        Toast.makeText(this, "Logged out.", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, OnboardingActivity::class.java))
        finish()
    }
    
    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(pkgName)
    }
    
    override fun onDestroy() {
        if (isReceiverRegistered) unregisterReceiver(statusReceiver)
        super.onDestroy()
    }

    // --- Data & Adapters ---
    
    data class AppItem(val name: String, val packageName: String, val icon: Drawable, var isSelected: Boolean)

    // Adapter for the Status Card (Header)
    class HeaderAdapter(private var status: String, private var details: String) : RecyclerView.Adapter<HeaderAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvStatus: TextView = view.findViewById(R.id.tvStatus)
            val tvDetails: TextView = view.findViewById(R.id.tvDetails)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            // Inflate a simple layout containing the card. 
            // We need to create a layout file for this or inflate a view programmatically.
            // For simplicity, we'll assume a layout 'item_header_status.xml' exists.
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_header_status, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.tvStatus.text = status
            holder.tvDetails.text = details
        }
        
        override fun getItemCount() = 1
        
        fun updateStatus(newStatus: String, newDetails: String) {
            status = newStatus
            details = newDetails
            notifyItemChanged(0)
        }
    }

    class AppAdapter(
        private var apps: MutableList<AppItem>, 
        private val prefs: android.content.SharedPreferences,
        private val onCheckedChange: (AppItem, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.app_icon)
            val name: TextView = view.findViewById(R.id.app_name)
            val packageName: TextView = view.findViewById(R.id.app_package)
            val checkbox: com.google.android.material.checkbox.MaterialCheckBox = view.findViewById(R.id.app_checkbox)
            val typeChipGroup: com.google.android.material.chip.ChipGroup = view.findViewById(R.id.type_chip_group)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.icon.setImageDrawable(app.icon)
            holder.name.text = app.name
            holder.packageName.text = app.packageName
            
            holder.checkbox.setOnCheckedChangeListener(null)
            holder.checkbox.isChecked = app.isSelected
            holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                app.isSelected = isChecked
                onCheckedChange(app, isChecked)
            }

            val savedType = prefs.getInt("app_type_${app.packageName}", 2) 
            holder.typeChipGroup.setOnCheckedStateChangeListener(null)
            when (savedType) {
                0 -> holder.typeChipGroup.check(R.id.chip_playing)
                2 -> holder.typeChipGroup.check(R.id.chip_listening)
                3 -> holder.typeChipGroup.check(R.id.chip_watching)
            }
            holder.typeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
                val newType = when (checkedIds.firstOrNull()) {
                    R.id.chip_playing -> 0
                    R.id.chip_watching -> 3
                    else -> 2 
                }
                prefs.edit().putInt("app_type_${app.packageName}", newType).apply()
            }
        }

        override fun getItemCount() = apps.size
        
        fun updateList(newList: List<AppItem>) {
            apps = newList.toMutableList()
            notifyDataSetChanged()
        }
    }
}
