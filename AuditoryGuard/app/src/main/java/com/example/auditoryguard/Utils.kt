package com.example.auditoryguard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object Utils {

    const val INFERENCE_INPUT_WIDTH = 640
    const val INFERENCE_INPUT_HEIGHT = 480

    data class PreparedBitmap(val bitmap: Bitmap, val width: Int, val height: Int)

    fun imageProxyToInferenceBitmap(imageProxy: ImageProxy): PreparedBitmap {
        val bitmap = imageProxyToBitmap(imageProxy)
        val inferenceBitmap = if (bitmap.width != INFERENCE_INPUT_WIDTH || bitmap.height != INFERENCE_INPUT_HEIGHT) {
            Bitmap.createScaledBitmap(bitmap, INFERENCE_INPUT_WIDTH, INFERENCE_INPUT_HEIGHT, true).also {
                bitmap.recycle()
            }
        } else {
            bitmap
        }
        return PreparedBitmap(inferenceBitmap, inferenceBitmap.width, inferenceBitmap.height)
    }

    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val bytes = imageProxyToNv21(imageProxy)
        
        val yuvImage = YuvImage(
            bytes, ImageFormat.NV21, 
            imageProxy.width, imageProxy.height, null
        )
        
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
        val jpegBytes = out.toByteArray()
        
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: error("Failed to decode camera frame")
        
        if (imageProxy.imageInfo.rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            return rotated
        }
        return bitmap
    }

    private fun imageProxyToNv21(imageProxy: ImageProxy): ByteArray {
        val width = imageProxy.width
        val height = imageProxy.height
        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]
        val nv21 = ByteArray(width * height * 3 / 2)

        var outputOffset = 0
        val yBuffer = yPlane.buffer.duplicate()
        for (row in 0 until height) {
            val rowOffset = row * yPlane.rowStride
            yBuffer.position(rowOffset)
            yBuffer.get(nv21, outputOffset, width)
            outputOffset += width
        }

        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()
        val chromaHeight = height / 2
        val chromaWidth = width / 2
        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val vIndex = row * vPlane.rowStride + col * vPlane.pixelStride
                val uIndex = row * uPlane.rowStride + col * uPlane.pixelStride
                nv21[outputOffset++] = vBuffer.get(vIndex)
                nv21[outputOffset++] = uBuffer.get(uIndex)
            }
        }

        return nv21
    }

    // Battery and distance helpers
    fun isBatteryWarningNeeded(): Boolean {
        // Placeholder - could integrate with BatteryManager
        return false
    }

    const val TARGET_RESOLUTION_WIDTH = 640
    const val TARGET_RESOLUTION_HEIGHT = 480
}
