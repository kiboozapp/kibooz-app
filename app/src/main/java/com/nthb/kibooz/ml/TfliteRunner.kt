package com.nthb.kibooz.ml

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

/**
 * Membungkus satu [Interpreter] TFLite: memperkecil bitmap, mengubahnya menjadi masukan tensor sesuai [config],
 * menjalankan model dan mengembalikan probabilitas tiap label. Ukuran masukan, tipe data dan parameter kuantisasi
 * dibaca dari modelnya sendiri, jadi model pengganti dan model Kibooz sungguhan memakai kode yang sama.
 *
 * Tidak aman dipakai dari beberapa thread, panggil dari thread analyzer saja. Bitmap tidak disimpan.
 */
class TfliteRunner(
    model: ByteBuffer,
    private val config: ModelConfig,
    val labels: List<String>
) : AutoCloseable {

    private val interpreter = Interpreter(model, Interpreter.Options().setNumThreads(THREADS))

    private val inputHeight: Int
    private val inputWidth: Int
    private val inputKind: TensorKind
    private val inputScale: Float
    private val inputZeroPoint: Int
    private val outputKind: TensorKind
    private val outputScale: Float
    private val outputZeroPoint: Int

    init {
        try {
            val input = interpreter.getInputTensor(0)
            val shape = input.shape() // [1, tinggi, lebar, kanal]
            require(shape.size == 4 && shape[0] == 1 && shape[3] == config.channels) {
                "Masukan model ${shape.toList()} tidak cocok dengan colorMode ${config.colorMode}"
            }
            inputHeight = shape[1]
            inputWidth = shape[2]
            inputKind = input.dataType().toKind()
            input.quantizationParams().let { inputScale = it.scale; inputZeroPoint = it.zeroPoint }

            val output = interpreter.getOutputTensor(0)
            val count = output.shape().last()
            require(count == labels.size) { "Model mengeluarkan $count nilai, daftar label berisi ${labels.size}" }
            outputKind = output.dataType().toKind()
            output.quantizationParams().let { outputScale = it.scale; outputZeroPoint = it.zeroPoint }
        } catch (e: RuntimeException) {
            interpreter.close()
            throw e
        }
    }

    /** Probabilitas tiap label sesuai urutan [labels]. */
    fun probabilities(bitmap: Bitmap): FloatArray {
        val pixels = pixelsFor(bitmap)
        val input = ModelMath.encodeInput(
            pixels, config.channels, config.mean, config.std, inputKind, inputScale, inputZeroPoint
        )
        val output = ByteBuffer.allocateDirect(labels.size * outputKind.bytes).order(ByteOrder.nativeOrder())

        interpreter.run(input, output)

        val raw = ModelMath.decodeOutput(output, labels.size, outputKind, outputScale, outputZeroPoint)
        return if (config.softmax) ModelMath.softmax(raw) else raw
    }

    override fun close() {
        interpreter.close()
    }

    private fun pixelsFor(source: Bitmap): IntArray {
        val squared = if (config.centerCrop) {
            val side = minOf(source.width, source.height)
            Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        } else {
            source
        }
        val scaled = Bitmap.createScaledBitmap(squared, inputWidth, inputHeight, true)

        try {
            return IntArray(inputWidth * inputHeight).also {
                scaled.getPixels(it, 0, inputWidth, 0, 0, inputWidth, inputHeight)
            }
        } finally {
            // createBitmap dan createScaledBitmap mengembalikan bitmap asalnya jika ukurannya sudah sama
            if (scaled !== squared) scaled.recycle()
            if (squared !== source) squared.recycle()
        }
    }

    private fun DataType.toKind() = when (this) {
        DataType.FLOAT32 -> TensorKind.FLOAT32
        DataType.UINT8 -> TensorKind.UINT8
        DataType.INT8 -> TensorKind.INT8
        else -> throw IllegalArgumentException("Tipe tensor $this belum didukung")
    }

    private companion object {
        const val THREADS = 2
    }
}
