package ru.ozero.commonvpn.split

import android.net.VpnService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.settings.SplitTunnelMode

class TunBuilderConfiguratorTest {

    private val configurator = TunBuilderConfigurator(packageName = "ru.ozero.app")

    private fun mockBuilder(): VpnService.Builder = mockk(relaxed = true)

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
            SplitTunnelConfig(mode = SplitTunnelMode.ALLOWLIST, packages = setOf("com.x")),
        )
        verify(exactly = 0) { b.addRoute("::", 0) }
    }

    @Test
    fun blocklistDoesNotAddIpv6Route() {
        val b = mockBuilder()
        every { b.addDisallowedApplication(any()) } returns b
        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.BLOCKLIST, packages = setOf("com.x")),
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
                packages = setOf("com.example.browser", "com.example.app"),
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
                packages = setOf("ru.ozero.app", "com.example.x"),
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
                packages = setOf("com.bank.app"),
            ),
        )
        verify(exactly = 1) { b.addRoute("0.0.0.0", 0) }
        verify { b.addDisallowedApplication("com.bank.app") }
        verify(exactly = 0) { b.addDisallowedApplication("ru.ozero.app") }
    }

    @Test
    fun allowlistEmptySetIncludesOnlySelf() {
        val b = mockBuilder()
        every { b.addAllowedApplication(any()) } returns b
        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.ALLOWLIST, packages = emptySet()))
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
                packages = setOf("ru.ozero.app", "com.example.x"),
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
            SplitTunnelConfig(mode = SplitTunnelMode.BLOCKLIST, packages = setOf("com.bank.app")),
            excludeSelf = true,
        )
        verify { b.addDisallowedApplication("ru.ozero.app") }
        verify { b.addDisallowedApplication("com.bank.app") }
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
                packages = setOf("missing.pkg", "ok.pkg"),
            ),
        )
        verify(exactly = 1) { b.addAllowedApplication("missing.pkg") }
        verify(exactly = 1) { b.addAllowedApplication("ok.pkg") }
    }

    @Test
    fun defaultBehaviorNeverExcludesSelfFromTun() {
        for (mode in SplitTunnelMode.values()) {
            val b = mockBuilder()
            every { b.addAllowedApplication(any()) } returns b
            every { b.addDisallowedApplication(any()) } returns b
            configurator.apply(b, SplitTunnelConfig(mode = mode, packages = setOf("com.bank.app")))
            verify(exactly = 0) {
                b.addDisallowedApplication("ru.ozero.app")
            }
        }
    }
}
