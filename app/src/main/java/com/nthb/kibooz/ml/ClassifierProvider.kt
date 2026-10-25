package com.nthb.kibooz.ml

import android.content.Context
import android.util.Log
import com.nthb.kibooz.BuildConfig

/**
 * Memuat pengklasifikasi dari `assets/`. Jika modelnya hilang atau rusak, build debug memakai pengganti tetap
 * (stub) supaya layar tetap bisa dicoba, sedangkan build lain mengembalikan null dan layar menolak dibuka daripada
 * menampilkan hasil yang dikarang.
 */
object ClassifierProvider {

    private const val TAG = "ClassifierProvider"

    fun emotion(context: Context): EmotionClassifier? = try {
        TfliteEmotionClassifier.fromAssets(context)
    } catch (e: Exception) {
        Log.w(TAG, "Model emosi tidak bisa dimuat", e)
        if (BuildConfig.DEBUG) StubEmotionClassifier() else null
    }

    fun trash(context: Context): TrashClassifier? = try {
        TfliteTrashClassifier.fromAssets(context)
    } catch (e: Exception) {
        Log.w(TAG, "Model sampah tidak bisa dimuat", e)
        if (BuildConfig.DEBUG) StubTrashClassifier() else null
    }
}
