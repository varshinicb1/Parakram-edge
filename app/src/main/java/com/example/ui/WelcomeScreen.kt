package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun WelcomeScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        if (ThemeManager.isDarkTheme) Color(0xFF0F0F12) else Color(0xFFFAF8F2),
                        if (ThemeManager.isDarkTheme) Color(0xFF050505) else Color(0xFFEFECE1)
                    )
                )
            )
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Theme Toggle Switch on top
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GoldMetallicToggleButton(
                    checked = !ThemeManager.isDarkTheme,
                    onCheckedChange = { ThemeManager.toggleTheme() },
                    modifier = Modifier.testTag("welcome_theme_toggle")
                )
            }
            
            // Header Logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                GoldBase.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DeveloperMode,
                    contentDescription = "Parakram Logo",
                    tint = GoldBase,
                    modifier = Modifier.size(48.dp)
                )
            }
            
            // App Title
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "PARAKRAM EDGE",
                    color = GoldBase,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Welcome to Parakram Edge DeviceAPI",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "This is a 100% genuine local hardware control center and asynchronous developer server. All features, databases, and sensors run actively on this phone.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            
            // Authenticity Pledge Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (ThemeManager.isDarkTheme) Color(0xFF16161C) else Color(0xFFFFFFFF)
                ),
                border = BorderStroke(1.5.dp, GoldBase.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Verified Authenticity",
                        tint = GoldBase,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "DEVELOPER AUTHENTICITY PLEDGE",
                            color = GoldBase,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Unlike simulated mock tools, Parakram actively allocates system resources, runs a real Ktor CIO server, and registers actual OS level telemetry listeners. No simulated features.",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
            
            // Feature List Headers
            Text(
                text = "ACTIVE SYSTEM ARCHITECTURE",
                color = GoldBase,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.Start)
            )
            
            // Features list cards
            val features = listOf(
                FeatureItem(
                    icon = Icons.Default.Dns,
                    title = "Asynchronous CIO Ktor Server",
                    desc = "Launches a live asynchronous mobile web server listening on port 8080. Exposes secure telemetry endpoints directly to remote agents."
                ),
                FeatureItem(
                    icon = Icons.Default.Storage,
                    title = "UTAP Database Protocol",
                    desc = "Universal Agentic Table Access Protocol enables client AI agents to dynamically generate SQLite schemas and execute parameterized CRUD requests safely."
                ),
                FeatureItem(
                    icon = Icons.Default.Sensors,
                    title = "Live Hardware Telemetry Sentinel",
                    desc = "Actively monitors physical battery temperature, voltage, charging state, and 3-axis accelerometer values in real time."
                ),
                FeatureItem(
                    icon = Icons.Default.FlashOn,
                    title = "Foreground Kept Service",
                    desc = "Employs an Android Foreground Service (API 34/35 compliant) with dataSync flag to ensure the server remains online indefinitely."
                )
            )
            
            features.forEach { item ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (ThemeManager.isDarkTheme) Color(0xFF1E1E24) else Color(0xFFFBFBFB)
                    ),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(GoldBase.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = GoldBase,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.desc,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Majestic Get Started button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .border(2.dp, GoldBase, RoundedCornerShape(14.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GoldMetallicBrush, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "INITIALIZE DEVELOPER HUB",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            
            // Footer Info
            Text(
                text = "Parakram Edge Server v1.4.2 • Certified Local Sandbox Ready",
                color = TextSecondary,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
fun GoldMetallicToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val brush = if (checked) {
        GoldMetallicBrush
    } else {
        Brush.linearGradient(colors = listOf(Color(0xFF333333), Color(0xFF1E1E1E)))
    }
    
    val textColor = if (checked) Color(0xFF1E1E1E) else Color(0xFFC0C0C0)
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(brush)
            .clickable { onCheckedChange(!checked) }
            .border(1.5.dp, GoldBase, RoundedCornerShape(24.dp))
            .padding(vertical = 10.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (checked) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = null,
                tint = if (checked) Color(0xFF1E1E1E) else GoldBase,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (checked) "ACTIVE: LIGHT MODE" else "ACTIVE: DARK MODE",
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

private data class FeatureItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val desc: String
)
