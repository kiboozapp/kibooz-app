package com.nthb.kibooz.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * MonthlyBarChartView - Grafik Batang Bulanan (Jan - Agu)
 */
class MonthlyBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val months = arrayOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu")

    private val paintHappy = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFC107") }
    private val paintKalem = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4CAF50") }
    private val paintBingung = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF9800") }
    private val paintMarah = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E53935") }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9CA3AF")
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        val leftPad = 40f
        val bottomPad = 50f
        val topPad = 20f

        val chartHeight = h - topPad - bottomPad
        val groupWidth = (w - leftPad - 20f) / months.size
        val barWidth = groupWidth / 5.5f

        for (i in months.indices) {
            val groupStartX = leftPad + (i * groupWidth)

            // 4 bars per month (Marah, Happy, Kalem, Bingung)
            val hMarah = (floatArrayOf(5f, 5f, 10f, 5f, 10f, 15f, 5f, 5f)[i] / 20f) * chartHeight
            val hHappy = (floatArrayOf(20f, 15f, 15f, 20f, 15f, 20f, 20f, 20f)[i] / 20f) * chartHeight
            val hKalem = (floatArrayOf(15f, 20f, 10f, 15f, 10f, 10f, 5f, 15f)[i] / 20f) * chartHeight
            val hBingung = (floatArrayOf(10f, 5f, 15f, 5f, 20f, 8f, 20f, 10f)[i] / 20f) * chartHeight

            canvas.drawRoundRect(RectF(groupStartX, topPad + chartHeight - hMarah, groupStartX + barWidth, topPad + chartHeight), 4f, 4f, paintMarah)
            canvas.drawRoundRect(RectF(groupStartX + barWidth * 1.2f, topPad + chartHeight - hHappy, groupStartX + barWidth * 2.2f, topPad + chartHeight), 4f, 4f, paintHappy)
            canvas.drawRoundRect(RectF(groupStartX + barWidth * 2.4f, topPad + chartHeight - hKalem, groupStartX + barWidth * 3.4f, topPad + chartHeight), 4f, 4f, paintKalem)
            canvas.drawRoundRect(RectF(groupStartX + barWidth * 3.6f, topPad + chartHeight - hBingung, groupStartX + barWidth * 4.6f, topPad + chartHeight), 4f, 4f, paintBingung)

            // Month text
            canvas.drawText(months[i], groupStartX + (groupWidth / 2f), h - 15f, textPaint)
        }
    }
}
