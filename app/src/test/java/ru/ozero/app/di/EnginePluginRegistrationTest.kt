package ru.ozero.app.di

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class EnginePluginRegistrationTest {

    private val moduleRoot = File(System.getProperty("user.dir") ?: ".")

    private fun module(name: String): String {
        val f = File(moduleRoot, "src/main/java/ru/ozero/app/di/$name")
        assertTrue(f.exists(), "$name не найден: $f")
        return f.readText()
    }

    @Test
    fun `EnginesModule регистрирует ByeDpi через IntoSet EnginePlugin`() {
        val src = module("EnginesModule.kt")
        assertTrue(
            src.contains("@IntoSet") && src.contains(": EnginePlugin"),
            "EnginesModule обязан иметь @IntoSet provider возвращающий EnginePlugin — " +
                "иначе ByeDpi не попадёт в enginePlugins set и routeTrafficForEngine не найдёт движок.",
        )
        assertTrue(
            src.contains("byeDpiEngine: ByeDpiEngine"),
            "Provider обязан принимать ByeDpiEngine как dependency — без этого ByeDpi не привязан к graph.",
        )
    }

    @Test
    fun `UrnetworkModule регистрирует EngineUrnetwork через IntoSet EnginePlugin`() {
        val src = module("UrnetworkModule.kt")
        assertTrue(
            src.contains("@IntoSet") && src.contains(": EnginePlugin"),
            "UrnetworkModule обязан иметь @IntoSet provider возвращающий EnginePlugin — иначе " +
                "EngineUrnetwork не попадёт в enginePlugins set, routeTrafficForEngine не найдёт " +
                "движок и URnetwork получит HEV TProxy fallback вместо attachTun (regression: " +
                "packet pump не подключится → пакеты не пойдут).",
        )
        assertTrue(
            src.contains("configStore = store") &&
                src.contains("sdkBridge = bridge") &&
                src.contains("authService = authService") &&
                src.contains("deviceIdentity = deviceIdentity"),
            "Provider обязан вызывать EngineUrnetwork с четырьмя named-args (configStore, sdkBridge, " +
                "authService, deviceIdentity) — иначе walletAuth не подключён к DI graph и per-device " +
                "регистрация выпадет в guest fallback.",
        )
    }

    @Test
    fun `WarpModule регистрирует EngineWarp через IntoSet EnginePlugin`() {
        val src = module("WarpModule.kt")
        assertTrue(
            src.contains("@IntoSet") && src.contains(": EnginePlugin"),
            "WarpModule обязан иметь @IntoSet provider возвращающий EnginePlugin — без этого " +
                "Warp engine отсутствует в enginePlugins, routeTrafficForEngine fallback на HEV " +
                "= неправильный routing для WireGuard трафика.",
        )
    }

    @Test
    fun `все модули InstallIn SingletonComponent`() {
        listOf("EnginesModule.kt", "UrnetworkModule.kt", "WarpModule.kt").forEach { name ->
            val src = module(name)
            assertTrue(
                src.contains("@InstallIn(SingletonComponent::class)"),
                "$name обязан быть @InstallIn(SingletonComponent) — engine plugins живут весь lifecycle " +
                    "приложения, не привязаны к Activity/Fragment scope.",
            )
        }
    }

    @Test
    fun `EngineUrnetwork implements EnginePlugin AND TunFdAcceptor`() {
        val src = File(
            moduleRoot,
            "../engine-urnetwork/src/main/java/ru/ozero/engineurnetwork/EngineUrnetwork.kt",
        ).readText()
        assertTrue(
            src.contains("EnginePlugin, TunFdAcceptor") ||
                src.contains("TunFdAcceptor, EnginePlugin"),
            "EngineUrnetwork обязан implement обе interface'а — без EnginePlugin DI не подцепит, " +
                "без TunFdAcceptor routeTrafficForEngine не вызовет attachTun.",
        )
    }

    @Test
    fun `routeTrafficForEngine ищет engine по EngineId через firstOrNull`() {
        val src = File(
            moduleRoot,
            "../common-vpn/src/main/java/ru/ozero/commonvpn/StartSequenceCoordinator.kt",
        ).readText()
        val routeBlock = src.substringAfter("private suspend fun routeTrafficForEngine")
        assertTrue(
            routeBlock.contains("deps.enginePlugins.firstOrNull { it.id == engineId }"),
            "routeTrafficForEngine обязан использовать enginePlugins.firstOrNull { it.id == engineId } — " +
                "если изменить лookup на по-классу или по hash — DI graph рассыпается без compile error.",
        )
    }
}
