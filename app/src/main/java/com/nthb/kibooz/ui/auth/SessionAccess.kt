package com.nthb.kibooz.ui.auth

import com.nthb.kibooz.data.session.Role

/** Siapa yang boleh membuka sebuah layar. */
enum class Access { PUBLIC, GURU, WALI, ANY_ROLE }

private const val APP_PACKAGE = "com.nthb.kibooz."

// Layar kelas dibuka dari kedua portal, DetailKelasActivity menautkan ke tiga lainnya. Papan peringkat
// ada di package wali, tetapi Guru juga membukanya dari banner ranking di dasbor
private val SHARED_CLASS_SCREENS = setOf(
    "com.nthb.kibooz.ui.guru.DetailKelasActivity",
    "com.nthb.kibooz.ui.guru.VideoPembelajaranActivity",
    "com.nthb.kibooz.ui.guru.PesertaKelasActivity",
    "com.nthb.kibooz.ui.guru.ForumDiskusiActivity",
    "com.nthb.kibooz.ui.wali.LeaderboardActivity"
)

// Layar profil ada di package profile, jadi package-nya tidak menunjukkan peran. Yang boleh dibuka tiap peran ditulis
// satu per satu, dan layar profil yang belum tertulis di sini wajib punya sesi tetapi terbuka bagi kedua peran
private val GURU_PROFILE_SCREENS = setOf(
    "com.nthb.kibooz.ui.profile.ProfilGuruActivity"
)
private val WALI_PROFILE_SCREENS = setOf(
    "com.nthb.kibooz.ui.profile.ProfilWaliActivity"
)

/**
 * Aturan akses menurut nama kelas Activity. Layar milik aplikasi yang tidak dikenal wajib punya sesi,
 * supaya layar baru tidak terbuka tanpa login sebelum aturannya ditulis di sini.
 */
fun accessFor(className: String): Access = when {
    !className.startsWith(APP_PACKAGE) -> Access.PUBLIC
    className.startsWith("${APP_PACKAGE}ui.splash.") ||
        className.startsWith("${APP_PACKAGE}ui.onboarding.") ||
        className.startsWith("${APP_PACKAGE}ui.auth.") -> Access.PUBLIC
    className in SHARED_CLASS_SCREENS -> Access.ANY_ROLE
    className in GURU_PROFILE_SCREENS -> Access.GURU
    className in WALI_PROFILE_SCREENS -> Access.WALI
    className == "${APP_PACKAGE}ui.camera.CameraScanMoodActivity" -> Access.GURU
    className == "${APP_PACKAGE}ui.camera.CameraScanSampahActivity" -> Access.WALI
    className.startsWith("${APP_PACKAGE}ui.guru.") -> Access.GURU
    className.startsWith("${APP_PACKAGE}ui.wali.") -> Access.WALI
    else -> Access.ANY_ROLE
}

/** [role] null berarti tidak ada sesi. ADMIN belum punya layar di aplikasi. */
fun Access.allows(role: Role?): Boolean = when (this) {
    Access.PUBLIC -> true
    Access.GURU -> role == Role.GURU
    Access.WALI -> role == Role.WALI
    Access.ANY_ROLE -> role == Role.GURU || role == Role.WALI
}
