package com.shakeutility.flashlight

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

class ShakeDetector(private val onShakeListener: OnShakeListener) : SensorEventListener {
    interface OnShakeListener {
        fun onChopChop()
    }


    private var lastChopTime = 0L
    private var chopCount = 0

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit


    companion object {
        private const val TILT_THRESHOLD_G = 0.8f      // side-down tilt
        private const val SHAKE_THRESHOLD_G = 1.5f     // shake magnitude
        private const val SHAKE_TIMEOUT_MS = 400L      // max time between shakes
    }

    private var lastShakeTime = 0L
    private var shakeCount = 0

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val gX = event.values[0] / SensorManager.GRAVITY_EARTH
        val gY = event.values[1] / SensorManager.GRAVITY_EARTH
        val now = System.currentTimeMillis()

        // Confirm the phone is side-down
        val sideDown = gX > TILT_THRESHOLD_G || gX < -TILT_THRESHOLD_G
        if (!sideDown) {
            shakeCount = 0
            return
        }

        // Detect a hammer-like downward chop on the Y-axis
        if (gY < -SHAKE_THRESHOLD_G) {
            if (shakeCount == 0 || now - lastShakeTime <= SHAKE_TIMEOUT_MS) {
                shakeCount++
                lastShakeTime = now
            } else {
                shakeCount = 1
                lastShakeTime = now
            }
        }

        // When we get two chops in quick succession, fire the event
        if (shakeCount == 2) {
            onShakeListener.onChopChop()         // now better named onHammer()
            shakeCount = 0
        }
    }
}
