package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.settings.SettingsModel
import kotlin.test.assertEquals

class EngineWarpBuildManualConfigTest {

    @Test
    fun `EngineWarp buildManualConfig всегда возвращает Warp object`() {
        val src by lazy {
            java.io.File(System.getProperty("user.dir") ?: ".", "src/main/java/ru/ozero/enginewarp/EngineWarp.kt").readText()
        }
        val block = src.substringAfter("override fun buildManualConfig").substringBefore("override suspend fun start")
        assertEquals(
            true,
            block.contains("EngineConfig.Warp"),
            "EngineWarp.buildManualConfig обязан возвращать EngineConfig.Warp (singleton object). " +
                "WARP не требует settings — конфиг получает через WarpAutoConfig race.",
        )
    }
}
