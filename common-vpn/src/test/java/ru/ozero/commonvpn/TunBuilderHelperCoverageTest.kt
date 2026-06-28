package ru.ozero.commonvpn

import android.net.VpnService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import ru.ozero.commonvpn.split.SplitTunnelConfig
import ru.ozero.enginescore.TunSpec
import ru.ozero.enginescore.settings.SplitTunnelMode

class TunBuilderHelperCoverageTest {

    @Test
    fun `applyEngineTunSpec applies base session mtu blocking address dns and default v4 route`() {
        val builder = builder()
        val helper = helper(builder)

        helper.applyEngineTunSpec(
            spec = baseSpec(dnsServers = listOf("1.1.1.1", "8.8.8.8")),
            ipv6Enabled = false,
        )

        verify(exactly = 1) { builder.setSession("engine") }
        verify(exactly = 1) { builder.setMtu(1400) }
        verify(exactly = 1) { builder.setBlocking(true) }
        verify(exactly = 1) { builder.addAddress("10.0.0.2", 32) }
        verify(exactly = 1) { builder.addDnsServer("1.1.1.1") }
        verify(exactly = 1) { builder.addDnsServer("8.8.8.8") }
        verify(exactly = 2) { builder.allowFamily(any()) }
        verify(exactly = 1) { builder.addRoute("0.0.0.0", 0) }
    }

    @Test
    fun `applyEngineTunSpec excludes RFC1918 routes when requested`() {
        val builder = builder()

        helper(builder).applyEngineTunSpec(
            spec = baseSpec(excludeRfc1918 = true),
            ipv6Enabled = false,
        )

        verify(exactly = 1) { builder.addRoute("0.0.0.0", 0) }
    }

    @Test
    fun `applyEngineTunSpec falls back to full default route when RFC1918 exclusions are disabled`() {
        val builder = builder()

        helper(builder).applyEngineTunSpec(
            spec = baseSpec(excludeRfc1918 = false),
            ipv6Enabled = false,
        )

        verify(exactly = 1) { builder.addRoute("0.0.0.0", 0) }
    }

    @Test
    fun `applyEngineTunSpec continues when RFC1918 excludeRoute throws`() {
        val builder = builder()
        every { builder.excludeRoute(any()) } throws IllegalArgumentException("bad route")

        helper(builder).applyEngineTunSpec(
            spec = baseSpec(excludeRfc1918 = true),
            ipv6Enabled = false,
        )

        verify(exactly = 1) { builder.addRoute("0.0.0.0", 0) }
    }

    @Test
    fun `applyEngineTunSpec continues when lockdown or metered calls throw`() {
        val builder = builder()
        every { builder.setUnderlyingNetworks(null) } throws IllegalStateException("underlying")
        every { builder.setMetered(false) } throws IllegalStateException("metered")

        helper(builder).applyEngineTunSpec(
            spec = baseSpec(dnsServers = listOf("1.1.1.1")),
            ipv6Enabled = false,
        )

        verify(exactly = 1) { builder.addRoute("0.0.0.0", 0) }
    }

    @Test
    fun `applyEngineTunSpec uses explicit v4 cidr routes and skips invalid cidrs`() {
        val builder = builder()
        val helper = helper(builder)

        helper.applyEngineTunSpec(
            spec = baseSpec(
                routeAllV4 = false,
                routeCidrsV4 = listOf("8.8.8.0/24", "bad", " /32", "9.9.9.9/not-number"),
            ),
            ipv6Enabled = false,
        )

        verify(exactly = 1) { builder.addRoute("8.8.8.0", 24) }
        verify(exactly = 0) { builder.addRoute("0.0.0.0", 0) }
        verify(exactly = 0) { builder.addRoute("bad", any()) }
        verify(exactly = 0) { builder.addRoute("9.9.9.9", any()) }
    }

    @Test
    fun `applyEngineTunSpec adds ipv6 address default route and cidr routes from spec`() {
        val defaultBuilder = builder()
        helper(defaultBuilder).applyEngineTunSpec(
            spec = baseSpec(
                allowFamilyV6 = true,
                ipv6Address = "fd00::2",
                ipv6PrefixLength = 128,
                routeAllV6 = true,
            ),
            ipv6Enabled = false,
        )

        verify(exactly = 1) { defaultBuilder.addAddress("fd00::2", 128) }
        verify(exactly = 1) { defaultBuilder.addRoute("::", 0) }

        val cidrBuilder = builder()
        helper(cidrBuilder).applyEngineTunSpec(
            spec = baseSpec(
                allowFamilyV6 = true,
                ipv6Address = "fd00::3",
                ipv6PrefixLength = 64,
                routeAllV6 = false,
                routeCidrsV6 = listOf("2001:db8::/32", "bad-v6"),
            ),
            ipv6Enabled = false,
        )

        verify(exactly = 1) { cidrBuilder.addAddress("fd00::3", 64) }
        verify(exactly = 1) { cidrBuilder.addRoute("2001:db8::", 32) }
        verify(exactly = 0) { cidrBuilder.addRoute("::", 0) }
    }

    @Test
    fun `applyEngineTunSpec skips ipv6 address when spec has no ipv6 address`() {
        val builder = builder()

        helper(builder).applyEngineTunSpec(
            spec = baseSpec(allowFamilyV6 = true, ipv6Address = null),
            ipv6Enabled = true,
        )

        verify(exactly = 0) { builder.addAddress("fd00::2", any()) }
        verify(exactly = 0) { builder.addRoute("::", 0) }
    }

    @Test
    fun `applyEngineTunSpec skips ipv6 when allowFamilyV6 is false even if address exists`() {
        val builder = builder()

        helper(builder).applyEngineTunSpec(
            spec = baseSpec(
                allowFamilyV6 = false,
                ipv6Address = "fd00::4",
                ipv6PrefixLength = 128,
                routeAllV6 = true,
            ),
            ipv6Enabled = true,
        )

        verify(exactly = 0) { builder.addAddress("fd00::4", 128) }
        verify(exactly = 0) { builder.addRoute("::", 0) }
    }

    @Test
    fun `buildTunBuilder applies base byedpi address session fallback dns and split config`() {
        val builder = builder()

        helper(builder, packageName = "ru.ozero.app").buildTunBuilder(
            splitConfig = SplitTunnelConfig(mode = SplitTunnelMode.ALL),
        )

        verify(exactly = 1) { builder.addAddress(TunBuilderHelper.TUN_ADDRESS, TunBuilderHelper.TUN_PREFIX_LENGTH) }
        verify(exactly = 1) { builder.setSession("Ozero") }
        verify(exactly = 1) { builder.addDnsServer(TunBuilderHelper.TUN_DNS_SERVERS.first()) }
        verify(exactly = 1) { builder.addRoute("0.0.0.0", 0) }
        verify(exactly = 0) { builder.addDisallowedApplication("ru.ozero.app") }
    }

    @Test
    fun `buildTunBuilder applies lockdown only when requested`() {
        val disabledBuilder = builder()
        helper(disabledBuilder).buildTunBuilder(applyUnderlying = false)

        val enabledBuilder = builder()
        helper(enabledBuilder).buildTunBuilder(applyUnderlying = true)

        verify(exactly = 1) { disabledBuilder.addRoute("0.0.0.0", 0) }
        verify(exactly = 1) { enabledBuilder.addRoute("0.0.0.0", 0) }
    }

    @Test
    fun `buildTunBuilder continues when lockdown and metered calls throw`() {
        val builder = builder()
        every { builder.setUnderlyingNetworks(null) } throws IllegalStateException("underlying")
        every { builder.setMetered(false) } throws IllegalStateException("metered")

        helper(builder).buildTunBuilder(applyUnderlying = true)

        verify(exactly = 1) { builder.addRoute("0.0.0.0", 0) }
    }

    @Test
    fun `buildTunBuilder takes only first custom dns and enables ipv6 route when requested`() {
        val builder = builder()

        helper(builder).buildTunBuilder(
            ipv6Enabled = true,
            customDnsServers = listOf("9.9.9.9", "1.0.0.1"),
        )

        verify(exactly = 1) { builder.addDnsServer("9.9.9.9") }
        verify(exactly = 0) { builder.addDnsServer("1.0.0.1") }
        verify(exactly = 1) {
            builder.addAddress(TunBuilderHelper.TUN_ADDRESS_V6, TunBuilderHelper.TUN_PREFIX_LENGTH_V6)
        }
        verify(exactly = 1) { builder.addRoute("::", 0) }
    }

    @Test
    fun `buildTunBuilder continues when dns fails and does not exclude self`() {
        val builder = builder()
        every { builder.addDnsServer("9.9.9.9") } throws IllegalArgumentException("bad dns")
        helper(builder, packageName = "ru.ozero.app").buildTunBuilder(
            splitConfig = SplitTunnelConfig(mode = SplitTunnelMode.BYPASS_LAN),
            customDnsServers = listOf("9.9.9.9"),
        )

        verify(exactly = 1) { builder.addDnsServer("9.9.9.9") }
        verify(exactly = 0) { builder.addDisallowedApplication("ru.ozero.app") }
        verify(exactly = 1) { builder.addRoute("8.0.0.0", 7) }
    }

    @Test
    fun `buildTunBuilder defaults dns to first public v4 and skips ipv6 when disabled`() {
        val builder = builder()

        helper(builder).buildTunBuilder(
            splitConfig = SplitTunnelConfig(mode = SplitTunnelMode.ALL),
            ipv6Enabled = false,
            customDnsServers = emptyList(),
        )

        verify(exactly = 1) { builder.addDnsServer(TunBuilderHelper.TUN_DNS_SERVERS.first()) }
        verify(exactly = 0) { builder.addAddress(TunBuilderHelper.TUN_ADDRESS_V6, any()) }
        verify(exactly = 0) { builder.addRoute("::", 0) }
    }

    @Test
    fun `applyEngineTunSpec skips invalid dns route and continues with valid route`() {
        val builder = builder()
        every { builder.addDnsServer("bad-dns") } throws IllegalArgumentException("dns")

        helper(builder).applyEngineTunSpec(
            spec = baseSpec(
                dnsServers = listOf("bad-dns", "1.1.1.1"),
                routeAllV4 = false,
                routeCidrsV4 = listOf("8.8.8.0/24"),
            ),
            ipv6Enabled = false,
        )

        verify(exactly = 1) { builder.addDnsServer("bad-dns") }
        verify(exactly = 1) { builder.addDnsServer("1.1.1.1") }
        verify(exactly = 1) { builder.addRoute("8.8.8.0", 24) }
    }

    private fun helper(
        builder: VpnService.Builder,
        packageName: String = "ru.ozero.test",
    ): TunBuilderHelper {
        val service = mockk<VpnService>()
        every { service.packageName } returns packageName
        return TunBuilderHelper(service, builderFactory = { builder })
    }

    private fun builder(): VpnService.Builder = mockk(relaxed = true) {
        every { setSession(any()) } returns this@mockk
        every { setMtu(any()) } returns this@mockk
        every { setBlocking(any()) } returns this@mockk
        every { addAddress(any<String>(), any()) } returns this@mockk
        every { addDnsServer(any<String>()) } returns this@mockk
        every { allowFamily(any()) } returns this@mockk
        every { addRoute(any<String>(), any()) } returns this@mockk
        every { excludeRoute(any()) } returns this@mockk
        every { addAllowedApplication(any()) } returns this@mockk
        every { addDisallowedApplication(any()) } returns this@mockk
        every { setMetered(any()) } returns this@mockk
        every { setUnderlyingNetworks(any()) } returns this@mockk
    }

    private fun baseSpec(
        dnsServers: List<String> = emptyList(),
        allowFamilyV6: Boolean = false,
        ipv6Address: String? = null,
        ipv6PrefixLength: Int = 0,
        routeAllV4: Boolean = true,
        routeAllV6: Boolean = false,
        routeCidrsV4: List<String> = emptyList(),
        routeCidrsV6: List<String> = emptyList(),
        excludeRfc1918: Boolean = false,
    ): TunSpec = TunSpec(
        sessionName = "engine",
        mtu = 1400,
        blocking = true,
        ipv4Address = "10.0.0.2",
        ipv4PrefixLength = 32,
        dnsServers = dnsServers,
        allowFamilyV6 = allowFamilyV6,
        ipv6Address = ipv6Address,
        ipv6PrefixLength = ipv6PrefixLength,
        routeAllV4 = routeAllV4,
        routeAllV6 = routeAllV6,
        routeCidrsV4 = routeCidrsV4,
        routeCidrsV6 = routeCidrsV6,
        excludeRfc1918 = excludeRfc1918,
    )
}
