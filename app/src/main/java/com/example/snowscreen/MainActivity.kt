package com.example.snowscreen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.snowscreen.model.WeatherMode
import com.example.snowscreen.service.SnowService
import com.example.snowscreen.ui.theme.SnowScreenTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnowScreenTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SnowControlScreen(
                        onCheckOverlayPermission = { checkOverlayPermission() },
                        hasOverlayPermission = { hasOverlayPermission() }
                    )
                }
            }
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnowControlScreen(
    onCheckOverlayPermission: () -> Unit,
    hasOverlayPermission: () -> Boolean
) {
    val context = LocalContext.current
    var isRunning by remember { mutableStateOf(SnowService.isRunning) }
    var hasPermission by remember { mutableStateOf(hasOverlayPermission()) }

    var flakeCount by remember { mutableStateOf(180f) }
    var speed by remember { mutableStateOf(1.0f) }
    var size by remember { mutableStateOf(1.0f) }
    var wind by remember { mutableStateOf(0.0f) }
    var tiltEnabled by remember { mutableStateOf(false) }
    var gustsEnabled by remember { mutableStateOf(false) }
    var onlyHomeLock by remember { mutableStateOf(false) }
    var selectedWeather by remember { mutableStateOf(WeatherMode.GENTLE) }

    fun sendUpdate() {
        if (SnowService.isRunning) {
            val intent = Intent(context, SnowService::class.java).apply {
                action = SnowService.ACTION_UPDATE
                putExtra(SnowService.EXTRA_FLAKE_COUNT, flakeCount.toInt())
                putExtra(SnowService.EXTRA_SPEED, speed)
                putExtra(SnowService.EXTRA_SIZE, size)
                putExtra(SnowService.EXTRA_WIND, wind)
                putExtra(SnowService.EXTRA_TILT_ENABLED, tiltEnabled)
                putExtra(SnowService.EXTRA_GUSTS_ENABLED, gustsEnabled)
                putExtra(SnowService.EXTRA_ONLY_HOME_LOCK, onlyHomeLock)
            }
            context.startService(intent)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Deep Night Blue
                        Color(0xFF1E293B), // Slate Blue
                        Color(0xFF334155)  // Muted Frost
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // App Title Header
            Text(
                text = "❄️ Snow on Screen Pro",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "3D Parallax Depth, Status Bar & Lock Screen Snowfall",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8)
            )

            // Status Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isRunning) Color(0xFF065F46).copy(alpha = 0.8f) else Color(0xFF1E293B).copy(alpha = 0.9f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isRunning) "Status: Active ❄️" else "Status: Stopped ⏸️",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isRunning) "Running over Status Bar & Lock Screen" else "Tap start to activate snow effect",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isRunning) Color(0xFF34D399) else Color(0xFFEF4444))
                    )
                }
            }

            // Permission Card if missing
            AnimatedVisibility(visible = !hasPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D).copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "⚠️ Overlay Permission Needed",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "To show snow over status bar, home screen, and lock screen, please grant overlay permission.",
                            color = Color(0xFFFECACA),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onCheckOverlayPermission() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text("Grant Permission", color = Color(0xFF7F1D1D))
                        }
                    }
                }
            }

            // Start / Stop Main Button
            Button(
                onClick = {
                    hasPermission = hasOverlayPermission()
                    if (!hasPermission) {
                        onCheckOverlayPermission()
                        return@Button
                    }

                    if (isRunning) {
                        val intent = Intent(context, SnowService::class.java).apply {
                            action = SnowService.ACTION_STOP
                        }
                        context.startService(intent)
                        isRunning = false
                    } else {
                        val intent = Intent(context, SnowService::class.java).apply {
                            action = SnowService.ACTION_START
                            putExtra(SnowService.EXTRA_FLAKE_COUNT, flakeCount.toInt())
                            putExtra(SnowService.EXTRA_SPEED, speed)
                            putExtra(SnowService.EXTRA_SIZE, size)
                            putExtra(SnowService.EXTRA_WIND, wind)
                            putExtra(SnowService.EXTRA_TILT_ENABLED, tiltEnabled)
                            putExtra(SnowService.EXTRA_GUSTS_ENABLED, gustsEnabled)
                            putExtra(SnowService.EXTRA_ONLY_HOME_LOCK, onlyHomeLock)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                        isRunning = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color(0xFFDC2626) else Color(0xFF3B82F6)
                )
            ) {
                Text(
                    text = if (isRunning) "Stop Snow Effect 🛑" else "Start Snow Effect ❄️",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Weather Presets Section
            Text(
                text = "Weather Presets",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.align(Alignment.Start)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WeatherMode.entries.forEach { mode ->
                    if (mode != WeatherMode.CUSTOM) {
                        FilterChip(
                            selected = selectedWeather == mode,
                            onClick = {
                                selectedWeather = mode
                                when (mode) {
                                    WeatherMode.GENTLE -> { flakeCount = 120f; speed = 0.8f; size = 1.0f }
                                    WeatherMode.BLIZZARD -> { flakeCount = 250f; speed = 2.2f; size = 1.3f }
                                    WeatherMode.MAGICAL -> { flakeCount = 180f; speed = 1.2f; size = 1.5f }
                                    else -> {}
                                }
                                sendUpdate()
                            },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Interactive Toggles Section
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Physics, Display & Smart Toggles",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )

                    // Only Home & Lock Screen Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "🏠 Only Home & Lock Screen", color = Color.White, fontWeight = FontWeight.Medium)
                            Text(text = "Hide snow when opening other apps", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                        Switch(
                            checked = onlyHomeLock,
                            onCheckedChange = {
                                onlyHomeLock = it
                                selectedWeather = WeatherMode.CUSTOM
                                sendUpdate()
                            }
                        )
                    }

                    HorizontalDivider(color = Color(0xFF334155))

                    // Sensor Tilt Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "📱 Tilt Sensor Wind", color = Color.White, fontWeight = FontWeight.Medium)
                            Text(text = "Tilt your phone to blow snow sideways", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                        Switch(
                            checked = tiltEnabled,
                            onCheckedChange = {
                                tiltEnabled = it
                                selectedWeather = WeatherMode.CUSTOM
                                sendUpdate()
                            }
                        )
                    }

                    HorizontalDivider(color = Color(0xFF334155))

                    // Random Wind Gusts Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "🌪️ Random Wind Gusts", color = Color.White, fontWeight = FontWeight.Medium)
                            Text(text = "Periodic sudden gusts of wind blowing snow", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                        Switch(
                            checked = gustsEnabled,
                            onCheckedChange = {
                                gustsEnabled = it
                                selectedWeather = WeatherMode.CUSTOM
                                sendUpdate()
                            }
                        )
                    }
                }
            }

            // Custom Sliders Settings Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "3D Parallax & Fine-Tuning",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )

                    // Density
                    SettingSlider(
                        title = "Snow Density: ${flakeCount.toInt()}",
                        value = flakeCount,
                        valueRange = 50f..300f,
                        onValueChange = {
                            flakeCount = it
                            selectedWeather = WeatherMode.CUSTOM
                            sendUpdate()
                        }
                    )

                    // Speed
                    SettingSlider(
                        title = "Fall Speed: ${String.format(Locale.US, "%.1f", speed)}x",
                        value = speed,
                        valueRange = 0.5f..3.0f,
                        onValueChange = {
                            speed = it
                            selectedWeather = WeatherMode.CUSTOM
                            sendUpdate()
                        }
                    )

                    // Size
                    SettingSlider(
                        title = "Flake Size: ${String.format(Locale.US, "%.1f", size)}x",
                        value = size,
                        valueRange = 0.5f..2.0f,
                        onValueChange = {
                            size = it
                            selectedWeather = WeatherMode.CUSTOM
                            sendUpdate()
                        }
                    )

                    // Wind
                    SettingSlider(
                        title = "Base Wind / Drift: ${String.format(Locale.US, "%.1f", wind)}",
                        value = wind,
                        valueRange = -3.0f..3.0f,
                        onValueChange = {
                            wind = it
                            selectedWeather = WeatherMode.CUSTOM
                            sendUpdate()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun SettingSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFE2E8F0))
        Slider(
            value = value,
            valueRange = valueRange,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF60A5FA),
                activeTrackColor = Color(0xFF3B82F6),
                inactiveTrackColor = Color(0xFF475569)
            )
        )
    }
}
