package ru.ozero.commonvpn.split

import android.net.VpnService
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.settings.SplitTunnelMode

class TunBuilderConfiguratorTest {

    private val configurator = TunBuilderConfigurator(packageName = "ru.ozero.app")

    private fun mockBuilder(): VpnService.Builder = mockk(relaxed = true)

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun allModeAddsDefaultRouteV4Only() {
        val b = mockBuilder()
        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.ALL))
        verify(exactly = 1) { b.addRoute("0.0.0.0", 0) }
        verify(exactly = 0) { b.addRoute("::", 0) }
        verify(exactly = 0) { b.addAllowedApplication(any()) }
        verify(exactly = 0) { b.addDisallowedApplication(any()) }
    }

    @Test
    fun bypassLanAddsV4RoutesOnly() {
        val b = mockBuilder()
        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.BYPASS_LAN))
        verify(exactly = 0) { b.addRoute("0.0.0.0", 0) }
        verify { b.addRoute("8.0.0.0", 7) }
        verify { b.addRoute("1.0.0.0", 8) }
        verify(exactly = 0) { b.addRoute("2000::", 3) }
        verify(exactly = 0) { b.addDisallowedApplication(any()) }
    }

    @Test
    fun allowlistDoesNotAddIpv6Route() {
        val b = mockBuilder()
        every { b.addAllowedApplication(any()) } returns b
        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.ALLOWLIST, allowlist = setOf("com.x")),
        )
        verify(exactly = 0) { b.addRoute("::", 0) }
    }

    @Test
    fun allowlistIncludeSelfTrueSkipsKillAllFallbackAndAddsSelfOnlyOnce() {
        val b = mockBuilder()
        every { b.addAllowedApplication(any()) } returns b

        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.ALLOWLIST,
                allowlist = setOf("ru.ozero.app", "com.example.one"),
            ),
            excludeSelf = false,
        )

        verify(exactly = 1) { b.addAllowedApplication("com.example.one") }
        verify(exactly = 1) { b.addAllowedApplication("ru.ozero.app") }
    }

    @Test
    fun allowlistIncludeSelfTrueContinuesWhenSelfAddFails() {
        val b = mockBuilder()
        every { b.addAllowedApplication("com.example.one") } returns b
        every {
            b.addAllowedApplication("ru.ozero.app")
        } throws android.content.pm.PackageManager.NameNotFoundException()

        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.ALLOWLIST,
                allowlist = setOf("ru.ozero.app", "com.example.one"),
            ),
            excludeSelf = false,
        )

        verify(exactly = 1) { b.addAllowedApplication("com.example.one") }
        verify(exactly = 1) { b.addAllowedApplication("ru.ozero.app") }
    }

    @Test
    fun blocklistDoesNotAddIpv6Route() {
        val b = mockBuilder()
        every { b.addDisallowedApplication(any()) } returns b
        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.BLOCKLIST, blocklist = setOf("com.x")),
        )
        verify(exactly = 0) { b.addRoute("::", 0) }
    }

    @Test
    fun allowlistAddsRouteAndPackages() {
        val b = mockBuilder()
        every { b.addAllowedApplication(any()) } returns b
        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.ALLOWLIST,
                allowlist = setOf("com.example.browser", "com.example.app"),
            ),
        )
        verify(exactly = 1) { b.addRoute("0.0.0.0", 0) }
        verify { b.addAllowedApplication("com.example.browser") }
        verify { b.addAllowedApplication("com.example.app") }
    }

    @Test
    fun allowlistDefaultIncludesSelf() {
        val b = mockBuilder()
        every { b.addAllowedApplication(any()) } returns b
        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.ALLOWLIST,
                allowlist = setOf("ru.ozero.app", "com.example.x"),
            ),
        )
        verify(exactly = 1) { b.addAllowedApplication("com.example.x") }
        verify { b.addAllowedApplication("ru.ozero.app") }
    }

    @Test
    fun blocklistDefaultDoesNotAddSelfToDisallowed() {
        val b = mockBuilder()
        every { b.addDisallowedApplication(any()) } returns b
        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.BLOCKLIST,
                blocklist = setOf("com.bank.app"),
            ),
        )
        verify(exactly = 1) { b.addRoute("0.0.0.0", 0) }
        verify { b.addDisallowedApplication("com.bank.app") }
        verify(exactly = 0) { b.addDisallowedApplication("ru.ozero.app") }
    }

    @Test
    fun allowlistIgnoresPackageNotFoundAndContinuesWithOtherPackages() {
        val b = mockBuilder()
        every {
            b.addAllowedApplication("missing.pkg")
        } throws android.content.pm.PackageManager.NameNotFoundException()
        every { b.addAllowedApplication("ok.pkg") } returns b

        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.ALLOWLIST,
                allowlist = setOf("missing.pkg", "ok.pkg"),
            ),
        )

        verify(exactly = 1) { b.addAllowedApplication("missing.pkg") }
        verify(exactly = 1) { b.addAllowedApplication("ok.pkg") }
    }

    @Test
    fun allowlistEmptySetIncludesOnlySelf() {
        val b = mockBuilder()
        every { b.addAllowedApplication(any()) } returns b
        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.ALLOWLIST, allowlist = emptySet()))
        verify(exactly = 1) { b.addRoute("0.0.0.0", 0) }
        verify(exactly = 1) { b.addAllowedApplication("ru.ozero.app") }
    }

    @Test
    fun allowlistExcludeSelfTrueSkipsSelf() {
        val b = mockBuilder()
        every { b.addAllowedApplication(any()) } returns b
        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.ALLOWLIST,
                allowlist = setOf("ru.ozero.app", "com.example.x"),
            ),
            excludeSelf = true,
        )
        verify(exactly = 0) { b.addAllowedApplication("ru.ozero.app") }
        verify(exactly = 1) { b.addAllowedApplication("com.example.x") }
    }

    @Test
    fun allModeExcludeSelfTrueAddsDisallowed() {
        val b = mockBuilder()
        every { b.addDisallowedApplication(any()) } returns b
        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.ALL), excludeSelf = true)
        verify(exactly = 1) { b.addDisallowedApplication("ru.ozero.app") }
    }

    @Test
    fun bypassLanExcludeSelfTrueAddsDisallowed() {
        val b = mockBuilder()
        every { b.addDisallowedApplication(any()) } returns b
        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.BYPASS_LAN), excludeSelf = true)
        verify(exactly = 1) { b.addDisallowedApplication("ru.ozero.app") }
    }

    @Test
    fun blocklistExcludeSelfTrueAddsSelfToDisallowed() {
        val b = mockBuilder()
        every { b.addDisallowedApplication(any()) } returns b
        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.BLOCKLIST, blocklist = setOf("com.bank.app")),
            excludeSelf = true,
        )
        verify { b.addDisallowedApplication("ru.ozero.app") }
        verify { b.addDisallowedApplication("com.bank.app") }
    }

    @Test
    fun blocklistExcludeSelfTrueContinuesWhenSelfAddFails() {
        val b = mockBuilder()
        every { b.addDisallowedApplication("ok.pkg") } returns b
        every {
            b.addDisallowedApplication("ru.ozero.app")
        } throws android.content.pm.PackageManager.NameNotFoundException()

        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.BLOCKLIST, blocklist = setOf("ok.pkg")),
            excludeSelf = true,
        )

        verify(exactly = 1) { b.addDisallowedApplication("ru.ozero.app") }
        verify(exactly = 1) { b.addDisallowedApplication("ok.pkg") }
    }

    @Test
    fun allowlistExcludeSelfTrueWithOnlySelfAddsSelfAsKillAllFallback() {
        val b = mockBuilder()
        every { b.addAllowedApplication(any()) } returns b

        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.ALLOWLIST, allowlist = setOf("ru.ozero.app")),
            excludeSelf = true,
        )

        verify(exactly = 1) { b.addRoute("0.0.0.0", 0) }
        verify(exactly = 1) { b.addAllowedApplication("ru.ozero.app") }
        verify(exactly = 0) { b.addDisallowedApplication(any()) }
    }

    @Test
    fun allowlistExcludeSelfTrueDoesNotFallbackWhenOtherPackagesSucceed() {
        val b = mockBuilder()
        every { b.addAllowedApplication(any()) } returns b

        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.ALLOWLIST,
                allowlist = setOf("ru.ozero.app", "com.example.one", "com.example.two"),
            ),
            excludeSelf = true,
        )

        verify(exactly = 1) { b.addAllowedApplication("com.example.one") }
        verify(exactly = 1) { b.addAllowedApplication("com.example.two") }
        verify(exactly = 0) { b.addAllowedApplication("ru.ozero.app") }
    }

    @Test
    fun allowlistExcludeSelfTrueWithEmptySetAddsSelfAsKillAllFallback() {
        val b = mockBuilder()
        every { b.addAllowedApplication(any()) } returns b

        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.ALLOWLIST, allowlist = emptySet()),
            excludeSelf = true,
        )

        verify(exactly = 1) { b.addAllowedApplication("ru.ozero.app") }
    }

    @Test
    fun allowlistDefaultFallsBackToSelfWhenAllPackagesFail() {
        val b = mockBuilder()
        every { b.addAllowedApplication("bad.pkg") } throws android.content.pm.PackageManager.NameNotFoundException()
        every { b.addAllowedApplication("ru.ozero.app") } returns b

        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.ALLOWLIST, allowlist = setOf("bad.pkg")),
        )

        verify(exactly = 1) { b.addAllowedApplication("bad.pkg") }
        verify(exactly = 1) { b.addAllowedApplication("ru.ozero.app") }
    }

    @Test
    fun allowlistExcludeSelfTrueWithAllPackagesFailUsesSelfKillAllFallback() {
        val b = mockBuilder()
        every { b.addAllowedApplication("bad.pkg") } throws android.content.pm.PackageManager.NameNotFoundException()
        every { b.addAllowedApplication("ru.ozero.app") } returns b

        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.ALLOWLIST, allowlist = setOf("bad.pkg")),
            excludeSelf = true,
        )

        verify(exactly = 1) { b.addAllowedApplication("bad.pkg") }
        verify(exactly = 1) { b.addAllowedApplication("ru.ozero.app") }
    }

    @Test
    fun allowlistExcludeSelfTrueDoesNotFallbackWhenAnyNonSelfPackageAdded() {
        val b = mockBuilder()
        every { b.addAllowedApplication(any()) } returns b

        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.ALLOWLIST,
                allowlist = setOf("ru.ozero.app", "com.example.one", "com.example.two"),
            ),
            excludeSelf = true,
        )

        verify(exactly = 1) { b.addAllowedApplication("com.example.one") }
        verify(exactly = 1) { b.addAllowedApplication("com.example.two") }
        verify(exactly = 0) { b.addAllowedApplication("ru.ozero.app") }
    }

    @Test
    fun blocklistSkipsSelfEvenWhenPresentInConfig() {
        val b = mockBuilder()
        every { b.addDisallowedApplication(any()) } returns b

        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.BLOCKLIST,
                blocklist = setOf("ru.ozero.app", "com.bank.app", "com.chat.app"),
            ),
        )

        verify(exactly = 0) { b.addDisallowedApplication("ru.ozero.app") }
        verify(exactly = 1) { b.addDisallowedApplication("com.bank.app") }
        verify(exactly = 1) { b.addDisallowedApplication("com.chat.app") }
    }

    @Test
    fun blocklistGracefullyIgnoresPackageNotFoundAndContinues() {
        val b = mockBuilder()
        every {
            b.addDisallowedApplication("missing.pkg")
        } throws android.content.pm.PackageManager.NameNotFoundException()
        every { b.addDisallowedApplication("ok.pkg") } returns b

        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.BLOCKLIST,
                blocklist = setOf("missing.pkg", "ok.pkg"),
            ),
        )

        verify(exactly = 1) { b.addDisallowedApplication("missing.pkg") }
        verify(exactly = 1) { b.addDisallowedApplication("ok.pkg") }
    }

    @Test
    fun blocklistExcludeSelfTrueContinuesWhenSelfExclusionFails() {
        val b = mockBuilder()
        every {
            b.addDisallowedApplication("ru.ozero.app")
        } throws android.content.pm.PackageManager.NameNotFoundException()
        every { b.addDisallowedApplication("ok.pkg") } returns b

        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.BLOCKLIST, blocklist = setOf("ok.pkg")),
            excludeSelf = true,
        )

        verify(exactly = 1) { b.addDisallowedApplication("ru.ozero.app") }
        verify(exactly = 1) { b.addDisallowedApplication("ok.pkg") }
    }

    @Test
    fun allModeExcludeSelfTrueContinuesWhenSelfExclusionFails() {
        val b = mockBuilder()
        every {
            b.addDisallowedApplication("ru.ozero.app")
        } throws android.content.pm.PackageManager.NameNotFoundException()

        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.ALL), excludeSelf = true)

        verify(exactly = 1) { b.addRoute("0.0.0.0", 0) }
        verify(exactly = 1) { b.addDisallowedApplication("ru.ozero.app") }
    }

    @Test
    fun bypassLanWithoutExcludeSelfDoesNotTouchApplications() {
        val b = mockBuilder()

        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.BYPASS_LAN), excludeSelf = false)

        verify(exactly = 0) { b.addAllowedApplication(any()) }
        verify(exactly = 0) { b.addDisallowedApplication(any()) }
    }

    @Test
    fun gracefullyIgnoresPackageNotFound() {
        val b = mockBuilder()
        every {
            b.addAllowedApplication("missing.pkg")
        } throws android.content.pm.PackageManager.NameNotFoundException()
        every { b.addAllowedApplication("ok.pkg") } returns b
        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.ALLOWLIST,
                allowlist = setOf("missing.pkg", "ok.pkg"),
            ),
        )
        verify(exactly = 1) { b.addAllowedApplication("missing.pkg") }
        verify(exactly = 1) { b.addAllowedApplication("ok.pkg") }
    }

    @Test
    fun defaultAllModeNeverExcludesSelfFromTun() {
        val b = mockBuilder()

        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.ALL))

        verify(exactly = 0) { b.addDisallowedApplication("ru.ozero.app") }
    }

    @Test
    fun defaultBypassLanModeNeverExcludesSelfFromTun() {
        val b = mockBuilder()

        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.BYPASS_LAN))

        verify(exactly = 0) { b.addDisallowedApplication("ru.ozero.app") }
    }

    @Test
    fun defaultAllowlistModeNeverExcludesSelfFromTun() {
        val b = mockBuilder()
        every { b.addAllowedApplication(any()) } returns b

        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.ALLOWLIST, allowlist = setOf("com.bank.app")),
        )

        verify(exactly = 0) { b.addDisallowedApplication("ru.ozero.app") }
    }

    @Test
    fun defaultBlocklistModeNeverExcludesSelfFromTun() {
        val b = mockBuilder()
        every { b.addDisallowedApplication(any()) } returns b

        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.BLOCKLIST, blocklist = setOf("com.bank.app")),
        )

        verify(exactly = 0) { b.addDisallowedApplication("ru.ozero.app") }
    }
}
