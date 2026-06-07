package com.example.auditoryguard

import android.content.Context
import android.graphics.RectF
import android.os.Build
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class HazardDetector(private val context: Context, private val alertManager: AlertManager) {

    interface DetectionListener {
        fun onDetections(
            detections: List<OverlayView.Detection>,
            sourceWidth: Int,
            sourceHeight: Int
        )
        fun onRiskEvent(event: RiskEvent)
    }

    private var objectDetector: ObjectDetector? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val cooldownMap = mutableMapOf<String, Long>()
    private val COOLDOWN_MS = 4000L
    private val CONFIDENCE_THRESHOLD = 0.65f
    private val MIN_AREA_RATIO = 0.01f  // 1% of image area (was 2%)

    private val detectorExecutor = Executors.newSingleThreadExecutor()

    var detectionListener: DetectionListener? = null

    private val riskAnalyzer = RiskAnalyzer()

    // Frame skipping: only run inference on every Nth frame to keep latency low.
    private val frameSkip = 2
    private val frameCounter = AtomicInteger(0)
    private val inFlight = AtomicBoolean(false)

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("efficientdet_lite2.tflite")
                .build()

            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setMaxResults(5)
                .setScoreThreshold(CONFIDENCE_THRESHOLD)
                .setRunningMode(RunningMode.IMAGE)
                .build()

            objectDetector = ObjectDetector.createFromOptions(context, options)
            Log.i("HazardDetector", "MediaPipe model loaded on CPU (${Build.MODEL})")
        } catch (e: Exception) {
            Log.e("HazardDetector", "Failed to load MediaPipe model: ${e.message}", e)
        }
    }

    fun processFrame(imageProxy: ImageProxy) {
        if (objectDetector == null) {
            imageProxy.close()
            return
        }

        // Drop frames while a detection is already running, and only process every Nth frame.
        val count = frameCounter.incrementAndGet()
        if (count % (frameSkip + 1) != 0 || !inFlight.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        coroutineScope.launch {
            try {
                val t0 = System.nanoTime()
                val prepared = Utils.imageProxyToInferenceBitmap(imageProxy)
                val t1 = System.nanoTime()
                val mpImage = BitmapImageBuilder(prepared.bitmap).build()
                val t2 = System.nanoTime()
                val result = objectDetector?.detect(mpImage)
                val t3 = System.nanoTime()
                val inferenceW = Utils.INFERENCE_INPUT_WIDTH
                val inferenceH = Utils.INFERENCE_INPUT_HEIGHT
                result?.let {
                    val detections = buildDetections(it, inferenceW, inferenceH)
                    detectionListener?.onDetections(detections, inferenceW, inferenceH)

                    val trackedDetections = detections.map { d ->
                        TrackedDetection(d.label, d.score, d.rect, System.currentTimeMillis())
                    }

                    val riskEvents = riskAnalyzer.analyze(
                        trackedDetections,
                        prepared.bitmap,
                        Utils.INFERENCE_INPUT_WIDTH,
                        Utils.INFERENCE_INPUT_HEIGHT
                    )

                    processRiskEvents(riskEvents)

                    if (detections.isNotEmpty()) {
                        Log.d("HazardDetector", "Visible labels: ${detections.map { it.label }.joinToString(", ")}")
                    }
                    val totalMs = (t3 - t0) / 1_000_000
                    val prepMs = (t1 - t0) / 1_000_000
                    val detectMs = (t3 - t2) / 1_000_000
                    Log.d("HazardDetector", "DETECTION [${Build.MODEL}] total=${totalMs}ms prep=${prepMs}ms detect=${detectMs}ms")
                }
                prepared.bitmap.recycle()
            } catch (e: Exception) {
                Log.e("HazardDetector", "Detection error: ${e.message}", e)
            } finally {
                inFlight.set(false)
                imageProxy.close()
            }
        }
    }

    private fun buildDetections(
        result: ObjectDetectorResult,
        imageWidth: Int,
        imageHeight: Int
    ): List<OverlayView.Detection> {
        val list = mutableListOf<OverlayView.Detection>()
        for (detection in result.detections()) {
            val category = detection.categories().firstOrNull() ?: continue
            if (category.score() < CONFIDENCE_THRESHOLD) continue
            val bbox = detection.boundingBox()
            if (!isCloseEnough(bbox, imageWidth, imageHeight)) continue

            val rect = RectF(
                bbox.left,
                bbox.top,
                bbox.right,
                bbox.bottom
            )
            list.add(
                OverlayView.Detection(
                    rect = rect,
                    label = category.categoryName(),
                    score = category.score()
                )
            )
        }
        return list
    }

    private fun processRiskEvents(events: List<RiskEvent>) {
        val now = System.currentTimeMillis()

        for (event in events) {
            val key = event.type.name
            val lastAlert = cooldownMap[key] ?: 0

            if (now - lastAlert > COOLDOWN_MS) {
                cooldownMap[key] = now
                alertManager.triggerAlert(event)
                detectionListener?.onRiskEvent(event)
                Log.d("HazardDetector", "RISK: ${event.type.name} — ${event.message}")
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
