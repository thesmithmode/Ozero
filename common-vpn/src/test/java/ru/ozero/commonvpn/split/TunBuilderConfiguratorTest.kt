package ru.ozero.commonvpn.split

import android.content.pm.PackageManager
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.settings.SplitTunnelMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TunBuilderConfiguratorTest {

    private val configurator = TunBuilderConfigurator(packageName = "ru.ozero.app")

    @Test
    fun allModeAddsDefaultRouteV4Only() {
        val b = recordingBuilder()
        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.ALL))
        assertEquals(1, b.routeCalls.count { it == "0.0.0.0" to 0 })
        assertEquals(0, b.routeCalls.count { it == "::" to 0 })
        assertEquals(emptyList(), b.allowedCalls)
        assertEquals(emptyList(), b.disallowedCalls)
    }

    @Test
    fun bypassLanAddsV4RoutesOnly() {
        val b = recordingBuilder()
        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.BYPASS_LAN))
        assertEquals(0, b.routeCalls.count { it == "0.0.0.0" to 0 })
        assertEquals(1, b.routeCalls.count { it == "8.0.0.0" to 7 })
        assertEquals(1, b.routeCalls.count { it == "1.0.0.0" to 8 })
        assertEquals(0, b.routeCalls.count { it == "2000::" to 3 })
        assertEquals(emptyList(), b.disallowedCalls)
    }

    @Test
    fun allowlistDoesNotAddIpv6Route() {
        val b = recordingBuilder()
        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.ALLOWLIST, allowlist = setOf("com.x")),
        )
        assertEquals(0, b.routeCalls.count { it == "::" to 0 })
    }

    @Test
    fun allowlistIncludeSelfTrueSkipsKillAllFallbackAndAddsSelfOnlyOnce() {
        val b = recordingBuilder()

        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.ALLOWLIST,
                allowlist = setOf("ru.ozero.app", "com.example.one"),
            ),
            excludeSelf = false,
        )

        assertEquals(1, b.allowedCalls.count { it == "com.example.one" })
        assertEquals(1, b.allowedCalls.count { it == "ru.ozero.app" })
    }

    @Test
    fun allowlistIncludeSelfTrueContinuesWhenSelfAddFails() {
        val b = recordingBuilder(failedAllowed = setOf("ru.ozero.app"))

        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.ALLOWLIST,
                allowlist = setOf("ru.ozero.app", "com.example.one"),
            ),
            excludeSelf = false,
        )

        assertEquals(1, b.allowedCalls.count { it == "com.example.one" })
        assertEquals(1, b.allowedCalls.count { it == "ru.ozero.app" })
    }

    @Test
    fun blocklistDoesNotAddIpv6Route() {
        val b = recordingBuilder()
        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.BLOCKLIST, blocklist = setOf("com.x")),
        )
        assertEquals(0, b.routeCalls.count { it == "::" to 0 })
    }

    @Test
    fun allowlistAddsRouteAndPackages() {
        val b = recordingBuilder()
        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.ALLOWLIST,
                allowlist = setOf("com.example.browser", "com.example.app"),
            ),
        )
        assertEquals(1, b.routeCalls.count { it == "0.0.0.0" to 0 })
        assertEquals(1, b.allowedCalls.count { it == "com.example.browser" })
        assertEquals(1, b.allowedCalls.count { it == "com.example.app" })
    }

    @Test
    fun allowlistDefaultIncludesSelf() {
        val b = recordingBuilder()
        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.ALLOWLIST,
                allowlist = setOf("ru.ozero.app", "com.example.x"),
            ),
        )
        assertEquals(1, b.allowedCalls.count { it == "com.example.x" })
        assertEquals(1, b.allowedCalls.count { it == "ru.ozero.app" })
    }

    @Test
    fun blocklistDefaultDoesNotAddSelfToDisallowed() {
        val b = recordingBuilder()
        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.BLOCKLIST,
                blocklist = setOf("com.bank.app"),
            ),
        )
        assertEquals(1, b.routeCalls.count { it == "0.0.0.0" to 0 })
        assertEquals(1, b.disallowedCalls.count { it == "com.bank.app" })
        assertEquals(0, b.disallowedCalls.count { it == "ru.ozero.app" })
    }

    @Test
    fun allowlistIgnoresPackageNotFoundAndContinuesWithOtherPackages() {
        val b = recordingBuilder(failedAllowed = setOf("missing.pkg"))

        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.ALLOWLIST,
                allowlist = setOf("missing.pkg", "ok.pkg"),
            ),
        )

        assertEquals(1, b.allowedCalls.count { it == "missing.pkg" })
        assertEquals(1, b.allowedCalls.count { it == "ok.pkg" })
    }

    @Test
    fun allowlistEmptySetIncludesOnlySelf() {
        val b = recordingBuilder()
        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.ALLOWLIST, allowlist = emptySet()))
        assertEquals(1, b.routeCalls.count { it == "0.0.0.0" to 0 })
        assertEquals(1, b.allowedCalls.count { it == "ru.ozero.app" })
    }

    @Test
    fun allowlistExcludeSelfTrueSkipsSelf() {
        val b = recordingBuilder()
        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.ALLOWLIST,
                allowlist = setOf("ru.ozero.app", "com.example.x"),
            ),
            excludeSelf = true,
        )
        assertEquals(0, b.allowedCalls.count { it == "ru.ozero.app" })
        assertEquals(1, b.allowedCalls.count { it == "com.example.x" })
    }

    @Test
    fun allModeExcludeSelfTrueAddsDisallowed() {
        val b = recordingBuilder()
        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.ALL), excludeSelf = true)
        assertEquals(1, b.disallowedCalls.count { it == "ru.ozero.app" })
    }

    @Test
    fun bypassLanExcludeSelfTrueAddsDisallowed() {
        val b = recordingBuilder()
        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.BYPASS_LAN), excludeSelf = true)
        assertEquals(1, b.disallowedCalls.count { it == "ru.ozero.app" })
    }

    @Test
    fun blocklistExcludeSelfTrueAddsSelfToDisallowed() {
        val b = recordingBuilder()
        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.BLOCKLIST, blocklist = setOf("com.bank.app")),
            excludeSelf = true,
        )
        assertEquals(1, b.disallowedCalls.count { it == "ru.ozero.app" })
        assertEquals(1, b.disallowedCalls.count { it == "com.bank.app" })
    }

    @Test
    fun blocklistExcludeSelfTrueContinuesWhenSelfAddFails() {
        val b = recordingBuilder(failedDisallowed = setOf("ru.ozero.app"))

        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.BLOCKLIST, blocklist = setOf("ok.pkg")),
            excludeSelf = true,
        )

        assertEquals(1, b.disallowedCalls.count { it == "ru.ozero.app" })
        assertEquals(1, b.disallowedCalls.count { it == "ok.pkg" })
    }

    @Test
    fun allowlistExcludeSelfTrueWithOnlySelfAddsSelfAsKillAllFallback() {
        val b = recordingBuilder()

        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.ALLOWLIST, allowlist = setOf("ru.ozero.app")),
            excludeSelf = true,
        )

        assertEquals(1, b.routeCalls.count { it == "0.0.0.0" to 0 })
        assertEquals(1, b.allowedCalls.count { it == "ru.ozero.app" })
        assertEquals(emptyList(), b.disallowedCalls)
    }

    @Test
    fun allowlistExcludeSelfTrueDoesNotFallbackWhenOtherPackagesSucceed() {
        val b = recordingBuilder()

        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.ALLOWLIST,
                allowlist = setOf("ru.ozero.app", "com.example.one", "com.example.two"),
            ),
            excludeSelf = true,
        )

        assertEquals(1, b.allowedCalls.count { it == "com.example.one" })
        assertEquals(1, b.allowedCalls.count { it == "com.example.two" })
        assertEquals(0, b.allowedCalls.count { it == "ru.ozero.app" })
    }

    @Test
    fun allowlistExcludeSelfTrueWithEmptySetAddsSelfAsKillAllFallback() {
        val b = recordingBuilder()

        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.ALLOWLIST, allowlist = emptySet()),
            excludeSelf = true,
        )

        assertEquals(1, b.allowedCalls.count { it == "ru.ozero.app" })
    }

    @Test
    fun allowlistDefaultFallsBackToSelfWhenAllPackagesFail() {
        val b = recordingBuilder(failedAllowed = setOf("bad.pkg"))

        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.ALLOWLIST, allowlist = setOf("bad.pkg")),
        )

        assertEquals(1, b.allowedCalls.count { it == "bad.pkg" })
        assertEquals(1, b.allowedCalls.count { it == "ru.ozero.app" })
    }

    @Test
    fun allowlistExcludeSelfTrueWithAllPackagesFailUsesSelfKillAllFallback() {
        val b = recordingBuilder(failedAllowed = setOf("bad.pkg"))

        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.ALLOWLIST, allowlist = setOf("bad.pkg")),
            excludeSelf = true,
        )

        assertEquals(1, b.allowedCalls.count { it == "bad.pkg" })
        assertEquals(1, b.allowedCalls.count { it == "ru.ozero.app" })
    }

    @Test
    fun allowlistExcludeSelfTrueDoesNotFallbackWhenAnyNonSelfPackageAdded() {
        val b = recordingBuilder()

        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.ALLOWLIST,
                allowlist = setOf("ru.ozero.app", "com.example.one", "com.example.two"),
            ),
            excludeSelf = true,
        )

        assertEquals(1, b.allowedCalls.count { it == "com.example.one" })
        assertEquals(1, b.allowedCalls.count { it == "com.example.two" })
        assertEquals(0, b.allowedCalls.count { it == "ru.ozero.app" })
    }

    @Test
    fun blocklistSkipsSelfEvenWhenPresentInConfig() {
        val b = recordingBuilder()

        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.BLOCKLIST,
                blocklist = setOf("ru.ozero.app", "com.bank.app", "com.chat.app"),
            ),
        )

        assertEquals(0, b.disallowedCalls.count { it == "ru.ozero.app" })
        assertEquals(1, b.disallowedCalls.count { it == "com.bank.app" })
        assertEquals(1, b.disallowedCalls.count { it == "com.chat.app" })
    }

    @Test
    fun blocklistGracefullyIgnoresPackageNotFoundAndContinues() {
        val b = recordingBuilder(failedDisallowed = setOf("missing.pkg"))

        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.BLOCKLIST,
                blocklist = setOf("missing.pkg", "ok.pkg"),
            ),
        )

        assertEquals(1, b.disallowedCalls.count { it == "missing.pkg" })
        assertEquals(1, b.disallowedCalls.count { it == "ok.pkg" })
    }

    @Test
    fun blocklistExcludeSelfTrueContinuesWhenSelfExclusionFails() {
        val b = recordingBuilder(failedDisallowed = setOf("ru.ozero.app"))

        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.BLOCKLIST, blocklist = setOf("ok.pkg")),
            excludeSelf = true,
        )

        assertEquals(1, b.disallowedCalls.count { it == "ru.ozero.app" })
        assertEquals(1, b.disallowedCalls.count { it == "ok.pkg" })
    }

    @Test
    fun allModeExcludeSelfTrueContinuesWhenSelfExclusionFails() {
        val b = recordingBuilder(failedDisallowed = setOf("ru.ozero.app"))

        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.ALL), excludeSelf = true)

        assertEquals(1, b.routeCalls.count { it == "0.0.0.0" to 0 })
        assertEquals(1, b.disallowedCalls.count { it == "ru.ozero.app" })
    }

    @Test
    fun bypassLanWithoutExcludeSelfDoesNotTouchApplications() {
        val b = recordingBuilder()

        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.BYPASS_LAN), excludeSelf = false)

        assertEquals(emptyList(), b.allowedCalls)
        assertEquals(emptyList(), b.disallowedCalls)
    }

    @Test
    fun gracefullyIgnoresPackageNotFound() {
        val b = recordingBuilder(failedAllowed = setOf("missing.pkg"))
        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.ALLOWLIST,
                allowlist = setOf("missing.pkg", "ok.pkg"),
            ),
        )
        assertEquals(1, b.allowedCalls.count { it == "missing.pkg" })
        assertEquals(1, b.allowedCalls.count { it == "ok.pkg" })
    }

    @Test
    fun defaultAllModeNeverExcludesSelfFromTun() {
        val b = recordingBuilder()

        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.ALL))

        assertFalse("ru.ozero.app" in b.disallowedCalls)
    }

    @Test
    fun defaultBypassLanModeNeverExcludesSelfFromTun() {
        val b = recordingBuilder()

        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.BYPASS_LAN))

        assertFalse("ru.ozero.app" in b.disallowedCalls)
    }

    @Test
    fun defaultAllowlistModeNeverExcludesSelfFromTun() {
        val b = recordingBuilder()

        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.ALLOWLIST, allowlist = setOf("com.bank.app")),
        )

        assertFalse("ru.ozero.app" in b.disallowedCalls)
    }

    @Test
    fun defaultBlocklistModeNeverExcludesSelfFromTun() {
        val b = recordingBuilder()

        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.BLOCKLIST, blocklist = setOf("com.bank.app")),
        )

        assertFalse("ru.ozero.app" in b.disallowedCalls)
    }

    private fun recordingBuilder(
        failedAllowed: Set<String> = emptySet(),
        failedDisallowed: Set<String> = emptySet(),
    ) = RecordingBuilder(failedAllowed, failedDisallowed)

    private class RecordingBuilder(
        private val failedAllowed: Set<String>,
        private val failedDisallowed: Set<String>,
    ) : TunBuilder {
        val routeCalls = mutableListOf<Pair<String, Int>>()
        val allowedCalls = mutableListOf<String>()
        val disallowedCalls = mutableListOf<String>()

        override fun addRoute(address: String, prefixLength: Int): TunBuilder {
            routeCalls += address to prefixLength
            return this
        }

        override fun addAllowedApplication(packageName: String): TunBuilder {
            allowedCalls += packageName
            if (packageName in failedAllowed) throw PackageManager.NameNotFoundException()
            return this
        }

        override fun addDisallowedApplication(packageName: String): TunBuilder {
            disallowedCalls += packageName
            if (packageName in failedDisallowed) throw PackageManager.NameNotFoundException()
            return this
        }
    }
}
