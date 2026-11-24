package com.nthb.kibooz.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.nthb.kibooz.data.remote.DailyMoodDistribution
import com.nthb.kibooz.ml.Emotion

/**
 * Donat proporsi empat emosi (Senang kuning, Bingung oranye, Sedih biru, Marah merah). Menyentuh sebuah irisan
 * memanggil [onSliceSelected] dengan emosi dan persentasenya (FR-3.4). Tanpa data digambar cincin abu-abu.
 */
class DonutMoodChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var percentages: Map<Emotion, Float> = MoodChartMath.ORDER.associateWith { 0f }
    private var animationProgress: Float = 1f
    private var animator: android.animation.ValueAnimator? = null

    /** Dipanggil dengan irisan yang disentuh. Sentuhan di luar irisan tidak memanggilnya. */
    var onSliceSelected: ((Emotion, Float) -> Unit)? = null

    private val rectF = RectF()
    private val strokeWidthDp = 22f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val strokePx = strokeWidthDp * resources.displayMetrics.density
        paint.strokeWidth = strokePx
        val pad = strokePx / 2f + 4f
        rectF.set(pad, pad, w - pad, h - pad)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (percentages.values.sum() <= 0f) {
            paint.color = MoodColors.EMPTY
            canvas.drawArc(rectF, 0f, 360f, false, paint)
            return
        }

        var startAngle = -90f
        for (emotion in MoodChartMath.ORDER) {
            val sweep = percentages.getValue(emotion) * 3.6f * animationProgress
            if (sweep <= 0f) continue
            paint.color = MoodColors.of(emotion)
            canvas.drawArc(rectF, startAngle, sweep, false, paint)
            startAngle += sweep
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return event.actionMasked == MotionEvent.ACTION_DOWN

        val dx = event.x - width / 2f
        val dy = event.y - height / 2f
        val radius = rectF.width() / 2f
        val distance = kotlin.math.hypot(dx, dy)
        val half = strokeWidthDp * resources.displayMetrics.density / 2f

        // Hanya cincin yang bisa disentuh, bukan lubang di tengah atau ruang di luarnya
        if (distance in (radius - half)..(radius + half)) {
            MoodChartMath.sliceAt(MoodChartMath.angleFromTop(dx, dy), percentages)?.let { emotion ->
                onSliceSelected?.invoke(emotion, percentages.getValue(emotion))
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
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

    fun setDistribution(distribution: DailyMoodDistribution) {
        percentages = MoodChartMath.percentages(distribution)
        startAnimation()
    }

    /** Persentase langsung dari server (`donutSummary`). Emosi yang tidak ada dianggap 0. */
    fun setPercentages(values: Map<Emotion, Float>) {
        percentages = MoodChartMath.ORDER.associateWith { values[it] ?: 0f }
        startAnimation()
    }
}
