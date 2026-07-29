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
    var stability = 0f
    var rom = 0f
    var executionTime = 0L
    var maxHeight = 0f
    var squatDepth = 0f
    var fishCaught = 0
    var reactionTime = 0L
    
    // State
    var isInPose = false
    private var isLegLifted = false
    private var startTime = 0L
    var targetFish: Pair<Float, Float>? = null

    fun reset() {
        score = 0
        stability = 0f
        rom = 0f
        executionTime = 0L
        maxHeight = 0f
        squatDepth = 0f
        fishCaught = 0
        isInPose = false
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
        val leftAnkle = landmarks[27]; val rightAnkle = landmarks[28]
        val leftKnee = landmarks[25]; val rightKnee = landmarks[26]
        val leftHip = landmarks[23]; val rightHip = landmarks[24]
        val leftShoulder = landmarks[11]; val rightShoulder = landmarks[12]
        val nose = landmarks[0]

        val diffY = abs(leftAnkle.y() - rightAnkle.y())
        val threshold = 0.12f // Stricter threshold for lifting

        // 1. Detect if a leg is actually lifted high enough
        val isLeftLifted = leftAnkle.y() < rightAnkle.y() - threshold
        val isRightLifted = rightAnkle.y() < leftAnkle.y() - threshold
        
        // Stricter: Both feet must not be near the same Y level
        val bothFeetDown = abs(leftAnkle.y() - rightAnkle.y()) < 0.06f

        val liftedAnkle = if (isLeftLifted) leftAnkle else if (isRightLifted) rightAnkle else null
        val supportAnkle = if (isLeftLifted) rightAnkle else if (isRightLifted) leftAnkle else null
        val supportKnee = if (isLeftLifted) rightKnee else if (isRightLifted) leftKnee else null

        // 2. Validate "Flamingo Pose" (Foot near support knee)
        if (!bothFeetDown && liftedAnkle != null && supportKnee != null) {
            val distToKneeX = abs(liftedAnkle.x() - supportKnee.x())
            val distToKneeY = abs(liftedAnkle.y() - supportKnee.y())
            
            // Stricter pose validation
            if (distToKneeX < 0.1f && distToKneeY < 0.12f) {
                isInPose = true
            } else {
                isInPose = false
            }
        } else {
            isInPose = false
        }

        // 3. Start/Stop session based on pose
        if (isInPose && !isLegLifted) {
            isLegLifted = true
            startTime = SystemClock.uptimeMillis()
            maxHeight = 0f
        } else if (!isInPose && isLegLifted) {
            isLegLifted = false
            val duration = SystemClock.uptimeMillis() - startTime
            executionTime = duration
            score += (duration / 1000).toInt() * 10 // 10 points per second
        }

        // 4. Calculate Stability
        var currentFrameStability: Float
        
        if (isInPose && supportAnkle != null) {
            // A. Balance Axis (Nose over support foot)
            val balanceError = abs(nose.x() - supportAnkle.x())
            val balanceScore = (1.0f - (balanceError / 0.15f)).coerceIn(0f, 1f) * 60f
            
            // B. Torso Tilt (Shoulders vs Hips)
            val shoulderMidX = (leftShoulder.x() + rightShoulder.x()) / 2f
            val hipMidX = (leftHip.x() + rightHip.x()) / 2f
            val tiltError = abs(shoulderMidX - hipMidX)
            val tiltScore = (1.0f - (tiltError / 0.08f)).coerceIn(0f, 1f) * 20f
            
            // C. Shoulder Level (Horizontal shoulders)
            val shoulderLevelError = abs(leftShoulder.y() - rightShoulder.y())
            val levelScore = (1.0f - (shoulderLevelError / 0.05f)).coerceIn(0f, 1f) * 20f
            
            currentFrameStability = balanceScore + tiltScore + levelScore
        } else {
            // Quick decay to 0 if not in pose
            currentFrameStability = 0f
        }

        // Smoothing
        val alpha = if (isInPose) 0.15f else 0.4f
        stability = (stability * (1f - alpha) + currentFrameStability * alpha).coerceIn(0f, 100f)

        if (isLegLifted) {
            maxHeight = max(maxHeight, diffY)
            executionTime = SystemClock.uptimeMillis() - startTime
            
            val hip = if (isLeftLifted) leftHip else rightHip
            val knee = if (isLeftLifted) leftKnee else rightKnee
            val ankle = if (isLeftLifted) leftAnkle else rightAnkle
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
