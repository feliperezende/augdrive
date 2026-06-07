package com.example.auditoryguard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Detection(
        val rect: RectF,
        val label: String,
        val score: Float
    )

    data class LogEntry(
        val timestamp: Long,
        val text: String,
        val isRiskEvent: Boolean = false
    )

    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
    }

    private val textBackgroundPaint = Paint().apply {
        color = Color.argb(160, 0, 0, 0)
    }

    private val logTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        isAntiAlias = true
    }

    private val logBackgroundPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
    }

    private var detections: List<Detection> = emptyList()
    private var sourceWidth: Int = 0
    private var sourceHeight: Int = 0
    private val previewViewRect = RectF()
    private val logEntries = mutableListOf<LogEntry>()
    private val maxLogEntries = 6

    private val riskEventPaint = Paint().apply {
        color = Color.argb(255, 255, 80, 80)
        textSize = 32f
        isAntiAlias = true
        isFakeBoldText = true
    }

    fun setDetections(
        newDetections: List<Detection>,
        sourceWidth: Int = this.sourceWidth,
        sourceHeight: Int = this.sourceHeight
    ) {
        detections = newDetections
        this.sourceWidth = sourceWidth
        this.sourceHeight = sourceHeight
        invalidate()
    }

    fun appendLog(text: String, isRiskEvent: Boolean = false) {
        logEntries.add(LogEntry(System.currentTimeMillis(), text, isRiskEvent))
        if (logEntries.size > maxLogEntries) {
            logEntries.removeAt(0)
        }
        invalidate()
    }

    fun clearLog() {
        logEntries.clear()
        invalidate()
    }

    fun appendRiskEvent(text: String) {
        appendLog("⚠ $text", isRiskEvent = true)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val viewW = w.toFloat()
        val viewH = h.toFloat()
        if (viewW <= 0 || viewH <= 0) return

        val cameraAspect = 4f / 3f
        val viewAspect = viewW / viewH

        val visibleW: Float
        val visibleH: Float
        if (viewAspect > cameraAspect) {
            visibleH = viewH
            visibleW = visibleH * cameraAspect
        } else {
            visibleW = viewW
            visibleH = visibleW / cameraAspect
        }

        val left = (viewW - visibleW) / 2f
        val top = (viewH - visibleH) / 2f
        previewViewRect.set(left, top, left + visibleW, top + visibleH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawLog(canvas)
        drawDetections(canvas)
    }

    private fun drawDetections(canvas: Canvas) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || previewViewRect.isEmpty) return

        val sensorAspect = 4f / 3f
        for (detection in detections) {
            val src = detection.rect
            val nx0 = src.left / sourceWidth.toFloat()
            val ny0 = src.top / sourceHeight.toFloat()
            val nx1 = src.right / sourceWidth.toFloat()
            val ny1 = src.bottom / sourceHeight.toFloat()

            val sx0 = (nx0 - 0.5f) * sensorAspect + 0.5f
            val sy0 = ny0
            val sx1 = (nx1 - 0.5f) * sensorAspect + 0.5f
            val sy1 = ny1

            val rect = RectF(
                previewViewRect.left + sx0 * previewViewRect.width(),
                previewViewRect.top + sy0 * previewViewRect.height(),
                previewViewRect.left + sx1 * previewViewRect.width(),
                previewViewRect.top + sy1 * previewViewRect.height()
            )
            canvas.drawRect(rect, boxPaint)
            drawLabel(canvas, detection, rect)
        }
    }

    private fun drawLabel(canvas: Canvas, detection: Detection, anchor: RectF) {
        val labelText = "${detection.label} ${(detection.score * 100).toInt()}%"
        val textWidth = textPaint.measureText(labelText)
        val textHeight = textPaint.textSize
        val bgRect = RectF(
            anchor.left,
            (anchor.top - textHeight - 8f).coerceAtLeast(0f),
            anchor.left + textWidth + 16f,
            anchor.top
        )
        canvas.drawRect(bgRect, textBackgroundPaint)
        canvas.drawText(labelText, anchor.left + 8f, anchor.top - 6f, textPaint)
    }

    private fun drawLog(canvas: Canvas) {
        if (logEntries.isEmpty()) return

        val padding = 12f
        val lineHeight = logTextPaint.textSize * 1.4f
        val logH = logEntries.size * lineHeight + padding * 2
        val logW = canvas.width * 0.40f
        val logLeft = padding
        val logTop = canvas.height - logH - padding

        canvas.drawRect(logLeft, logTop, logLeft + logW, canvas.height - padding, logBackgroundPaint)

        val formatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        var y = logTop + padding + lineHeight
        for (entry in logEntries) {
            val timeStr = formatter.format(java.util.Date(entry.timestamp))
            val paint = if (entry.isRiskEvent) riskEventPaint else logTextPaint
            canvas.drawText("[$timeStr] ${entry.text}", logLeft + padding, y, paint)
            y += lineHeight
        }
    }
}
