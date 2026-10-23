package com.nthb.kibooz.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.nthb.kibooz.R

/** Satu wajah yang digambar. Kotak [box] memakai koordinat frame tegak dari MoodAnalysisAnalyzer. */
data class OverlayFace(
    val trackingId: Int,
    val box: Rect,
    val badge: String?,
    val studentName: String?
)

/**
 * Menggambar kotak, lencana emosi dan nama siswa untuk tiap wajah di atas PreviewView ber-scaleType FILL_CENTER.
 * Mengetuk sebuah wajah memanggil [onFaceTapped]. Emosi selalu ditulis sebagai teks, tidak hanya diwarnai.
 */
class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onFaceTapped: ((trackingId: Int) -> Unit)? = null

    private var frameWidth = 0
    private var frameHeight = 0
    private var faces: List<OverlayFace> = emptyList()
    private var pressedId: Int? = null

    private val density = resources.displayMetrics.density
    private val boxRadius = 8 * density
    private val pillPadX = 8 * density
    private val pillPadY = 3 * density
    private val gap = 6 * density
    private val touchPad = 8 * density

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12 * resources.displayMetrics.scaledDensity
        typeface = ResourcesCompat.getFont(context, R.font.baloo2) ?: Typeface.DEFAULT_BOLD
    }

    private val unassignedColor = ContextCompat.getColor(context, R.color.kibooz_white)
    private val assignedColor = ContextCompat.getColor(context, R.color.kibooz_primary_purple)
    private val badgeBackground = 0xE6FFFFFF.toInt()
    private val nameBackground = 0xB3000000.toInt()
    private val badgeText = ContextCompat.getColor(context, R.color.kibooz_text_dark)
    private val nameText = ContextCompat.getColor(context, R.color.kibooz_white)

    private val rect = RectF()

    fun setFaces(frameWidth: Int, frameHeight: Int, faces: List<OverlayFace>) {
        this.frameWidth = frameWidth
        this.frameHeight = frameHeight
        this.faces = faces
        contentDescription = context.getString(R.string.mood_wajah_terdeteksi_desc, faces.size)
        invalidate()
    }

    fun clear() {
        faces = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val mapping = mapping() ?: return

        for (face in faces) {
            val box = mapping.map(face.box.left, face.box.top, face.box.right, face.box.bottom)

            boxPaint.color = if (face.studentName == null) unassignedColor else assignedColor
            rect.set(box.left, box.top, box.right, box.bottom)
            canvas.drawRoundRect(rect, boxRadius, boxRadius, boxPaint)

            var top = box.bottom + gap
            val centerX = (box.left + box.right) / 2f

            top = drawPill(
                canvas, face.badge ?: context.getString(R.string.mood_menganalisis),
                centerX, top, badgeBackground, badgeText
            )
            drawPill(
                canvas, face.studentName ?: context.getString(R.string.mood_ketuk_pilih_siswa),
                centerX, top + gap / 2f, nameBackground, nameText
            )
        }
    }

    // Mengembalikan posisi atas untuk elemen berikutnya
    private fun drawPill(canvas: Canvas, text: String, centerX: Float, top: Float, background: Int, foreground: Int): Float {
        val textWidth = textPaint.measureText(text)
        val fontHeight = textPaint.fontMetrics.let { it.descent - it.ascent }
        val width = textWidth + pillPadX * 2
        val height = fontHeight + pillPadY * 2

        // Lencana tidak boleh keluar dari tampilan, walaupun wajahnya di tepi
        val left = (centerX - width / 2f).coerceIn(0f, (this.width - width).coerceAtLeast(0f))
        val clampedTop = top.coerceAtMost((this.height - height).coerceAtLeast(0f))

        rect.set(left, clampedTop, left + width, clampedTop + height)
        pillPaint.color = background
        canvas.drawRoundRect(rect, height / 2f, height / 2f, pillPaint)

        textPaint.color = foreground
        canvas.drawText(text, left + pillPadX, clampedTop + pillPadY - textPaint.fontMetrics.ascent, textPaint)

        return clampedTop + height
    }

    private fun mapping(): FrameMapping? =
        if (frameWidth > 0 && frameHeight > 0 && width > 0 && height > 0) {
            FrameMapping(frameWidth, frameHeight, width, height)
        } else {
            null
        }

    // Area ketuk mencakup kotak wajah dan lencana di bawahnya, yang tingginya sekitar 64dp
    private fun faceAt(x: Float, y: Float): OverlayFace? {
        val mapping = mapping() ?: return null

        return faces.lastOrNull { face ->
            val box = mapping.map(face.box.left, face.box.top, face.box.right, face.box.bottom)
            ViewBox(
                box.left - touchPad,
                box.top - touchPad,
                box.right + touchPad,
                box.bottom + 64 * density
            ).contains(x, y)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedId = faceAt(event.x, event.y)?.trackingId
                return pressedId != null
            }
            MotionEvent.ACTION_UP -> {
                val id = pressedId
                pressedId = null
                if (id != null && faceAt(event.x, event.y)?.trackingId == id) {
                    performClick()
                    onFaceTapped?.invoke(id)
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> pressedId = null
        }

        return pressedId != null
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
