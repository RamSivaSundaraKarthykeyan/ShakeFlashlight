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
import android.os.PowerManager
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
    private var wakeLock: PowerManager.WakeLock? = null

    // Ensure these constants are defined within your class, typically in a companion object:
    companion object {
        const val CHANNEL_ID = "ShakeDetectionChannel" // Or your preferred channel ID
        const val NOTIFICATION_ID = 1001 // Or your preferred notification ID
        // const val FOREGROUND_SERVICE_CHANNEL_ID = "shake_flashlight_channel" // This seems duplicative if CHANNEL_ID is already defined
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel() // Ensure this is called
        setupSensorManager()
        setupFlashlightController()
        acquireWakeLock()
        // Note: startShakeDetection() is moved to onStartCommand after startForeground
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
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID) // Use the defined CHANNEL_ID
            .setContentTitle("Flashlight Active")
            .setContentText("Shake to toggle flashlight.")
            .setSmallIcon(android.R.drawable.ic_menu_camera) // Replace with a proper icon from your drawables
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10 (API 29) and above, you must specify the type(s).
            // For your flashlight service, "camera" is the appropriate type.
            startForeground(
                NOTIFICATION_ID, // Use the defined NOTIFICATION_ID
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA // Correct type
            )
        } else {
            // For versions below Android 10 (API 29), you don't provide the type parameter.
            startForeground(NOTIFICATION_ID, notification) // Use the defined NOTIFICATION_ID
        }

        // It's good practice to start your actual service work after successfully calling startForeground
        startShakeDetection() // Assuming this method starts the sensor listening

        return START_STICKY
    }


    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ShakeFlashlight::WakeLock"
        )
        wakeLock?.acquire(10*60*1000L /*10 minutes*/)
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
        refreshWakeLock()
    }

    private fun refreshWakeLock() {
        wakeLock?.let { wl ->
            if (wl.isHeld) {
                wl.release()
            }
        }
        acquireWakeLock()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Shake Detection Service", // User-visible name
                NotificationManager.IMPORTANCE_LOW // Or other importance
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
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(shakeDetector)
        wakeLock?.let { wl ->
            if (wl.isHeld) {
                wl.release()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flashlightController.turnOffFlashlight()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}