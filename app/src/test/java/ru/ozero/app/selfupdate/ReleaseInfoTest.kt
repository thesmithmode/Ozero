package ru.ozero.app.selfupdate

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseInfoTest {

    private fun rel(tag: String) = ReleaseInfo(tag = tag, apkUrl = "u", sigUrl = "s")

    @Test
    fun parsesPlain() = assertEquals(Triple(1, 2, 3), rel("v1.2.3").semver())

    @Test
    fun parsesWithRc() = assertEquals(Triple(1, 2, 3), rel("v1.2.3-rc1").semver())

    @Test
    fun parsesWithoutVPrefix() = assertEquals(Triple(0, 1, 0), rel("0.1.0").semver())

    @Test
    fun rejectsMalformed() = assertNull(rel("foo").semver())

    @Test
    fun rejectsTooShort() = assertNull(rel("v1.2").semver())

    @Test
    fun newerMajor() = assertTrue(rel("v2.0.0").isNewerThan("v1.99.99"))

    @Test
    fun olderMajor() = assertFalse(rel("v1.0.0").isNewerThan("v2.0.0"))

    @Test
    fun newerMinor() = assertTrue(rel("v1.2.0").isNewerThan("v1.1.99"))

    @Test
    fun newerPatch() = assertTrue(rel("v1.0.1").isNewerThan("v1.0.0"))

    @Test
    fun samePatch() = assertFalse(rel("v1.0.0").isNewerThan("v1.0.0"))

    @Test
    fun rcDoesNotInfluenceSemver() = assertFalse(rel("v1.0.0-rc1").isNewerThan("v1.0.0"))

    @Test
    fun prereleaseRejectedEvenIfNewerSemver() {
        val r = ReleaseInfo(tag = "v2.0.0-rc1", apkUrl = "u", sigUrl = "s", isPrerelease = true)
        assertFalse(r.isNewerThan("v1.0.0"))
    }

    @Test
    fun stableReleaseAcceptedWhenNewer() {
        val r = ReleaseInfo(tag = "v2.0.0", apkUrl = "u", sigUrl = "s", isPrerelease = false)
        assertTrue(r.isNewerThan("v1.0.0"))
    }
}
