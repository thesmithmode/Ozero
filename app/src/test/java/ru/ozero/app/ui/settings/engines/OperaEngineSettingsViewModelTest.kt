package ru.ozero.app.ui.settings.engines

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import kotlin.test.assertEquals

class OperaEngineSettingsViewModelTest {
    @Test
    fun `settings follow repository and persist user choices`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repo = mockk<SettingsRepository>()
            val state = MutableStateFlow(SettingsModel.DEFAULT)
            every { repo.settings } returns state
            coEvery { repo.setOperaRegion(any()) } coAnswers {
                state.value = state.value.copy(operaRegion = firstArg())
            }
            coEvery { repo.setOperaSelection(any()) } coAnswers {
                state.value = state.value.copy(operaSelection = firstArg())
            }
            coEvery { repo.setOperaAutoEnabled(any()) } returns Unit
            val model = OperaEngineSettingsViewModel(repo)
            advanceUntilIdle()
            assertEquals("EU", model.settings.value.operaRegion)
            model.selectRegion("AM")
            model.selectServer("random")
            model.setAutoEnabled(true)
            advanceUntilIdle()
            coVerify(exactly = 1) { repo.setOperaRegion("AM") }
            coVerify(exactly = 1) { repo.setOperaSelection("random") }
            coVerify(exactly = 1) { repo.setOperaAutoEnabled(true) }
            assertEquals("AM", model.settings.value.operaRegion)
            assertEquals("random", model.settings.value.operaSelection)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
