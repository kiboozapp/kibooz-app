package com.nthb.kibooz.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt

/**
 * DonutMoodChartView - Custom Donut Chart untuk memvisualisasikan 4 kategori mood
 * (Happy: Kuning, Bingung: Oranye, Kalem: Hijau, Marah: Merah)
 */
class DonutMoodChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Nilai persentase masing-masing mood (total 100)
    var happyPct: Float = 40f
    var bingungPct: Float = 20f
    var kalemPct: Float = 30f
    var marahPct: Float = 10f

    private val rectF = RectF()
    private val strokeWidthDp = 22f

    private val paintHappy = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFC107") // Happy - Yellow
        strokeCap = Paint.Cap.ROUND
    }

    private val paintBingung = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FF9800") // Bingung - Orange
        strokeCap = Paint.Cap.ROUND
    }

    private val paintKalem = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#4CAF50") // Kalem - Green
        strokeCap = Paint.Cap.ROUND
    }

    private val paintMarah = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#E53935") // Marah - Red
        strokeCap = Paint.Cap.ROUND
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val strokePx = strokeWidthDp * resources.displayMetrics.density
        paintHappy.strokeWidth = strokePx
        paintBingung.strokeWidth = strokePx
        paintKalem.strokeWidth = strokePx
        paintMarah.strokeWidth = strokePx

        val pad = strokePx / 2f + 4f
        rectF.set(pad, pad, w - pad, h - pad)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val happyAngle = (happyPct / 100f) * 360f
        val bingungAngle = (bingungPct / 100f) * 360f
        val kalemAngle = (kalemPct / 100f) * 360f
        val marahAngle = (marahPct / 100f) * 360f

        var startAngle = -90f

        // Draw each arc
        drawSegment(canvas, startAngle, happyAngle, paintHappy)
        startAngle += happyAngle

        drawSegment(canvas, startAngle, bingungAngle, paintBingung)
        startAngle += bingungAngle

        drawSegment(canvas, startAngle, kalemAngle, paintKalem)
        startAngle += kalemAngle

        drawSegment(canvas, startAngle, marahAngle, paintMarah)
    }

    /** Busur tanpa sudut dilewati, karena ujung bulat membuatnya tergambar sebagai titik. */
    private fun drawSegment(canvas: Canvas, startAngle: Float, sweep: Float, paint: Paint) {
        if (sweep > 0f) canvas.drawArc(rectF, startAngle, sweep, false, paint)
    }

    /** Mengganti warna segmen ketiga, hijau (Kalem) secara bawaan. Dasbor Guru memakainya untuk Sedih. */
    fun setThirdSegmentColor(@ColorInt color: Int) {
        paintKalem.color = color
        invalidate()
    }

    fun updateData(happy: Float, bingung: Float, kalem: Float, marah: Float) {
        this.happyPct = happy
        this.bingungPct = bingung
        this.kalemPct = kalem
        this.marahPct = marah
        invalidate()
    }
}
