package com.shakeutility.flashlight

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

class ShakeDetector(private val listener: OnShakeListener) : SensorEventListener {

    interface OnShakeListener {
        fun onChopChop()
    }

    companion object {
        private const val SHAKE_THRESHOLD = 2.5f        // Acceleration threshold in g's
        private const val TIME_WINDOW_MS = 1000L        // Time window for two chops
        private const val MIN_INTERVAL_MS = 150L        // Min time between chops
        private const val GRAVITY_FILTER_ALPHA = 0.8f   // Low-pass filter constant
    }

    private val gravity = FloatArray(3)
    private var firstChopTime = 0L
    private var chopCount = 0
    private var lastChopTime = 0L

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val now = System.currentTimeMillis()

        // Apply low-pass filter to isolate gravity
        gravity[0] = GRAVITY_FILTER_ALPHA * gravity[0] + (1 - GRAVITY_FILTER_ALPHA) * event.values[0]
        gravity[1] = GRAVITY_FILTER_ALPHA * gravity[1] + (1 - GRAVITY_FILTER_ALPHA) * event.values[1]
        gravity[2] = GRAVITY_FILTER_ALPHA * gravity[2] + (1 - GRAVITY_FILTER_ALPHA) * event.values[2]

        // Remove gravity to get linear acceleration
        val linearAccelX = event.values[0] - gravity[0]
        val linearAccelY = event.values[1] - gravity[1]
        val linearAccelZ = event.values[2] - gravity[2]

        // Calculate total acceleration magnitude (like Motorola does)
        val totalAccel = sqrt(
            linearAccelX * linearAccelX +
                    linearAccelY * linearAccelY +
                    linearAccelZ * linearAccelZ
        ) / SensorManager.GRAVITY_EARTH

        Log.v("ShakeDetector", "Total accel: $totalAccel")

        // Reset if too much time passed since first chop
        if (chopCount > 0 && (now - firstChopTime) > TIME_WINDOW_MS) {
            Log.d("ShakeDetector", "Time window expired, resetting")
            resetChopCount()
        }

        // Detect significant movement (chop)
        if (totalAccel > SHAKE_THRESHOLD) {
            // Prevent multiple detections of same physical motion
            if (now - lastChopTime < MIN_INTERVAL_MS) {
                return
            }

            lastChopTime = now

            if (chopCount == 0) {
                // First chop detected
                chopCount = 1
                firstChopTime = now
                Log.d("ShakeDetector", "First chop detected: $totalAccel g")
            } else if (chopCount == 1) {
                // Second chop detected
                val timeBetween = now - firstChopTime
                Log.i("ShakeDetector", "Second chop detected: $totalAccel g (${timeBetween}ms after first)")

                listener.onChopChop()
                resetChopCount()
            }
        }
    }

    private fun resetChopCount() {
        chopCount = 0
        firstChopTime = 0L
    }
}
