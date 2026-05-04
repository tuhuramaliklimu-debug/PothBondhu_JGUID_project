package com.jgd.pothbondhu.myapplication.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class CrashDetectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var powerManager: PowerManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var lastUpdateTime: Long = 0
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private val SHAKE_THRESHOLD = 35 // Adjust this value for sensitivity (30-50 is typical)

    private var crashCountdownActive = false
    private var crashDetectionEnabled = true

    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "crash_detection_channel"

        fun isEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("crash_detection_enabled", true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        initSensors()
        acquireWakeLock()
        startForegroundService()
    }

    private fun initSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer != null) {
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_UI
            )
            android.util.Log.d("CrashDetection", "Accelerometer initialized")
        } else {
            android.util.Log.e("CrashDetection", "No accelerometer found on this device")
        }
    }

    private fun acquireWakeLock() {
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CrashDetection:WakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes
    }

    private fun startForegroundService() {
        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PothBondhu")
            .setContentText("Crash detection is active. Your safety is being monitored.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Crash Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "PothBondhu crash detection service"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!crashDetectionEnabled) return
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val curTime = System.currentTimeMillis()

        if (lastUpdateTime == 0L) {
            lastUpdateTime = curTime
            lastX = x
            lastY = y
            lastZ = z
            return
        }

        val timeDiff = curTime - lastUpdateTime
        if (timeDiff > 0) {
            val speed = sqrt(
                ((x - lastX) * (x - lastX) +
                        (y - lastY) * (y - lastY) +
                        (z - lastZ) * (z - lastZ)).toDouble()
            ) / timeDiff * 10000

            if (speed > SHAKE_THRESHOLD && !crashCountdownActive) {
                android.util.Log.d("CrashDetection", "Crash detected! Speed: $speed")
                triggerCrashAlert()
            }
        }

        lastUpdateTime = curTime
        lastX = x
        lastY = y
        lastZ = z
    }

    private fun triggerCrashAlert() {
        crashCountdownActive = true

        // Show crash alert dialog via broadcast
        val intent = Intent("CRASH_DETECTED")
        intent.putExtra("message", "Vehicle crash detected. Starting SOS countdown.")
        sendBroadcast(intent)

        // Wait 5 seconds for user to cancel
        android.os.Handler(mainLooper).postDelayed({
            if (crashCountdownActive) {
                executeCrashSOS()
            }
        }, 5000)
    }

    private fun executeCrashSOS() {
        crashCountdownActive = false

        val intent = Intent(this, MainActivity::class.java)
        intent.action = "AUTO_SOS"
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)

        android.util.Log.d("CrashDetection", "Auto SOS triggered due to crash detection")
    }

    fun cancelCrashAlert() {
        crashCountdownActive = false
        android.util.Log.d("CrashDetection", "Crash alert cancelled by user")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        wakeLock?.release()
    }
}