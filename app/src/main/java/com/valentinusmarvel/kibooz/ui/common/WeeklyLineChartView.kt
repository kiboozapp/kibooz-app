package com.valentinusmarvel.kibooz.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * WeeklyLineChartView - Grafik Garis Tren 4 Emosi (Senin - Sabtu)
 */
class WeeklyLineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val days = arrayOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu")

    // Data points per emotion (values 0 - 20)
    private val dataHappy = floatArrayOf(10f, 15f, 15f, 20f, 20f, 15f)
    private val dataKalem = floatArrayOf(5f, 10f, 10f, 15f, 15f, 10f)
    private val dataBingung = floatArrayOf(15f, 12f, 10f, 10f, 7f, 5f)
    private val dataMarah = floatArrayOf(10f, 7f, 5f, 5f, 5f, 5f)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9CA3AF")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F1F5F9")
        strokeWidth = 2f
    }

    private fun createLinePaint(hexColor: String) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(hexColor)
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val paintHappy = createLinePaint("#FFC107")
    private val paintKalem = createLinePaint("#4CAF50")
    private val paintBingung = createLinePaint("#FF9800")
    private val paintMarah = createLinePaint("#E53935")

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        val leftPad = 60f
        val rightPad = 40f
        val topPad = 30f
        val bottomPad = 60f

        val chartWidth = w - leftPad - rightPad
        val chartHeight = h - topPad - bottomPad

        // Draw horizontal grid lines (0, 5, 10, 15, 20)
        for (i in 0..4) {
            val y = topPad + chartHeight - (i * chartHeight / 4)
            canvas.drawLine(leftPad, y, w - rightPad, y, gridPaint)
            canvas.drawText("${i * 5}", leftPad - 25f, y + 10f, textPaint)
        }

        val stepX = chartWidth / (days.size - 1)

        // Draw day labels
        for (i in days.indices) {
            val x = leftPad + (i * stepX)
            canvas.drawText(days[i], x, h - 15f, textPaint)
        }

        // Helper to draw a line
        fun drawDataLine(data: FloatArray, paint: Paint) {
            val path = Path()
            for (i in data.indices) {
                val x = leftPad + (i * stepX)
                val y = topPad + chartHeight - ((data[i] / 20f) * chartHeight)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                canvas.drawCircle(x, y, 6f, paint)
            }
            canvas.drawPath(path, paint)
        }

        drawDataLine(dataHappy, paintHappy)
        drawDataLine(dataKalem, paintKalem)
        drawDataLine(dataBingung, paintBingung)
        drawDataLine(dataMarah, paintMarah)
    }
}
