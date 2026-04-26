package ru.ozero.commonvpn.split

import android.net.VpnService
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class TunBuilderConfiguratorTest {

    private val configurator = TunBuilderConfigurator(packageName = "ru.ozero.app")

    private fun mockBuilder(): VpnService.Builder = mockk(relaxed = true)

    @Test
    fun allModeAddsDefaultRoute() {
        val b = mockBuilder()
        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.ALL))
        verify(exactly = 1) { b.addRoute("0.0.0.0", 0) }
        verify(exactly = 1) { b.addRoute("::", 0) }
        verify(exactly = 0) { b.addAllowedApplication(any()) }
        verify(exactly = 0) { b.addDisallowedApplication(any()) }
    }

    @Test
    fun bypassLanAddsAllNonPrivateRoutes() {
        val b = mockBuilder()
        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.BYPASS_LAN))
        // Не должно быть default route 0.0.0.0/0 целиком
        verify(exactly = 0) { b.addRoute("0.0.0.0", 0) }
        // Один из ожидаемых routes
        verify { b.addRoute("8.0.0.0", 7) }
        verify { b.addRoute("1.0.0.0", 8) }
        // IPv6 global unicast добавляется (ULA/link-local исключены)
        verify(exactly = 1) { b.addRoute("2000::", 3) }
    }

    @Test
    fun allowlistAddsIpv6DefaultRoute() {
        val b = mockBuilder()
        every { b.addAllowedApplication(any()) } returns b
        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.ALLOWLIST, packages = setOf("com.x")),
        )
        verify(exactly = 1) { b.addRoute("::", 0) }
    }

    @Test
    fun blocklistAddsIpv6DefaultRoute() {
        val b = mockBuilder()
        every { b.addDisallowedApplication(any()) } returns b
        configurator.apply(
            b,
            SplitTunnelConfig(mode = SplitTunnelMode.BLOCKLIST, packages = setOf("com.x")),
        )
        verify(exactly = 1) { b.addRoute("::", 0) }
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
    fun allowlistSkipsOwnPackage() {
        val b = mockBuilder()
        every { b.addAllowedApplication(any()) } returns b
        configurator.apply(
            b,
            SplitTunnelConfig(
                mode = SplitTunnelMode.ALLOWLIST,
                packages = setOf("ru.ozero.app", "com.example.x"),
            ),
        )
        verify(exactly = 0) { b.addAllowedApplication("ru.ozero.app") }
        verify(exactly = 1) { b.addAllowedApplication("com.example.x") }
    }

    @Test
    fun blocklistAddsRouteAndDisallowedPackages() {
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
    }

    @Test
    fun allowlistEmptySetFiltersOnlySelf() {
        val b = mockBuilder()
        every { b.addAllowedApplication(any()) } returns b
        configurator.apply(b, SplitTunnelConfig(mode = SplitTunnelMode.ALLOWLIST, packages = emptySet()))
        verify(exactly = 1) { b.addRoute("0.0.0.0", 0) }
        // Пустой ALLOWLIST = kill-all: добавляется только self-package в фильтр
        // (Android не направляет own VPN traffic в собственный TUN → 0 пакетов через VPN)
        verify(exactly = 1) { b.addAllowedApplication("ru.ozero.app") }
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
        verify { b.addAllowedApplication("ok.pkg") }
        // missing.pkg вызвался но обёрнут в runCatching — тест проверяет что выполнение продолжилось
        confirmVerified()
    }
}
