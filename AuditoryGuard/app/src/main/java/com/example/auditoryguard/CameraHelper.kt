package com.example.auditoryguard

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import android.util.Log
import android.util.Size


class CameraHelper(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner? = null,
    private val hazardDetector: HazardDetector
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val analysisScope = CoroutineScope(Dispatchers.Default)
    private var preview: Preview? = null
    private var surfaceProvider: Preview.SurfaceProvider? = null

    fun setSurfaceProvider(provider: Preview.SurfaceProvider) {
        surfaceProvider = provider
        preview?.setSurfaceProvider(provider)
    }

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
                this.preview = preview
                surfaceProvider?.let { preview.setSurfaceProvider(it) }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                val owner = lifecycleOwner ?: object : LifecycleOwner {
                    private val registry = LifecycleRegistry(this).apply {
                        currentState = Lifecycle.State.RESUMED
                    }
                    override val lifecycle: Lifecycle = registry
                }
                cameraProvider?.bindToLifecycle(owner, cameraSelector, preview, imageAnalysis)
                Log.i("CameraHelper", "Camera bound to lifecycle")
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
