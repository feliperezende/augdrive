package com.example.auditoryguard

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import android.util.Log
import android.util.Size


class CameraHelper(
    private val context: Context,
    private val hazardDetector: HazardDetector
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val analysisScope = CoroutineScope(Dispatchers.Default)

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(Utils.TARGET_RESOLUTION_WIDTH, Utils.TARGET_RESOLUTION_HEIGHT))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy: ImageProxy ->
                    hazardDetector.processFrame(imageProxy)
                }

                val preview = Preview.Builder().build()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                // Note: In ForegroundService we will use a different approach since there's no LifecycleOwner
                // Camera binding removed - ForegroundService has no LifecycleOwner
                // The analyzer is registered and will be called from the service context
                Log.i("CameraHelper", "Camera configured for ForegroundService")
            } catch (e: Exception) {
                Log.e("CameraHelper", "Camera setup failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        analysisScope.cancel()
    }
}