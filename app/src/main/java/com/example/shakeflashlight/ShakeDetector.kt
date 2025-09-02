package com.example.shakeflashlight

package com.shakeutility.flashlight

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

class ShakeDetector(private val onShakeListener: OnShakeListener) : SensorEventListener {

    interface OnShakeListener {
        fun onDoubleShake()
    }

    companion object {
        private const val SHAKE_THRESHOLD_GRAVITY = 2.7f
        private const val SHAKE_SLOP_TIME_MS = 500
        private const val SHAKE_COUNT_RESET_TIME_MS = 3000
        private const val REQUIRED_SHAKE_COUNT = 2
    }

    private var mShakeTimestamp: Long = 0
    private var mShakeCount = 0
    private var mLastX = 0f
    private var mLastY = 0f
    private var mLastZ = 0f
    private var mLastUpdate: Long = 0

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }

    override fun onSensorChanged(event: SensorEvent) {
        val currentTime = System.currentTimeMillis()

        if (currentTime - mLastUpdate > 100) { // Limit updates to 10Hz for battery optimization
            val diffTime = currentTime - mLastUpdate
            mLastUpdate = currentTime

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val speed = abs(x + y + z - mLastX - mLastY - mLastZ) / diffTime * 10000

            // Convert to G-force
            val gX = x / SensorManager.GRAVITY_EARTH
            val gY = y / SensorManager.GRAVITY_EARTH
            val gZ = z / SensorManager.GRAVITY_EARTH

            val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

            // Focus on left-right movement (X-axis primarily)
            val horizontalForce = abs(gX)

            if (gForce > SHAKE_THRESHOLD_GRAVITY && horizontalForce > 1.5f) {
                val now = System.currentTimeMillis()

                // Ignore shake events too close to each other
                if (mShakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                    return
                }

                // Reset the shake count after timeout
                if (mShakeTimestamp + SHAKE_COUNT_RESET_TIME_MS < now) {
                    mShakeCount = 0
                }

                mShakeTimestamp = now
                mShakeCount++

                if (mShakeCount >= REQUIRED_SHAKE_COUNT) {
                    onShakeListener.onDoubleShake()
                    mShakeCount = 0 // Reset count after successful detection
                }
            }

            mLastX = x
            mLastY = y
            mLastZ = z
        }
    }
}