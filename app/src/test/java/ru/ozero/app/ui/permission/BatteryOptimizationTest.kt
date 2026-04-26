package ru.ozero.app.ui.permission

import android.os.Build
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BatteryOptimizationTest {

    @Test
    fun `isApplicable false on api below 23`() {
        assertFalse(BatteryOptimization.isApplicable(Build.VERSION_CODES.LOLLIPOP))
        assertFalse(BatteryOptimization.isApplicable(Build.VERSION_CODES.LOLLIPOP_MR1))
    }

    @Test
    fun `isApplicable true on api 23 and above`() {
        assertTrue(BatteryOptimization.isApplicable(Build.VERSION_CODES.M))
        assertTrue(BatteryOptimization.isApplicable(Build.VERSION_CODES.TIRAMISU))
        assertTrue(BatteryOptimization.isApplicable(Build.VERSION_CODES.VANILLA_ICE_CREAM))
    }

    @Test
    fun `resolve returns NotApplicable below api 23 regardless of state`() {
        val sdk = Build.VERSION_CODES.LOLLIPOP
        assertEquals(
            BatteryOptimization.State.NotApplicable,
            BatteryOptimization.resolve(sdk, isIgnoring = false, alreadyShown = false),
        )
        assertEquals(
            BatteryOptimization.State.NotApplicable,
            BatteryOptimization.resolve(sdk, isIgnoring = true, alreadyShown = true),
        )
    }

    @Test
    fun `resolve returns AlreadyWhitelisted when system already ignores optimizations`() {
        val sdk = Build.VERSION_CODES.TIRAMISU
        assertEquals(
            BatteryOptimization.State.AlreadyWhitelisted,
            BatteryOptimization.resolve(sdk, isIgnoring = true, alreadyShown = false),
        )
        assertEquals(
            BatteryOptimization.State.AlreadyWhitelisted,
            BatteryOptimization.resolve(sdk, isIgnoring = true, alreadyShown = true),
        )
    }

    @Test
    fun `resolve returns Skip when prompt was already shown once`() {
        val sdk = Build.VERSION_CODES.TIRAMISU
        assertEquals(
            BatteryOptimization.State.Skip,
            BatteryOptimization.resolve(sdk, isIgnoring = false, alreadyShown = true),
        )
    }

    @Test
    fun `resolve returns NeedsPrompt on first VPN enable when not whitelisted`() {
        val sdk = Build.VERSION_CODES.TIRAMISU
        assertEquals(
            BatteryOptimization.State.NeedsPrompt,
            BatteryOptimization.resolve(sdk, isIgnoring = false, alreadyShown = false),
        )
    }
}
