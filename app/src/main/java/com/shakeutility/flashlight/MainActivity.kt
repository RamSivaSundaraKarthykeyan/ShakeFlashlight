package com.shakeutility.flashlight

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.shakeutility.flashlight.R

class MainActivity : AppCompatActivity(), ShakeDetector.OnShakeListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var shakeDetector: ShakeDetector
    private lateinit var flashlightController: FlashlightController

    private lateinit var serviceToggle: Switch
    private lateinit var sensitivitySeekBar: SeekBar
    private lateinit var statusText: TextView
    private lateinit var batteryOptimizationButton: Button

    private lateinit var batteryOptimizationLauncher: ActivityResultLauncher<Intent>
    private val CAMERA_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupSensorManager()
        setupFlashlightController()
        checkPermissions()
        initBatteryOptimizationLauncher()
        setupBatteryOptimization()
    }

    private fun initViews() {
        serviceToggle = findViewById(R.id.serviceToggle)
        sensitivitySeekBar = findViewById(R.id.sensitivitySeekBar)
        statusText = findViewById(R.id.statusText)
        batteryOptimizationButton = findViewById(R.id.batteryOptimizationButton)

        serviceToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startShakeService()
            } else {
                stopShakeService()
            }
        }

        sensitivitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateStatusText("Sensitivity: $progress%")
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        batteryOptimizationButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestBatteryOptimizationExemption()
            }
        }
    }

    private fun setupSensorManager() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        shakeDetector = ShakeDetector(this)
    }

    private fun setupFlashlightController() {
        flashlightController = FlashlightController(this)
        if (!flashlightController.isFlashlightAvailable()) {
            Toast.makeText(this, "Flashlight not available on this device", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        }
    }

    private fun initBatteryOptimizationLauncher() {
        batteryOptimizationLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // Check if the user granted the permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                batteryOptimizationButton.visibility = if (powerManager.isIgnoringBatteryOptimizations(packageName)) Button.GONE else Button.VISIBLE
            }
        }
    }
    private fun setupBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                batteryOptimizationButton.visibility = Button.VISIBLE
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun requestBatteryOptimizationExemption() {
        try {
            val intent = Intent().apply {
                // Correct usage:
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:$packageName")
            }
            batteryOptimizationLauncher.launch(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        }
    }

    private fun startShakeService() {
        accelerometer?.let { sensor ->
            sensorManager.registerListener(shakeDetector, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            updateStatusText("Shake detection active")

            val sharedPrefs = getSharedPreferences("shake_flashlight_prefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putBoolean("service_enabled", true).apply()

            val serviceIntent = Intent(this, ShakeDetectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } ?: run {
            Toast.makeText(this, "Accelerometer not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopShakeService() {
        sensorManager.unregisterListener(shakeDetector)
        updateStatusText("Shake detection inactive")

        val sharedPrefs = getSharedPreferences("shake_flashlight_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("service_enabled", false).apply()

        val serviceIntent = Intent(this, ShakeDetectionService::class.java)
        stopService(serviceIntent)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onDoubleShake() {
        runOnUiThread {
            val success = flashlightController.toggleFlashlight()
            val status = if (flashlightController.getFlashlightState()) "ON" else "OFF"
            updateStatusText("Flashlight $status")

            if (!success) {
                Toast.makeText(this, "Failed to toggle flashlight", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateStatusText(text: String) {
        statusText.text = text
    }

    override fun onResume() {
        super.onResume()
        if (serviceToggle.isChecked) {
            accelerometer?.let { sensor ->
                sensorManager.registerListener(shakeDetector, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(shakeDetector)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onDestroy() {
        super.onDestroy()
        flashlightController.turnOffFlashlight()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Camera permission required for flashlight", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }
}