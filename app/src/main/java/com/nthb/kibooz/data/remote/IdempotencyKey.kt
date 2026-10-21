package com.nthb.kibooz.data.remote

import java.util.UUID

/** Nama header yang membuat POST aman diulang. Pola yang diterima server: `[A-Za-z0-9_-]{8,64}`. */
const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"

/** Kunci acak baru untuk satu aksi. Pakai kunci yang sama pada setiap percobaan ulang aksi itu. */
fun newIdempotencyKey(): String = UUID.randomUUID().toString()
