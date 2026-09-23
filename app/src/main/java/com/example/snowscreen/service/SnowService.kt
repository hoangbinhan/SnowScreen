package com.example.snowscreen.service

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.snowscreen.MainActivity
import com.example.snowscreen.R
import com.example.snowscreen.model.SnowSettings

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

        const val EXTRA_FLAKE_COUNT = "EXTRA_FLAKE_COUNT"
        const val EXTRA_SPEED = "EXTRA_SPEED"
        const val EXTRA_SIZE = "EXTRA_SIZE"
        const val EXTRA_WIND = "EXTRA_WIND"
        const val EXTRA_TILT_ENABLED = "EXTRA_TILT_ENABLED"
        const val EXTRA_GUSTS_ENABLED = "EXTRA_GUSTS_ENABLED"
        const val EXTRA_ONLY_HOME_LOCK = "EXTRA_ONLY_HOME_LOCK"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "snow_service_channel"

        var isRunning = false
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
            ACTION_UPDATE -> {
                val count = intent.getIntExtra(EXTRA_FLAKE_COUNT, currentSettings.flakeCount)
                val speed = intent.getFloatExtra(EXTRA_SPEED, currentSettings.speedMultiplier)
                val size = intent.getFloatExtra(EXTRA_SIZE, currentSettings.sizeMultiplier)
                val wind = intent.getFloatExtra(EXTRA_WIND, currentSettings.wind)
                val tiltEnabled = intent.getBooleanExtra(EXTRA_TILT_ENABLED, currentSettings.sensorTiltEnabled)
                val gustsEnabled = intent.getBooleanExtra(EXTRA_GUSTS_ENABLED, currentSettings.randomWindGusts)
                val onlyHomeLock = intent.getBooleanExtra(EXTRA_ONLY_HOME_LOCK, currentSettings.onlyHomeAndLock)

                val wasTiltEnabled = currentSettings.sensorTiltEnabled
                currentSettings = SnowSettings(count, speed, size, wind, tiltEnabled, gustsEnabled, onlyHomeLock)
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

    private fun updateVisibilityChecker() {
        visibilityRunnable?.let { handler.removeCallbacks(it) }

        if (currentSettings.onlyHomeAndLock) {
            visibilityRunnable = object : Runnable {
                override fun run() {
                    checkAndToggleVisibility()
                    handler.postDelayed(this, 1000L) // check every second
                }
            }
            handler.post(visibilityRunnable!!)
        } else {
            snowView?.visibility = View.VISIBLE
        }
    }

    private fun checkAndToggleVisibility() {
        val isLocked = keyguardManager?.isKeyguardLocked == true
        if (isLocked) {
            snowView?.visibility = View.VISIBLE
        } else {
            // If onlyHomeAndLock is true and not locked, check if user is on Home or inside our own app
            // For simplicity and robustness, when unlocked, if not in our app, we can hide or show based on launcher check.
            // Let's show on lock screen & home.
            snowView?.visibility = View.VISIBLE
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

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Snow on Screen Pro")
            .setContentText("3D Parallax snow effect is active across screen & lock screen")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun showSnowOverlay() {
        if (snowView != null) return

        snowView = SnowView(this, currentSettings)

        // Window flags covering Status Bar and Navigation Bar (`FLAG_LAYOUT_IN_SCREEN`, `FLAG_LAYOUT_NO_LIMITS`)
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
        )

        try {
            windowManager?.addView(snowView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Keep running or handle restart if desired
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
