package com.example.auditoryguard

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var statusText: TextView

    private var cameraHelper: CameraHelper? = null
    private var hazardDetector: HazardDetector? = null
    private var alertManager: AlertManager? = null
    private var foregroundService: ForegroundService? = null
    private var bound = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            startAuditoryGuard()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? ForegroundService.LocalBinder
            binder?.getService()?.let { svc ->
                foregroundService = svc
                hazardDetector = svc.getHazardDetector()
                alertManager = svc.getAlertManager()
                hazardDetector?.detectionListener = object : HazardDetector.DetectionListener {
                    override fun onDetections(
                        detections: List<OverlayView.Detection>,
                        sourceWidth: Int,
                        sourceHeight: Int
                    ) {
                        runOnUiThread {
                            overlayView.setDetections(detections, sourceWidth, sourceHeight)
                            for (det in detections) {
                                val emoji = when {
                                    det.label.contains("person") -> "🧑"
                                    det.label.contains("car") || det.label.contains("truck") || det.label.contains("bus") || det.label.contains("motorcycle") || det.label.contains("bicycle") -> "🚗"
                                    det.label.contains("traffic") || det.label.contains("light") || det.label.contains("semáforo") -> "🚦"
                                    else -> "🔴"
                                }
                                overlayView.appendLog("$emoji ${det.label} (${(det.score * 100).toInt()}%)")
                            }
                        }
                    }

                    override fun onRiskEvent(event: RiskEvent) {
                        runOnUiThread {
                            overlayView.appendRiskEvent("${event.message} ${event.label} ${(event.score * 100).toInt()}%")
                        }
                    }
                }
                rebindCameraToActivity()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            foregroundService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        statusText = findViewById(R.id.statusText)

        findViewById<TextView>(R.id.warningText).text = getString(R.string.splash_warning)

        findViewById<Button>(R.id.startButton).setOnClickListener {
            checkPermissionsAndStart()
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopAuditoryGuard()
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, ForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        bound = true
    }

    override fun onResume() {
        super.onResume()
        rebindCameraToActivity()
    }

    override fun onPause() {
        super.onPause()
        // Release the activity-bound camera; service will rebind it for background
        cameraHelper?.stopCamera()
        cameraHelper = null
        foregroundService?.takeOverCameraForBackground()
        updateStatus("Camera: in background (service)")
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
    }

    private fun rebindCameraToActivity() {
        val detector = hazardDetector ?: return
        if (cameraHelper != null) return

        foregroundService?.releaseCamera()

        val helper = CameraHelper(this, this, detector)
        helper.setSurfaceProvider(previewView.surfaceProvider)
        helper.startCamera()
        cameraHelper = helper
        updateStatus("Camera: live (preview)")
    }

    private fun updateStatus(text: String) {
        runOnUiThread { statusText.text = text }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startAuditoryGuard()
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startAuditoryGuard() {
        val intent = Intent(this, ForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, "AuditoryGuard started", Toast.LENGTH_SHORT).show()
    }

    private fun stopAuditoryGuard() {
        stopService(Intent(this, ForegroundService::class.java))
        Toast.makeText(this, "AuditoryGuard stopped", Toast.LENGTH_SHORT).show()
    }
}
