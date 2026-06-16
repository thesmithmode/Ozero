package ru.ozero.app.ui.settings.engines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.engineurnetwork.UrnetworkCachedLocation
import ru.ozero.engineurnetwork.UrnetworkConfig
import ru.ozero.engineurnetwork.UrnetworkLocationSelection
import ru.ozero.engineurnetwork.selectedLocation
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
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
        assertIs<UrnetworkSettingsUiState.Ready>(awaitReadyState(v))
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
        assertIs<UrnetworkSettingsUiState.Ready>(awaitReadyState(v))
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
        assertIs<UrnetworkSettingsUiState.Ready>(awaitReadyState(v))
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
            "updateLocations обязан читать filtered.bestMatches — иначе SDK top-matches " +
                "не попадают в UI (Москва пустая при поиске)",
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
            val after = awaitReadyState(v)
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
        val after = awaitReadyState(v)
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
        val after = awaitReadyState(v)
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
        backgroundScope.launch {
            v.uiState.collect { state ->
                if (state is UrnetworkSettingsUiState.NotConnected) sawNotConnected = true
            }
        }
        advanceUntilIdle()
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
    fun `selectLocation persists countryCode в settings когда engine не активен`() = runTest {
        val locB = FakeLocationToken("DE")
        val bridge = FakeUrnetworkBridge(deviceAvailable = true)
        val settings = FakeSettingsRepo()
        val v = UrnetworkLocationsViewModel(bridge, settings, fakeUrnetworkConfigStoreWithJwt(), idleTunnel())
        advanceUntilIdle()
        v.selectLocation(locB)
        advanceUntilIdle()
        assertTrue(
            settings.countryCodeUpdates.contains("DE"),
            "selectLocation обязан вызывать setUrnetworkCountryCode даже когда URnetwork не активен",
        )
    }

    @Test
    fun `selectLocation НЕ вызывает connectTo когда engine не активен`() = runTest {
        val locB = FakeLocationToken("DE")
        val bridge = FakeUrnetworkBridge(deviceAvailable = true)
        val v = UrnetworkLocationsViewModel(bridge, FakeSettingsRepo(), fakeUrnetworkConfigStoreWithJwt(), idleTunnel())
        advanceUntilIdle()
        v.selectLocation(locB)
        runCurrent()
        assertEquals(
            0,
            bridge.connectToCallCount.get(),
            "bridge.connectTo не должен вызываться пока URnetwork не активен",
        )
    }

    @Test
    fun `selectLocation обновляет selectedLocation в Ready state когда engine не активен`() = runTest {
        val locB = FakeLocationToken("DE")
        val bridge = FakeUrnetworkBridge(deviceAvailable = true)
        val v = UrnetworkLocationsViewModel(bridge, FakeSettingsRepo(), fakeUrnetworkConfigStoreWithJwt(), idleTunnel())
        advanceUntilIdle()
        assertIs<UrnetworkSettingsUiState.Ready>(awaitReadyState(v))
        v.selectLocation(locB)
        runCurrent()
        val after = awaitReadyState(v)
        assertIs<UrnetworkSettingsUiState.Ready>(after)
        assertEquals(locB, after.selectedLocation)
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

    @Test
    fun `selectLocation persists countryCode in engine configStore for backup and next start`() = runTest {
        val store = fakeUrnetworkConfigStoreWithJwt()
        val bridge = FakeUrnetworkBridge(deviceAvailable = true)
        val v = UrnetworkLocationsViewModel(bridge, FakeSettingsRepo(), store, idleTunnel())
        advanceUntilIdle()
        v.selectLocation(FakeLocationToken("DE"))
        advanceUntilIdle()
        assertEquals("DE", store.selectedLocation().first().countryCode)
    }

    @Test
    fun `settings screen shows stored country before SDK selectedLocation is available`() = runTest {
        val store = ru.ozero.engineurnetwork.InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                byClientJwt = "test-jwt",
                selectedLocation = UrnetworkLocationSelection(countryCode = "DE", region = null, city = null),
                cachedCountries = listOf(UrnetworkCachedLocation(name = "Germany", countryCode = "DE")),
            ),
        )
        val bridge = FakeUrnetworkBridge(deviceAvailable = true, initialLocation = null)
        val v = UrnetworkLocationsViewModel(bridge, FakeSettingsRepo(), store, idleTunnel())
        advanceUntilIdle()
        val state = assertIs<UrnetworkSettingsUiState.Ready>(awaitReadyState(v))
        assertEquals("DE", state.selectedLocation?.countryCode)
    }

    @Test
    fun `settings screen falls back to stored country when SDK selectedLocation is best available token`() = runTest {
        val store = ru.ozero.engineurnetwork.InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                byClientJwt = "test-jwt",
                selectedLocation = UrnetworkLocationSelection(countryCode = "DE", region = null, city = null),
                cachedCountries = listOf(UrnetworkCachedLocation(name = "Germany", countryCode = "DE")),
            ),
        )
        val bridge = FakeUrnetworkBridge(
            deviceAvailable = true,
            initialLocation = FakeLocationToken(countryCode = null, bestAvailable = true),
        )
        val v = UrnetworkLocationsViewModel(bridge, FakeSettingsRepo(), store, idleTunnel())
        advanceUntilIdle()
        val state = assertIs<UrnetworkSettingsUiState.Ready>(awaitReadyState(v))
        assertEquals("DE", state.selectedLocation?.countryCode)
    }

    @Test
    fun `settings screen observes config store update after ViewModel creation`() = runTest {
        val store = ru.ozero.engineurnetwork.InMemoryUrnetworkConfigStore(
            UrnetworkConfig(byClientJwt = "test-jwt"),
        )
        val bridge = FakeUrnetworkBridge(deviceAvailable = true, initialLocation = null)
        val v = UrnetworkLocationsViewModel(bridge, FakeSettingsRepo(), store, idleTunnel())
        advanceUntilIdle()
        store.update {
            it.copy(
                selectedLocation = UrnetworkLocationSelection(countryCode = "DE", region = null, city = null),
                cachedCountries = listOf(UrnetworkCachedLocation(name = "Germany", countryCode = "DE")),
            )
        }
        advanceUntilIdle()
        val state = assertIs<UrnetworkSettingsUiState.Ready>(awaitReadyState(v))
        assertEquals("DE", state.selectedLocation?.countryCode)
    }

    @Test
    fun `settings screen resets to NotConnected when cached locations become empty`() = runTest {
        val store = ru.ozero.engineurnetwork.InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                byClientJwt = "test-jwt",
                cachedCountries = listOf(UrnetworkCachedLocation(name = "Germany", countryCode = "DE")),
            ),
        )
        val bridge = FakeUrnetworkBridge(deviceAvailable = false, initialLocation = null)
        val v = UrnetworkLocationsViewModel(bridge, FakeSettingsRepo(), store, idleTunnel())
        advanceUntilIdle()
        assertIs<UrnetworkSettingsUiState.Ready>(awaitReadyState(v))

        store.update {
            it.copy(
                cachedCountries = emptyList(),
                cachedRegions = emptyList(),
                cachedCities = emptyList(),
                cachedBestMatches = emptyList(),
            )
        }
        advanceUntilIdle()

        assertIs<UrnetworkSettingsUiState.NotConnected>(v.uiState.value)
    }

    @Test
    fun `setSearchQuery trims input and filters cached locations and best matches`() = runTest {
        val store = ru.ozero.engineurnetwork.InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                cachedCountries = listOf(
                    UrnetworkCachedLocation(name = "Germany", countryCode = "DE"),
                    UrnetworkCachedLocation(name = "Poland", countryCode = "PL"),
                ),
                cachedBestMatches = listOf(
                    UrnetworkCachedLocation(name = "Germany Top", countryCode = "DE"),
                ),
            ),
        )
        val bridge = FakeUrnetworkBridge(deviceAvailable = false)
        val v = UrnetworkLocationsViewModel(bridge, FakeSettingsRepo(), store, idleTunnel())
        advanceUntilIdle()

        v.setSearchQuery("  гер  ")
        advanceUntilIdle()

        val filtered = assertIs<UrnetworkSettingsUiState.Ready>(v.uiState.value)
        assertEquals(1, filtered.countries.size)
        assertEquals("DE", filtered.countries.single().countryCode)
        assertEquals(1, filtered.bestMatches.size)

        v.setSearchQuery("   ")
        advanceUntilIdle()

        val blank = assertIs<UrnetworkSettingsUiState.Ready>(v.uiState.value)
        assertTrue(blank.bestMatches.isEmpty())
    }

    @Test
    fun `selectedLocationFromStore synthesizes fallback token when SDK has no exact match`() = runTest {
        val store = ru.ozero.engineurnetwork.InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                byClientJwt = "test-jwt",
                selectedLocation = UrnetworkLocationSelection(
                    countryCode = "DE",
                    region = "Bavaria",
                    city = "Munich",
                ),
            ),
        )
        val bridge = FakeUrnetworkBridge(deviceAvailable = true, initialLocation = null)
        val v = UrnetworkLocationsViewModel(bridge, FakeSettingsRepo(), store, idleTunnel())
        advanceUntilIdle()

        val ready = awaitReadyState(v)
        assertEquals("DE", ready.selectedLocation?.countryCode)
        assertEquals("Bavaria", ready.selectedLocation?.region)
        assertEquals("Munich", ready.selectedLocation?.city)
    }

    @Test
    fun `setSearchQuery filters cached regions and cities independently`() = runTest {
        val store = ru.ozero.engineurnetwork.InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                cachedRegions = listOf(
                    UrnetworkCachedLocation(name = "Bavaria", countryCode = "DE", region = "Bavaria"),
                    UrnetworkCachedLocation(name = "Saxony", countryCode = "DE", region = "Saxony"),
                ),
                cachedCities = listOf(
                    UrnetworkCachedLocation(name = "Munich", countryCode = "DE", region = "Bavaria", city = "Munich"),
                    UrnetworkCachedLocation(name = "Warsaw", countryCode = "PL", city = "Warsaw"),
                ),
            ),
        )
        val v = UrnetworkLocationsViewModel(
            FakeUrnetworkBridge(deviceAvailable = false),
            FakeSettingsRepo(),
            store,
            idleTunnel(),
        )
        advanceUntilIdle()

        v.setSearchQuery("mun")
        advanceUntilIdle()

        val state = assertIs<UrnetworkSettingsUiState.Ready>(v.uiState.value)
        assertEquals(emptyList(), state.regions)
        assertEquals("Munich", state.cities.single().name)
    }

    @Test
    fun `selectLocation null chooses best available when engine is active`() = runTest {
        val bridge = FakeUrnetworkBridge(connected = true, initialLocation = FakeLocationToken("DE"))
        val store = fakeUrnetworkConfigStoreWithJwt()
        val v = UrnetworkLocationsViewModel(bridge, FakeSettingsRepo(), store, activeTunnel())
        advanceUntilIdle()

        v.selectLocation(null)
        advanceUntilIdle()

        assertEquals(1, bridge.connectBestAvailableCallCount.get())
        assertEquals(null, store.selectedLocation().first().countryCode)
    }

    @Test
    fun `selectLocation custom token uses preferred location reconnect path`() = runTest {
        val custom = UrnetworkCachedLocation(name = "Munich", countryCode = "DE", region = "Bavaria", city = "Munich")
        val bridge = FakeUrnetworkBridge(connected = true, initialLocation = FakeLocationToken("US"))
        val store = fakeUrnetworkConfigStoreWithJwt()
        val v = UrnetworkLocationsViewModel(bridge, FakeSettingsRepo(), store, activeTunnel())
        advanceUntilIdle()

        v.selectLocation(custom)
        advanceUntilIdle()

        assertEquals(0, bridge.connectToCallCount.get())
        assertEquals(1, bridge.connectPreferredLocationCallCount.get())
        assertEquals("DE", bridge.lastPreferredLocation?.countryCode)
        assertEquals("Bavaria", bridge.lastPreferredLocation?.region)
        assertEquals("Munich", bridge.lastPreferredLocation?.city)
    }

    @Test
    fun `cached country with invalid code has blank flag and russian name`() = runTest {
        val store = ru.ozero.engineurnetwork.InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                cachedCountries = listOf(
                    UrnetworkCachedLocation(name = "Unknown", countryCode = "U"),
                    UrnetworkCachedLocation(name = "Germany", countryCode = "de"),
                ),
            ),
        )
        val v = UrnetworkLocationsViewModel(
            FakeUrnetworkBridge(deviceAvailable = false),
            FakeSettingsRepo(),
            store,
            idleTunnel(),
        )
        advanceUntilIdle()

        val state = assertIs<UrnetworkSettingsUiState.Ready>(v.uiState.value)
        assertEquals("", state.countries[0].flag)
        assertEquals("", state.countries[0].nameRu)
        assertTrue(state.countries[1].flag.isNotBlank())
        assertTrue(state.countries[1].nameRu.isNotBlank())
    }

    @Test
    fun `blank search keeps countries regions cities and hides best matches`() = runTest {
        val store = ru.ozero.engineurnetwork.InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                cachedCountries = listOf(UrnetworkCachedLocation(name = "Germany", countryCode = "DE")),
                cachedRegions = listOf(
                    UrnetworkCachedLocation(name = "Bavaria", countryCode = "DE", region = "Bavaria"),
                ),
                cachedCities = listOf(
                    UrnetworkCachedLocation(name = "Munich", countryCode = "DE", city = "Munich"),
                ),
                cachedBestMatches = listOf(
                    UrnetworkCachedLocation(name = "Munich Top", countryCode = "DE", city = "Munich"),
                ),
            ),
        )
        val v = UrnetworkLocationsViewModel(
            FakeUrnetworkBridge(deviceAvailable = false),
            FakeSettingsRepo(),
            store,
            idleTunnel(),
        )
        advanceUntilIdle()

        v.setSearchQuery("")
        advanceUntilIdle()

        val state = assertIs<UrnetworkSettingsUiState.Ready>(v.uiState.value)
        assertEquals(1, state.countries.size)
        assertEquals(1, state.regions.size)
        assertEquals(1, state.cities.size)
        assertTrue(state.bestMatches.isEmpty())
    }

    @Test
    fun `search query matches russian country name and country code`() = runTest {
        val store = ru.ozero.engineurnetwork.InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                cachedCountries = listOf(
                    UrnetworkCachedLocation(name = "Germany", countryCode = "DE"),
                    UrnetworkCachedLocation(name = "Poland", countryCode = "PL"),
                ),
            ),
        )
        val v = UrnetworkLocationsViewModel(
            FakeUrnetworkBridge(deviceAvailable = false),
            FakeSettingsRepo(),
            store,
            idleTunnel(),
        )
        advanceUntilIdle()

        v.setSearchQuery("de")
        advanceUntilIdle()

        val byCode = assertIs<UrnetworkSettingsUiState.Ready>(v.uiState.value)
        assertEquals("DE", byCode.countries.single().countryCode)

        v.setSearchQuery("гер")
        advanceUntilIdle()

        val byRuName = assertIs<UrnetworkSettingsUiState.Ready>(v.uiState.value)
        assertEquals("DE", byRuName.countries.single().countryCode)
    }

    @Test
    fun `setProvidePaused from NotConnected builds ready state from cached lists`() = runTest {
        val store = fakeUrnetworkConfigStore()
        val v = UrnetworkLocationsViewModel(
            FakeUrnetworkBridge(deviceAvailable = false),
            FakeSettingsRepo(),
            store,
            idleTunnel(),
        )
        advanceUntilIdle()
        assertIs<UrnetworkSettingsUiState.NotConnected>(v.uiState.value)

        v.setProvidePaused(true)
        advanceUntilIdle()

        val state = assertIs<UrnetworkSettingsUiState.Ready>(v.uiState.value)
        assertTrue(state.providePaused)
        assertTrue(state.countries.isEmpty())
    }

    @Test
    fun `refresh unavailable device clears cached ready state to NotConnected`() = runTest {
        val store = ru.ozero.engineurnetwork.InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                cachedCountries = listOf(UrnetworkCachedLocation(name = "Germany", countryCode = "DE")),
            ),
        )
        val v = UrnetworkLocationsViewModel(
            FakeUrnetworkBridge(deviceAvailable = false),
            FakeSettingsRepo(),
            store,
            idleTunnel(),
        )
        advanceUntilIdle()
        assertIs<UrnetworkSettingsUiState.Ready>(v.uiState.value)

        v.refresh()
        advanceUntilIdle()

        assertIs<UrnetworkSettingsUiState.NotConnected>(v.uiState.value)
    }

    @Test
    fun `isUrnetworkActive follows only URnetwork connecting and connected states`() = runTest {
        val tunnel = idleTunnel()
        val v = vm(tunnel = tunnel)
        advanceUntilIdle()
        assertEquals(false, v.isUrnetworkActive.value)

        tunnel.onConnecting(ru.ozero.enginescore.EngineId.BYEDPI)
        advanceUntilIdle()
        assertEquals(false, v.isUrnetworkActive.value)

        tunnel.onProbing()
        advanceUntilIdle()
        tunnel.onConnecting(ru.ozero.enginescore.EngineId.URNETWORK)
        advanceUntilIdle()
        assertEquals(true, v.isUrnetworkActive.value)

        tunnel.onEngineStarted(ru.ozero.enginescore.EngineId.URNETWORK, 1080)
        advanceUntilIdle()
        assertEquals(true, v.isUrnetworkActive.value)

        tunnel.onDisconnecting()
        advanceUntilIdle()
        assertEquals(false, v.isUrnetworkActive.value)
    }

    @Test
    fun `stored region and city resolve to exact cached location`() = runTest {
        val store = ru.ozero.engineurnetwork.InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                byClientJwt = "test-jwt",
                selectedLocation = UrnetworkLocationSelection(
                    countryCode = "DE",
                    region = "Bavaria",
                    city = "Munich",
                ),
                cachedCountries = listOf(UrnetworkCachedLocation(name = "Germany", countryCode = "DE")),
                cachedRegions = listOf(
                    UrnetworkCachedLocation(name = "Bavaria", countryCode = "DE", region = "Bavaria"),
                ),
                cachedCities = listOf(
                    UrnetworkCachedLocation(
                        name = "Munich",
                        countryCode = "DE",
                        region = "Bavaria",
                        city = "Munich",
                    ),
                ),
            ),
        )
        val v = UrnetworkLocationsViewModel(
            FakeUrnetworkBridge(deviceAvailable = true, initialLocation = null),
            FakeSettingsRepo(),
            store,
            idleTunnel(),
        )
        advanceUntilIdle()

        val state = assertIs<UrnetworkSettingsUiState.Ready>(awaitReadyState(v))
        assertEquals("Bavaria", state.selectedLocation?.region)
        assertEquals("Munich", state.selectedLocation?.city)
    }

    @Test
    fun `ready config update keeps current sdk selected location`() = runTest {
        val store = ru.ozero.engineurnetwork.InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                byClientJwt = "test-jwt",
                cachedCountries = listOf(UrnetworkCachedLocation(name = "Germany", countryCode = "DE")),
            ),
        )
        val v = UrnetworkLocationsViewModel(
            FakeUrnetworkBridge(deviceAvailable = true, initialLocation = FakeLocationToken("US")),
            FakeSettingsRepo(),
            store,
            idleTunnel(),
        )
        advanceUntilIdle()
        assertEquals("US", awaitReadyState(v).selectedLocation?.countryCode)

        store.update {
            it.copy(
                selectedLocation = UrnetworkLocationSelection(countryCode = "DE", region = null, city = null),
                cachedCountries = listOf(UrnetworkCachedLocation(name = "Germany", countryCode = "DE")),
            )
        }
        advanceUntilIdle()

        assertEquals("US", awaitReadyState(v).selectedLocation?.countryCode)
    }

    @Test
    fun `inactive available device builds paused ready state without cache`() = runTest {
        val tunnel = activeTunnel()
        val v = UrnetworkLocationsViewModel(
            FakeUrnetworkBridge(connected = false, deviceAvailable = true),
            FakeSettingsRepo(),
            fakeUrnetworkConfigStore(),
            tunnel,
        )
        advanceUntilIdle()

        tunnel.onDisconnecting()
        advanceUntilIdle()

        val state = assertIs<UrnetworkSettingsUiState.Ready>(v.uiState.value)
        assertTrue(state.providePaused)
        assertTrue(state.countries.isEmpty())
    }

    @Test
    fun `switching indicator clears when peers appear before timeout`() = runTest {
        val bridge = FakeUrnetworkBridge(
            connected = true,
            initialLocation = FakeLocationToken("US"),
            peerCountValue = 1,
        )
        val v = vm(bridge = bridge, tunnel = activeTunnel())
        runCurrent()

        v.selectLocation(FakeLocationToken("DE"))
        runCurrent()

        assertEquals(true, v.switchingCountry.value)
        advanceTimeBy(1_600L)
        runCurrent()
        assertEquals(false, v.switchingCountry.value)
        assertTrue(bridge.peerCountCallCount.get() > 0)
    }

    private suspend fun TestScope.awaitReadyState(v: UrnetworkLocationsViewModel): UrnetworkSettingsUiState.Ready {
        repeat(10) {
            val state = v.uiState.value
            if (state is UrnetworkSettingsUiState.Ready) return state
            runCurrent()
            advanceUntilIdle()
            dispatcher.scheduler.advanceUntilIdle()
        }
        return assertIs<UrnetworkSettingsUiState.Ready>(v.uiState.value)
    }
}
