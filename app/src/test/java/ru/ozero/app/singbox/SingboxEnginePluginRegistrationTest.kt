package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SingboxEnginePluginRegistrationTest {

    @Test
    fun `should SingboxModule use IntoSet annotation for EnginePlugin binding`() {
        val root = locateRepoRoot()
        val module = File(root, "app/src/main/java/ru/ozero/app/di/SingboxModule.kt")
        assertTrue(module.isFile, "SingboxModule.kt must exist in app/di/")
        val content = module.readText()

        assertTrue(
            content.contains("@IntoSet"),
            "SingboxModule must use @IntoSet — without it SingboxEngine is not added to the EnginePlugin set " +
                "and will never appear in auto-mode or manual selection",
        )
        assertTrue(
            content.contains("EnginePlugin"),
            "SingboxModule @IntoSet binding must return EnginePlugin type",
        )
    }

    @Test
    fun `should SingboxModule installed in SingletonComponent`() {
        val root = locateRepoRoot()
        val content = File(root, "app/src/main/java/ru/ozero/app/di/SingboxModule.kt").readText()

        assertTrue(
            content.contains("SingletonComponent"),
            "SingboxModule must be installed in SingletonComponent",
        )
    }

    @Test
    fun `should SingboxEngine declare id as SINGBOX`() {
        val root = locateRepoRoot()
        val engine = File(
            root,
            "engine-singbox/src/main/java/ru/ozero/enginesingbox/SingboxEngine.kt",
        )
        assertTrue(engine.isFile, "SingboxEngine.kt must exist")
        val content = engine.readText()

        assertTrue(
            content.contains("EngineId.SINGBOX"),
            "SingboxEngine must declare id = EngineId.SINGBOX",
        )
    }

    @Test
    fun `should SINGBOX id exist in EngineId enum`() {
        val root = locateRepoRoot()
        val engineId = File(
            root,
            "engines-core/src/main/java/ru/ozero/enginescore/EngineId.kt",
        )
        assertTrue(engineId.isFile, "EngineId.kt must exist in engines-core")
        val content = engineId.readText()

        assertTrue(
            content.contains("SINGBOX"),
            "EngineId must have SINGBOX entry",
        )
    }

    @Test
    fun `should EngineConfig have Singbox data class`() {
        val root = locateRepoRoot()
        val engineConfig = File(
            root,
            "engines-core/src/main/java/ru/ozero/enginescore/EngineConfig.kt",
        )
        assertTrue(engineConfig.isFile, "EngineConfig.kt must exist")
        val content = engineConfig.readText()

        assertTrue(
            content.contains("class Singbox") || content.contains("data class Singbox"),
            "EngineConfig must have Singbox data class with beanBlob",
        )
        assertTrue(
            content.contains("beanBlob"),
            "EngineConfig.Singbox must have beanBlob: ByteArray for Kryo-serialized bean",
        )
    }

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("repo root not found")
    }
}
