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

    private val serviceSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt")
        assertTrue(f.exists(), "OzeroVpnService.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `anchors — функции-границы существуют в helper и сервисе`() {
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
            assertTrue(serviceSource.contains(anchor), "Anchor потерян в OzeroVpnService.kt: '$anchor'")
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
    fun `buildTunBuilder вызывает blackholeIpv6 при ipv6Enabled false`() {
        val body = helperSource
            .substringAfter("fun buildTunBuilder(")
            .substringBefore("private fun applyLockdown")
        assertTrue(
            body.contains("blackholeIpv6(builder"),
            "buildTunBuilder обязан вызывать blackholeIpv6 в else-ветке при ipv6Enabled=false для закрытия IPv6 leak",
        )
    }

    @Test
    fun `applyEngineTunSpec вызывает blackholeIpv6 если allowFamilyV6 false`() {
        val body = helperSource
            .substringAfter("fun applyEngineTunSpec(")
            .substringBefore("fun buildTunBuilder(")
        assertTrue(
            body.contains("blackholeIpv6(builder"),
            "applyEngineTunSpec обязан вызывать blackholeIpv6 в else-ветке для engines с allowFamilyV6=false",
        )
    }

    @Test
    fun `applyEngineTunSpec принимает ipv6Enabled параметр и форсит blackhole при false`() {
        val sig = helperSource.substringAfter("fun applyEngineTunSpec(").substringBefore("): VpnService.Builder")
        assertTrue(
            sig.contains("ipv6Enabled"),
            "applyEngineTunSpec обязан иметь параметр ipv6Enabled — без него WARP/URnetwork с " +
                "allowFamilyV6=true в spec проигнорируют пользовательский switch и leak IPv6 наружу",
        )
        val body = helperSource
            .substringAfter("fun applyEngineTunSpec(")
            .substringBefore("fun buildTunBuilder(")
        assertTrue(
            body.contains("if (ipv6Enabled && spec.allowFamilyV6"),
            "IF-ветка обязана начинаться с ipv6Enabled — иначе spec.allowFamilyV6=true (WARP) " +
                "обходит пользовательский запрет IPv6 и пускает CF v6 наружу. Body:\n$body",
        )
    }

    @Test
    fun `establishTunForEngine принимает ipv6Enabled и пропускает в applyEngineTunSpec`() {
        val sig = serviceSource
            .substringAfter("private suspend fun establishTunForEngine")
            .substringBefore("): ParcelFileDescriptor?")
        assertTrue(
            sig.contains("ipv6Enabled"),
            "establishTunForEngine обязан принимать ipv6Enabled чтобы WARP/URnetwork TUN " +
                "уважал пользовательский switch IPv6.",
        )
        val body = serviceSource
            .substringAfter("private suspend fun establishTunForEngine")
            .substringBefore("private fun captureTunIfaceName")
        assertTrue(
            body.contains("applyEngineTunSpec(spec, ipv6Enabled)"),
            "applyEngineTunSpec обязан получать ipv6Enabled из establishTunForEngine. Body:\n$body",
        )
    }
}
