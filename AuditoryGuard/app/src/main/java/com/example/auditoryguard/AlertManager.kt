package com.example.auditoryguard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.util.Log
import com.example.auditoryguard.R

class AlertManager(private val context: Context) {

    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<String, Int>()
    private val loadedSoundIds = mutableMapOf<String, Int>()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()

        soundIds["person"] = R.raw.alert_person
        soundIds["car"] = R.raw.alert_car
        soundIds["light"] = R.raw.alert_light

        soundIds.forEach { (type, resId) ->
            if (resId != 0) {
                val soundPoolId = soundPool?.load(context, resId, 1) ?: 0
                loadedSoundIds[type] = soundPoolId
            }
        }
    }

    fun triggerAlert(type: String) {
        val soundId = loadedSoundIds[type] ?: return
        if (soundId <= 0) return

        val savedVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, maxVolume, 0)

        soundPool?.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
        Log.d("AlertManager", "Playing alert for: $type (volume overridden to max)")

        // Restore original volume after a short delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, savedVolume, 0)
        }, 500)
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        soundIds.clear()
    }
}
