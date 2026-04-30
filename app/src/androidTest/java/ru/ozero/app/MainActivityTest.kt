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
import ru.ozero.commonvpn.TunnelController
import ru.ozero.enginescore.EngineId
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var tunnelController: TunnelController

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
        tunnelController.onEngineStarted(EngineId.BYEDPI, 1080)

        composeRule
            .onNodeWithContentDescription("Отключить VPN")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("Подключено")
            .assertIsDisplayed()
    }
}
