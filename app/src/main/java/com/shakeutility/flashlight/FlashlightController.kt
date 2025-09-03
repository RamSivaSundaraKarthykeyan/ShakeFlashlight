package com.shakeutility.flashlight

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Camera // For old API
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import android.widget.Toast

@Suppress("DEPRECATION") // Suppress deprecation warning for the old Camera API
class FlashlightController(private val context: Context) {

    // For API 23+ (CameraManager was added in API 21, but setTorchMode is API 23)
    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null

    // For API < 23 (Old Camera API)
    private var legacyCamera: Camera? = null
    private var legacyCameraParameters: Camera.Parameters? = null

    private var isFlashlightOn: Boolean = false
    private var isFlashAvailable: Boolean = false

    init {
        isFlashAvailable = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

        Log.i("FlashlightController", "Initializing...")
        isFlashAvailable = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        Log.d("FlashlightController", "Flash available feature: $isFlashAvailable")
        // ... Add logs after cameraId is set or legacyCamera is opened
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (cameraId != null) Log.i("FlashlightController", "API M+: CameraId set: $cameraId")
            else if(isFlashAvailable) Log.e("FlashlightController", "API M+: CameraId is NULL despite flash feature.")
        } else {
            if (legacyCamera != null) Log.i("FlashlightController", "API <M: Legacy camera opened.")
            else if(isFlashAvailable) Log.e("FlashlightController", "API <M: Legacy camera is NULL despite flash feature.")
        }
        if (!isFlashAvailable) {
            Log.e("FlashlightController", "Device does not have a camera flash.")
            // Optionally show a toast here or let the calling code handle it via isFlashlightAvailable()
        } else {
            // Initialization logic differs based on API level
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Initialize for API 23+
                try {
                    // CameraManager itself is available from API 21
                    cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager?
                    cameraManager?.let { cm ->
                        for (id in cm.cameraIdList) {
                            val characteristics = cm.getCameraCharacteristics(id)
                            val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                            if (hasFlash == true) {
                                cameraId = id
                                break
                            }
                        }
                        if (cameraId == null) {
                            Log.e("FlashlightController", "No camera with flash unit found (API 23+).")
                            isFlashAvailable = false // Update status
                        }
                    } ?: run {
                        Log.e("FlashlightController", "CameraManager is null (API 23+).")
                        isFlashAvailable = false
                    }
                } catch (e: Exception) { // Catch generic Exception for robustness during init
                    Log.e("FlashlightController", "Error initializing CameraManager (API 23+): ${e.message}")
                    isFlashAvailable = false
                }
            } else {
                // Initialize for API < 23 (Old Camera API)
                try {
                    legacyCamera = Camera.open() // Might throw RuntimeException if camera in use or not available
                    legacyCamera?.let { cam ->
                        legacyCameraParameters = cam.parameters
                        val supportedFlashModes = legacyCameraParameters?.supportedFlashModes
                        if (supportedFlashModes == null || !supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                            Log.e("FlashlightController", "Torch mode not supported by this camera (Old API).")
                            isFlashAvailable = false
                            releaseLegacyCameraInternal() // Release if not usable
                        }
                    } ?: run {
                        Log.e("FlashlightController", "Failed to open legacy camera (it's null).")
                        isFlashAvailable = false
                    }
                } catch (e: RuntimeException) {
                    Log.e("FlashlightController", "RuntimeException opening legacy camera: ${e.message}")
                    isFlashAvailable = false
                }
            }
        }
    }

    fun isFlashlightAvailable(): Boolean {
        return isFlashAvailable
    }

    fun toggleFlashlight(): Boolean {
        if (!isFlashAvailable) {
            Log.w("FlashlightController", "Attempted to toggle flashlight but no flash is available.")
            return false
        }
        return if (isFlashlightOn) {
            turnOff()
        } else {
            turnOn()
        }
    }

    private fun turnOn(): Boolean {
        if (!isFlashAvailable) return false
        Log.d("FlashlightController", "turnOn() called. API Level: ${Build.VERSION.SDK_INT}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (cameraManager == null || cameraId == null) {
                Log.e("FlashlightController", "CameraManager or CameraId not initialized for API 23+ turnOn.")
                return false
            }
            try {
                cameraManager!!.setTorchMode(cameraId!!, true)
                isFlashlightOn = true
                Log.i("FlashlightController", "Flashlight turned ON (API 23+)")
                return true
            } catch (e: CameraAccessException) {
                Log.e("FlashlightController", "Cannot turn on flashlight (API 23+): ${e.message}")
                return false
            }
        } else {
            legacyCamera?.let { cam ->
                legacyCameraParameters?.let { params ->
                    try {
                        params.flashMode = Camera.Parameters.FLASH_MODE_TORCH
                        cam.parameters = params
                        cam.startPreview() // Some devices need preview started for torch mode
                        isFlashlightOn = true
                        Log.i("FlashlightController", "Flashlight turned ON (Old API)")
                        return true
                    } catch (e: RuntimeException) {
                        Log.e("FlashlightController", "RuntimeException turning on flashlight (Old API): ${e.message}")
                        return false
                    }
                }
            }
            Log.e("FlashlightController", "Legacy camera or params not initialized for turnOn.")
            return false
        }
    }

    private fun turnOff(): Boolean { // Changed to private as toggleFlashlight is the public API
        // No need to check isFlashAvailable here as toggleFlashlight does it.
        // If it's called directly, the caller should ensure availability.
        Log.d("FlashlightController", "turnOff() called. API Level: ${Build.VERSION.SDK_INT}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (cameraManager == null || cameraId == null) {
                Log.e("FlashlightController", "CameraManager or CameraId not initialized for API 23+ turnOff.")
                return false
            }
            try {
                cameraManager!!.setTorchMode(cameraId!!, false)
                isFlashlightOn = false
                Log.i("FlashlightController", "Flashlight turned OFF (API 23+)")
                return true
            } catch (e: CameraAccessException) {
                Log.e("FlashlightController", "Cannot turn off flashlight (API 23+): ${e.message}")
                return false
            }
        } else {
            legacyCamera?.let { cam ->
                legacyCameraParameters?.let { params ->
                    try {
                        params.flashMode = Camera.Parameters.FLASH_MODE_OFF
                        cam.parameters = params
                        // It's good practice to stop preview if you started it for torch on
                        // However, some apps keep it running if the camera object is reused.
                        // For simplicity, if torch on started it, torch off might not explicitly stop it
                        // unless release() is called.
                        // cam.stopPreview(); // Consider the lifecycle of the preview
                        isFlashlightOn = false
                        Log.i("FlashlightController", "Flashlight turned OFF (Old API)")
                        return true
                    } catch (e: RuntimeException) {
                        Log.e("FlashlightController", "RuntimeException turning off flashlight (Old API): ${e.message}")
                        return false
                    }
                }
            }
            Log.e("FlashlightController", "Legacy camera or params not initialized for turnOff.")
            return false
        }
    }

    // Public method to turn off flashlight, e.g., from service's onDestroy
    fun turnOffFlashlightCompletely() {
        if (isFlashlightOn) {
            turnOff()
        }
    }


    fun getFlashlightState(): Boolean {
        return isFlashlightOn
    }

    // Internal release for legacy camera to be used if init fails for it
    private fun releaseLegacyCameraInternal() {
        legacyCamera?.let { cam ->
            try {
                // Ensure flash is off if it was somehow turned on
                if (cam.parameters?.flashMode == Camera.Parameters.FLASH_MODE_TORCH) {
                    val params = cam.parameters
                    params.flashMode = Camera.Parameters.FLASH_MODE_OFF
                    cam.parameters = params
                }
                cam.stopPreview() // Important: stop preview if it was started
                cam.release()
                Log.i("FlashlightController", "Internal: Old API Camera released.")
            } catch (e: RuntimeException) {
                Log.e("FlashlightController", "Internal: Error releasing old API Camera: ${e.message}")
            }
            legacyCamera = null
            legacyCameraParameters = null
        }
    }

    // Call this when the service is destroyed or flashlight is no longer needed
    fun release() {
        Log.i("FlashlightController", "Release called. Flashlight was ${if(isFlashlightOn) "ON" else "OFF"}")
        if (isFlashlightOn) {
            turnOff() // Attempt to turn it off using the appropriate API version
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            releaseLegacyCameraInternal()
        }
        // For CameraManager (API 21+), no explicit release of torch mode is typically needed here.
        // The system handles it when the app loses camera access or is closed.
        // cameraId and cameraManager can remain as they are, they don't hold resources like Camera.open() does.
        isFlashlightOn = false // Ensure state is reset
    }
}
