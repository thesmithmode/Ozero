package ru.ozero.app.ui.strategy

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class StrategyTestViewModelSentinelTest {

    @Test
    fun `evolution orchestration is launched on ioDispatcher`() {
        val source = viewModelSource()
        assertTrue(
            source.contains("testJob = viewModelScope.launch(ioDispatcher)"),
            "strategy scan orchestration must not run on Main dispatcher",
        )
    }

    @Test
    fun `evolution ui state has distinct phases before first generation completes`() {
        val source = viewModelSource()
        assertTrue(source.contains("enum class EvolutionUiPhase"))
        assertTrue(source.contains("CandidateBuilding"))
        assertTrue(source.contains("InitialPopulation"))
        assertTrue(source.contains("Generation"))
    }

    private fun viewModelSource(): String =
        File(System.getProperty("user.dir") ?: ".")
            .resolve("src/main/java/ru/ozero/app/ui/strategy/StrategyTestViewModel.kt")
            .readText()
}
