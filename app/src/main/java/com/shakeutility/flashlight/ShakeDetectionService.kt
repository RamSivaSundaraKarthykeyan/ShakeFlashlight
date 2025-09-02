package com.shakeutility.flashlight

import android.app.*
import android.app.PendingIntent
import android.app.Service
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
// import androidx.privacysandbox.tools.core.generator.build
import com.shakeutility.flashlight.MainActivity


const val CHANNEL_ID = "MyForegroundServiceChannel"

class ShakeDetectionService : Service(), ShakeDetector.OnShakeListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var shakeDetector: ShakeDetector
    private lateinit var flashlightController: FlashlightController

    companion object {
        const val CHANNEL_ID = "ShakeDetectionChannel"
        const val NOTIFICATION_ID = 1
        const val FOREGROUND_SERVICE_CHANNEL_ID = "shake_flashlight_channel"

    }

    override fun onCreate() {
        super.onCreate()
        setupSensorManager()
        setupFlashlightController()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Prepare your PendingIntent
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            pendingIntentFlags
        )

        // Build the notification using your service’s CHANNEL_ID
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Flashlight Active")
            .setContentText("Shake to toggle flashlight.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        // Start the service in the foreground
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }



    private fun setupSensorManager() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        shakeDetector = ShakeDetector(this)
    }

    private fun setupFlashlightController() {
        flashlightController = FlashlightController(this)
    }

    private fun startShakeDetection() {
        accelerometer?.let { sensor ->
            sensorManager.registerListener(
                shakeDetector,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onDoubleShake() {
        flashlightController.toggleFlashlight()

        // Update notification
        val updatedNotification = createNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, updatedNotification)
    }



    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "My Foreground Service Channel", // User-visible name
                NotificationManager.IMPORTANCE_DEFAULT // Or other importance
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, com.shakeutility.flashlight.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val flashlightStatus = if (flashlightController.getFlashlightState()) "ON" else "OFF"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Shake Flashlight Active")
            .setContentText("Shake device left-right twice to toggle. Flashlight: $flashlightStatus")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(shakeDetector)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flashlightController.turnOffFlashlight()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}