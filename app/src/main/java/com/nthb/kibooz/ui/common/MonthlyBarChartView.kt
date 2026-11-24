package com.nthb.kibooz.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.nthb.kibooz.data.remote.MonthlyCount
import com.nthb.kibooz.ml.Emotion

/**
 * Batang jumlah catatan tiap emosi selama satu bulan (FR-3.3). Jumlahnya ditulis di atas batang
 * sehingga detailnya terbaca tanpa menyentuh (FR-3.4).
 */
class MonthlyBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var counts: Map<Emotion, Int> = MoodChartMath.ORDER.associateWith { 0 }
    private var animationProgress: Float = 1f
    private var animator: android.animation.ValueAnimator? = null

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B7280")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 750L
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                animationProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setData(distribution: List<MonthlyCount>) {
        counts = MoodChartMath.ORDER.associateWith { emotion ->
            distribution.filter { it.mood == emotion }.sumOf { it.count }
        }
        startAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        val sidePad = 30f
        val bottomPad = 60f
        val topPad = 40f

        val chartHeight = h - topPad - bottomPad
        val slot = (w - 2 * sidePad) / MoodChartMath.ORDER.size
        val barWidth = slot * 0.5f
        val max = counts.values.maxOrNull() ?: 0

        MoodChartMath.ORDER.forEachIndexed { index, emotion ->
            val count = counts.getValue(emotion)
            val centerX = sidePad + slot * index + slot / 2f
            val barHeight = MoodChartMath.barFraction(count, max) * chartHeight * animationProgress

            barPaint.color = MoodColors.of(emotion)
            canvas.drawRoundRect(
                RectF(centerX - barWidth / 2f, topPad + chartHeight - barHeight, centerX + barWidth / 2f, topPad + chartHeight),
                8f,
                8f,
                barPaint
            )
            val displayCount = (count * animationProgress).toInt()
            canvas.drawText("$displayCount", centerX, topPad + chartHeight - barHeight - 12f, textPaint)
            canvas.drawText(emotion.displayName(), centerX, h - 15f, textPaint)
        }
    }
}
