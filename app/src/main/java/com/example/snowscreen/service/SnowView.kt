package com.example.snowscreen.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Choreographer
import android.view.View
import com.example.snowscreen.model.SnowSettings
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class SnowView(context: Context, private var settings: SnowSettings = SnowSettings()) : View(context), Choreographer.FrameCallback {

    private data class Snowflake(
        var x: Float,
        var y: Float,
        var radius: Float,
        var speedY: Float,
        var alpha: Int,
        var angle: Float,
        var angleSpeed: Float,
        var flakeType: Int,
        var depthLayer: Int // 0: Background (far, slow, small), 1: Midground, 2: Foreground (near, fast, large)
    )

    private val snowflakes = mutableListOf<Snowflake>()
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 240, 245, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        strokeCap = Paint.Cap.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private var lastFrameTimeNanos: Long = 0L
    private val choreographer = Choreographer.getInstance()
    private var isAnimating = false

    private var gustTimer = 0f
    private var currentGustWind = 0f
    private var tiltWind = 0f

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun updateSettings(newSettings: SnowSettings) {
        settings = newSettings
        adjustFlakeCount()
        invalidate()
    }

    fun updateTilt(tiltX: Float) {
        if (settings.sensorTiltEnabled) {
            tiltWind = -tiltX * 1.2f
        } else {
            tiltWind = 0f
        }
    }

    private fun adjustFlakeCount() {
        val targetCount = settings.flakeCount
        val width = width.toFloat().takeIf { it > 0f } ?: 1080f
        val height = height.toFloat().takeIf { it > 0f } ?: 1920f

        while (snowflakes.size < targetCount) {
            snowflakes.add(createFlake(width, height))
        }
        while (snowflakes.size > targetCount && snowflakes.isNotEmpty()) {
            snowflakes.removeAt(snowflakes.size - 1)
        }
    }

    private fun createFlake(w: Float, h: Float, randomizeY: Boolean = true): Snowflake {
        // Assign depth layer: 0 = Background (30%), 1 = Midground (50%), 2 = Foreground (20%)
        val layerRoll = Random.nextFloat()
        val depthLayer = when {
            layerRoll < 0.3f -> 0
            layerRoll < 0.8f -> 1
            else -> 2
        }

        // 3D Parallax Depth scaling based on layer
        val (radius, speedY, alpha) = when (depthLayer) {
            0 -> Triple(Random.nextFloat() * 2.5f + 1.5f, Random.nextFloat() * 1.2f + 0.6f, Random.nextInt(90, 150)) // Far, small, slow, dim
            1 -> Triple(Random.nextFloat() * 4.5f + 2.5f, Random.nextFloat() * 2.2f + 1.2f, Random.nextInt(150, 210)) // Mid
            else -> Triple(Random.nextFloat() * 7.5f + 5.0f, Random.nextFloat() * 3.8f + 2.4f, Random.nextInt(210, 255)) // Near, large, fast, bright
        }

        val type = Random.nextInt(0, 4)
        return Snowflake(
            x = Random.nextFloat() * w,
            y = if (randomizeY) Random.nextFloat() * h else -radius * 4,
            radius = radius,
            speedY = speedY,
            alpha = alpha,
            angle = Random.nextFloat() * 360f,
            angleSpeed = (Random.nextFloat() - 0.5f) * 2.5f,
            flakeType = type,
            depthLayer = depthLayer
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        snowflakes.clear()
        val count = settings.flakeCount
        for (i in 0 until count) {
            snowflakes.add(createFlake(w.toFloat(), h.toFloat(), randomizeY = true))
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAnimating = true
        lastFrameTimeNanos = System.nanoTime()
        choreographer.postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isAnimating = false
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isAnimating) return

        val deltaSec = if (lastFrameTimeNanos == 0L) 0.016f else (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
        lastFrameTimeNanos = frameTimeNanos

        updateWindGusts(deltaSec)
        updateSnowflakes(deltaSec)
        invalidate()

        choreographer.postFrameCallback(this)
    }

    private fun updateWindGusts(delta: Float) {
        if (settings.randomWindGusts) {
            gustTimer += delta
            if (gustTimer > 6f) {
                currentGustWind = (Random.nextFloat() - 0.5f) * 6.0f
                gustTimer = 0f
            } else if (gustTimer > 3f) {
                currentGustWind *= 0.95f
            }
        } else {
            currentGustWind = 0f
            gustTimer = 0f
        }
    }

    private fun updateSnowflakes(delta: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val speedMul = settings.speedMultiplier
        val sizeMul = settings.sizeMultiplier
        // Parallax wind effect: Foreground reacts more to wind than background
        val baseWind = settings.wind + currentGustWind + tiltWind

        for (flake in snowflakes) {
            val depthFactor = when (flake.depthLayer) {
                0 -> 0.6f // background moves slower with wind
                1 -> 1.0f
                else -> 1.4f // foreground reacts strongly to wind
            }

            flake.angle += flake.angleSpeed * delta * 25f
            val sway = sin(Math.toRadians(flake.angle.toDouble())).toFloat() * 1.5f

            flake.y += flake.speedY * speedMul * 45f * delta
            flake.x += (sway + baseWind * depthFactor) * 45f * delta

            val boundMargin = flake.radius * 6 * sizeMul
            if (flake.y > h + boundMargin) {
                flake.y = -boundMargin
                flake.x = Random.nextFloat() * w
            }
            if (flake.x > w + boundMargin) {
                flake.x = -boundMargin
            } else if (flake.x < -boundMargin) {
                flake.x = w + boundMargin
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sizeMul = settings.sizeMultiplier

        // Render in depth order: Layer 0 (Background) -> Layer 1 (Midground) -> Layer 2 (Foreground)
        // This ensures proper visual layering and 3D depth perception
        for (layer in 0..2) {
            for (flake in snowflakes) {
                if (flake.depthLayer != layer) continue

                paint.alpha = flake.alpha
                strokePaint.alpha = flake.alpha
                glowPaint.alpha = (flake.alpha * 0.3f).toInt()

                val r = flake.radius * sizeMul
                val cx = flake.x
                val cy = flake.y

                canvas.save()
                canvas.rotate(flake.angle, cx, cy)

                when (flake.flakeType) {
                    0 -> {
                        // Stellar Dendrite
                        canvas.drawCircle(cx, cy, r * 0.3f, glowPaint)
                        for (i in 0..2) {
                            val angle = i * Math.PI / 3.0
                            val cosA = cos(angle).toFloat()
                            val sinA = sin(angle).toFloat()
                            
                            val x1 = cx - cosA * r * 2.2f
                            val y1 = cy - sinA * r * 2.2f
                            val x2 = cx + cosA * r * 2.2f
                            val y2 = cy + sinA * r * 2.2f
                            
                            canvas.drawLine(x1, y1, x2, y2, strokePaint)

                            val mx1 = cx - cosA * r * 1.1f
                            val my1 = cy - sinA * r * 1.1f
                            drawSideBranch(canvas, mx1, my1, angle + Math.PI / 4.0, r * 0.7f)
                            drawSideBranch(canvas, mx1, my1, angle - Math.PI / 4.0, r * 0.7f)

                            val mx2 = cx + cosA * r * 1.1f
                            val my2 = cy + sinA * r * 1.1f
                            drawSideBranch(canvas, mx2, my2, angle + Math.PI / 4.0, r * 0.7f)
                            drawSideBranch(canvas, mx2, my2, angle - Math.PI / 4.0, r * 0.7f)
                        }
                    }
                    1 -> {
                        // Hexagonal Plate Crystal
                        canvas.drawCircle(cx, cy, r * 1.2f, glowPaint)
                        for (i in 0..5) {
                            val angle = i * Math.PI / 3.0
                            val nextAngle = (i + 1) * Math.PI / 3.0
                            val x1 = cx + cos(angle).toFloat() * r * 1.5f
                            val y1 = cy + sin(angle).toFloat() * r * 1.5f
                            val x2 = cx + cos(nextAngle).toFloat() * r * 1.5f
                            val y2 = cy + sin(nextAngle).toFloat() * r * 1.5f
                            canvas.drawLine(x1, y1, x2, y2, strokePaint)
                            canvas.drawLine(cx, cy, x1, y1, strokePaint)
                        }
                    }
                    2 -> {
                        // Needle Crystal Star
                        canvas.drawCircle(cx, cy, r * 0.4f, glowPaint)
                        for (i in 0..3) {
                            val angle = i * Math.PI / 4.0
                            val cosA = cos(angle).toFloat()
                            val sinA = sin(angle).toFloat()
                            canvas.drawLine(
                                cx - cosA * r * 2.0f, cy - sinA * r * 2.0f,
                                cx + cosA * r * 2.0f, cy + sinA * r * 2.0f,
                                strokePaint
                            )
                        }
                    }
                    3 -> {
                        // Glowing Fluffy Flake
                        canvas.drawCircle(cx, cy, r * 1.8f, glowPaint)
                        canvas.drawCircle(cx, cy, r * 0.8f, paint)
                        strokePaint.strokeWidth = 1.2f
                        canvas.drawLine(cx - r * 1.5f, cy, cx + r * 1.5f, cy, strokePaint)
                        canvas.drawLine(cx, cy - r * 1.5f, cx, cy + r * 1.5f, strokePaint)
                        strokePaint.strokeWidth = 1.8f
                    }
                }

                canvas.restore()
            }
        }
    }

    private fun drawSideBranch(canvas: Canvas, x: Float, y: Float, angle: Double, length: Float) {
        val cosA = cos(angle).toFloat()
        val sinA = sin(angle).toFloat()
        canvas.drawLine(x, y, x + cosA * length, y + sinA * length, strokePaint)
    }
}
