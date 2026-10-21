package com.nthb.kibooz.data.remote

import okhttp3.ConnectionSpec

/**
 * Alamat https hanya boleh memakai TLS 1.2 dan 1.3 dengan cipher modern. Alamat http, yang hanya
 * lolos di build debug ke 10.0.2.2, tetap boleh tanpa TLS.
 */
internal fun connectionSpecsFor(baseUrl: String): List<ConnectionSpec> =
    if (baseUrl.startsWith("https://", ignoreCase = true)) {
        listOf(ConnectionSpec.RESTRICTED_TLS)
    } else {
        listOf(ConnectionSpec.RESTRICTED_TLS, ConnectionSpec.CLEARTEXT)
    }
