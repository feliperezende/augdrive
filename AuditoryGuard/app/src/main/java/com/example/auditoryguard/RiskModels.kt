package com.example.auditoryguard

import android.graphics.RectF

enum class RiskEventType {
    PEDESTRIAN_IN_PATH,
    VEHICLE_CLOSING_FAST,
    POSSIBLE_BRAKING,
    RED_LIGHT_AHEAD
}

data class TrackedDetection(
    val label: String,
    val score: Float,
    val rect: RectF,
    val timestampMs: Long
)

data class RiskEvent(
    val type: RiskEventType,
    val label: String,
    val score: Float,
    val rect: RectF,
    val message: String
)