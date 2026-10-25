package com.nthb.kibooz.ml

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelGateTest {

    @Test
    fun `a real model is allowed everywhere`() {
        assertTrue(isScanAllowed(isPlaceholder = false, debugBuild = false, allowPlaceholderFlag = false))
    }

    @Test
    fun `a placeholder is blocked in a normal release build`() {
        assertFalse(isScanAllowed(isPlaceholder = true, debugBuild = false, allowPlaceholderFlag = false))
    }

    @Test
    fun `a placeholder is allowed in a debug build`() {
        assertTrue(isScanAllowed(isPlaceholder = true, debugBuild = true, allowPlaceholderFlag = false))
    }

    @Test
    fun `a placeholder is allowed in an internal build made with the flag`() {
        assertTrue(isScanAllowed(isPlaceholder = true, debugBuild = false, allowPlaceholderFlag = true))
    }
}
