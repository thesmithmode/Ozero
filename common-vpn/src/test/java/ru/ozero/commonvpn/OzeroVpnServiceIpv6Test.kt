package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class OzeroVpnServiceIpv6Test {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt")
        assertTrue(f.exists(), "OzeroVpnService.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `buildTunBuilder принимает ipv6Enabled параметр`() {
        val sig = source.substringAfter("internal fun buildTunBuilder").substringBefore("): Builder")
        assertTrue(
            sig.contains("ipv6Enabled"),
            "buildTunBuilder обязан иметь параметр ipv6Enabled для conditional IPv6 routes",
        )
    }

    @Test
    fun `buildTunBuilder добавляет IPv6 route только при ipv6Enabled true`() {
        val body = source.substringAfter("internal fun buildTunBuilder")
            .substringBefore("private fun buildNotification")
        assertTrue(body.contains("if (ipv6Enabled)"), "IPv6 route conditional обязателен")
        assertTrue(
            body.contains("addRoute(\"::\", 0)") || body.contains("addRoute(\"::\",0)"),
            "buildTunBuilder при ipv6Enabled должен делать addRoute IPv6 default",
        )
        assertTrue(
            body.contains("addAddress(TUN_ADDRESS_V6"),
            "buildTunBuilder при ipv6Enabled должен делать addAddress IPv6",
        )
    }

    @Test
    fun `startVpn читает ipv6Enabled из settingsRepository перед buildTunBuilder`() {
        val body = source.substringAfter("private fun startVpn()").substringBefore("private fun stopVpn()")
        val readIdx = body.indexOf("settingsRepository.settings")
        val builderIdx = body.indexOf("buildTunBuilder(")
        assertTrue(
            readIdx in 0 until builderIdx,
            "startVpn обязан читать settings ДО buildTunBuilder для передачи ipv6Enabled",
        )
        assertTrue(body.contains("ipv6Enabled"), "startVpn должен передавать ipv6Enabled в buildTunBuilder")
    }

    @Test
    fun `OzeroVpnService инжектит SettingsRepository`() {
        assertTrue(
            source.contains("@Inject lateinit var settingsRepository"),
            "OzeroVpnService обязан @Inject settingsRepository — SettingsRepository доступен через :engines-core",
        )
    }
}
