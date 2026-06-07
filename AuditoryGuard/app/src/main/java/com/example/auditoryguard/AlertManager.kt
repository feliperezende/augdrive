package com.example.auditoryguard

import android.content.Context
import android.media.SoundPool
import android.media.AudioAttributes
import android.util.Log
import com.example.auditoryguard.R

class AlertManager(private val context: Context) {

    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<String, Int>()
    private val loadedSoundIds = mutableMapOf<String, Int>()
    private var volume = 0.7f  // 70% of max volume

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()

        // Load raw sound resources
        soundIds["person"] = R.raw.alert_person
        soundIds["car"] = R.raw.alert_car
        soundIds["light"] = R.raw.alert_light

        // Preload sounds (SoundPool.load is async; in production add OnLoadCompleteListener)
        soundIds.forEach { (type, resId) ->
            if (resId != 0) {
                val soundPoolId = soundPool?.load(context, resId, 1) ?: 0
                loadedSoundIds[type] = soundPoolId
            }
        }
    }

    fun triggerAlert(type: String) {
        val soundId = loadedSoundIds[type] ?: return
        if (soundId > 0) {
            soundPool?.play(soundId, volume, volume, 1, 0, 1.0f)
            Log.d("AlertManager", "Playing alert for: $type")
        } else {
            // Fallback using resource ID (for demo purposes)
            soundPool?.play(soundIds[type] ?: 0, volume, volume, 1, 0, 1.0f)
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        soundIds.clear()
    }
}