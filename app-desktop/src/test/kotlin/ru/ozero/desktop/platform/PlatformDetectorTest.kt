package ru.ozero.desktop.platform

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PlatformDetectorTest {

    @Nested
    inner class OsDetection {

        @Test
        fun `should detect exactly one OS`() {
            val results = listOf(
                PlatformDetector.isWindows(),
                PlatformDetector.isMac(),
                PlatformDetector.isLinux(),
            )
            assertEquals(1, results.count { it })
        }

        private fun assertEquals(expected: Int, actual: Int) {
            org.junit.jupiter.api.Assertions.assertEquals(expected, actual)
        }
    }

    @Nested
    inner class AdminDetection {

        @Test
        fun `isAdmin should return boolean without throwing`() {
            val result = PlatformDetector.isAdmin()
            assertNotNull(result)
        }
    }

    @Nested
    inner class WintunDetection {

        @Test
        fun `hasWintun should return false on non-Windows`() {
            if (!PlatformDetector.isWindows()) {
                assertFalse(PlatformDetector.hasWintun())
            }
        }

        @Test
        fun `hasWintun should return boolean without throwing`() {
            val result = PlatformDetector.hasWintun()
            assertNotNull(result)
        }
    }

    @Nested
    inner class CanUseTun {

        @Test
        fun `canUseTun should return boolean without throwing`() {
            val result = PlatformDetector.canUseTun()
            assertNotNull(result)
        }

        @Test
        fun `canUseTun on non-Windows without root should be false`() {
            if (!PlatformDetector.isWindows() && System.getProperty("user.name") != "root") {
                assertFalse(PlatformDetector.canUseTun())
            }
        }
    }
}
