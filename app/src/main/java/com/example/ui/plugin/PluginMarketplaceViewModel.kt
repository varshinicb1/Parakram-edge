package com.example.ui.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MobileServerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class Plugin(
    val id: Int,
    val name: String,
    val displayName: String,
    val description: String,
    val author: String,
    val category: String,
    val iconUrl: String,
    val tier: String,
    val homepage: String,
    val versions: List<PluginVersion> = emptyList()
)

@Serializable
data class PluginVersion(
    val id: Int,
    val version: String,
    val codeUrl: String,
    val minSdkVersion: String,
    val changelog: String,
    val checksum: String,
    val downloads: Int,
    val createdAt: String
)

@Serializable
data class InstalledPlugin(
    val name: String,
    val displayName: String,
    val version: String,
    val enabled: Boolean,
    val installedAt: String
)

class PluginMarketplaceViewModel(private val serverManager: MobileServerManager) : ViewModel() {
    private val _plugins = MutableStateFlow<List<Plugin>>(emptyList())
    val plugins: androidx.lifecycle.LiveData<List<Plugin>> = _plugins.asStateFlow().distinctUntilChanged().map { it }.asLiveData()

    private val _installedPlugins = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val installedPlugins: androidx.lifecycle.LiveData<List<InstalledPlugin>> = _installedPlugins.asStateFlow().distinctUntilChanged().map { it }.asLiveData()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: androidx.lifecycle.LiveData<Boolean> = _isLoading.asStateFlow().distinctUntilChanged().map { it }.asLiveData()

    private val _error = MutableStateFlow<String?>(null)
    val error: androidx.lifecycle.LiveData<String?> = _error.asStateFlow().distinctUntilChanged().map { it }.asLiveData()

    private val _selectedCategory = MutableStateFlow<String>("all")
    val selectedCategory: androidx.lifecycle.LiveData<String> = _selectedCategory.asStateFlow().distinctUntilChanged().map { it }.asLiveData()

    private val registryUrl = "https://registry.parakram.dev"
    private val deviceId = android.os.Build.MODEL.replace(" ", "-") + "_" + java.util.UUID.randomUUID().toString().take(8)

    fun loadPlugins(category: String = "all") {
        _selectedCategory.value = category
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            try {
                val url = URL("$registryUrl/api/plugins${if (category != "all") "?category=$category" else ""}")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                if (conn.responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().use { it.readText() }
                    val list = Json.decodeFromString(pluginsSerializer, json)
                    _plugins.value = list
                } else {
                    _error.value = "Failed to load plugins: ${conn.responseCode}"
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadInstalledPlugins() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("$registryUrl/api/installed/$deviceId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                if (conn.responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().use { it.readText() }
                    val list = Json.decodeFromString(installedPluginsSerializer, json)
                    _installedPlugins.value = list
                }
            } catch (e: Exception) {
                // Silently fail for installed plugins
            }
        }
    }

    fun installPlugin(plugin: Plugin) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val latestVersion = plugin.versions.maxByOrNull { it.createdAt }?.version ?: "latest"
                val url = URL("$registryUrl/api/install")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                
                val body = """{"plugin_name": "${plugin.name}", "device_id": "$deviceId", "version": "$latestVersion"}"""
                conn.outputStream.use { it.write(body.toByteArray()) }
                
                if (conn.responseCode == 200) {
                    loadInstalledPlugins()
                }
            } catch (e: Exception) {
                _error.value = "Install failed: ${e.message}"
            }
        }
    }

    fun togglePlugin(installed: InstalledPlugin, enable: Boolean) {
        // Would call a PUT /api/install endpoint to toggle enabled state
        // For now just refresh
        loadInstalledPlugins()
    }
}

private val pluginsSerializer = kotlinx.serialization.json.Json { }.serializer().elementSerializer
private val installedPluginsSerializer = kotlinx.serialization.json.Json { }.serializer().elementSerializer