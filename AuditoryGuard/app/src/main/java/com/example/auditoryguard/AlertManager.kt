package com.example.auditoryguard

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

class AlertManager(private val context: Context) {

    private var audioManager: AudioManager? = null

    init {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.d("AlertManager", "AlertManager initialized (ToneGenerator mode)")
    }

    fun triggerAlert(type: String) {
        val am = audioManager ?: return

        val savedVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)

        val toneType: Int = when (type) {
            "person" -> ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK
            "car" -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
            "light" -> ToneGenerator.TONE_CDMA_ALERT_AUTOREDIAL_LITE
            else -> ToneGenerator.TONE_CDMA_ALERT_AUTOREDIAL_LITE
        }

        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        tg.startTone(toneType, 600)
        Log.d("AlertManager", "Playing tone for: $type (toneType=$toneType)")

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            tg.release()
            am.setStreamVolume(AudioManager.STREAM_MUSIC, savedVol, 0)
        }, 700)
    }

    fun triggerAlert(event: RiskEvent) {
        val am = audioManager ?: return

        val savedVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)

        val toneType: Int = when (event.type) {
            RiskEventType.PEDESTRIAN_IN_PATH -> ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK
            RiskEventType.VEHICLE_CLOSING_FAST -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
            RiskEventType.POSSIBLE_BRAKING -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
            RiskEventType.RED_LIGHT_AHEAD -> ToneGenerator.TONE_CDMA_ALERT_AUTOREDIAL_LITE
        }

        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        tg.startTone(toneType, 700)
        Log.d("AlertManager", "Playing risk alert: ${event.type.name} (toneType=$toneType)")

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            tg.release()
            am.setStreamVolume(AudioManager.STREAM_MUSIC, savedVol, 0)
        }, 800)
    }

    fun release() {
        // Nothing to release with ToneGenerator
    }
}