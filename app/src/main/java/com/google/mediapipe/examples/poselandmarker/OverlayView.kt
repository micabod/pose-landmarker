/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var results: PoseLandmarkerResult? = null
    private var pointPaint = Paint()
    private var linePaint = Paint()
    private var textPaint = Paint()
    private var gamePaint = Paint()

    private var analyzer: MovementAnalyzer? = null

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

    init {
        initPaints()
    }

    fun clear() {
        results = null
        pointPaint.reset()
        linePaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        linePaint.color =
            ContextCompat.getColor(context!!, R.color.mp_color_primary)
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL

        textPaint.color = Color.WHITE
        textPaint.textSize = 48f
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.setShadowLayer(5f, 2f, 2f, Color.BLACK)

        gamePaint.strokeWidth = 8f
        gamePaint.style = Paint.Style.FILL
    }

    fun setAnalyzer(analyzer: MovementAnalyzer) {
        this.analyzer = analyzer
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        results?.let { poseLandmarkerResult ->
            for (landmark in poseLandmarkerResult.landmarks()) {
                for (normalizedLandmark in landmark) {
                    canvas.drawPoint(
                        normalizedLandmark.x() * imageWidth * scaleFactor + offsetX,
                        normalizedLandmark.y() * imageHeight * scaleFactor + offsetY,
                        pointPaint
                    )
                }

                PoseLandmarker.POSE_LANDMARKS.forEach {
                    canvas.drawLine(
                        landmark.get(it!!.start()).x() * imageWidth * scaleFactor + offsetX,
                        landmark.get(it.start()).y() * imageHeight * scaleFactor + offsetY,
                        landmark.get(it.end()).x() * imageWidth * scaleFactor + offsetX,
                        landmark.get(it.end()).y() * imageHeight * scaleFactor + offsetY,
                        linePaint
                    )
                }
            }
        }

        // Draw Game Elements and Metrics
        analyzer?.let { ana ->
            drawMetrics(canvas, ana)
            
            when (ana.currentMode) {
                MovementAnalyzer.GameMode.SURF_CATCH -> drawSurfGame(canvas, ana)
                MovementAnalyzer.GameMode.FLAMENCO -> drawFlamencoGame(canvas, ana)
                else -> {}
            }

            // Draw status message
            drawStatusMessage(canvas, ana)
        }
    }

    private fun drawStatusMessage(canvas: Canvas, ana: MovementAnalyzer) {
        if (ana.currentMode == MovementAnalyzer.GameMode.NONE) return

        val message: String
        when {
            // Mal parada (vale para los dos juegos): aviso en rojo.
            ana.positionWarning.isNotEmpty() -> {
                message = ana.positionWarning
                textPaint.color = Color.RED
            }
            // Flamenco: mensajes de estabilidad como antes.
            ana.currentMode == MovementAnalyzer.GameMode.FLAMENCO -> {
                message = when {
                    !ana.isInPose -> "ESPERANDO POSTURA..."
                    ana.stability < 30 -> "¡MUÉVETE MENOS!"
                    ana.stability < 70 -> "¡ESTABILÍZATE!"
                    else -> "¡MUY BIEN!"
                }
                textPaint.color = when {
                    !ana.isInPose -> Color.WHITE
                    ana.stability < 50 -> Color.RED
                    else -> Color.GREEN
                }
            }
            // Surf bien parada: pantalla limpia.
            else -> return
        }

        // Smaller text size to avoid cutting off
        textPaint.textSize = 42f
        val textWidth = textPaint.measureText(message)
        
        // Draw a semi-transparent background for the message
        gamePaint.color = Color.BLACK
        gamePaint.alpha = 100
        val padding = 20f
        val textHeight = 42f
        canvas.drawRect(
            (width - textWidth) / 2f - padding,
            height * 0.15f - textHeight,
            (width + textWidth) / 2f + padding,
            height * 0.15f + padding,
            gamePaint
        )
        
        canvas.drawText(message, (width - textWidth) / 2f, height * 0.15f, textPaint)
        textPaint.textSize = 48f // Reset to default
    }

    private fun drawMetrics(canvas: Canvas, ana: MovementAnalyzer) {
        val xPos = 50f
        var yPos = 120f
        val lineSpacing = 60f

        // Mode and Instructions
        textPaint.color = Color.CYAN
        canvas.drawText("MODO: ${ana.currentMode}", xPos, yPos, textPaint)
        yPos += lineSpacing
        textPaint.color = Color.WHITE

        if (ana.currentMode == MovementAnalyzer.GameMode.NONE) {
            canvas.drawText("TOCA LA PANTALLA PARA EMPEZAR", xPos, height / 2f, textPaint)
            return
        }

        canvas.drawText("PUNTAJE: ${ana.score}", xPos, yPos, textPaint)
        yPos += lineSpacing
        
        // Dynamic color for stability
        textPaint.color = when {
            ana.stability > 80 -> Color.GREEN
            ana.stability > 50 -> Color.YELLOW
            else -> Color.RED
        }
        canvas.drawText("ESTABILIDAD: ${ana.stability.toInt()}%", xPos, yPos, textPaint)
        textPaint.color = Color.WHITE
        
        yPos += lineSpacing
        canvas.drawText("ROM: ${ana.rom.toInt()}°", xPos, yPos, textPaint)
        yPos += lineSpacing
        
        if (ana.currentMode == MovementAnalyzer.GameMode.FLAMENCO) {
            canvas.drawText("TIEMPO: ${(ana.executionTime / 1000f)}s", xPos, yPos, textPaint)
        } else if (ana.currentMode == MovementAnalyzer.GameMode.SURF_CATCH) {
            canvas.drawText("PECES: ${ana.fishCaught}", xPos, yPos, textPaint)
        }
    }

    private fun drawSurfGame(canvas: Canvas, ana: MovementAnalyzer) {
        // Draw Surfboard
        gamePaint.color = Color.parseColor("#FFD700") // Gold
        gamePaint.alpha = 150
        val boardWidth = width * 0.6f
        val boardHeight = 40f
        val left = (width - boardWidth) / 2
        val top = height * 0.85f
        canvas.drawRoundRect(left, top, left + boardWidth, top + boardHeight, 20f, 20f, gamePaint)

        // Draw Target Fish / Estrella.
        // Si está mal parada, se dibuja en gris para marcar que NO cuenta.
        ana.targetFish?.let { fish ->
            val active = ana.isWellPositioned
            gamePaint.color = if (active) Color.CYAN else Color.GRAY
            gamePaint.alpha = if (active) 255 else 120
            canvas.drawCircle(fish.first * width, fish.second * height, 40f, gamePaint)
            
            // simple fish tail
            val tailPath = android.graphics.Path()
            tailPath.moveTo(fish.first * width + 40f, fish.second * height)
            tailPath.lineTo(fish.first * width + 70f, fish.second * height - 30f)
            tailPath.lineTo(fish.first * width + 70f, fish.second * height + 30f)
            tailPath.close()
            canvas.drawPath(tailPath, gamePaint)
        }
    }

    private fun drawFlamencoGame(canvas: Canvas, ana: MovementAnalyzer) {
        // Visual indicator for max height
        gamePaint.color = Color.GREEN
        gamePaint.alpha = 100
        val barHeight = ana.maxHeight * height
        canvas.drawRect(width - 100f, height - barHeight, width - 50f, height.toFloat(), gamePaint)
        
        // Draw Balance Axis and Straightness indicators
        results?.let { poseLandmarkerResult ->
            if (poseLandmarkerResult.landmarks().isNotEmpty()) {
                val landmarks = poseLandmarkerResult.landmarks()[0]
                val leftAnkle = landmarks[27]; val rightAnkle = landmarks[28]
                val leftShoulder = landmarks[11]; val rightShoulder = landmarks[12]
                val leftHip = landmarks[23]; val rightHip = landmarks[24]
                val nose = landmarks[0]
                
                // 1. Support Leg Axis
                val isLeftLifted = leftAnkle.y() < rightAnkle.y() - 0.1f
                val isRightLifted = rightAnkle.y() < leftAnkle.y() - 0.1f
                val supportAnkle = if (isLeftLifted) rightAnkle else if (isRightLifted) leftAnkle else null
                
                if (supportAnkle != null) {
                    val lineX = supportAnkle.x() * imageWidth * scaleFactor + offsetX
                    gamePaint.color = Color.WHITE
                    gamePaint.alpha = 80
                    gamePaint.strokeWidth = 4f
                    canvas.drawLine(lineX, 0f, lineX, height.toFloat(), gamePaint)
                    
                    // Balance point (Nose)
                    val noseX = nose.x() * imageWidth * scaleFactor + offsetX
                    val noseY = nose.y() * imageHeight * scaleFactor + offsetY
                    gamePaint.color = if (abs(nose.x() - supportAnkle.x()) < 0.05f) Color.GREEN else Color.RED
                    gamePaint.alpha = 200
                    canvas.drawCircle(noseX, noseY, 20f, gamePaint)
                }

                // 2. Straightness: Shoulders Horizontal
                val sLeftX = leftShoulder.x() * imageWidth * scaleFactor + offsetX
                val sLeftY = leftShoulder.y() * imageHeight * scaleFactor + offsetY
                val sRightX = rightShoulder.x() * imageWidth * scaleFactor + offsetX
                val sRightY = rightShoulder.y() * imageHeight * scaleFactor + offsetY
                
                val shoulderTilt = abs(leftShoulder.y() - rightShoulder.y())
                gamePaint.color = if (shoulderTilt < 0.04f) Color.GREEN else Color.RED
                gamePaint.strokeWidth = 10f
                canvas.drawLine(sLeftX, sLeftY, sRightX, sRightY, gamePaint)

                // 3. Torso Alignment (Shoulders over Hips)
                val shoulderMidX = (leftShoulder.x() + rightShoulder.x()) / 2f
                val hipMidX = (leftHip.x() + rightHip.x()) / 2f
                if (abs(shoulderMidX - hipMidX) > 0.05f) {
                    gamePaint.color = Color.RED
                    gamePaint.alpha = 150
                    // Draw warning arrow or line
                    val midSX = shoulderMidX * imageWidth * scaleFactor + offsetX
                    val midSY = (leftShoulder.y() + rightShoulder.y()) / 2f * imageHeight * scaleFactor + offsetY
                    val midHX = hipMidX * imageWidth * scaleFactor + offsetX
                    val midHY = (leftHip.y() + rightHip.y()) / 2f * imageHeight * scaleFactor + offsetY
                    canvas.drawLine(midSX, midSY, midHX, midHY, gamePaint)
                }
            }
        }
    }

    fun setResults(
        poseLandmarkerResults: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        results = poseLandmarkerResults

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scaleFactor = when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                min(width * 1f / imageWidth, height * 1f / imageHeight)
            }
            RunningMode.LIVE_STREAM -> {
                // Use MAX to fill the screen (FILL_CENTER) as it's more common
                // and often matches how CameraX displays the preview by default
                max(width * 1f / imageWidth, height * 1f / imageHeight)
            }
        }
        
        offsetX = (width - imageWidth * scaleFactor) / 2f
        offsetY = (height - imageHeight * scaleFactor) / 2f
        
        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 12F
    }
}