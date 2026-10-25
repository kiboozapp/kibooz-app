package com.nthb.kibooz.ml

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.roundToInt

/** Tipe data tensor TFLite yang dipakai model kita. */
enum class TensorKind(val bytes: Int) { FLOAT32(4), UINT8(1), INT8(1) }

/**
 * Hitungan murni di sekitar interpreter TFLite, dipisah supaya bisa diuji di JVM tanpa perangkat.
 */
object ModelMath {

    /**
     * Mengubah piksel ARGB menjadi isi tensor masukan. Tiap nilai kanal (0 sampai 255, atau luma untuk 1 kanal)
     * menjadi (nilai - mean) / std, lalu untuk tensor terkuantisasi menjadi bulat(x / scale) + zeroPoint.
     */
    fun encodeInput(
        argb: IntArray,
        channels: Int,
        mean: Float,
        std: Float,
        kind: TensorKind,
        scale: Float,
        zeroPoint: Int
    ): ByteBuffer {
        require(channels == 1 || channels == 3) { "channels harus 1 atau 3" }

        val buffer = ByteBuffer.allocateDirect(argb.size * channels * kind.bytes).order(ByteOrder.nativeOrder())

        fun put(value: Float) {
            val x = (value - mean) / std
            when (kind) {
                TensorKind.FLOAT32 -> buffer.putFloat(x)
                TensorKind.UINT8 -> buffer.put((x / scale).roundToInt().plus(zeroPoint).coerceIn(0, 255).toByte())
                TensorKind.INT8 -> buffer.put((x / scale).roundToInt().plus(zeroPoint).coerceIn(-128, 127).toByte())
            }
        }

        for (pixel in argb) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            if (channels == 1) {
                put(0.299f * r + 0.587f * g + 0.114f * b)
            } else {
                put(r.toFloat())
                put(g.toFloat())
                put(b.toFloat())
            }
        }

        buffer.rewind()
        return buffer
    }

    /** Membaca [count] nilai keluaran dan mengembalikannya sebagai float, dikembalikan dari kuantisasi jika perlu. */
    fun decodeOutput(buffer: ByteBuffer, count: Int, kind: TensorKind, scale: Float, zeroPoint: Int): FloatArray {
        val source = buffer.duplicate().order(ByteOrder.nativeOrder()).also { it.rewind() }

        return FloatArray(count) {
            when (kind) {
                TensorKind.FLOAT32 -> source.float
                TensorKind.UINT8 -> ((source.get().toInt() and 0xFF) - zeroPoint) * scale
                TensorKind.INT8 -> (source.get().toInt() - zeroPoint) * scale
            }
        }
    }

    fun softmax(scores: FloatArray): FloatArray {
        val max = scores.maxOrNull() ?: return scores
        val exps = FloatArray(scores.size) { exp(scores[it] - max) }
        val sum = exps.sum()

        return FloatArray(scores.size) { exps[it] / sum }
    }

    /** Jumlah probabilitas label di tiap kelompok. Label yang tidak masuk kelompok mana pun tidak dihitung. */
    fun groupMass(probabilities: FloatArray, labels: List<String>, groups: Map<String, List<String>>): Map<String, Float> {
        require(probabilities.size == labels.size) {
            "Model mengeluarkan ${probabilities.size} nilai, tetapi daftar label berisi ${labels.size}"
        }

        val index = labels.withIndex().associate { it.value to it.index }
        return groups.mapValues { (_, members) -> members.sumOf { probabilities[index.getValue(it)].toDouble() }.toFloat() }
    }

    /** Kelompok dengan probabilitas terbesar. Bila semuanya 0, kelompok pertama dengan keyakinan 0. */
    fun best(mass: Map<String, Float>): Pair<String, Float> {
        val top = mass.maxByOrNull { it.value } ?: error("Tidak ada kelompok")
        return top.key to top.value.coerceIn(0f, 1f)
    }
}
