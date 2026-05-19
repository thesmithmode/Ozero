package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TunBuilderHelperContractTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/TunBuilderHelper.kt")
        assertTrue(f.exists(), "TunBuilderHelper.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `helper exposes TUN_ADDRESS, TUN_PREFIX_LENGTH, TUN_ADDRESS_V6, TUN_PREFIX_LENGTH_V6, TUN_DNS_SERVERS`() {
        assertEquals("10.10.10.10", TunBuilderHelper.TUN_ADDRESS)
        assertEquals(32, TunBuilderHelper.TUN_PREFIX_LENGTH)
        assertTrue(TunBuilderHelper.TUN_ADDRESS_V6.startsWith("fd"))
        assertEquals(128, TunBuilderHelper.TUN_PREFIX_LENGTH_V6)
        assertTrue(TunBuilderHelper.TUN_DNS_SERVERS.isNotEmpty(), "DNS servers list не должен быть пустым")
    }

    @Test
    fun `helper имеет applyEngineTunSpec, buildTunBuilder, applyLockdown, blackholeIpv6 методы`() {
        assertTrue(source.contains("fun applyEngineTunSpec("), "applyEngineTunSpec обязан существовать")
        assertTrue(source.contains("fun buildTunBuilder("), "buildTunBuilder обязан существовать")
        assertTrue(source.contains("private fun applyLockdown("), "applyLockdown обязан быть private fun")
        assertTrue(source.contains("private fun blackholeIpv6("), "blackholeIpv6 обязан быть private fun")
    }

    @Test
    fun `applyEngineTunSpec возвращает VpnService Builder и принимает spec + ipv6Enabled`() {
        val sig = source.substringAfter("fun applyEngineTunSpec(").substringBefore("): VpnService.Builder")
        assertTrue(sig.contains("spec: TunSpec"), "applyEngineTunSpec принимает spec: TunSpec")
        assertTrue(sig.contains("ipv6Enabled: Boolean"), "applyEngineTunSpec принимает ipv6Enabled")
    }

    @Test
    fun `buildTunBuilder имеет default splitConfig + ipv6Enabled=false + customDnsServers=empty`() {
        val sig = source.substringAfter("fun buildTunBuilder(").substringBefore("): VpnService.Builder")
        assertTrue(
            sig.contains("splitConfig: SplitTunnelConfig = SplitTunnelConfig()"),
            "splitConfig обязан иметь default SplitTunnelConfig()",
        )
        assertTrue(
            sig.contains("ipv6Enabled: Boolean = false"),
            "ipv6Enabled обязан иметь default false — backward compat для killswitch TUN startup",
        )
        assertTrue(
            sig.contains("customDnsServers: List<String> = emptyList()"),
            "customDnsServers обязан иметь default empty — fallback к TUN_DNS_SERVERS",
        )
    }

    @Test
    fun `applyLockdown gated на LOLLIPOP_MR1 — setUnderlyingNetworks API доступен с 22+`() {
        val body = source.substringAfter("private fun applyLockdown").substringBefore("private fun blackholeIpv6")
        assertTrue(
            body.contains("LOLLIPOP_MR1"),
            "applyLockdown обязан проверять SDK_INT >= LOLLIPOP_MR1 — setUnderlyingNetworks API22+",
        )
    }

    @Test
    fun `applyLockdown runCatching - не валит TUN builder при отказе API`() {
        val body = source.substringAfter("private fun applyLockdown").substringBefore("private fun blackholeIpv6")
        assertTrue(
            body.contains("runCatching"),
            "applyLockdown обязан runCatching — vendor ROMs могут throw на setUnderlyingNetworks",
        )
        assertTrue(
            body.contains("PersistentLoggers.warn"),
            "Failure обязан попадать в boot.log через PersistentLoggers.warn",
        )
    }

    @Test
    fun `blackholeIpv6 добавляет ULA address fd00 и route default`() {
        val body = source.substringAfter("private fun blackholeIpv6").substringBefore("companion object")
        assertTrue(body.contains("addAddress(TUN_ADDRESS_V6"), "blackhole обязан addAddress IPv6")
        assertTrue(body.contains("addRoute(\"::\", 0)"), "blackhole обязан addRoute :: 0")
    }

    @Test
    fun `buildTunBuilder использует customDnsServers если непустой иначе TUN_DNS_SERVERS`() {
        val body = source.substringAfter("fun buildTunBuilder(").substringBefore("private fun applyLockdown")
        assertTrue(
            body.contains("if (customDnsServers.isNotEmpty()) customDnsServers else TUN_DNS_SERVERS"),
            "custom DNS должен иметь приоритет, fallback к TUN_DNS_SERVERS",
        )
        assertTrue(
            body.contains(".take(1)"),
            "ByeDPI pipeline parity: ровно один DNS — upstream ByeByeDPI добавляет 1, " +
                "лишние DNS дублируют lookup и тормозят resolve через TUN",
        )
    }

    @Test
    fun `buildTunBuilder НЕ ставит setMtu — TUN link MTU независим от lwIP YAML mtu`() {
        val body = source.substringAfter("fun buildTunBuilder(").substringBefore("private fun applyLockdown")
        assertTrue(
            !body.contains(".setMtu("),
            "buildTunBuilder обязан НЕ вызывать setMtu — ByeByeDPI 1.7.4 reference этого не делает. " +
                "Android default ~1500 link MTU. lwIP YAML mtu=8500 = internal buffer cap, " +
                "никак не связан с TUN link MTU. Регрессия 89a6ecf3 (v0.1.6) откачена в v0.1.8.",
        )
    }

    @Test
    fun `buildTunBuilder вызывает TunBuilderConfigurator с excludeSelf=true и service packageName`() {
        val body = source.substringAfter("fun buildTunBuilder(").substringBefore("private fun applyLockdown")
        assertTrue(
            body.contains("TunBuilderConfigurator(service.packageName)"),
            "configurator обязан брать packageName из переданного VpnService — иначе self-исключение не сработает",
        )
        assertTrue(body.contains("excludeSelf = true"), "excludeSelf=true для всех движков")
    }

    @Test
    fun `applyEngineTunSpec RFC1918 excludeRoute только на TIRAMISU+ и при excludeRfc1918=true`() {
        val body = source.substringAfter("fun applyEngineTunSpec(").substringBefore("fun buildTunBuilder(")
        assertTrue(
            body.contains("TIRAMISU && spec.excludeRfc1918"),
            "RFC1918 exclude требует TIRAMISU API (33+) И spec.excludeRfc1918=true — иначе fallback на полную 0.0.0.0/0",
        )
        assertTrue(body.contains("\"10.0.0.0\""), "должен включать 10.0.0.0/8")
        assertTrue(body.contains("\"172.16.0.0\""), "должен включать 172.16.0.0/12")
        assertTrue(body.contains("\"192.168.0.0\""), "должен включать 192.168.0.0/16")
    }

    @Test
    fun `applyEngineTunSpec setMetered false только на Q+ — pre-Q deprecated API`() {
        val body = source.substringAfter("fun applyEngineTunSpec(").substringBefore("fun buildTunBuilder(")
        val qIdx = body.indexOf("VERSION_CODES.Q")
        val metIdx = body.indexOf("setMetered(false)")
        assertTrue(qIdx >= 0 && metIdx >= 0 && qIdx < metIdx, "setMetered(false) обязан быть под Q+ gate")
    }

    @Test
    fun `helper принимает VpnService и не зависит от OzeroVpnService — тестируемость`() {
        val classDecl = source.substringAfter("class TunBuilderHelper").substringBefore("{").trim()
        assertTrue(
            classDecl.contains("private val service: VpnService"),
            "TunBuilderHelper зависит от VpnService (для Builder + packageName), " +
                "не от OzeroVpnService — иначе circular dependency и сложность тестирования",
        )
        assertTrue(
            !source.contains("OzeroVpnService"),
            "TunBuilderHelper не должен ссылаться на OzeroVpnService напрямую — нарушение модульности",
        )
    }
}
