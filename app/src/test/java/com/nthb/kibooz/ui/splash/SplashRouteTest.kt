package com.nthb.kibooz.ui.splash

import com.nthb.kibooz.data.session.Role
import org.junit.Assert.assertEquals
import org.junit.Test

class SplashRouteTest {

    @Test
    fun `a stored teacher or parent session opens its own dashboard`() {
        assertEquals(SplashRoute.DASHBOARD_GURU, routeFor(Role.GURU))
        assertEquals(SplashRoute.DASHBOARD_WALI, routeFor(Role.WALI))
    }

    @Test
    fun `no session starts at the onboarding`() {
        assertEquals(SplashRoute.ONBOARDING, routeFor(null))
    }

    @Test
    fun `an admin session has no portal and starts at the onboarding`() {
        assertEquals(SplashRoute.ONBOARDING, routeFor(Role.ADMIN))
    }
}
