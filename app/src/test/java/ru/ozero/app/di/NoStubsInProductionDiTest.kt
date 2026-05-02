package ru.ozero.app.di

import io.mockk.mockk
import org.junit.jupiter.api.Test
import ru.ozero.enginebyedpi.ByeDpiEngine
import ru.ozero.enginebyedpi.ByeDpiProxy
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoStubsInProductionDiTest {

    private val forbiddenTokens = listOf("Stub", "Fake", "Mock", "Placeholder", "Dummy")

    private val moduleSource: String by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/app/di/EnginesModule.kt")
        assertTrue(f.exists(), "EnginesModule.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `EnginesModule source has no stub-like engine bindings`() {
        val src = moduleSource
        for (tok in forbiddenTokens) {
            assertFalse(
                src.contains("${tok}Engine") || src.contains("${tok}EnginePlugin"),
                "EnginesModule references forbidden engine type containing '$tok'",
            )
        }
    }

    @Test
    fun `EnginesModule source has no TODO or error placeholder bindings`() {
        val src = moduleSource
        val intoSetMethods = Regex(
            """@IntoSet[\s\S]*?fun\s+\w+\([^)]*\)\s*:\s*EnginePlugin\s*=\s*([^\n]+)""",
        ).findAll(src).map { it.groupValues[1].trim() }.toList()

        assertTrue(
            intoSetMethods.isNotEmpty(),
            "EnginesModule должен иметь хотя бы один @IntoSet EnginePlugin binding",
        )
        for (expr in intoSetMethods) {
            assertFalse(
                expr.startsWith("TODO(") || expr.startsWith("error("),
                "EnginePlugin binding не должен возвращать TODO()/error(): '$expr'",
            )
        }
    }

    @Test
    fun `provideByeDpiEngine returns real ByeDpiEngine in production package`() {
        val proxy = mockk<ByeDpiProxy>(relaxed = true)
        val byeDpi = EnginesModule.provideByeDpiEngineDirect(proxy)
        val plugin: EnginePlugin = EnginesModule.provideByeDpiEngine(byeDpi)

        assertEquals(EngineId.BYEDPI, plugin.id)
        assertTrue(
            plugin is ByeDpiEngine,
            "Production binding должен быть ByeDpiEngine, получено: ${plugin::class.qualifiedName}",
        )

        val cls = plugin::class.qualifiedName ?: error("class qualifiedName == null")
        for (tok in forbiddenTokens) {
            assertFalse(
                cls.contains(tok),
                "Engine класс содержит запрещённый токен '$tok': $cls",
            )
        }
        assertTrue(
            cls.startsWith("ru.ozero.engine"),
            "Engine класс должен жить в ru.ozero.engine* пакете, получено: $cls",
        )
    }

    @Test
    fun `provideByeDpiProxy returns real ByeDpiProxy`() {
        val proxy = EnginesModule.provideByeDpiProxy()
        val cls = proxy::class.qualifiedName ?: error("class qualifiedName == null")
        for (tok in forbiddenTokens) {
            assertFalse(
                cls.contains(tok),
                "ByeDpiProxy класс содержит запрещённый токен '$tok': $cls",
            )
        }
        assertEquals("ru.ozero.enginebyedpi.ByeDpiProxy", cls)
    }

    @Test
    fun `UrnetworkModule temporarily binds Stub — TODO марк обязателен`() {
        // v0.0.2-4: temporary revert на StubUrnetworkSdkBridge из-за libgojni.so collision
        // между URnetworkSdk.aar и userwireguard.aar (оба gomobile bind, оба ship libgojni).
        // AGP merge перезаписывает .so → "No implementation found for Sdk._init()".
        // Sentinel этого теста — гарантировать что TODO не потерян: когда AAR rebuilt
        // с merged go-modules, тест должен быть инвертирован обратно (assert Real, not Stub).
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/app/di/UrnetworkModule.kt")
        assertTrue(f.exists(), "UrnetworkModule.kt не найден: $f")
        val src = f.readText()
        assertTrue(
            Regex("""=\s*StubUrnetworkSdkBridge\s*\(""").containsMatchIn(src),
            "v0.0.2-4: UrnetworkModule обязан биндить StubUrnetworkSdkBridge до rebuild AAR. " +
                "Если AAR fixed (один gomobile bind с обоими go-modules) — обнови этот тест " +
                "и DI binding на RealUrnetworkSdkBridge.",
        )
        assertTrue(
            src.contains("TODO(v0.0.2-5)") || src.contains("TODO(v0.0.3)"),
            "UrnetworkModule.provideUrnetworkSdkBridge ОБЯЗАН иметь TODO marker для возврата к Real bridge " +
                "после rebuild AAR. Без TODO забудем восстановить.",
        )
    }

    @Test
    fun `UrnetworkModule binds RealUrnetworkAuthService — not Unimplemented`() {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/app/di/UrnetworkModule.kt")
        assertTrue(f.exists(), "UrnetworkModule.kt не найден: $f")
        val src = f.readText()
        assertTrue(
            src.contains("RealUrnetworkAuthService"),
            "UrnetworkModule обязан биндить RealUrnetworkAuthService — Unimplemented режим запрещён.",
        )
        assertFalse(
            Regex("""=\s*UnimplementedUrnetworkAuthService\s*\(""").containsMatchIn(src),
            "UrnetworkModule не должен инстанциировать UnimplementedUrnetworkAuthService — " +
                "Real service активирован, использует SDK Api через NetworkSpace.",
        )
    }
}
