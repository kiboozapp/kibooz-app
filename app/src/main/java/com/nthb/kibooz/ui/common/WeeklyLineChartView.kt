package com.nthb.kibooz.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.nthb.kibooz.data.remote.WeeklyPoint

/**
 * Garis skor kebahagiaan rata-rata kelas, Senin sampai Jumat, 0 sampai 100 (FR-3.2). Angka tiap titik
 * ditulis di atas titiknya sehingga detailnya terbaca tanpa menyentuh (FR-3.4).
 */
class WeeklyLineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var labels: List<String> = emptyList()
    private var values: List<Int> = emptyList()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B7280")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E5E7EB")
        strokeWidth = 2f
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MoodColors.SENANG
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MoodColors.SENANG }

    fun setData(points: List<WeeklyPoint>) {
        labels = points.map { MoodChartMath.dayLabel(it.day) }
        values = points.map { it.averageHappyScore.coerceIn(0, 100) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        val leftPad = 70f
        val rightPad = 40f
        val topPad = 40f
        val bottomPad = 60f

        val chartWidth = w - leftPad - rightPad
        val chartHeight = h - topPad - bottomPad

        textPaint.textAlign = Paint.Align.RIGHT
        for (i in 0..4) {
            val y = topPad + chartHeight - (i * chartHeight / 4)
            canvas.drawLine(leftPad, y, w - rightPad, y, gridPaint)
            canvas.drawText("${i * 25}", leftPad - 14f, y + 10f, textPaint)
        }
        textPaint.textAlign = Paint.Align.CENTER

        if (values.isEmpty()) return

        val stepX = if (values.size > 1) chartWidth / (values.size - 1) else 0f
        val path = Path()
        for (i in values.indices) {
            val x = if (values.size > 1) leftPad + i * stepX else leftPad + chartWidth / 2f
            val y = topPad + chartHeight - (values[i] / 100f) * chartHeight
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            canvas.drawText(labels[i], x, h - 15f, textPaint)
        }
        canvas.drawPath(path, linePaint)

        for (i in values.indices) {
            val x = if (values.size > 1) leftPad + i * stepX else leftPad + chartWidth / 2f
            val y = topPad + chartHeight - (values[i] / 100f) * chartHeight
            canvas.drawCircle(x, y, 9f, dotPaint)
            canvas.drawText("${values[i]}", x, y - 18f, textPaint)
        }
    }
}
