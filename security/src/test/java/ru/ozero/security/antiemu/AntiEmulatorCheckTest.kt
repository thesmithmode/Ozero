package ru.ozero.security.antiemu

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AntiEmulatorCheckTest {

    private fun check(f: DeviceFingerprint) = AntiEmulatorCheck(provider = { f })

    private val realDevice = DeviceFingerprint(
        brand = "samsung",
        device = "starlte",
        product = "starltexx",
        model = "SM-G960F",
        manufacturer = "samsung",
        hardware = "exynos9810",
        fingerprint = "samsung/starltexx/starlte:10/QP1A.190711.020/G960FXXUEFUC1:user/release-keys",
    )

    private val androidStudioEmulator = DeviceFingerprint(
        brand = "google",
        device = "generic_x86_64",
        product = "sdk_gphone_x86_64",
        model = "Android SDK built for x86_64",
        manufacturer = "Google",
        hardware = "ranchu",
        fingerprint = "google/sdk_gphone_x86_64/generic_x86_64:11/RSR1.201013.001/6903271:userdebug/dev-keys",
    )

    private val genymotion = DeviceFingerprint(
        brand = "generic",
        device = "generic",
        product = "vbox86p",
        model = "Custom Phone",
        manufacturer = "Genymotion",
        hardware = "vbox86",
        fingerprint = "generic/vbox86p/vbox86p:8.0.0/OPR4.170623.020/eng:userdebug/test-keys",
    )

    @Test
    fun realDeviceNotDetected() {
        assertFalse(check(realDevice).isEmulator())
    }

    @Test
    fun androidStudioEmulatorDetected() {
        val c = check(androidStudioEmulator)
        assertTrue(c.isEmulator())
        assertTrue(c.matchedReasons().any { it.contains("ranchu") })
    }

    @Test
    fun genymotionDetected() {
        val c = check(genymotion)
        assertTrue(c.isEmulator())
        assertTrue(c.matchedReasons().any { it.contains("Genymotion", ignoreCase = true) })
    }

    @Test
    fun goldfishHardwareDetected() {
        val f = realDevice.copy(hardware = "goldfish")
        assertTrue(check(f).isEmulator())
    }

    @Test
    fun testKeysIgnored() {
        val f = realDevice.copy(fingerprint = "samsung/x/y:10/Z:user/test-keys")
        assertFalse(check(f).isEmulator())
    }

    @Test
    fun matchedReasonsEmptyForReal() {
        assertTrue(check(realDevice).matchedReasons().isEmpty())
    }
}
