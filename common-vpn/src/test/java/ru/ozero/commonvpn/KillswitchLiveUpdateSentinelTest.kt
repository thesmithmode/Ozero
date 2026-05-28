package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class KillswitchLiveUpdateSentinelTest {

    private val serviceSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt")
        assertTrue(f.exists(), "OzeroVpnService.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `onCreate подписывается на settings flow для live update killswitchCached`() {
        assertTrue(
            serviceSource.contains("observeKillswitchSetting()"),
            "onCreate обязан вызывать observeKillswitchSetting() — иначе UI toggle mid-session не пробрасывается.",
        )
    }

    @Test
    fun `observeKillswitchSetting использует settingsRepository settings + distinctUntilChanged + launchIn`() {
        val body = serviceSource.substringAfter("private fun observeKillswitchSetting()")
            .substringBefore("companion object")
        assertTrue(body.contains("settingsRepository.settings"), "источник — settingsRepository.settings flow")
        assertTrue(
            body.contains("it.trafficMode == TrafficMode.TUN && it.killswitchEnabled"),
            "извлекает effective killswitch",
        )
        assertTrue(body.contains("distinctUntilChanged()"), "distinctUntilChanged — без шумных перезаписей")
        assertTrue(body.contains("killswitchCached = enabled"), "запись в @Volatile cache")
        assertTrue(body.contains("launchIn(serviceScope)"), "subscription живёт в serviceScope")
    }
}
