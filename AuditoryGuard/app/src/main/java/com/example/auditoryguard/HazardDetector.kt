package com.example.auditoryguard

import android.content.Context
import android.graphics.RectF
import com.google.mediapipe.tasks.core.BaseOptions
// TODO: Fix MediaPipe Image import - current version has package conflict
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import androidx.camera.core.ImageProxy
import android.util.Log

class HazardDetector(private val context: Context, private val alertManager: AlertManager) {

    private var objectDetector: ObjectDetector? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val cooldownMap = mutableMapOf<String, Long>()
    private val COOLDOWN_MS = 4000L
    private val CONFIDENCE_THRESHOLD = 0.80f
    private val MIN_AREA_RATIO = 0.02f  // Spatial filter: >2% of image area

    private val detectorExecutor = Executors.newSingleThreadExecutor()

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("efficientdet_lite2.tflite")
                .build()

            val options = com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setMaxResults(5)
                .setScoreThreshold(CONFIDENCE_THRESHOLD)
                .setRunningMode(RunningMode.IMAGE)
                .build()

            objectDetector = ObjectDetector.createFromOptions(context, options)
        } catch (e: Exception) {
            android.util.Log.e("HazardDetector", "Failed to load MediaPipe model", e)
        }
    }

    fun processFrame(imageProxy: ImageProxy) {
        if (objectDetector == null) {
            imageProxy.close()
            return
        }

        coroutineScope.launch {
            try {
                val rotation = imageProxy.imageInfo.rotationDegrees
                val bitmap = Utils.imageProxyToBitmap(imageProxy)
                // TODO: Fix MediaPipe image creation - current version has import conflict with android.graphics
                // val mpImage = Image.createFromBitmap(bitmap)
                // val result = objectDetector?.detect(mpImage)
                val result = null // Placeholder until MediaPipe is fixed
                result?.let { processResults(it, imageProxy.width, imageProxy.height) }
            } catch (e: Exception) {
                android.util.Log.e("HazardDetector", "Detection error", e)
            } finally {
                imageProxy.close()
            }
        }
    }

    private fun processResults(result: ObjectDetectorResult, width: Int, height: Int) {
        val now = System.currentTimeMillis()

        for (detection in result.detections()) {
            val category = detection.categories().firstOrNull() ?: continue
            if (category.score() < CONFIDENCE_THRESHOLD) continue

            val bbox = detection.boundingBox()
            if (!isCloseEnough(bbox, width, height)) continue

            val label = category.categoryName().lowercase()
            val key = when {
                label.contains("person") -> "person"
                label.contains("car") -> "car"
                label.contains("traffic light") || label.contains("stoplight") -> "light"
                else -> continue
            }

            val lastAlert = cooldownMap[key] ?: 0
            if (now - lastAlert > COOLDOWN_MS) {
                cooldownMap[key] = now
                alertManager.triggerAlert(key)
                android.util.Log.d("HazardDetector", "ALERT: $key detected")
            }
        }
    }

    private fun isCloseEnough(bbox: RectF, imageWidth: Int, imageHeight: Int): Boolean {
        val area = (bbox.width() * bbox.height())
        val imageArea = (imageWidth * imageHeight).toFloat()
        return area > (imageArea * MIN_AREA_RATIO)
    }

    fun shutdown() {
        coroutineScope.cancel()
        detectorExecutor.shutdown()
        objectDetector?.close()
    }
}