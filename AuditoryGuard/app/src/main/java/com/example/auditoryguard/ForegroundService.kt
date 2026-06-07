package com.example.auditoryguard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log
import kotlinx.coroutines.*

class ForegroundService : Service() {

    private var hazardDetector: HazardDetector? = null
    private var alertManager: AlertManager? = null
    private var cameraHelper: CameraHelper? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "auditoryguard_channel"
        const val NOTIFICATION_ID = 1
    }

    inner class LocalBinder : android.os.Binder() {
        fun getService(): ForegroundService = this@ForegroundService
    }

    fun getHazardDetector(): HazardDetector? = hazardDetector
    fun getAlertManager(): AlertManager? = alertManager
    fun takeOverCameraForBackground() {
        // Service camera is already running in background; nothing to do
    }
    fun releaseCamera() {
        cameraHelper?.stopCamera()
        cameraHelper = null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        alertManager = AlertManager(this)
        hazardDetector = HazardDetector(this, alertManager!!)
        cameraHelper = CameraHelper(this, null, hazardDetector!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        serviceScope.launch {
            cameraHelper?.startCamera()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onDestroy() {
        super.onDestroy()
        cameraHelper?.stopCamera()
        hazardDetector?.shutdown()
        alertManager?.release()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AuditoryGuard Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for hazard detection"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}