package com.example.ui.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CompositionLocalProvider
import androidx.compose.material3.ContentAlpha
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.CheckCircle
import androidx.compose.material3.icons.filled.Download
import androidx.compose.material3.icons.filled.Error
import androidx.compose.material3.icons.filled.Extension
import androidx.compose.material3.icons.filled.FilterList
import androidx.compose.material3.icons.filled.Info
import androidx.compose.material3.icons.filled.Installed
import androidx.compose.material3.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.compose.placeholder
import coil3.compose.error
import coil3.request.ImageRequest

@Composable
fun PluginMarketplaceScreen(
    viewModel: PluginMarketplaceViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val plugins by viewModel.plugins.observeAsState(emptyList())
    val installedPlugins by viewModel.installedPlugins.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState(null)
    val selectedCategory by viewModel.selectedCategory.observeAsState("all")
    val snackbarHostState = remember { SnackbarHostState() }
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }

    val categories = listOf("all", "communication", "automation", "hardware", "productivity", "utility")

    val installedNames = installedPlugins.map { it.name }.toSet()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plugin Marketplace") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { viewModel.loadPlugins(selectedCategory) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top
            ) {
                // Category filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        androidx.compose.material3.FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.loadPlugins(cat) },
                            label = { Text(cat.uppercaseFirst()) },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        )
                    }
                }

                Divider()

                // Error banner
                error?.let { msg ->
                    androidx.compose.material3.Banner(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        onDismiss = { viewModel._error.value = null },
                        content = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                                Text(msg, color = MaterialTheme.colorScheme.onErrorContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    )
                }

                // Plugin list
                if (isLoading && plugins.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (plugins.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.Extension, contentDescription = "No plugins", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), size = 64.dp)
                            Text("No plugins found", fontWeight = FontWeight.Medium, fontSize = 18.sp)
                            Text("Try a different category or check your connection", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(plugins) { plugin ->
                            PluginCard(
                                plugin = plugin,
                                isInstalled = installedNames.contains(plugin.name),
                                onInstall = {
                                    viewModel.installPlugin(plugin)
                                    showSnackbar = true
                                    snackbarMessage = "${plugin.displayName} installed"
                                    snackbarHostState.showSnackbar(snackbarMessage)
                                },
                                onToggle = { enabled ->
                                    // Toggle installed plugin
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PluginCard(
    plugin: Plugin,
    isInstalled: Boolean,
    onInstall: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(plugin.iconUrl).crossfade(true).build(),
                    contentDescription = plugin.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(48.dp),
                    placeholder = { Icon(Icons.Default.Extension, contentDescription = "Plugin icon", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    error = { Icon(Icons.Default.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error) }
                )

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))

                // Info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            plugin.displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Tier badge
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .background(
                                    when (plugin.tier) {
                                        "pro" -> MaterialTheme.colorScheme.tertiaryContainer
                                        "enterprise" -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            Text(
                                plugin.tier.uppercaseFirst(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = when (plugin.tier) {
                                    "pro" -> MaterialTheme.colorScheme.onTertiaryContainer
                                    "enterprise" -> MaterialTheme.colorScheme.onSecondaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Text(
                        plugin.description,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )

                    // Author & category
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "by ${plugin.author} • ${plugin.category}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        if (plugin.versions.isNotEmpty()) {
                            Text(
                                "v${plugin.versions.maxByOrNull { it.createdAt }?.version ?: "1.0"}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Action button
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (isInstalled) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onToggle(false) },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Installed", size = 18.dp)
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(4.dp))
                        Text("Uninstall", fontWeight = FontWeight.Medium)
                    }
                } else {
                    androidx.compose.material3.FilledButton(
                        onClick = onInstall,
                        colors = androidx.compose.material3.ButtonDefaults.filledButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Install", size = 18.dp)
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(4.dp))
                        Text("Install", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

private fun String.uppercaseFirst(): String = if (isEmpty()) this else this[0].uppercase() + this.substring(1)