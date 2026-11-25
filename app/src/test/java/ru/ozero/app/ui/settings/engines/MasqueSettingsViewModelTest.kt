package ru.ozero.app.ui.settings.engines

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.enginemasque.MasqueProfile
import ru.ozero.enginemasque.MasqueProfileStore
import ru.ozero.enginemasque.MasqueRuntime
import ru.ozero.enginemasque.MasqueSettings
import ru.ozero.enginemasque.MasqueTransport
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MasqueSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = mockk<MasqueProfileStore>(relaxed = true)
    private val runtime = mockk<MasqueRuntime>()
    private val saved = MutableStateFlow(MasqueSettings())

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { store.settings() } returns saved
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `registration requires user action and prevents duplicate requests`() = runTest {
        val profile = MasqueProfile.parse(PROFILE)
        val result = CompletableDeferred<MasqueProfile>()
        coEvery { runtime.register(true) } coAnswers { result.await() }
        coEvery { store.save(profile) } coAnswers { saved.value = saved.value.copy(profile = profile) }
        val vm = MasqueSettingsViewModel(store, runtime)
        backgroundScope.launch { vm.state.collect() }
        runCurrent()
        assertTrue(vm.state.value.loaded)
        assertFalse(vm.state.value.registered)
        coVerify(exactly = 0) { runtime.register(any()) }
        vm.register()
        vm.register()
        runCurrent()
        assertTrue(vm.state.value.busy)
        result.complete(profile)
        runCurrent()
        assertTrue(vm.state.value.registered)
        assertFalse(vm.state.value.busy)
        coVerify(exactly = 1) { runtime.register(true) }
        coVerify(exactly = 1) { store.save(profile) }
    }

    @Test
    fun `settings reflect store changes and failures clear on retry`() = runTest {
        val vm = MasqueSettingsViewModel(store, runtime)
        backgroundScope.launch { vm.state.collect() }
        runCurrent()
        coEvery { store.setTransport(MasqueTransport.H2) } throws IllegalStateException("fixture")
        vm.selectTransport(MasqueTransport.H2)
        runCurrent()
        assertTrue(vm.state.value.error)
        coEvery { store.setTransport(MasqueTransport.H2) } coAnswers {
            saved.value = saved.value.copy(transport = MasqueTransport.H2)
        }
        vm.selectTransport(MasqueTransport.H2)
        runCurrent()
        assertFalse(vm.state.value.error)
        assertEquals(MasqueTransport.H2, vm.state.value.transport)
        coEvery { store.setAutoEnabled(false) } coAnswers { saved.value = saved.value.copy(autoEnabled = false) }
        vm.setAutoEnabled(false)
        runCurrent()
        assertFalse(vm.state.value.autoEnabled)
        saved.value = saved.value.copy(corrupt = true)
        runCurrent()
        assertTrue(vm.state.value.error)
        coEvery { store.clear() } coAnswers { saved.value = saved.value.copy(profile = null, corrupt = false) }
        vm.clear()
        runCurrent()
        assertFalse(vm.state.value.error)
        assertFalse(vm.state.value.registered)
    }

    @Test
    fun `storage failure is visible without a registration request`() = runTest {
        every { store.settings() } returns flow { error("fixture") }
        val vm = MasqueSettingsViewModel(store, runtime)
        backgroundScope.launch { vm.state.collect() }
        runCurrent()
        assertTrue(vm.state.value.loaded)
        assertTrue(vm.state.value.error)
        coVerify(exactly = 0) { runtime.register(any()) }
    }

    private companion object {
        val PROFILE = """{"private_key":"test","endpoint_pub_key":"test","endpoint_v4":"192.0.2.1",
            |"ipv4":"192.0.2.2","ipv6":"2001:db8::1","id":"test","access_token":"test"}
        """.trimMargin()
    }
}
