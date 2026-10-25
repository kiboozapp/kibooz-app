package com.nthb.kibooz.ml

import com.nthb.kibooz.BuildConfig

/**
 * Apakah layar pemindai boleh dibuka. Model sungguhan ([isPlaceholder] false) selalu boleh. Model pengganti
 * hanya boleh di build debug atau build yang sengaja dibuat dengan `-Pkibooz.allowPlaceholderModels=true`
 * (misalnya untuk demo internal), supaya hasil contoh tidak sampai ke pengguna sungguhan tanpa disadari.
 */
fun isScanAllowed(
    isPlaceholder: Boolean,
    debugBuild: Boolean = BuildConfig.DEBUG,
    allowPlaceholderFlag: Boolean = BuildConfig.ALLOW_PLACEHOLDER_MODELS
): Boolean = !isPlaceholder || debugBuild || allowPlaceholderFlag
