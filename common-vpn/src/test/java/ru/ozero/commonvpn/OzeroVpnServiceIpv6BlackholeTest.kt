package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class OzeroVpnServiceIpv6BlackholeTest {

    private val helperSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/TunBuilderHelper.kt")
        assertTrue(f.exists(), "TunBuilderHelper.kt не найден: $f")
        f.readText()
    }

    private val coordinatorSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/StartSequenceCoordinator.kt")
        assertTrue(f.exists(), "StartSequenceCoordinator.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `anchors — функции-границы существуют в helper и coordinator`() {
        listOf(
            "private fun blackholeIpv6",
            "fun buildTunBuilder(",
            "fun applyEngineTunSpec(",
        ).forEach { anchor ->
            assertTrue(helperSource.contains(anchor), "Anchor потерян в TunBuilderHelper.kt: '$anchor'")
        }
        listOf(
            "private suspend fun establishTunForEngine(",
            "private fun captureTunIfaceName(",
        ).forEach { anchor ->
            assertTrue(
                coordinatorSource.contains(anchor),
                "Anchor потерян в StartSequenceCoordinator.kt: '$anchor'",
            )
        }
    }

    @Test
    fun `blackholeIpv6 helper существует и добавляет address+route`() {
        assertTrue(
            helperSource.contains("private fun blackholeIpv6"),
            "TunBuilderHelper обязан иметь private fun blackholeIpv6 для null-routing IPv6 при ipv6Enabled=false",
        )
        val body = helperSource.substringAfter("private fun blackholeIpv6").substringBefore("companion object")
        assertTrue(
            body.contains("addAddress(TUN_ADDRESS_V6"),
            "blackholeIpv6 обязан вызывать addAddress(TUN_ADDRESS_V6) — " +
                "без IPv6 address на TUN маршрут не активируется",
        )
        assertTrue(
            body.contains("addRoute(\"::\", 0)"),
            "blackholeIpv6 обязан вызывать addRoute('::',0) для перехвата всего IPv6 в TUN",
        )
    }

    @Test
    fun `buildTunBuilder НЕ blackhole IPv6 — upstream parity`() {
        val body = helperSource
            .substringAfter("fun buildTunBuilder(")
            .substringBefore("private fun applyLockdown")
        assertTrue(
            !body.contains("blackholeIpv6(builder"),
            "buildTunBuilder НЕ должен blackhole IPv6 при ipv6Enabled=false — upstream ByeByeDPI 1.7.4 " +
                "не делает blackhole, hev получает только IPv4 fd, иначе IPv6 пакеты заходят в hev " +
                "и SOCKS upstream (ByeDPI без IPv6) их отбрасывает → traffic stuck.",
        )
    }

    @Test
    fun `applyEngineTunSpec при ipv6Enabled всегда добавляет IPv6 default route`() {
        val body = helperSource
            .substringAfter("fun applyEngineTunSpec(")
            .substringBefore("fun buildTunBuilder(")
        assertTrue(
            body.contains("if (ipv6Enabled)"),
            "applyEngineTunSpec обязан учитывать пользовательский IPv6 switch для full-IP-stack движков.",
        )
        assertTrue(
            body.substringAfter("if (ipv6Enabled)").substringBefore("} else if").contains("addRoute(\"::\", 0)"),
            "applyEngineTunSpec при ipv6Enabled=true обязан перехватывать IPv6 default route.",
        )
    }

    @Test
    fun `applyEngineTunSpec принимает ipv6Enabled параметр`() {
        val sig = helperSource.substringAfter("fun applyEngineTunSpec(").substringBefore("): VpnService.Builder")
        assertTrue(
            sig.contains("ipv6Enabled"),
            "applyEngineTunSpec обязан иметь параметр ipv6Enabled — передаётся из StartSequenceCoordinator",
        )
    }

    @Test
    fun `establishTunForEngine принимает ipv6Enabled и пропускает в applyEngineTunSpec`() {
        val sig = coordinatorSource
            .substringAfter("private suspend fun establishTunForEngine")
            .substringBefore("): ParcelFileDescriptor?")
        assertTrue(
            sig.contains("ipv6Enabled"),
            "establishTunForEngine обязан принимать ipv6Enabled чтобы WARP/URnetwork TUN " +
                "уважал пользовательский switch IPv6.",
        )
        val body = coordinatorSource
            .substringAfter("private suspend fun establishTunForEngine")
            .substringBefore("private fun captureTunIfaceName")
        assertTrue(
            body.contains("applyEngineTunSpec(spec, ipv6Enabled)"),
            "applyEngineTunSpec обязан получать ipv6Enabled из establishTunForEngine. Body:\n$body",
        )
    }
}
