package com.nthb.kibooz.ui.auth

import com.nthb.kibooz.data.session.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAccessTest {

    private fun ui(name: String) = "com.nthb.kibooz.ui.$name"

    @Test
    fun `splash, onboarding and the auth screens are public`() {
        listOf("splash.SplashActivity", "onboarding.OnboardingActivity", "auth.LoginActivity", "auth.RoleSelectionActivity")
            .forEach { assertEquals(it, Access.PUBLIC, accessFor(ui(it))) }
    }

    @Test
    fun `screens of a portal need its own role`() {
        assertEquals(Access.GURU, accessFor(ui("guru.DashboardGuruActivity")))
        assertEquals(Access.GURU, accessFor(ui("guru.ProfilGuruActivity")))
        assertEquals(Access.WALI, accessFor(ui("wali.DashboardWaliActivity")))
        assertEquals(Access.WALI, accessFor(ui("wali.ProfilWaliActivity")))
    }

    @Test
    fun `the leaderboard is open to both roles because the guru dashboard banner opens it`() {
        assertEquals(Access.ANY_ROLE, accessFor(ui("wali.LeaderboardActivity")))
    }

    @Test
    fun `the class screens are open to both roles because a parent reaches them from the class list`() {
        listOf("DetailKelasActivity", "VideoPembelajaranActivity", "PesertaKelasActivity", "ForumDiskusiActivity")
            .forEach { assertEquals(it, Access.ANY_ROLE, accessFor(ui("guru.$it"))) }
    }

    @Test
    fun `each camera screen belongs to the role that opens it`() {
        assertEquals(Access.GURU, accessFor(ui("camera.CameraScanMoodActivity")))
        assertEquals(Access.WALI, accessFor(ui("camera.CameraScanSampahActivity")))
    }

    @Test
    fun `an unknown screen of the app needs a session and a library screen is left alone`() {
        assertEquals(Access.ANY_ROLE, accessFor(ui("baru.LayarBaruActivity")))
        assertEquals(Access.PUBLIC, accessFor("com.google.android.gms.oss.licenses.OssLicensesActivity"))
    }

    @Test
    fun `a role is allowed only where the access names it`() {
        assertTrue(Access.PUBLIC.allows(null))
        assertTrue(Access.GURU.allows(Role.GURU))
        assertFalse(Access.GURU.allows(Role.WALI))
        assertFalse(Access.WALI.allows(Role.GURU))
        assertTrue(Access.ANY_ROLE.allows(Role.GURU))
        assertTrue(Access.ANY_ROLE.allows(Role.WALI))
    }

    @Test
    fun `no session and an admin session get into no protected screen`() {
        listOf(Access.GURU, Access.WALI, Access.ANY_ROLE).forEach {
            assertFalse(it.name, it.allows(null))
            assertFalse(it.name, it.allows(Role.ADMIN))
        }
    }
}
