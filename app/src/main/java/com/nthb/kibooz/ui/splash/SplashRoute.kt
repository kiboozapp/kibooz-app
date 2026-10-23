package com.nthb.kibooz.ui.splash

import com.nthb.kibooz.data.session.Role

/** Layar pertama setelah splash. */
enum class SplashRoute { ONBOARDING, DASHBOARD_GURU, DASHBOARD_WALI }

/** Sesi tersimpan membuka portal sesuai perannya, selain itu pengguna mulai dari onboarding. ADMIN belum punya portal. */
fun routeFor(role: Role?): SplashRoute = when (role) {
    Role.GURU -> SplashRoute.DASHBOARD_GURU
    Role.WALI -> SplashRoute.DASHBOARD_WALI
    Role.ADMIN, null -> SplashRoute.ONBOARDING
}
