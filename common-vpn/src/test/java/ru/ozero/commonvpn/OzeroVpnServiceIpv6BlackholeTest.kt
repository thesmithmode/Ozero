package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class OzeroVpnServiceIpv6BlackholeTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt")
        assertTrue(f.exists(), "OzeroVpnService.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `blackholeIpv6 helper существует и добавляет address+route`() {
        assertTrue(
            source.contains("private fun blackholeIpv6"),
            "OzeroVpnService обязан иметь private fun blackholeIpv6 для null-routing IPv6 при ipv6Enabled=false",
        )
        val body = source.substringAfter("private fun blackholeIpv6").substringBefore("internal fun buildTunBuilder")
        assertTrue(
            body.contains("addAddress(TUN_ADDRESS_V6"),
            "blackholeIpv6 обязан вызывать addAddress(TUN_ADDRESS_V6) — без IPv6 address на TUN маршрут не активируется",
        )
        assertTrue(
            body.contains("addRoute(\"::\", 0)"),
            "blackholeIpv6 обязан вызывать addRoute('::',0) для перехвата всего IPv6 в TUN",
        )
    }

    @Test
    fun `buildTunBuilder вызывает blackholeIpv6 при ipv6Enabled false`() {
        val body = source
            .substringAfter("internal fun buildTunBuilder")
            .substringBefore("private fun buildNotification")
        assertTrue(
            body.contains("blackholeIpv6(builder"),
            "buildTunBuilder обязан вызывать blackholeIpv6 в else-ветке при ipv6Enabled=false для закрытия IPv6 leak",
        )
    }

    @Test
    fun `applyEngineTunSpec вызывает blackholeIpv6 если allowFamilyV6 false`() {
        val body = source
            .substringAfter("internal fun applyEngineTunSpec")
            .substringBefore("internal fun buildTunBuilder")
        assertTrue(
            body.contains("blackholeIpv6(builder"),
            "applyEngineTunSpec обязан вызывать blackholeIpv6 в else-ветке для engines с allowFamilyV6=false",
        )
    }
}
