package com.hoangbinhan.snowscreen.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.Choreographer
import android.view.View
import com.hoangbinhan.snowscreen.model.SnowSettings
import kotlin.math.abs
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
        var baseAlpha: Int,
        var angle: Float,
        var angleSpeed: Float,
        var flakeType: Int, // 0..7
        var depthLayer: Int, // 0: Background, 1: Midground, 2: Foreground
        var isMelting: Boolean,
        var meltStartY: Float,
        var meltEndY: Float
    )

    private val snowflakes = mutableListOf<Snowflake>()
    private val path = Path()
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 240, 245, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
        strokeCap = Paint.Cap.ROUND
    }

    private var lastFrameTimeNanos: Long = 0L
    private val choreographer = Choreographer.getInstance()
    private var isAnimating = false
    private var paused = false

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
            // Smooth sideways wind drift from sensor X axis tilt
            val clampedTilt = (tiltX * 0.2f).coerceIn(-2.0f, 2.0f)
            tiltWind = -clampedTilt
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

    private fun getEnabledFlakeType(): Int {
        val enabledTypes = mutableListOf<Int>()
        if (settings.enableDendrite) enabledTypes.add(0)
        if (settings.enableHexagon) enabledTypes.add(1)
        if (settings.enableStar) enabledTypes.add(2)
        if (settings.enableCluster) enabledTypes.add(3)
        if (settings.enableDiamond) enabledTypes.add(4)
        if (settings.enableStar12) enabledTypes.add(5)
        if (settings.enableCross) enabledTypes.add(6)
        if (settings.enableDust) enabledTypes.add(7)

        if (enabledTypes.isEmpty()) return 0
        return enabledTypes.random()
    }

    private fun createFlake(w: Float, h: Float, randomizeY: Boolean = true): Snowflake {
        val layerRoll = Random.nextFloat()
        val depthLayer = when {
            layerRoll < 0.3f -> 0
            layerRoll < 0.8f -> 1
            else -> 2
        }

        val (radius, speedY, baseAlpha) = when (depthLayer) {
            0 -> Triple(Random.nextFloat() * 2.5f + 1.5f, Random.nextFloat() * 1.0f + 0.8f, Random.nextInt(100, 150))
            1 -> Triple(Random.nextFloat() * 4.5f + 2.5f, Random.nextFloat() * 2.0f + 1.2f, Random.nextInt(150, 210))
            else -> Triple(Random.nextFloat() * 7.5f + 4.5f, Random.nextFloat() * 3.5f + 2.2f, Random.nextInt(210, 255))
        }

        val isMelting = Random.nextFloat() < 0.2f
        val startY = if (randomizeY) Random.nextFloat() * h else -radius * 4
        val endY = if (isMelting) startY + Random.nextFloat() * (h * 0.4f) + (h * 0.2f) else h * 2f

        return Snowflake(
            x = Random.nextFloat() * w,
            y = startY,
            radius = radius,
            speedY = speedY,
            alpha = baseAlpha,
            baseAlpha = baseAlpha,
            angle = Random.nextFloat() * 360f,
            angleSpeed = (Random.nextFloat() - 0.5f) * 2.5f,
            flakeType = getEnabledFlakeType(),
            depthLayer = depthLayer,
            isMelting = isMelting,
            meltStartY = startY,
            meltEndY = endY
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

    /** Freezes the animation (no frame callbacks, no drawing) while paused; resumes without a time jump. */
    fun setPaused(paused: Boolean) {
        if (this.paused == paused) return
        this.paused = paused
        if (paused) {
            isAnimating = false
            choreographer.removeFrameCallback(this)
        } else if (isAttachedToWindow) {
            isAnimating = true
            lastFrameTimeNanos = System.nanoTime()
            choreographer.postFrameCallback(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (paused) return
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

        // When screen is landscape or heavily tilted, slightly dampen speed (30% reduction) for a smooth, gentle fall
        val isLandscape = w > h || abs(tiltWind) > 1.0f
        val orientationDampener = if (isLandscape) 0.7f else 1.0f

        val speedMul = settings.speedMultiplier * orientationDampener
        val sizeMul = settings.sizeMultiplier
        val baseWind = (settings.wind + currentGustWind + tiltWind) * orientationDampener

        for (flake in snowflakes) {
            val depthFactor = when (flake.depthLayer) {
                0 -> 0.6f
                1 -> 1.0f
                else -> 1.4f
            }

            flake.angle += flake.angleSpeed * delta * 25f
            val sway = sin(Math.toRadians(flake.angle.toDouble())).toFloat() * 1.5f

            val fallSpeed = (flake.speedY * speedMul).coerceAtLeast(0.7f)
            flake.y += fallSpeed * 45f * delta
            flake.x += (sway + baseWind * depthFactor) * 45f * delta

            if (flake.isMelting) {
                if (flake.y >= flake.meltStartY) {
                    val progress = (flake.y - flake.meltStartY) / (flake.meltEndY - flake.meltStartY)
                    if (progress >= 1.0f) {
                        resetFlake(flake, w, h)
                    } else {
                        flake.alpha = (flake.baseAlpha * (1.0f - progress)).toInt().coerceAtLeast(0)
                    }
                }
            }

            val boundMargin = flake.radius * 6 * sizeMul
            if (flake.y > h + boundMargin) {
                resetFlake(flake, w, h)
            }
            if (flake.x > w + boundMargin) {
                flake.x = -boundMargin
            } else if (flake.x < -boundMargin) {
                flake.x = w + boundMargin
            }
        }
    }

    private fun resetFlake(flake: Snowflake, w: Float, h: Float) {
        flake.y = -flake.radius * 4
        flake.x = Random.nextFloat() * w
        flake.alpha = flake.baseAlpha
        flake.flakeType = getEnabledFlakeType()
        flake.isMelting = Random.nextFloat() < 0.2f
        flake.meltStartY = Random.nextFloat() * (h * 0.3f)
        flake.meltEndY = flake.meltStartY + Random.nextFloat() * (h * 0.4f) + (h * 0.2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sizeMul = settings.sizeMultiplier

        for (layer in 0..2) {
            for (flake in snowflakes) {
                if (flake.depthLayer != layer || flake.alpha <= 0) continue

                paint.alpha = flake.alpha
                strokePaint.alpha = flake.alpha

                val r = flake.radius * sizeMul
                val cx = flake.x
                val cy = flake.y

                canvas.save()
                canvas.rotate(flake.angle, cx, cy)

                when (flake.flakeType) {
                    0 -> {
                        // Type 0: 6-Ray Stellar Dendrite
                        canvas.drawCircle(cx, cy, r * 0.35f, paint)
                        for (i in 0..5) {
                            val angleRad = i * Math.PI / 3.0
                            val cosA = cos(angleRad).toFloat()
                            val sinA = sin(angleRad).toFloat()
                            val tipX = cx + cosA * r * 2.2f
                            val tipY = cy + sinA * r * 2.2f
                            canvas.drawLine(cx, cy, tipX, tipY, strokePaint)

                            val midX = cx + cosA * r * 1.3f
                            val midY = cy + sinA * r * 1.3f
                            drawBranch(canvas, midX, my1 = midY, angle = angleRad + Math.PI / 4.0, length = r * 0.6f)
                            drawBranch(canvas, midX, my1 = midY, angle = angleRad - Math.PI / 4.0, length = r * 0.6f)
                        }
                    }
                    1 -> {
                        // Type 1: Hexagonal Plate Crystal
                        canvas.drawCircle(cx, cy, r * 0.4f, paint)
                        for (i in 0..5) {
                            val angleRad = i * Math.PI / 3.0
                            val cosA = cos(angleRad).toFloat()
                            val sinA = sin(angleRad).toFloat()
                            val tipX = cx + cosA * r * 1.8f
                            val tipY = cy + sinA * r * 1.8f
                            canvas.drawLine(cx, cy, tipX, tipY, strokePaint)
                        }
                        for (i in 0..5) {
                            val a1 = i * Math.PI / 3.0
                            val a2 = (i + 1) * Math.PI / 3.0
                            val x1 = cx + cos(a1).toFloat() * r * 1.8f
                            val y1 = cy + sin(a1).toFloat() * r * 1.8f
                            val x2 = cx + cos(a2).toFloat() * r * 1.8f
                            val y2 = cy + sin(a2).toFloat() * r * 1.8f
                            canvas.drawLine(x1, y1, x2, y2, strokePaint)
                        }
                    }
                    2 -> {
                        // Type 2: 8-Pointed Crystal Star
                        canvas.drawCircle(cx, cy, r * 0.4f, paint)
                        for (i in 0..3) {
                            val angleRad = i * Math.PI / 4.0
                            val cosA = cos(angleRad).toFloat()
                            val sinA = sin(angleRad).toFloat()
                            canvas.drawLine(
                                cx - cosA * r * 1.8f, cy - sinA * r * 1.8f,
                                cx + cosA * r * 1.8f, cy + sinA * r * 1.8f,
                                strokePaint
                            )
                        }
                    }
                    3 -> {
                        // Type 3: Fluffy Cluster
                        canvas.drawCircle(cx, cy, r * 0.7f, paint)
                        for (i in 0..5) {
                            val angleRad = i * Math.PI / 3.0
                            val satX = cx + cos(angleRad).toFloat() * r * 1.4f
                            val satY = cy + sin(angleRad).toFloat() * r * 1.4f
                            canvas.drawCircle(satX, satY, r * 0.3f, paint)
                        }
                    }
                    4 -> {
                        // Type 4: Diamond Tip Crystal
                        canvas.drawCircle(cx, cy, r * 0.3f, paint)
                        for (i in 0..5) {
                            val angleRad = i * Math.PI / 3.0
                            val cosA = cos(angleRad).toFloat()
                            val sinA = sin(angleRad).toFloat()
                            val tipX = cx + cosA * r * 1.8f
                            val tipY = cy + sinA * r * 1.8f
                            canvas.drawLine(cx, cy, tipX, tipY, strokePaint)

                            path.reset()
                            path.moveTo(tipX, tipY)
                            path.lineTo(tipX - sinA * r * 0.3f, tipY + cosA * r * 0.3f)
                            path.lineTo(tipX + cosA * r * 0.4f, tipY + sinA * r * 0.4f)
                            path.lineTo(tipX + sinA * r * 0.3f, tipY - cosA * r * 0.3f)
                            path.close()
                            canvas.drawPath(path, paint)
                        }
                    }
                    5 -> {
                        // Type 5: 12-Pointed Radiant Star
                        for (i in 0..11) {
                            val angleRad = i * Math.PI / 6.0
                            val cosA = cos(angleRad).toFloat()
                            val sinA = sin(angleRad).toFloat()
                            val len = if (i % 2 == 0) r * 2.0f else r * 1.2f
                            canvas.drawLine(cx, cy, cx + cosA * len, cy + sinA * len, strokePaint)
                        }
                        canvas.drawCircle(cx, cy, r * 0.4f, paint)
                    }
                    6 -> {
                        // Type 6: Cross Ice Crystal
                        for (i in 0..3) {
                            val angleRad = i * Math.PI / 2.0
                            val cosA = cos(angleRad).toFloat()
                            val sinA = sin(angleRad).toFloat()
                            canvas.drawLine(cx, cy, cx + cosA * r * 2.2f, cy + sinA * r * 2.2f, strokePaint)
                            canvas.drawCircle(cx + cosA * r * 2.2f, cy + sinA * r * 2.2f, r * 0.3f, paint)
                        }
                        for (i in 0..3) {
                            val angleRad = i * Math.PI / 2.0 + Math.PI / 4.0
                            val cosA = cos(angleRad).toFloat()
                            val sinA = sin(angleRad).toFloat()
                            canvas.drawLine(cx, cy, cx + cosA * r * 1.1f, cy + sinA * r * 1.1f, strokePaint)
                        }
                    }
                    7 -> {
                        // Type 7: Soft Snow Dust
                        canvas.drawCircle(cx, cy, r * 1.2f, paint)
                    }
                }

                canvas.restore()
            }
        }
    }

    private fun drawBranch(canvas: Canvas, mx1: Float, my1: Float, angle: Double, length: Float) {
        val cosA = cos(angle).toFloat()
        val sinA = sin(angle).toFloat()
        canvas.drawLine(mx1, my1, mx1 + cosA * length, my1 + sinA * length, strokePaint)
    }
}
