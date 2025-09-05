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
        private const val MAX_INTERVAL_MS = 600L        // Max time between chops (running filter)
        private const val GRAVITY_FILTER_ALPHA = 0.8f   // Low-pass filter constant

        // Running detection parameters
        private const val RUNNING_WINDOW_MS = 3000L     // 3 second window to detect running
        private const val RUNNING_MIN_EVENTS = 6        // Min events in window to consider running
        private const val RUNNING_FREQUENCY_MIN = 1.5f  // Min frequency (Hz) for running steps
        private const val RUNNING_FREQUENCY_MAX = 4.0f  // Max frequency (Hz) for running steps
    }

    private val gravity = FloatArray(3)
    private var firstChopTime = 0L
    private var chopCount = 0
    private var lastChopTime = 0L

    // Running detection
    private val recentShakes = mutableListOf<Long>()
    private var isRunningDetected = false

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

        // Calculate total acceleration magnitude
        val totalAccel = sqrt(
            linearAccelX * linearAccelX +
                    linearAccelY * linearAccelY +
                    linearAccelZ * linearAccelZ
        ) / SensorManager.GRAVITY_EARTH

        // Clean up old shake records
        recentShakes.removeAll { now - it > RUNNING_WINDOW_MS }

        // Reset if too much time passed since first chop
        if (chopCount > 0 && (now - firstChopTime) > TIME_WINDOW_MS) {
            Log.d("ShakeDetector", "Time window expired, resetting")
            resetChopCount()
        }

        // Detect significant movement (chop)
        if (totalAccel > SHAKE_THRESHOLD) {
            // Add to recent shakes for running detection
            recentShakes.add(now)

            // Check if this looks like running pattern
            isRunningDetected = detectRunningPattern(now)

            if (isRunningDetected) {
                Log.d("ShakeDetector", "Running detected - ignoring motion")
                resetChopCount() // Clear any pending chops
                return
            }

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
                // Second chop detected - check timing
                val timeBetween = now - firstChopTime

                // Reject if timing suggests running (too regular/frequent)
                if (timeBetween < MAX_INTERVAL_MS) {
                    Log.i("ShakeDetector", "Second chop detected: $totalAccel g (${timeBetween}ms after first)")
                    listener.onChopChop()
                } else {
                    Log.d("ShakeDetector", "Second chop too slow (${timeBetween}ms) - might be running")
                }

                resetChopCount()
            }
        }
    }

    private fun detectRunningPattern(currentTime: Long): Boolean {
        if (recentShakes.size < RUNNING_MIN_EVENTS) {
            return false
        }

        // Calculate average frequency of recent shakes
        val timeSpan = currentTime - recentShakes.first()
        val frequency = (recentShakes.size - 1).toFloat() / (timeSpan / 1000f) // Hz

        // Check if frequency matches typical running cadence
        val isRunningFrequency = frequency >= RUNNING_FREQUENCY_MIN && frequency <= RUNNING_FREQUENCY_MAX

        // Check for regularity (running has more consistent timing than intentional chops)
        val intervals = mutableListOf<Long>()
        for (i in 1 until recentShakes.size) {
            intervals.add(recentShakes[i] - recentShakes[i - 1])
        }

        if (intervals.size >= 3) {
            val avgInterval = intervals.average()
            val variance = intervals.map { (it - avgInterval) * (it - avgInterval) }.average()
            val stdDev = sqrt(variance.toFloat())
            val coefficient = stdDev / avgInterval.toFloat()

            // Running has lower coefficient of variation (more regular)
            val isRegular = coefficient < 0.3f

            Log.v("ShakeDetector", "Freq: $frequency Hz, Regular: $isRegular (coeff: $coefficient)")

            return isRunningFrequency && isRegular
        }

        return false
    }

    private fun resetChopCount() {
        chopCount = 0
        firstChopTime = 0L
    }
}