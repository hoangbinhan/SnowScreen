package com.hoangbinhan.snowscreen.service

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.hoangbinhan.snowscreen.MainActivity
import com.hoangbinhan.snowscreen.R
import com.hoangbinhan.snowscreen.model.SnowSettings

class SnowService : Service(), SensorEventListener {

    private var windowManager: WindowManager? = null
    private var snowView: SnowView? = null
    private var currentSettings = SnowSettings()

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var keyguardManager: KeyguardManager? = null

    private val handler = Handler(Looper.getMainLooper())
    private var visibilityRunnable: Runnable? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_UPDATE = "ACTION_UPDATE"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"

        const val EXTRA_FLAKE_COUNT = "EXTRA_FLAKE_COUNT"
        const val EXTRA_SPEED = "EXTRA_SPEED"
        const val EXTRA_SIZE = "EXTRA_SIZE"
        const val EXTRA_WIND = "EXTRA_WIND"
        const val EXTRA_TILT_ENABLED = "EXTRA_TILT_ENABLED"
        const val EXTRA_GUSTS_ENABLED = "EXTRA_GUSTS_ENABLED"
        const val EXTRA_ONLY_HOME_LOCK = "EXTRA_ONLY_HOME_LOCK"
        const val EXTRA_DENDRITE = "EXTRA_DENDRITE"
        const val EXTRA_HEXAGON = "EXTRA_HEXAGON"
        const val EXTRA_STAR = "EXTRA_STAR"
        const val EXTRA_CLUSTER = "EXTRA_CLUSTER"
        const val EXTRA_DIAMOND = "EXTRA_DIAMOND"
        const val EXTRA_STAR12 = "EXTRA_STAR12"
        const val EXTRA_CROSS = "EXTRA_CROSS"
        const val EXTRA_DUST = "EXTRA_DUST"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "snow_service_channel"

        var isRunning = false
            private set

        var isPaused = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> if (isRunning) setPaused(true)
            ACTION_RESUME -> if (isRunning) setPaused(false)
            ACTION_UPDATE -> {
                val count = intent.getIntExtra(EXTRA_FLAKE_COUNT, currentSettings.flakeCount)
                val speed = intent.getFloatExtra(EXTRA_SPEED, currentSettings.speedMultiplier)
                val size = intent.getFloatExtra(EXTRA_SIZE, currentSettings.sizeMultiplier)
                val wind = intent.getFloatExtra(EXTRA_WIND, currentSettings.wind)
                val tiltEnabled = intent.getBooleanExtra(EXTRA_TILT_ENABLED, currentSettings.sensorTiltEnabled)
                val gustsEnabled = intent.getBooleanExtra(EXTRA_GUSTS_ENABLED, currentSettings.randomWindGusts)
                val onlyHomeLock = intent.getBooleanExtra(EXTRA_ONLY_HOME_LOCK, currentSettings.onlyHomeAndLock)
                
                val enableDendrite = intent.getBooleanExtra(EXTRA_DENDRITE, currentSettings.enableDendrite)
                val enableHexagon = intent.getBooleanExtra(EXTRA_HEXAGON, currentSettings.enableHexagon)
                val enableStar = intent.getBooleanExtra(EXTRA_STAR, currentSettings.enableStar)
                val enableCluster = intent.getBooleanExtra(EXTRA_CLUSTER, currentSettings.enableCluster)
                val enableDiamond = intent.getBooleanExtra(EXTRA_DIAMOND, currentSettings.enableDiamond)
                val enableStar12 = intent.getBooleanExtra(EXTRA_STAR12, currentSettings.enableStar12)
                val enableCross = intent.getBooleanExtra(EXTRA_CROSS, currentSettings.enableCross)
                val enableDust = intent.getBooleanExtra(EXTRA_DUST, currentSettings.enableDust)

                val wasTiltEnabled = currentSettings.sensorTiltEnabled
                currentSettings = SnowSettings(
                    flakeCount = count,
                    speedMultiplier = speed,
                    sizeMultiplier = size,
                    wind = wind,
                    sensorTiltEnabled = tiltEnabled,
                    randomWindGusts = gustsEnabled,
                    onlyHomeAndLock = onlyHomeLock,
                    enableDendrite = enableDendrite,
                    enableHexagon = enableHexagon,
                    enableStar = enableStar,
                    enableCluster = enableCluster,
                    enableDiamond = enableDiamond,
                    enableStar12 = enableStar12,
                    enableCross = enableCross,
                    enableDust = enableDust
                )
                snowView?.updateSettings(currentSettings)

                if (tiltEnabled && !wasTiltEnabled) {
                    registerSensor()
                } else if (!tiltEnabled && wasTiltEnabled) {
                    unregisterSensor()
                }

                updateVisibilityChecker()
            }
            else -> {
                if (!isRunning) {
                    startForegroundServiceWithNotification()
                    showSnowOverlay()
                    if (currentSettings.sensorTiltEnabled) {
                        registerSensor()
                    }
                    updateVisibilityChecker()
                    isRunning = true
                }
            }
        }
        return START_STICKY
    }

    private fun setPaused(paused: Boolean) {
        isPaused = paused
        snowView?.setPaused(paused)
        updateVisibilityChecker()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun updateVisibilityChecker() {
        visibilityRunnable?.let { handler.removeCallbacks(it) }

        if (isPaused) {
            snowView?.visibility = View.GONE
            return
        }

        if (currentSettings.onlyHomeAndLock) {
            visibilityRunnable = object : Runnable {
                override fun run() {
                    checkAndToggleVisibility()
                    handler.postDelayed(this, 1000L) // check every 1 sec
                }
            }
            handler.post(visibilityRunnable!!)
        } else {
            snowView?.visibility = View.VISIBLE
        }
    }

    private fun getLauncherPackageName(): String? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    private fun getForegroundPackageName(): String? {
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - 4000, time)
        val event = UsageEvents.Event()
        var currentApp: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                currentApp = event.packageName
            }
        }
        return currentApp
    }

    private fun checkAndToggleVisibility() {
        // 1. Lock Screen Check
        val isLocked = keyguardManager?.isKeyguardLocked == true
        if (isLocked) {
            snowView?.visibility = View.VISIBLE
            return
        }

        // 2. Unlocked Home Screen vs Other App Check
        val foregroundApp = getForegroundPackageName()
        val launcherApp = getLauncherPackageName()

        // Show snow if currently on Launcher, on our own app, or if foreground app cannot be determined
        if (foregroundApp == null || foregroundApp == launcherApp || foregroundApp == packageName) {
            snowView?.visibility = View.VISIBLE
        } else {
            // Hide snow when user opens other 3rd party apps
            snowView?.visibility = View.GONE
        }
    }

    private fun registerSensor() {
        accelerometer?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun unregisterSensor() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val tiltX = event.values[0]
            snowView?.updateTilt(tiltX)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startForegroundServiceWithNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Snow Effect Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps snow falling effect active on screen, status bar, and lock screen"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = buildNotification()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val toggleIntent = PendingIntent.getService(
            this, 2,
            Intent(this, SnowService::class.java).apply {
                action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 3,
            Intent(this, SnowService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Snow Screen")
            .setContentText(if (isPaused) "Snow paused" else "Snow effect active")
            .setSmallIcon(R.drawable.ic_stat_snow)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, if (isPaused) "Resume" else "Pause", toggleIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun showSnowOverlay() {
        if (snowView != null) return

        snowView = SnowView(this, currentSettings)

        val layoutParamFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            layoutParamFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            windowManager?.addView(snowView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restartServiceIntent = Intent(applicationContext, SnowService::class.java).apply {
            action = ACTION_START
        }
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext, 1, restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmService = applicationContext.getSystemService(ALARM_SERVICE) as AlarmManager
        alarmService.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        visibilityRunnable?.let { handler.removeCallbacks(it) }
        unregisterSensor()
        isRunning = false
        isPaused = false
        if (snowView != null && windowManager != null) {
            try {
                windowManager?.removeView(snowView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            snowView = null
        }
    }
}
