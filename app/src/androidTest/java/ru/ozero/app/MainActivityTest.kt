package ru.ozero.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ozero.coreorchestrator.Orchestrator
import ru.ozero.coreorchestrator.OrchestratorTransition
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var orchestrator: Orchestrator

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun initialScreenShowsConnectButton() {
        composeRule
            .onNodeWithContentDescription("Подключить VPN")
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun initialScreenShowsIdleStatus() {
        composeRule
            .onNodeWithText("Выключено")
            .assertIsDisplayed()
    }

    @Test
    fun connectedStateShowsDisconnectButton() {
        orchestrator.dispatch(OrchestratorTransition.Connect)
        orchestrator.dispatch(OrchestratorTransition.ProbeComplete(ru.ozero.coreapi.EngineId.BYEDPI))
        orchestrator.dispatch(OrchestratorTransition.ConnectSuccess(ru.ozero.coreapi.EngineId.BYEDPI, 1080))

        composeRule
            .onNodeWithContentDescription("Отключить VPN")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("Подключено")
            .assertIsDisplayed()
    }
}
