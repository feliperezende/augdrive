package com.example.auditoryguard

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log

class RiskAnalyzer {

    private val history = mutableListOf<TrackedDetection>()
    private val maxHistoryMs = 2000L

    companion object {
        private val FRAME_WIDTH = Utils.INFERENCE_INPUT_WIDTH.toFloat()
        private val FRAME_HEIGHT = Utils.INFERENCE_INPUT_HEIGHT.toFloat()

        // Road corridor: aggressive pedestrian alert zone, covering most of the lower frame.
        private val ROAD_CORRIDOR = RectF(0.10f, 0.30f, 0.90f, 1.0f)

        // Vehicle forward corridor (slightly wider)
        private val FORWARD_CORRIDOR = RectF(0.20f, 0.30f, 0.80f, 1.0f)

        // Traffic light zone: upper-middle
        private val LIGHT_ZONE = RectF(0.25f, 0.0f, 0.75f, 0.50f)

        // Min area thresholds (% of frame)
        private const val MIN_PERSON_AREA = 0.003f
        private const val MIN_VEHICLE_AREA = 0.035f
        private const val MIN_LIGHT_AREA = 0.008f

        // Vehicle closing: area growth threshold
        private const val CLOSING_AREA_GROWTH_PCT = 0.20f

        // Min consecutive frames for persistence
        private const val MIN_PERSISTENCE_FRAMES = 2

        private const val PERSON_LATERAL_MOVEMENT_THRESHOLD = 0.03f
        private const val PERSON_NEAR_BOTTOM_THRESHOLD = 0.55f

        // Red pixel thresholds for traffic light
        private const val RED_RATIO_THRESHOLD = 0.30f
        private const val GREEN_YELLOW_REJECT_THRESHOLD = 0.25f
    }

    fun analyze(
        detections: List<TrackedDetection>,
        bitmap: Bitmap,
        imageWidth: Int,
        imageHeight: Int
    ): List<RiskEvent> {
        val now = System.currentTimeMillis()

        // Analyze each scenario against prior history, then append this frame.
        history.removeAll { now - it.timestampMs > maxHistoryMs }

        val events = mutableListOf<RiskEvent>()

        analyzePedestrianInPath(detections)?.let { events.add(it) }
        analyzeVehicleClosing(detections)?.let { events.add(it) }
        analyzeRedLight(detections, bitmap, imageWidth, imageHeight)?.let { events.add(it) }

        history.addAll(detections.map { d ->
            TrackedDetection(d.label, d.score, d.rect, now)
        })

        return events
    }

    private fun analyzePedestrianInPath(detections: List<TrackedDetection>): RiskEvent? {
        val now = System.currentTimeMillis()

        val persons = detections.filter {
            isPerson(it.label) && normalizedArea(it.rect) >= MIN_PERSON_AREA
        }

        if (persons.isEmpty()) return null

        for (person in persons) {
            if (!intersectsCorridor(person.rect, ROAD_CORRIDOR)) continue

            // Check if person is in the road corridor (lower-middle)
            val centerX = person.rect.centerX() / FRAME_WIDTH
            val bottomY = person.rect.bottom / FRAME_HEIGHT

            if (centerX < ROAD_CORRIDOR.left || centerX > ROAD_CORRIDOR.right) continue
            if (bottomY < ROAD_CORRIDOR.top) continue

            // Check recent history for lateral movement or entrance into danger zone
            val recentPersons = history.filter {
                isPerson(it.label) && now - it.timestampMs < 1500
            }

            val isMovingLaterally = recentPersons.size >= 2 &&
                recentPersons.any { p ->
                    val prevCenter = p.rect.centerX() / FRAME_WIDTH
                    kotlin.math.abs(prevCenter - centerX) > PERSON_LATERAL_MOVEMENT_THRESHOLD
                }

            val justEnteredDanger = recentPersons.size < 3 &&
                recentPersons.none { p ->
                    intersectsCorridor(p.rect, ROAD_CORRIDOR)
                }

            if (isMovingLaterally || justEnteredDanger || bottomY > PERSON_NEAR_BOTTOM_THRESHOLD) {
                Log.d("RiskAnalyzer", "PEDESTRIAN_IN_PATH: centerX=$centerX, bottomY=$bottomY")
                return RiskEvent(
                    type = RiskEventType.PEDESTRIAN_IN_PATH,
                    label = person.label,
                    score = person.score,
                    rect = person.rect,
                    message = "PEDESTRIAN IN PATH"
                )
            }
        }
        return null
    }

    private fun analyzeVehicleClosing(detections: List<TrackedDetection>): RiskEvent? {
        val now = System.currentTimeMillis()

        val vehicles = detections.filter {
            isVehicle(it.label) && normalizedArea(it.rect) >= MIN_VEHICLE_AREA
        }

        if (vehicles.isEmpty()) return null

        for (vehicle in vehicles) {
            if (!intersectsCorridor(vehicle.rect, FORWARD_CORRIDOR)) continue

            val areaNow = normalizedArea(vehicle.rect)
            val bottomNow = vehicle.rect.bottom / FRAME_HEIGHT

            // Look for area growth in recent frames
            val recentVehicles = history.filter {
                isVehicle(it.label) && now - it.timestampMs < 1500
            }.sortedBy { it.timestampMs }

            if (recentVehicles.size < 2) {
                // First detection of a close vehicle - warn
                if (areaNow >= MIN_VEHICLE_AREA * 2 && bottomNow > 0.60f) {
                    Log.d("RiskAnalyzer", "VEHICLE_CLOSING_FAST: first frame close vehicle, area=$areaNow")
                    return RiskEvent(
                        type = RiskEventType.VEHICLE_CLOSING_FAST,
                        label = vehicle.label,
                        score = vehicle.score,
                        rect = vehicle.rect,
                        message = "VEHICLE CLOSING"
                    )
                }
                continue
            }

            val oldest = recentVehicles.first()
            val areaGrowth = (areaNow - normalizedArea(oldest.rect)) / normalizedArea(oldest.rect)

            if (areaGrowth >= CLOSING_AREA_GROWTH_PCT && bottomNow > 0.55f) {
                Log.d("RiskAnalyzer", "VEHICLE_CLOSING_FAST: area grew ${(areaGrowth*100).toInt()}%, bottomY=$bottomNow")
                return RiskEvent(
                    type = RiskEventType.VEHICLE_CLOSING_FAST,
                    label = vehicle.label,
                    score = vehicle.score,
                    rect = vehicle.rect,
                    message = "VEHICLE CLOSING"
                )
            }
        }
        return null
    }

    private fun analyzeRedLight(
        detections: List<TrackedDetection>,
        bitmap: Bitmap,
        imageWidth: Int,
        imageHeight: Int
    ): RiskEvent? {
        val lights = detections.filter {
            isTrafficLight(it.label) && normalizedArea(it.rect) >= MIN_LIGHT_AREA
        }

        if (lights.isEmpty()) return null

        for (light in lights) {
            if (!intersectsCorridor(light.rect, LIGHT_ZONE)) continue

            val centerX = light.rect.centerX() / FRAME_WIDTH
            val centerY = light.rect.centerY() / FRAME_HEIGHT

            if (centerX < LIGHT_ZONE.left || centerX > LIGHT_ZONE.right) continue
            if (centerY > LIGHT_ZONE.bottom) continue

            // Analyze pixel colors inside the light bbox
            val redRatio = redPixelRatio(bitmap, light.rect, imageWidth, imageHeight)
            val greenRatio = greenYellowPixelRatio(bitmap, light.rect, imageWidth, imageHeight)

            if (redRatio > RED_RATIO_THRESHOLD && greenRatio < GREEN_YELLOW_REJECT_THRESHOLD) {
                Log.d("RiskAnalyzer", "RED_LIGHT_AHEAD: redRatio=$redRatio, greenRatio=$greenRatio")
                return RiskEvent(
                    type = RiskEventType.RED_LIGHT_AHEAD,
                    label = light.label,
                    score = light.score,
                    rect = light.rect,
                    message = "RED LIGHT AHEAD"
                )
            }
        }
        return null
    }

    private fun redPixelRatio(bitmap: Bitmap, rect: RectF, imgW: Int, imgH: Int): Float {
        val left = (rect.left / FRAME_WIDTH * imgW).toInt().coerceIn(0, imgW - 1)
        val top = (rect.top / FRAME_HEIGHT * imgH).toInt().coerceIn(0, imgH - 1)
        val right = (rect.right / FRAME_WIDTH * imgW).toInt().coerceIn(left, imgW - 1)
        val bottom = (rect.bottom / FRAME_HEIGHT * imgH).toInt().coerceIn(top, imgH - 1)

        if (right <= left || bottom <= top) return 0f

        val w = right - left
        val h = bottom - top
        val total = w * h
        if (total <= 0) return 0f

        var redCount = 0
        val step = 2.coerceAtMost(kotlin.math.sqrt(total.toFloat()).toInt())

        for (y in top until bottom step step) {
            for (x in left until right step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                if (r > 150 && r > g * 1.5 && r > b * 1.5 && g < 100 && b < 100) {
                    redCount++
                }
            }
        }

        val sampled = ((w / step) * (h / step)).toFloat()
        return if (sampled > 0) redCount / sampled else 0f
    }

    private fun greenYellowPixelRatio(bitmap: Bitmap, rect: RectF, imgW: Int, imgH: Int): Float {
        val left = (rect.left / FRAME_WIDTH * imgW).toInt().coerceIn(0, imgW - 1)
        val top = (rect.top / FRAME_HEIGHT * imgH).toInt().coerceIn(0, imgH - 1)
        val right = (rect.right / FRAME_WIDTH * imgW).toInt().coerceIn(left, imgW - 1)
        val bottom = (rect.bottom / FRAME_HEIGHT * imgH).toInt().coerceIn(top, imgH - 1)

        if (right <= left || bottom <= top) return 0f

        val w = right - left
        val h = bottom - top
        val total = w * h
        if (total <= 0) return 0f

        var greenCount = 0
        val step = 2.coerceAtMost(kotlin.math.sqrt(total.toFloat()).toInt())

        for (y in top until bottom step step) {
            for (x in left until right step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Green or yellow dominant
                if ((g > r * 1.2 && g > b * 1.2) || (r > 150 && g > 150 && b < 100)) {
                    greenCount++
                }
            }
        }

        val sampled = ((w / step) * (h / step)).toFloat()
        return if (sampled > 0) greenCount / sampled else 0f
    }

    private fun normalizedArea(rect: RectF): Float {
        return (rect.width() * rect.height()) / (FRAME_WIDTH * FRAME_HEIGHT)
    }

    private fun intersectsCorridor(rect: RectF, corridor: RectF): Boolean {
        return rect.left < corridor.right && rect.right > corridor.left &&
            rect.top < corridor.bottom && rect.bottom > corridor.top
    }

    private fun isPerson(label: String): Boolean {
        val l = label.lowercase()
        return l.contains("person") || l.contains("pedestrian")
    }

    private fun isVehicle(label: String): Boolean {
        val l = label.lowercase()
        return l.contains("car") || l.contains("truck") || l.contains("bus") ||
            l.contains("motorcycle") || l.contains("bicycle") || l.contains("vehicle")
    }

    private fun isTrafficLight(label: String): Boolean {
        val l = label.lowercase()
        return l.contains("traffic") || l.contains("light") || l.contains("semáforo") ||
            l.contains("semaforo")
    }

    fun clearHistory() {
        history.clear()
    }
}
