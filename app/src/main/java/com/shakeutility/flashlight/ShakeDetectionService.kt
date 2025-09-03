package com.shakeutility.flashlight

import android.Manifest
import android.app.*
import android.app.PendingIntent
import android.app.Service
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.util.Log
import android.widget.Toast
import android.hardware.Camera // Import for older API
import android.content.pm.PackageManager
import android.content.Intent
import android.os.PowerManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import android.os.Vibrator
import android.os.VibrationEffect
import androidx.annotation.RequiresPermission
// import androidx.privacysandbox.tools.core.generator.build
import com.shakeutility.flashlight.MainActivity


const val CHANNEL_ID = "MyForegroundServiceChannel"

class ShakeDetectionService : Service(), ShakeDetector.OnShakeListener {

    private lateinit var vibrator: Vibrator
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
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        // 1) Initialize sensors first
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        shakeDetector = ShakeDetector(this)
        // 2) Now everything else
        createNotificationChannel()
        setupFlashlightController()
        acquireWakeLock()
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

        accelerometer?.let { sensor ->
            sensorManager.registerListener(
                shakeDetector,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        } ?: Log.e("ShakeService", "No accelerometer on device")
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
        if (accelerometer == null) {
            Log.e("ShakeService", "ACCELEROMETER NOT AVAILABLE IN SERVICE!")
        } else {
            Log.i("ShakeService", "Accelerometer available in service.")
        }
        shakeDetector = ShakeDetector(this)
        Log.i("ShakeService", "ShakeDetector initialized in service.")
    }

    private fun setupFlashlightController() {
        flashlightController = FlashlightController(this)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @RequiresPermission(Manifest.permission.VIBRATE)
    override fun onChopChop() {
        val vibrationMillis: Long = 100 // Duration of vibration

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // For API 26 (Android Oreo) and above
            val vibrationEffect = VibrationEffect.createOneShot(vibrationMillis, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(vibrationEffect)
        } else {
            // For versions below API 26 (deprecated method)
            @Suppress("DEPRECATION") // Suppress the deprecation warning for the older API
            vibrator.vibrate(vibrationMillis)
        }
        // Toggle flashlight
        Log.i("ShakeService", "SERVICE onChopChop RECEIVED!")
        // ... (vibration logic) ...
        Log.d("ShakeService", "Attempting to toggle flashlight...")
        val success = flashlightController.toggleFlashlight()
        Log.d("ShakeService", "Flashlight toggle success: $success. Is On: ${flashlightController.getFlashlightState()}")
        // ... (notification update logic) ...
            // Vibrate: short buzz on each toggle

        // Update notification
        val notification = createNotification()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
        refreshWakeLock()
    }

    private fun startShakeDetection() {
        accelerometer?.let { sensor ->
            val registered = sensorManager.registerListener(
                shakeDetector,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            Log.i("ShakeService", "Sensor listener registration attempt. Success: $registered")
        } ?: Log.e("ShakeService", "Cannot start shake detection, accelerometer is null.")
    }

    @RequiresApi(Build.VERSION_CODES.M)


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
        // ... unregister listeners, release wakelock ...
        if (::flashlightController.isInitialized) { // Check if initialized
            flashlightController.turnOffFlashlightCompletely() // Ensure it's off
            flashlightController.release() // Release resources
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}