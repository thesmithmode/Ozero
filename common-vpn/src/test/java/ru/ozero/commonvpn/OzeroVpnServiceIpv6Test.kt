package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class OzeroVpnServiceIpv6Test {

    private val serviceSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt")
        assertTrue(f.exists(), "OzeroVpnService.kt не найден: $f")
        f.readText()
    }

    private val coordinatorSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/StartSequenceCoordinator.kt")
        assertTrue(f.exists(), "StartSequenceCoordinator.kt не найден: $f")
        f.readText()
    }

    private val helperSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/TunBuilderHelper.kt")
        assertTrue(f.exists(), "TunBuilderHelper.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `buildTunBuilder принимает ipv6Enabled параметр`() {
        val sig = helperSource.substringAfter("fun buildTunBuilder(").substringBefore("): VpnService.Builder")
        assertTrue(
            sig.contains("ipv6Enabled"),
            "buildTunBuilder обязан иметь параметр ipv6Enabled для conditional IPv6 routes",
        )
    }

    @Test
    fun `buildTunBuilder добавляет IPv6 route ВСЕГДА - null-route при ipv6Enabled false`() {
        val body = helperSource.substringAfter("fun buildTunBuilder(")
            .substringBefore("private fun applyLockdown")
        assertTrue(
            body.contains("addRoute(\"::\", 0)") || body.contains("addRoute(\"::\",0)"),
            "buildTunBuilder обязан добавлять addRoute IPv6 default (::/0) ВСЕГДА — " +
                "при ipv6Enabled=true для маршрутизации IPv6 трафика, при false для null-route " +
                "чтобы закрыть IPv6-leak (трафик не проходит мимо VPN-туннеля).",
        )
        assertTrue(
            body.contains("if (ipv6Enabled)"),
            "IPv6 address conditional обязателен — addAddress(TUN_ADDRESS_V6) только если enabled",
        )
        assertTrue(
            body.contains("addAddress(TUN_ADDRESS_V6"),
            "buildTunBuilder при ipv6Enabled должен делать addAddress IPv6",
        )
    }

    @Test
    fun `run читает ipv6Enabled из settingsRepository перед buildTunBuilder`() {
        val body = coordinatorSource.substringAfter("suspend fun run()").substringBefore("suspend fun engineNeedsCustomTun")
        val readIdx = body.indexOf("settingsRepository.settings")
        val builderIdx = body.indexOf("tunBuilderHelper.buildTunBuilder(")
        assertTrue(
            readIdx in 0 until builderIdx,
            "run() обязан читать settings ДО buildTunBuilder для передачи ipv6Enabled",
        )
        assertTrue(body.contains("ipv6Enabled"), "run() должен передавать ipv6Enabled в buildTunBuilder")
    }

    @Test
    fun `anchors — функции-границы существуют`() {
        listOf("fun buildTunBuilder(").forEach { anchor ->
            assertTrue(helperSource.contains(anchor), "Anchor потерян в TunBuilderHelper.kt: '$anchor'")
        }
        listOf("private fun startVpn()", "private fun stopVpn()").forEach { anchor ->
            assertTrue(serviceSource.contains(anchor), "Anchor потерян в OzeroVpnService.kt: '$anchor'")
        }
    }

    @Test
    fun `OzeroVpnService инжектит SettingsRepository`() {
        assertTrue(
            serviceSource.contains("@Inject lateinit var settingsRepository"),
            "OzeroVpnService обязан @Inject settingsRepository — SettingsRepository доступен через :engines-core",
        )
    }
}
