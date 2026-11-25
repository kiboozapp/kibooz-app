package com.nthb.kibooz.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.nthb.kibooz.R
import com.nthb.kibooz.ml.TrashCategory
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Diagram lingkaran pembagian scan sampah: organik hijau, anorganik merah, B3 oranye, searah jarum jam dari atas.
 * Di dalam irisan hanya tertulis persentasenya; jumlah dan penjelasan ada di legenda layout. Tanpa scan digambar
 * lingkaran abu-abu.
 */
class TrashPieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var counts: Map<TrashCategory, Int> = TrashChartMath.ORDER.associateWith { 0 }
    private var percentages: Map<TrashCategory, Int> = TrashChartMath.ORDER.associateWith { 0 }
    private var hasData = false
    private var animationProgress = 1f
    private var animator: ValueAnimator? = null

    /** Warna diambil dari colors.xml, sama dengan kotak warna di legenda. */
    private val colors = mapOf(
        TrashCategory.ORGANIK to ContextCompat.getColor(context, R.color.waste_organic),
        TrashCategory.ANORGANIK to ContextCompat.getColor(context, R.color.waste_anorganic),
        TrashCategory.B3 to ContextCompat.getColor(context, R.color.waste_b3)
    )

    private val oval = RectF()
    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** Garis putih tipis di antara irisan supaya batas dua warna tetap jelas. */
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = dp(2f)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, resources.displayMetrics)
        typeface = if (isInEditMode) null else ResourcesCompat.getFont(context, R.font.baloo2)
        isFakeBoldText = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h) - dp(4f)
        val left = (w - size) / 2f
        val top = (h - size) / 2f
        oval.set(left, top, left + size, top + size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val total = counts.values.sum()
        if (total == 0) {
            slicePaint.color = MoodColors.EMPTY
            canvas.drawOval(oval, slicePaint)
            return
        }

        // Irisan digambar dari jumlah sebenarnya, labelnya dari persentase bulat yang totalnya 100
        var start = START_ANGLE
        for (category in TrashChartMath.ORDER) {
            val sweep = counts.getValue(category) * 360f / total * animationProgress
            if (sweep <= 0f) continue
            slicePaint.color = colors.getValue(category)
            canvas.drawArc(oval, start, sweep, true, slicePaint)
            if (sweep < 360f) canvas.drawArc(oval, start, sweep, true, borderPaint)
            start += sweep
        }

        // Label baru muncul setelah animasi selesai, supaya tidak ikut bergeser selama irisan tumbuh
        if (animationProgress < 1f) return
        val radius = oval.width() / 2f * LABEL_RADIUS
        val textOffset = (labelPaint.descent() + labelPaint.ascent()) / 2f
        start = START_ANGLE
        for (category in TrashChartMath.ORDER) {
            val sweep = counts.getValue(category) * 360f / total
            val percent = percentages.getValue(category)
            if (percent >= MIN_LABEL_PERCENT) {
                // Satu kategori 100%: labelnya di tengah lingkaran, bukan di tepi
                val r = if (percent == 100) 0f else radius
                val middle = Math.toRadians((start + sweep / 2f).toDouble())
                val x = oval.centerX() + r * cos(middle).toFloat()
                val y = oval.centerY() + r * sin(middle).toFloat() - textOffset
                canvas.drawText("$percent%", x, y, labelPaint)
            }
            start += sweep
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    /** Mengganti isi diagram. Animasi hanya diputar saat angkanya berubah, tidak setiap layar dimuat ulang. */
    fun setCounts(organik: Int, anorganik: Int, b3: Int) {
        val next = mapOf(TrashCategory.ORGANIK to organik, TrashCategory.ANORGANIK to anorganik, TrashCategory.B3 to b3)
        if (hasData && next == counts) return

        counts = next
        percentages = TrashChartMath.percentages(next)
        hasData = true
        contentDescription = context.getString(
            R.string.sampah_diagram_deskripsi,
            percentages.getValue(TrashCategory.ORGANIK),
            percentages.getValue(TrashCategory.ANORGANIK),
            percentages.getValue(TrashCategory.B3)
        )
        startAnimation()
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 750L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animationProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        /** Irisan pertama dimulai dari arah jam 12. */
        const val START_ANGLE = -90f

        /** Jarak label dari pusat, sebagai bagian dari jari-jari. */
        const val LABEL_RADIUS = 0.62f

        /** Irisan yang lebih kecil dari ini terlalu sempit untuk teks, jumlahnya tetap terbaca di legenda. */
        const val MIN_LABEL_PERCENT = 5
    }
}
