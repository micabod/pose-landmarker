package com.google.mediapipe.examples.poselandmarker

import android.os.SystemClock
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.*

class MovementAnalyzer {
    enum class GameMode { FLAMENCO, SURF_CATCH, NONE }
    var currentMode = GameMode.FLAMENCO

    // Metrics
    var score = 0
    var stability = 100f
    var rom = 0f
    var executionTime = 0L
    var maxHeight = 0f
    var squatDepth = 0f
    var fishCaught = 0
    var reactionTime = 0L
    
    // State
    private var isLegLifted = false
    private var startTime = 0L
    private var lastNoseX = 0.5f
    var targetFish: Pair<Float, Float>? = null

    fun reset() {
        score = 0
        stability = 100f
        rom = 0f
        executionTime = 0L
        maxHeight = 0f
        squatDepth = 0f
        fishCaught = 0
        isLegLifted = false
        startTime = 0L
        targetFish = null
    }

    fun analyze(result: PoseLandmarkerResult) {
        if (result.landmarks().isEmpty() || result.landmarks()[0].isEmpty()) return
        val landmarks = result.landmarks()[0]

        when (currentMode) {
            GameMode.FLAMENCO -> analyzeFlamenco(landmarks)
            GameMode.SURF_CATCH -> analyzeSurf(landmarks)
            else -> {}
        }
    }

    private fun analyzeFlamenco(landmarks: List<NormalizedLandmark>) {
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]
        val nose = landmarks[0]

        val diff = abs(leftAnkle.y() - rightAnkle.y())
        val threshold = 0.08f

        if (diff > threshold && !isLegLifted) {
            isLegLifted = true
            startTime = SystemClock.uptimeMillis()
            maxHeight = 0f
        } else if (diff < threshold * 0.6f && isLegLifted) {
            isLegLifted = false
            val duration = SystemClock.uptimeMillis() - startTime
            executionTime = duration
            score += (duration / 500).toInt() + (maxHeight * 200).toInt()
        }

            // Stability: Always measure horizontal deviation of the nose
            val deviation = abs(nose.x() - lastNoseX)
            stability = (stability * 0.95f + (1.0f - min(deviation * 10f, 1f)) * 100f * 0.05f).coerceIn(0f, 100f)
            lastNoseX = nose.x()

            if (isLegLifted) {
                maxHeight = max(maxHeight, diff)
                executionTime = SystemClock.uptimeMillis() - startTime
                
                // ROM for Flamenco: Angle of the lifted knee
                val hip = if (leftAnkle.y() < rightAnkle.y()) landmarks[23] else landmarks[24]
                val knee = if (leftAnkle.y() < rightAnkle.y()) landmarks[25] else landmarks[26]
                val ankle = if (leftAnkle.y() < rightAnkle.y()) landmarks[27] else landmarks[28]
                rom = calculateAngle(hip, knee, ankle)
            }
    }

    private fun analyzeSurf(landmarks: List<NormalizedLandmark>) {
        val leftHip = landmarks[23]; val leftKnee = landmarks[25]; val leftAnkle = landmarks[27]
        val rightHip = landmarks[24]; val rightKnee = landmarks[26]; val rightAnkle = landmarks[28]
        val leftWrist = landmarks[15]; val rightWrist = landmarks[16]
        val nose = landmarks[0]

        // 1. Squat Angle
        val leftAngle = calculateAngle(leftHip, leftKnee, leftAnkle)
        val rightAngle = calculateAngle(rightHip, rightKnee, rightAnkle)
        rom = (leftAngle + rightAngle) / 2f
        squatDepth = 180f - rom

        // 2. Stability
        val centerHipsX = (leftHip.x() + rightHip.x()) / 2f
        stability = (100f - abs(nose.x() - centerHipsX) * 200f).coerceIn(0f, 100f)

        // 3. Catch Fish
        if (targetFish == null) spawnFish()
        
        targetFish?.let { fish ->
            val distL = sqrt((leftWrist.x() - fish.first).pow(2) + (leftWrist.y() - fish.second).pow(2))
            val distR = sqrt((rightWrist.x() - fish.first).pow(2) + (rightWrist.y() - fish.second).pow(2))
            
            if (distL < 0.08f || distR < 0.08f) {
                fishCaught++
                score += 15
                spawnFish()
            }
        }
    }

    private fun spawnFish() {
        targetFish = Pair((0.2f..0.8f).random(), (0.3f..0.6f).random())
    }

    private fun calculateAngle(p1: NormalizedLandmark, p2: NormalizedLandmark, p3: NormalizedLandmark): Float {
        val radians = atan2(p3.y() - p2.y(), p3.x() - p2.x()) - atan2(p1.y() - p2.y(), p1.x() - p2.x())
        var angle = abs(radians * 180.0 / PI).toFloat()
        if (angle > 180.0) angle = 360f - angle
        return angle
    }

    private fun ClosedRange<Float>.random() = (Math.random().toFloat() * (endInclusive - start)) + start
}
