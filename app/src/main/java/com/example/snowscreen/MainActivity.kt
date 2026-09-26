package com.hoangbinhan.snowscreen

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hoangbinhan.snowscreen.billing.BillingManager
import com.hoangbinhan.snowscreen.billing.ProPreferences
import com.hoangbinhan.snowscreen.model.WeatherMode
import com.hoangbinhan.snowscreen.service.SnowService
import com.hoangbinhan.snowscreen.ui.ProUnlockDialog
import com.hoangbinhan.snowscreen.ui.theme.SnowScreenTheme
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private lateinit var proPreferences: ProPreferences
    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        proPreferences = ProPreferences(this)
        billingManager = BillingManager(this, proPreferences)
        billingManager.startConnection()

        enableEdgeToEdge()
        setContent {
            SnowScreenTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
                ) {
                    SnowControlScreen(
                        billingManager = billingManager,
                        onCheckOverlayPermission = { checkOverlayPermission() },
                        hasOverlayPermission = { hasOverlayPermission() },
                        onCheckUsageStatsPermission = { checkUsageStatsPermission() },
                        hasUsageStatsPermission = { hasUsageStatsPermission() },
                        onRequestPurchase = { billingManager.launchPurchaseFlow(this@MainActivity) }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.destroy()
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

    @Suppress("DEPRECATION")
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun checkUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
    }
}

@Composable
fun loadAssetImage(context: Context, path: String): ImageBitmap? {
    return remember(path) {
        try {
            context.assets.open(path).use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnowControlScreen(
    billingManager: BillingManager,
    onCheckOverlayPermission: () -> Unit,
    hasOverlayPermission: () -> Boolean,
    onCheckUsageStatsPermission: () -> Unit,
    hasUsageStatsPermission: () -> Boolean,
    onRequestPurchase: () -> Unit
) {
    val context = LocalContext.current
    var isRunning by remember { mutableStateOf(SnowService.isRunning) }
    var hasPermission by remember { mutableStateOf(hasOverlayPermission()) }
    var showGuideDialog by remember { mutableStateOf(!hasPermission) }

    val isProUnlocked by billingManager.isProUnlocked.collectAsState()
    var showProDialog by remember { mutableStateOf(false) }

    // Lifecycle observer to auto-detect when user returns from Settings page after granting permission
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val updatedPermission = hasOverlayPermission()
                hasPermission = updatedPermission
                if (updatedPermission) {
                    showGuideDialog = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var flakeCount by remember { mutableStateOf(70f) }
    var speed by remember { mutableStateOf(0.7f) }
    var size by remember { mutableStateOf(0.7f) }
    var wind by remember { mutableStateOf(-1.0f) }
    var tiltEnabled by remember { mutableStateOf(true) }
    var gustsEnabled by remember { mutableStateOf(true) }
    var onlyHomeLock by remember { mutableStateOf(false) }

    // 8 Snowflake shape states
    var enableDendrite by remember { mutableStateOf(true) }
    var enableHexagon by remember { mutableStateOf(true) }
    var enableStar by remember { mutableStateOf(true) }
    var enableCluster by remember { mutableStateOf(true) }
    var enableDiamond by remember { mutableStateOf(true) }
    var enableStar12 by remember { mutableStateOf(true) }
    var enableCross by remember { mutableStateOf(true) }
    var enableDust by remember { mutableStateOf(true) }

    var selectedWeather by remember { mutableStateOf(WeatherMode.GENTLE) }

    // Ice Blue Theme Palette
    val icePrimary = Color(0xFF00BCD4)
    val iceSurfaceVariant = Color(0xFF1E293B).copy(alpha = 0.85f)
    val iceOnSurfaceVariant = Color(0xFFE0F7FA)

    // Sweeping Light Shimmer Animation
    val infiniteTransition = rememberInfiniteTransition(label = "ShimmerTransition")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerOffset"
    )

    fun stopService() {
        val intent = Intent(context, SnowService::class.java).apply {
            action = SnowService.ACTION_STOP
        }
        context.startService(intent)
        isRunning = false
    }

    fun sendUpdate() {
        val allDisabled = !enableDendrite && !enableHexagon && !enableStar && !enableCluster &&
                          !enableDiamond && !enableStar12 && !enableCross && !enableDust

        if (allDisabled) {
            if (isRunning) {
                stopService()
            }
            return
        }

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
                putExtra(SnowService.EXTRA_DENDRITE, enableDendrite)
                putExtra(SnowService.EXTRA_HEXAGON, enableHexagon)
                putExtra(SnowService.EXTRA_STAR, enableStar)
                putExtra(SnowService.EXTRA_CLUSTER, enableCluster)
                putExtra(SnowService.EXTRA_DIAMOND, enableDiamond)
                putExtra(SnowService.EXTRA_STAR12, enableStar12)
                putExtra(SnowService.EXTRA_CROSS, enableCross)
                putExtra(SnowService.EXTRA_DUST, enableDust)
            }
            context.startService(intent)
        }
    }

    if (showGuideDialog && !hasPermission) {
        PermissionGuideDialog(
            onDismiss = { showGuideDialog = false },
            onOpenSettings = { onCheckOverlayPermission() }
        )
    }

    if (showProDialog) {
        ProUnlockDialog(
            onDismiss = { showProDialog = false },
            onUnlockClick = {
                onRequestPurchase()
                showProDialog = false
            },
            onRestoreClick = {
                billingManager.queryPurchases()
                showProDialog = false
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B132B),
                        Color(0xFF1C2541),
                        Color(0xFF3A506B)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Sweeping Light Gradient Brush
            val startX = shimmerOffset * 1000f
            val shimmerBrush = if (isRunning) {
                Brush.linearGradient(
                    colors = listOf(Color(0xFFFF3D00), Color(0xFFFF8A80), Color(0xFFFF3D00)),
                    start = Offset(startX - 250f, 0f),
                    end = Offset(startX + 250f, 0f)
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(Color(0xFF00BCD4), Color(0xFFFFFFFF), Color(0xFF00BCD4)),
                    start = Offset(startX - 250f, 0f),
                    end = Offset(startX + 250f, 0f)
                )
            }

            // Main Action Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(shimmerBrush)
            ) {
                Button(
                    onClick = {
                        hasPermission = hasOverlayPermission()
                        if (!hasPermission) {
                            showGuideDialog = true
                            return@Button
                        }

                        if (isRunning) {
                            stopService()
                        } else {
                            val allDisabled = !enableDendrite && !enableHexagon && !enableStar && !enableCluster &&
                                              !enableDiamond && !enableStar12 && !enableCross && !enableDust
                            if (allDisabled) return@Button

                            val intent = Intent(context, SnowService::class.java).apply {
                                action = SnowService.ACTION_START
                                putExtra(SnowService.EXTRA_FLAKE_COUNT, flakeCount.toInt())
                                putExtra(SnowService.EXTRA_SPEED, speed)
                                putExtra(SnowService.EXTRA_SIZE, size)
                                putExtra(SnowService.EXTRA_WIND, wind)
                                putExtra(SnowService.EXTRA_TILT_ENABLED, tiltEnabled)
                                putExtra(SnowService.EXTRA_GUSTS_ENABLED, gustsEnabled)
                                putExtra(SnowService.EXTRA_ONLY_HOME_LOCK, onlyHomeLock)
                                putExtra(SnowService.EXTRA_DENDRITE, enableDendrite)
                                putExtra(SnowService.EXTRA_HEXAGON, enableHexagon)
                                putExtra(SnowService.EXTRA_STAR, enableStar)
                                putExtra(SnowService.EXTRA_CLUSTER, enableCluster)
                                putExtra(SnowService.EXTRA_DIAMOND, enableDiamond)
                                putExtra(SnowService.EXTRA_STAR12, enableStar12)
                                putExtra(SnowService.EXTRA_CROSS, enableCross)
                                putExtra(SnowService.EXTRA_DUST, enableDust)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            isRunning = true
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = if (isRunning) Color.White else Color(0xFF0B132B)
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(text = if (isRunning) "🛑" else "❄️", fontSize = 26.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isRunning) "STOP SNOW EFFECT" else "START SNOW EFFECT",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Permission Warning Card
            AnimatedVisibility(
                visible = !hasPermission,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF7F1D1D).copy(alpha = 0.9f),
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "⚠️", fontSize = 20.sp)
                            Text(
                                text = "Overlay Permission Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "To render snow over the status bar, home screen, and lock screen, please grant 'Draw over other apps' permission.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFECACA)
                        )
                        Button(
                            onClick = { showGuideDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF7F1D1D)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Grant Permission Guide", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Weather Presets Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Weather Presets",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF80D8FF),
                    fontWeight = FontWeight.Bold
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
                                        WeatherMode.GENTLE -> { flakeCount = 70f; speed = 0.7f; size = 0.7f; wind = -1.0f }
                                        WeatherMode.BLIZZARD -> { flakeCount = 250f; speed = 2.2f; size = 1.3f; wind = 1.5f }
                                        WeatherMode.MAGICAL -> { flakeCount = 180f; speed = 1.2f; size = 1.5f; wind = 0.0f }
                                        else -> {}
                                    }
                                    sendUpdate()
                                },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = icePrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = iceSurfaceVariant,
                                    labelColor = iceOnSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }

            // Snowflake Shapes Customization Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = iceSurfaceVariant,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Snowflake Shapes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = iceOnSurfaceVariant
                    )

                    ShapeToggleRow(
                        flakeType = 0,
                        title = "Stellar Dendrite",
                        subtitle = "6-Ray fern crystal",
                        checked = enableDendrite,
                        onCheckedChange = {
                            enableDendrite = it
                            sendUpdate()
                        }
                    )

                    HorizontalDivider(color = Color(0xFF334155))

                    ShapeToggleRow(
                        flakeType = 1,
                        title = "Hexagonal Plate",
                        subtitle = "Geometric crystal star",
                        checked = enableHexagon,
                        onCheckedChange = {
                            enableHexagon = it
                            sendUpdate()
                        }
                    )

                    HorizontalDivider(color = Color(0xFF334155))

                    ShapeToggleRow(
                        flakeType = 2,
                        title = "8-Point Crystal Star",
                        subtitle = "Sharp intersecting needles",
                        checked = enableStar,
                        onCheckedChange = {
                            enableStar = it
                            sendUpdate()
                        }
                    )

                    HorizontalDivider(color = Color(0xFF334155))

                    ShapeToggleRow(
                        flakeType = 3,
                        title = "Fluffy Cluster",
                        subtitle = "Crystal bead cluster",
                        checked = enableCluster,
                        onCheckedChange = {
                            enableCluster = it
                            sendUpdate()
                        }
                    )

                    HorizontalDivider(color = Color(0xFF334155))

                    ShapeToggleRow(
                        flakeType = 4,
                        title = "Diamond Tip Crystal",
                        subtitle = "6-Ray diamond points",
                        checked = enableDiamond,
                        onCheckedChange = {
                            enableDiamond = it
                            sendUpdate()
                        }
                    )

                    HorizontalDivider(color = Color(0xFF334155))

                    ShapeToggleRow(
                        flakeType = 5,
                        title = "12-Point Radiant Star",
                        subtitle = "Alternating long/short rays",
                        checked = enableStar12,
                        onCheckedChange = {
                            enableStar12 = it
                            sendUpdate()
                        }
                    )

                    HorizontalDivider(color = Color(0xFF334155))

                    ShapeToggleRow(
                        flakeType = 6,
                        title = "Cross Ice Crystal",
                        subtitle = "Cross needle with dots",
                        checked = enableCross,
                        onCheckedChange = {
                            enableCross = it
                            sendUpdate()
                        }
                    )

                    HorizontalDivider(color = Color(0xFF334155))

                    ShapeToggleRow(
                        flakeType = 7,
                        title = "Soft Snow Dust",
                        subtitle = "Soft glowing snow particle",
                        checked = enableDust,
                        onCheckedChange = {
                            enableDust = it
                            sendUpdate()
                        }
                    )
                }
            }

            // Physics & Smart Toggles Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = iceSurfaceVariant,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Physics & Smart Toggles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = iceOnSurfaceVariant
                    )

                    // Only Home & Lock Screen Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "🏠", fontSize = 22.sp)
                            Column {
                                Text("Only Home & Lock Screen", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = iceOnSurfaceVariant)
                                Text("Hide snow when opening other apps", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
                            }
                        }
                        Switch(
                            checked = onlyHomeLock,
                            onCheckedChange = { checked ->
                                onlyHomeLock = checked
                                if (checked && !hasUsageStatsPermission()) {
                                    onCheckUsageStatsPermission()
                                }
                                selectedWeather = WeatherMode.CUSTOM
                                sendUpdate()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = icePrimary
                            )
                        )
                    }

                    HorizontalDivider(color = Color(0xFF334155))

                    // Sensor Tilt Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "📱", fontSize = 22.sp)
                            Column {
                                Text("Tilt Sensor Wind", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = iceOnSurfaceVariant)
                                Text("Tilt phone to blow snow sideways", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
                            }
                        }
                        Switch(
                            checked = tiltEnabled,
                            onCheckedChange = {
                                tiltEnabled = it
                                selectedWeather = WeatherMode.CUSTOM
                                sendUpdate()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = icePrimary
                            )
                        )
                    }

                    HorizontalDivider(color = Color(0xFF334155))

                    // Random Wind Gusts Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "🌪️", fontSize = 22.sp)
                            Column {
                                Text("Random Wind Gusts", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = iceOnSurfaceVariant)
                                Text("Periodic gusts blowing snow dynamically", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
                            }
                        }
                        Switch(
                            checked = gustsEnabled,
                            onCheckedChange = {
                                gustsEnabled = it
                                selectedWeather = WeatherMode.CUSTOM
                                sendUpdate()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = icePrimary
                            )
                        )
                    }
                }
            }

            // Advanced Sliders Card (Configuration) - Wrapped with Pro Lock Overlay
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = iceSurfaceVariant,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Configuration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = iceOnSurfaceVariant
                        )

                        SettingSlider(
                            title = "Snow Density: ${flakeCount.toInt()}",
                            value = flakeCount,
                            valueRange = 50f..300f,
                            enabled = isProUnlocked,
                            onValueChange = {
                                flakeCount = it
                                selectedWeather = WeatherMode.CUSTOM
                                sendUpdate()
                            }
                        )

                        SettingSlider(
                            title = "Fall Speed: ${String.format(Locale.US, "%.1f", speed)}x",
                            value = speed,
                            valueRange = 0.5f..3.0f,
                            enabled = isProUnlocked,
                            onValueChange = {
                                speed = it
                                selectedWeather = WeatherMode.CUSTOM
                                sendUpdate()
                            }
                        )

                        SettingSlider(
                            title = "Flake Size: ${String.format(Locale.US, "%.1f", size)}x",
                            value = size,
                            valueRange = 0.5f..2.0f,
                            enabled = isProUnlocked,
                            onValueChange = {
                                size = it
                                selectedWeather = WeatherMode.CUSTOM
                                sendUpdate()
                            }
                        )

                        SettingSlider(
                            title = "Base Wind / Drift: ${String.format(Locale.US, "%.1f", wind)}",
                            value = wind,
                            valueRange = -3.0f..3.0f,
                            enabled = isProUnlocked,
                            onValueChange = {
                                wind = it
                                selectedWeather = WeatherMode.CUSTOM
                                sendUpdate()
                            }
                        )
                    }
                }

                // Translucent Pro Lock Overlay when NOT unlocked
                if (!isProUnlocked) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                            .clickable { showProDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { showProDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E5FF),
                                contentColor = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("🔒 Unlock Configuration Pro", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "developed by Hoang Binh An",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8).copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun PermissionGuideDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(0) }
    val stepPaths = remember { listOf("guide/1.png", "guide/2.png", "guide/3.png", "guide/4.png") }
    val currentImageBitmap = loadAssetImage(context, stepPaths[currentStep])

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E293B),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Permission Guide",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE0F7FA)
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕", color = Color(0xFF94A3B8), fontSize = 18.sp)
                    }
                }

                Text(
                    text = "Follow these steps to grant 'Display over other apps' permission so snow can render over status bar & lock screen:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentImageBitmap != null) {
                        Image(
                            bitmap = currentImageBitmap,
                            contentDescription = "Step ${currentStep + 1}",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text("Step ${currentStep + 1}", color = Color.White)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { if (currentStep > 0) currentStep-- },
                        enabled = currentStep > 0
                    ) {
                        Text("◀ Prev", color = if (currentStep > 0) Color(0xFF00E5FF) else Color.Gray)
                    }

                    Text(
                        text = "Step ${currentStep + 1} / ${stepPaths.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFE0F7FA),
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(
                        onClick = { if (currentStep < stepPaths.size - 1) currentStep++ },
                        enabled = currentStep < stepPaths.size - 1
                    ) {
                        Text("Next ▶", color = if (currentStep < stepPaths.size - 1) Color(0xFF00E5FF) else Color.Gray)
                    }
                }

                Button(
                    onClick = {
                        onOpenSettings()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color(0xFF0F172A)
                    )
                ) {
                    Text("Grant Permission", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun SnowflakePreviewIcon(flakeType: Int, modifier: Modifier = Modifier) {
    val surfaceColor = Color(0xFF80D8FF)

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0xFF00BCD4).copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2.5f

            when (flakeType) {
                0 -> {
                    drawCircle(color = surfaceColor, radius = r * 0.35f, center = center)
                    for (i in 0..5) {
                        val angle = i * Math.PI / 3.0
                        val cosA = cos(angle).toFloat()
                        val sinA = sin(angle).toFloat()
                        val tip = Offset(center.x + cosA * r * 1.8f, center.y + sinA * r * 1.8f)
                        drawLine(color = surfaceColor, start = center, end = tip, strokeWidth = 2f, cap = StrokeCap.Round)
                    }
                }
                1 -> {
                    drawCircle(color = surfaceColor, radius = r * 0.4f, center = center)
                    for (i in 0..5) {
                        val angle = i * Math.PI / 3.0
                        val nextAngle = (i + 1) * Math.PI / 3.0
                        val p1 = Offset(center.x + cos(angle).toFloat() * r * 1.5f, center.y + sin(angle).toFloat() * r * 1.5f)
                        val p2 = Offset(center.x + cos(nextAngle).toFloat() * r * 1.5f, center.y + sin(nextAngle).toFloat() * r * 1.5f)
                        drawLine(color = surfaceColor, start = p1, end = p2, strokeWidth = 2f, cap = StrokeCap.Round)
                    }
                }
                2 -> {
                    drawCircle(color = surfaceColor, radius = r * 0.4f, center = center)
                    for (i in 0..3) {
                        val angle = i * Math.PI / 4.0
                        val cosA = cos(angle).toFloat()
                        val sinA = sin(angle).toFloat()
                        drawLine(
                            color = surfaceColor,
                            start = Offset(center.x - cosA * r * 1.6f, center.y - sinA * r * 1.6f),
                            end = Offset(center.x + cosA * r * 1.6f, center.y + sinA * r * 1.6f),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round
                        )
                    }
                }
                3 -> {
                    drawCircle(color = surfaceColor, radius = r * 0.7f, center = center)
                    for (i in 0..5) {
                        val angle = i * Math.PI / 3.0
                        val sat = Offset(center.x + cos(angle).toFloat() * r * 1.2f, center.y + sin(angle).toFloat() * r * 1.2f)
                        drawCircle(color = surfaceColor, radius = r * 0.3f, center = sat)
                    }
                }
                4 -> {
                    drawCircle(color = surfaceColor, radius = r * 0.3f, center = center)
                    for (i in 0..5) {
                        val angle = i * Math.PI / 3.0
                        val cosA = cos(angle).toFloat()
                        val sinA = sin(angle).toFloat()
                        val tip = Offset(center.x + cosA * r * 1.6f, center.y + sinA * r * 1.6f)
                        drawLine(color = surfaceColor, start = center, end = tip, strokeWidth = 2f, cap = StrokeCap.Round)
                    }
                }
                5 -> {
                    drawCircle(color = surfaceColor, radius = r * 0.3f, center = center)
                    for (i in 0..11) {
                        val angle = i * Math.PI / 6.0
                        val cosA = cos(angle).toFloat()
                        val sinA = sin(angle).toFloat()
                        val len = if (i % 2 == 0) r * 1.8f else r * 1.1f
                        val tip = Offset(center.x + cosA * len, center.y + sinA * len)
                        drawLine(color = surfaceColor, start = center, end = tip, strokeWidth = 1.8f, cap = StrokeCap.Round)
                    }
                }
                6 -> {
                    for (i in 0..3) {
                        val angle = i * Math.PI / 2.0
                        val cosA = cos(angle).toFloat()
                        val sinA = sin(angle).toFloat()
                        val tip = Offset(center.x + cosA * r * 1.8f, center.y + sinA * r * 1.8f)
                        drawLine(color = surfaceColor, start = center, end = tip, strokeWidth = 2f, cap = StrokeCap.Round)
                        drawCircle(color = surfaceColor, radius = r * 0.25f, center = tip)
                    }
                }
                7 -> {
                    drawCircle(color = surfaceColor, radius = r * 1.1f, center = center)
                }
            }
        }
    }
}

@Composable
fun ShapeToggleRow(
    flakeType: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            SnowflakePreviewIcon(flakeType = flakeType)
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = Color(0xFFE0F7FA))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF00BCD4)
            )
        )
    }
}

@Composable
fun SettingSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (enabled) Color(0xFFE0F7FA) else Color(0xFF64748B)
        )
        Slider(
            value = value,
            valueRange = valueRange,
            enabled = enabled,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = if (enabled) Color(0xFF80D8FF) else Color(0xFF475569),
                activeTrackColor = if (enabled) Color(0xFF00BCD4) else Color(0xFF334155),
                inactiveTrackColor = Color(0xFF1E293B)
            )
        )
    }
}
