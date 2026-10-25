package com.nthb.kibooz.ml

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Isi berkas `<nama model>.json` di `assets/`. Berkas ini yang membedakan model pengganti dari model Kibooz
 * sungguhan: model baru cukup mengganti `.tflite`, daftar label dan `groups`, tanpa mengubah kode.
 *
 * [groups] memetakan nama enum aplikasi (misalnya `SENANG` atau `ORGANIK`) ke label keluaran model yang masuk
 * kelompok itu. Keyakinan sebuah hasil adalah jumlah probabilitas label di kelompoknya.
 *
 * [placeholder] bernilai true selama modelnya pengganti sementara. Selama itu layar kamera menampilkan
 * "Model contoh" dan hanya bisa dibuka di build debug atau build internal (lihat [isScanAllowed]).
 */
data class ModelConfig(
    val name: String,
    val placeholder: Boolean = true,
    /** "GRAYSCALE" (1 kanal) atau "RGB" (3 kanal). Harus sama dengan bentuk masukan model. */
    val colorMode: String,
    /** Potong persegi dari tengah sebelum diperkecil, supaya objek tidak melar. */
    val centerCrop: Boolean = false,
    /** Nilai piksel diubah menjadi (piksel - mean) / std sebelum dikuantisasi sesuai parameter tensor masukan. */
    val mean: Float = 0f,
    val std: Float = 255f,
    /** True jika keluaran model berupa skor mentah yang belum melewati softmax. */
    val softmax: Boolean = false,
    val labels: List<String>? = null,
    /** Berkas di `assets/` berisi satu label per baris, dipakai jika [labels] kosong. */
    val labelsFile: String? = null,
    val groups: Map<String, List<String>>
) {
    val channels: Int
        get() = when (colorMode) {
            "GRAYSCALE" -> 1
            "RGB" -> 3
            else -> throw IllegalArgumentException("colorMode harus GRAYSCALE atau RGB, bukan $colorMode")
        }

    /**
     * Memeriksa isi konfigurasi terhadap daftar label yang sebenarnya. [allowedGroups] adalah nama enum yang boleh
     * dipakai (Emotion atau TrashCategory). Melempar [IllegalArgumentException] jika ada yang tidak cocok.
     */
    fun validate(resolvedLabels: List<String>, allowedGroups: Set<String>) {
        channels
        require(std != 0f) { "std tidak boleh 0" }
        require(resolvedLabels.isNotEmpty()) { "Daftar label kosong" }
        require(groups.isNotEmpty()) { "groups tidak boleh kosong" }

        val seen = mutableSetOf<String>()
        for ((group, members) in groups) {
            require(group in allowedGroups) { "Kelompok $group bukan salah satu dari $allowedGroups" }
            require(members.isNotEmpty()) { "Kelompok $group kosong" }
            for (label in members) {
                val occurrences = resolvedLabels.count { it == label }
                require(occurrences > 0) { "Label \"$label\" di kelompok $group tidak ada di daftar label model" }
                // ImageNet punya nama ganda (misalnya "crane"). Itu aman selama tidak dipakai di sebuah kelompok
                require(occurrences == 1) { "Label \"$label\" muncul $occurrences kali di daftar label, jadi ambigu untuk kelompok $group" }
                require(seen.add(label)) { "Label \"$label\" ada di lebih dari satu kelompok" }
            }
        }
    }

    companion object {
        private val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(ModelConfig::class.java)

        fun parse(json: String): ModelConfig =
            requireNotNull(adapter.fromJson(json)) { "Konfigurasi model kosong" }
    }
}
