package ru.ozero.app.ui.settings.engines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UrnetworkLocationsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(
        bridge: FakeUrnetworkBridge = FakeUrnetworkBridge(),
        store: ru.ozero.engineurnetwork.UrnetworkConfigStore = fakeUrnetworkConfigStore(),
        tunnel: ru.ozero.commonvpn.TunnelController = idleTunnel(),
    ) = UrnetworkLocationsViewModel(bridge, FakeSettingsRepo(), store, tunnel)

    @Test
    fun `uiState стартует как Loading`() = runTest {
        assertEquals(UrnetworkSettingsUiState.Loading, vm().uiState.value)
    }

    @Test
    fun `uiState переходит в NotConnected когда engine не активен`() = runTest {
        val v = vm(bridge = FakeUrnetworkBridge(connected = false))
        advanceUntilIdle()
        assertIs<UrnetworkSettingsUiState.NotConnected>(v.uiState.value)
    }

    @Test
    fun `initDeviceForLocations вызывается если byClientJwt задан в configStore`() = runTest {
        val bridge = FakeUrnetworkBridge(deviceAvailable = false)
        vm(bridge = bridge, store = fakeUrnetworkConfigStoreWithJwt())
        advanceUntilIdle()
        assertTrue(
            bridge.initDeviceCallCount.get() > 0,
            "initDeviceForLocations должен вызываться при byClientJwt != null",
        )
    }

    @Test
    fun `uiState переходит в Ready без VPN если initDeviceForLocations успешен`() = runTest {
        val v = vm(
            bridge = FakeUrnetworkBridge(deviceAvailable = true),
            store = fakeUrnetworkConfigStoreWithJwt(),
        )
        advanceUntilIdle()
        assertIs<UrnetworkSettingsUiState.Ready>(v.uiState.value)
    }

    @Test
    fun `uiState остаётся Ready при остановке VPN если isDeviceAvailable true`() = runTest {
        val tc = activeTunnel()
        val v = vm(
            bridge = FakeUrnetworkBridge(connected = true, deviceAvailable = true),
            store = fakeUrnetworkConfigStoreWithJwt(),
            tunnel = tc,
        )
        advanceUntilIdle()
        tc.onDisconnecting()
        advanceUntilIdle()
        assertIs<UrnetworkSettingsUiState.Ready>(v.uiState.value)
    }

    @Test
    fun `refresh не переходит в NotConnected если isRunning true даже когда isDeviceAvailable false`() = runTest {
        val v = vm(
            bridge = FakeUrnetworkBridge(connected = true, deviceAvailable = false),
            tunnel = activeTunnel(),
        )
        advanceUntilIdle()
        v.refresh()
        advanceUntilIdle()
        assertIs<UrnetworkSettingsUiState.Ready>(v.uiState.value)
    }

    @Test
    fun `sentinel — init обязан retry refresh пока bridge не готов`() {
        val source = java.io.File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/ui/settings/engines/UrnetworkLocationsViewModel.kt",
        ).readText()
        val initBlock = source.substringAfter("init {").substringBefore("\n    }\n")
        assertTrue(
            initBlock.contains("REFRESH_RETRY_ATTEMPTS") && initBlock.contains("refreshOnce()"),
            "init обязан retry refreshOnce пока bridge не готов — иначе stuck в NotConnected",
        )
        assertTrue(
            source.contains("REFRESH_RETRY_ATTEMPTS = ") &&
                Regex("REFRESH_RETRY_ATTEMPTS\\s*=\\s*(\\d+)").find(source)!!.groupValues[1].toInt() >= 5,
            "REFRESH_RETRY_ATTEMPTS обязан быть >= 5",
        )
    }

    @Test
    fun `sentinel — init использует collectLatest а не collect`() {
        val source = java.io.File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/ui/settings/engines/UrnetworkLocationsViewModel.kt",
        ).readText()
        val initBlock = source.substringAfter("init {").substringBefore("fun refresh()")
        assertTrue(
            initBlock.contains("collectLatest"),
            "init обязан использовать collectLatest для cancel semantics при active=false",
        )
        kotlin.test.assertFalse(
            initBlock.contains(".collect {"),
            "init не должен использовать .collect — только collectLatest",
        )
    }

    @Test
    fun `sentinel — selectLocation и setProvidePaused используют update а не value assignment`() {
        val source = java.io.File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/ui/settings/engines/UrnetworkLocationsViewModel.kt",
        ).readText()
        val selectBody = source
            .substringAfter("fun selectLocation(")
            .substringBefore("private fun startSwitchingIndicator")
        kotlin.test.assertFalse(
            selectBody.contains("_uiState.value = current.copy"),
            "selectLocation не должен использовать read-modify-write через value=copy — race condition",
        )
        assertTrue(
            selectBody.contains("_uiState.update"),
            "selectLocation обязан использовать _uiState.update для атомарного обновления",
        )
        val pauseBody = source.substringAfter("fun setProvidePaused(").substringBefore("private fun updateLocations")
        kotlin.test.assertFalse(
            pauseBody.contains("_uiState.value = current.copy"),
            "setProvidePaused не должен использовать read-modify-write через value=copy — race condition",
        )
        assertTrue(
            pauseBody.contains("_uiState.update"),
            "setProvidePaused обязан использовать _uiState.update для атомарного обновления",
        )
        val filterBody = source
            .substringAfter("private fun applyFilter(")
            .substringBefore("private fun teardownLocationsVc")
        kotlin.test.assertFalse(
            filterBody.contains("_uiState.value = UrnetworkSettingsUiState.Ready"),
            "applyFilter не должен использовать _uiState.value = Ready — race condition",
        )
        assertTrue(
            filterBody.contains("_uiState.update"),
            "applyFilter обязан использовать _uiState.update для атомарного обновления",
        )
    }

    @Test
    fun `sentinel — updateLocations читает regions, cities и bestMatches из FilteredLocations`() {
        val source = java.io.File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/ui/settings/engines/UrnetworkLocationsViewModel.kt",
        ).readText()
        val updateBody = source
            .substringAfter("private fun updateLocations(")
            .substringBefore("private fun buildLocationList")
        assertTrue(
            updateBody.contains("filtered.regions"),
            "updateLocations обязан читать filtered.regions — иначе регионы никогда не загружаются",
        )
        assertTrue(
            updateBody.contains("filtered.cities"),
            "updateLocations обязан читать filtered.cities — иначе города никогда не загружаются",
        )
        assertTrue(
            updateBody.contains("filtered.bestMatches"),
            "updateLocations обязан читать filtered.bestMatches — иначе SDK top-matches не попадают в UI (Москва пустая при поиске)",
        )
    }

    @Test
    fun `sentinel — UrnetworkSettingsUiState_Ready содержит поля regions, cities, bestMatches`() {
        val source = java.io.File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/ui/settings/engines/UrnetworkLocationsViewModel.kt",
        ).readText()
        val readyBlock = source.substringAfter("data class Ready(").substringBefore(") : UrnetworkSettingsUiState")
        assertTrue(readyBlock.contains("regions"), "UrnetworkSettingsUiState.Ready обязан иметь поле regions")
        assertTrue(readyBlock.contains("cities"), "UrnetworkSettingsUiState.Ready обязан иметь поле cities")
        assertTrue(
            readyBlock.contains("bestMatches"),
            "UrnetworkSettingsUiState.Ready обязан иметь поле bestMatches — " +
                "иначе SDK best matches не попадут в UI (Москва пустая при поиске Mos)",
        )
    }

    @Test
    fun `sentinel — LazyColumn items в location picker используют identityHashCode для ключа`() {
        val source = java.io.File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/ui/settings/engines/UrnetworkEngineSettingsScreen.kt",
        ).readText()
        listOf("bestMatches", "countries", "regions", "cities").forEach { section ->
            val itemsCall = source.substringAfter("items($section, key = ").substringBefore("\n")
            assertTrue(
                itemsCall.contains("System.identityHashCode(it.location)"),
                "items($section) обязан использовать System.identityHashCode(it.location) — " +
                    "name/countryCode не уникальны (две Moscow → duplicate key crash)",
            )
        }
    }

    @Test
    fun `switchingCountry стартует с false`() = runTest {
        assertEquals(false, vm().switchingCountry.value)
    }

    @Test
    fun `selectLocation активирует switchingCountry когда страна меняется`() = runTest {
        val locA = FakeLocationToken("US")
        val locB = FakeLocationToken("DE")
        val bridge = FakeUrnetworkBridge(connected = true, initialLocation = locA, peerCountValue = 0)
        val v = vm(bridge = bridge, tunnel = activeTunnel())
        runCurrent()
        v.selectLocation(locB)
        runCurrent()
        assertEquals(true, v.switchingCountry.value)
    }

    @Test
    fun `switchingCountry очищается после 15s budget когда peers не появились`() = runTest {
        val locA = FakeLocationToken("US")
        val locB = FakeLocationToken("DE")
        val bridge = FakeUrnetworkBridge(connected = true, initialLocation = locA, peerCountValue = 0)
        val v = vm(bridge = bridge, tunnel = activeTunnel())
        runCurrent()
        v.selectLocation(locB)
        runCurrent()
        assertEquals(true, v.switchingCountry.value)
        advanceTimeBy(20_000L)
        runCurrent()
        assertEquals(false, v.switchingCountry.value)
    }

    @Test
    fun `selectLocation обновляет selectedLocation в uiState`() = runTest {
        val locA = FakeLocationToken("US")
        val locB = FakeLocationToken("DE")
        val bridge = FakeUrnetworkBridge(connected = true, initialLocation = locA)
        val v = vm(bridge = bridge, tunnel = activeTunnel())
        advanceUntilIdle()
        v.refresh()
        advanceUntilIdle()
        val before = v.uiState.value
        if (before is UrnetworkSettingsUiState.Ready) {
            v.selectLocation(locB)
            runCurrent()
            val after = v.uiState.value
            if (after is UrnetworkSettingsUiState.Ready) {
                assertEquals(locB, after.selectedLocation)
            }
        }
    }

    @Test
    fun `setProvidePaused true когда engine активен — вызывает bridge и обновляет Ready providePaused`() = runTest {
        val bridge = FakeUrnetworkBridge(connected = true)
        val v = vm(bridge = bridge, tunnel = activeTunnel())
        advanceUntilIdle()
        v.refresh()
        advanceUntilIdle()
        val before = v.uiState.value
        assertIs<UrnetworkSettingsUiState.Ready>(before)
        assertEquals(false, before.providePaused)

        v.setProvidePaused(true)
        advanceUntilIdle()

        assertEquals(1, bridge.setProvidePausedCallCount.get())
        assertEquals(true, bridge.lastPausedValue)
        val after = v.uiState.value
        assertIs<UrnetworkSettingsUiState.Ready>(after)
        assertEquals(true, after.providePaused)
    }

    @Test
    fun `setProvidePaused false когда engine активен — снимает паузу и обновляет state`() = runTest {
        val bridge = FakeUrnetworkBridge(connected = true)
        val v = vm(bridge = bridge, tunnel = activeTunnel())
        advanceUntilIdle()
        v.refresh()
        advanceUntilIdle()
        v.setProvidePaused(true)
        advanceUntilIdle()
        v.setProvidePaused(false)
        advanceUntilIdle()

        assertEquals(2, bridge.setProvidePausedCallCount.get())
        assertEquals(false, bridge.lastPausedValue)
        val after = v.uiState.value
        assertIs<UrnetworkSettingsUiState.Ready>(after)
        assertEquals(false, after.providePaused)
    }

    @Test
    fun `setProvidePaused когда engine не активен — НЕ дёргает bridge`() = runTest {
        val bridge = FakeUrnetworkBridge(connected = false)
        val v = vm(bridge = bridge)
        advanceUntilIdle()
        v.setProvidePaused(true)
        advanceUntilIdle()
        assertEquals(
            0,
            bridge.setProvidePausedCallCount.get(),
            "bridge.setProvidePaused не должен вызываться когда isUrnetworkActive=false",
        )
        assertEquals(null, bridge.lastPausedValue)
    }

    @Test
    fun `bootstrap завершается до проверки NotConnected — нет флаша при наличии JWT`() = runTest {
        val bridge = FakeUrnetworkBridge(deviceAvailable = true)
        val store = fakeUrnetworkConfigStoreWithJwt()
        val v = vm(bridge = bridge, store = store)
        var sawNotConnected = false
        val job = launch {
            v.uiState.collect { state ->
                if (state is UrnetworkSettingsUiState.NotConnected) sawNotConnected = true
            }
        }
        advanceUntilIdle()
        job.cancel()
        kotlin.test.assertFalse(
            sawNotConnected,
            "uiState не должен переходить в NotConnected если JWT есть и initDeviceForLocations успешен",
        )
    }

    @Test
    fun `sentinel — SettingsCard не имеет параметра showProvide — ProvideSection всегда видна`() {
        val source = java.io.File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/ui/settings/engines/UrnetworkEngineSettingsScreen.kt",
        ).readText()
        val settingsCardSignature = source
            .substringAfter("private fun SettingsCard(")
            .substringBefore(") {")
        kotlin.test.assertFalse(
            settingsCardSignature.contains("showProvide"),
            "SettingsCard не должен иметь параметр showProvide — ProvideSection показывается всегда",
        )
        val settingsCardBody = source
            .substringAfter("private fun SettingsCard(")
            .substringBefore("private fun SectionDivider(")
        kotlin.test.assertFalse(
            settingsCardBody.contains("if (showProvide)"),
            "SettingsCard не должен содержать if(showProvide) — ProvideSection показывается всегда",
        )
    }

    @Test
    fun `sentinel — NotConnected branch содержит UrnetworkBalanceCard`() {
        val source = java.io.File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/ui/settings/engines/UrnetworkEngineSettingsScreen.kt",
        ).readText()
        val notConnectedBranch = source
            .substringAfter("UrnetworkSettingsUiState.NotConnected ->")
            .substringBefore("is UrnetworkSettingsUiState.Ready ->")
        assertTrue(
            notConnectedBranch.contains("UrnetworkBalanceCard"),
            "NotConnected branch обязан содержать UrnetworkBalanceCard — баланс виден всегда",
        )
    }
}
