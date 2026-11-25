package com.nthb.kibooz.ui.account

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.nthb.kibooz.util.AvatarCropMath
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * Tampilan pemotong foto profil interaktif dengan gestur geser, cubit (pinch-to-zoom), rotasi 90 derajat,
 * dan panduan lingkaran presisi agar posisi wajah dapat diatur sebelum diunggah.
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var sourceBitmap: Bitmap? = null
    private val imageMatrix = Matrix()
    private val matrixValues = FloatArray(9)
    private val mappedBounds = RectF()
    private val cropRect = RectF()

    private var minScale = 1f
    private var maxScale = 5f

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3161224")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scrimPath = Path()

    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private val lastTouch = PointF()
    private var isScaling = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val bmp = sourceBitmap ?: return false
                val curScale = currentScale()
                val factor = detector.scaleFactor
                val targetScale = (curScale * factor).coerceIn(minScale, maxScale)
                val appliedFactor = targetScale / curScale
                imageMatrix.postScale(appliedFactor, appliedFactor, detector.focusX, detector.focusY)
                constrainMatrix(bmp)
                invalidate()
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
            }
        }
    )

    fun setBitmap(bitmap: Bitmap) {
        sourceBitmap = bitmap
        resetFit()
    }

    fun rotate90() {
        val current = sourceBitmap ?: return
        val rotateMatrix = Matrix().apply { postRotate(90f) }
        val rotated = Bitmap.createBitmap(current, 0, 0, current.width, current.height, rotateMatrix, true)
        if (rotated !== current) {
            current.recycle()
        }
        sourceBitmap = rotated
        resetFit()
    }

    /** Memotong area di dalam lingkaran/kotak panduan lalu menyimpannya sebagai file JPEG <= 2 MiB. */
    fun exportCroppedJpeg(targetFile: File): Boolean {
        val bmp = sourceBitmap ?: return false
        if (cropRect.width() <= 0f || cropRect.height() <= 0f) return false

        val inverse = Matrix()
        if (!imageMatrix.invert(inverse)) return false

        val mappedCrop = RectF(cropRect)
        inverse.mapRect(mappedCrop)

        val rect = AvatarCropMath.clampCropRect(
            rawLeft = mappedCrop.left,
            rawTop = mappedCrop.top,
            rawRight = mappedCrop.right,
            rawBottom = mappedCrop.bottom,
            bitmapW = bmp.width,
            bitmapH = bmp.height
        )

        val cropped = Bitmap.createBitmap(bmp, rect.left, rect.top, rect.size, rect.size)
        val outSide = AvatarCropMath.outputSidePx(rect.size)
        val finalBitmap = if (cropped.width != outSide) {
            Bitmap.createScaledBitmap(cropped, outSide, outSide, true).also {
                if (it !== cropped) cropped.recycle()
            }
        } else {
            cropped
        }

        return try {
            val stream = ByteArrayOutputStream()
            var quality = 90
            while (quality >= 60) {
                stream.reset()
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                if (stream.size() <= AvatarCropMath.MAX_UPLOAD_BYTES) break
                quality -= 10
            }
            FileOutputStream(targetFile).use { fos ->
                fos.write(stream.toByteArray())
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            finalBitmap.recycle()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val size = min(w, h) * 0.78f
        val left = (w - size) / 2f
        val top = (h - size) / 2f
        cropRect.set(left, top, left + size, top + size)
        resetFit()
    }

    private fun resetFit() {
        val bmp = sourceBitmap ?: return
        if (cropRect.width() <= 0f) return

        minScale = AvatarCropMath.minCoverScale(bmp.width, bmp.height, cropRect.width())
        maxScale = minScale * 5f

        val initScale = minScale
        val scaledW = bmp.width * initScale
        val scaledH = bmp.height * initScale
        val dx = cropRect.centerX() - scaledW / 2f
        val dy = cropRect.centerY() - scaledH / 2f

        imageMatrix.reset()
        imageMatrix.postScale(initScale, initScale)
        imageMatrix.postTranslate(dx, dy)
        constrainMatrix(bmp)
        invalidate()
    }

    private fun currentScale(): Float {
        imageMatrix.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X].coerceAtLeast(0.001f)
    }

    private fun constrainMatrix(bmp: Bitmap) {
        mappedBounds.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        imageMatrix.mapRect(mappedBounds)

        val fixedLeft = AvatarCropMath.clampAxisOrigin(mappedBounds.left, mappedBounds.width(), cropRect.left, cropRect.right)
        val fixedTop = AvatarCropMath.clampAxisOrigin(mappedBounds.top, mappedBounds.height(), cropRect.top, cropRect.bottom)
        imageMatrix.postTranslate(fixedLeft - mappedBounds.left, fixedTop - mappedBounds.top)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = sourceBitmap
        if (bmp != null && !bmp.isRecycled) {
            canvas.drawBitmap(bmp, imageMatrix, bitmapPaint)
        }

        scrimPath.reset()
        scrimPath.fillType = Path.FillType.EVEN_ODD
        scrimPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        scrimPath.addOval(cropRect, Path.Direction.CW)
        canvas.drawPath(scrimPath, scrimPaint)

        canvas.drawRect(cropRect, guidePaint)
        canvas.drawOval(cropRect, borderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastTouch.set(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isScaling && activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    val idx = event.findPointerIndex(activePointerId)
                    if (idx >= 0) {
                        val x = event.getX(idx)
                        val y = event.getY(idx)
                        val bmp = sourceBitmap
                        if (bmp != null) {
                            imageMatrix.postTranslate(x - lastTouch.x, y - lastTouch.y)
                            constrainMatrix(bmp)
                            invalidate()
                        }
                        lastTouch.set(x, y)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newIdx = if (pointerIndex == 0) 1 else 0
                    if (newIdx < event.pointerCount) {
                        activePointerId = event.getPointerId(newIdx)
                        lastTouch.set(event.getX(newIdx), event.getY(newIdx))
                    } else {
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        return true
    }
}
