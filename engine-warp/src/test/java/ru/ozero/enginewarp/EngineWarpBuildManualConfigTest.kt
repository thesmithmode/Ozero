package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class EngineWarpBuildManualConfigTest {

    @Test
    fun `EngineWarp buildManualConfig возвращает EngineConfig Warp`() {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val src = File(moduleRoot, "src/main/java/ru/ozero/enginewarp/EngineWarp.kt").readText()
        val block = src
            .substringAfter("override fun buildManualConfig")
            .substringBefore("override suspend fun start")
        assertTrue(
            block.contains("EngineConfig.Warp"),
            "EngineWarp.buildManualConfig обязан возвращать EngineConfig.Warp singleton. " +
                "WARP не использует SettingsModel — конфиг получает через WarpAutoConfig race.",
        )
    }
}
