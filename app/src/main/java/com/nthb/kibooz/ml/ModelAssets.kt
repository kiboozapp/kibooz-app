package com.nthb.kibooz.ml

import android.content.Context
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Model, konfigurasi dan daftar label yang sudah dimuat dari `assets/` dan sudah divalidasi. */
class LoadedModel(val runner: TfliteRunner, val config: ModelConfig) : AutoCloseable {
    override fun close() = runner.close()
}

internal object ModelAssets {

    /**
     * Memuat `<base>.tflite` dan `<base>.json` dari `assets/`. Model dipetakan langsung dari berkas (tidak
     * dikompresi, lihat `noCompress` di build.gradle.kts), jadi tidak memakan memori sebesar ukuran model.
     * Melempar jika berkas tidak ada atau isinya tidak cocok, dan pemanggil memutuskan apa yang dilakukan.
     */
    fun load(context: Context, base: String, allowedGroups: Set<String>): LoadedModel {
        val config = ModelConfig.parse(context.assets.open("$base.json").bufferedReader().use { it.readText() })
        val labels = config.labels
            ?: config.labelsFile?.let { file ->
                context.assets.open(file).bufferedReader().useLines { lines -> lines.map { it.trim() }.filter { it.isNotEmpty() }.toList() }
            }
            ?: throw IllegalArgumentException("Konfigurasi $base.json butuh labels atau labelsFile")

        config.validate(labels, allowedGroups)

        val runner = TfliteRunner(mapModel(context, "$base.tflite"), config, labels)
        return LoadedModel(runner, config)
    }

    private fun mapModel(context: Context, name: String): ByteBuffer =
        context.assets.openFd(name).use { fd ->
            FileInputStream(fd.fileDescriptor).use { stream ->
                stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
}
