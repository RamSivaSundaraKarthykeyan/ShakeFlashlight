package com.shakeutility.flashlight

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.annotation.RequiresApi

class FlashlightController(private val context: Context) {

    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private var cameraId: String? = null
    private var isFlashlightOn = false

    init {
        try {
            cameraId = cameraManager.cameraIdList[0]
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun toggleFlashlight(): Boolean {
        return if (isFlashlightOn) {
            turnOffFlashlight()
        } else {
            turnOnFlashlight()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun turnOnFlashlight(): Boolean {
        return try {
            cameraId?.let { id ->
                cameraManager.setTorchMode(id, true)
                isFlashlightOn = true
                true
            } ?: false
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun turnOffFlashlight(): Boolean {
        return try {
            cameraId?.let { id ->
                cameraManager.setTorchMode(id, false)
                isFlashlightOn = false
                true
            } ?: false
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            false
        }
    }

    fun isFlashlightAvailable(): Boolean {
        return try {
            cameraManager.cameraIdList.isNotEmpty()
        } catch (e: CameraAccessException) {
            false
        }
    }

    fun getFlashlightState(): Boolean = isFlashlightOn
}