package ru.ozero.app.ui.settings.engines.singbox

import android.os.ParcelFileDescriptor
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginesingbox.RoutedProbeResult
import ru.ozero.enginesingbox.SingboxEngine
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.VpnSocketProtector
import ru.ozero.enginescore.VpnSocketProtectorHolder
import ru.ozero.enginescore.settings.AppMode
import ru.ozero.enginescore.settings.ByeDpiUiSettings
import ru.ozero.enginescore.settings.HostsMode
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.SplitTunnelMode
import ru.ozero.enginescore.settings.TrafficMode
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.entity.ProxyProfile
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SingboxProbeServiceTest {
    companion object {
        private val knownProfiles = ConcurrentHashMap<Long, ProxyProfile>()
    }

    @Test
    fun `profile probe returns typed final failure without diagnostic detail`() {
        val source = java.io.File(
            "src/main/java/ru/ozero/app/ui/settings/engines/singbox/SingboxProbeService.kt",
        ).readText()
        val failureBranch = source
            .substringAfter("is ru.ozero.enginesingbox.RoutedProbeResult.Failure ->")
            .substringBefore("if (attempt < PROBE_ATTEMPTS - 1)")

        assertFalse(failureBranch.contains("result.safeDetail != null"))
        assertTrue(failureBranch.contains("result.reason.profileProbeStatus()"))
    }

    private val selectedProfileKey = longPreferencesKey("singbox_selected_profile_id")
    private val beanKey = byteArrayPreferencesKey("singbox_vless_bean")
    private val dnsServersKey = stringSetPreferencesKey("singbox_dns_servers")

    @Test
    fun `profile status stores stable category instead of diagnostic detail`() {
        assertEquals("Remote closed", RoutedProbeResult.Reason.REMOTE_CLOSED.profileProbeStatus())
        assertEquals("SOCKS rejected", RoutedProbeResult.Reason.SOCKS_REPLY.profileProbeStatus())
        assertEquals("DNS failed", RoutedProbeResult.Reason.DNS.profileProbeStatus())
        assertEquals("TLS certificate", RoutedProbeResult.Reason.TLS_CERTIFICATE.profileProbeStatus())
        assertEquals("TLS handshake", RoutedProbeResult.Reason.TLS_HANDSHAKE.profileProbeStatus())
        assertEquals("Timeout", RoutedProbeResult.Reason.TIMEOUT.profileProbeStatus())
        assertEquals("Unexpected response", RoutedProbeResult.Reason.UNEXPECTED_RESPONSE.profileProbeStatus())
    }

    @Test
    fun `profile protector permits proxy-only probe without active VPN`() {
        assertTrue(ProfileProbeProtector().protect(protectorSocket(7)))
    }

    @Test
    fun `profile protector delegates success and failure to active VPN`() {
        var acceptedFd = -1
        val accepting = VpnSocketProtector { fd ->
            acceptedFd = fd
            true
        }
        VpnSocketProtectorHolder.bind(accepting)
        try {
            assertTrue(ProfileProbeProtector().protect(protectorSocket(8)))
            assertEquals(8, acceptedFd)
        } finally {
            VpnSocketProtectorHolder.unbind(accepting)
        }
        val rejecting = VpnSocketProtector { false }
        VpnSocketProtectorHolder.bind(rejecting)
        try {
            assertEquals(false, ProfileProbeProtector().protect(protectorSocket(9)))
        } finally {
            VpnSocketProtectorHolder.unbind(rejecting)
        }
    }

    private fun protectorSocket(fd: Int): ParcelFileDescriptor {
        val socket = mockk<ParcelFileDescriptor>(relaxed = true)
        every { socket.fd } returns fd
        return socket
    }

    @Test
    fun `probeAndAutoSelect preserves auto-select mode while updating latency`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(
            mutablePreferencesOf(selectedProfileKey to SingboxEngine.SELECTED_AUTO),
        )
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()

        val profile = makeProfile(id = 7L, host = "probe.example", port = 443)
        val probe = FakeProfileProbe(mapOf("probe.example:443" to 24))

        SingboxProbeService(dao, dataStore, probe).probeAndAutoSelect(listOf(profile))

        assertEquals(SingboxEngine.SELECTED_AUTO, prefsFlow.value[selectedProfileKey])
        assertNull(prefsFlow.value[beanKey])
        assertEquals(24, dao.latencies[7L])
    }

    @Test
    fun `probeAndAutoSelect skips unsupported transport before selecting fastest profile`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()

        val unsupported = makeProfile(id = 8L, host = "unsupported.example", port = 443, type = "splithttp")
        val supported = makeProfile(id = 7L, host = "supported.example", port = 443)
        val probe = FakeProfileProbe(mapOf("supported.example:443" to 9))

        SingboxProbeService(dao, dataStore, probe).probeAndAutoSelect(listOf(unsupported, supported))

        assertEquals(SingboxProbeService.LATENCY_FAILED, dao.latencies[8L])
        assertEquals(7L, prefsFlow.value[selectedProfileKey])
    }

    @Test
    fun `probeAndAutoSelect marks corrupted bean as failed and never calls probe`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val corrupted = ProxyProfile(
            id = 17L,
            groupId = 1L,
            name = "corrupted",
            beanBlob = byteArrayOf(1, 2, 3),
            protocolType = SingboxEngine.PROTOCOL_VLESS,
        )
        knownProfiles[corrupted.id] = corrupted
        val probe = CountingProfileProbe()
        val events = mutableListOf<Pair<Long, Boolean>>()

        SingboxProbeService(dao, dataStore, probe).probeAndAutoSelect(
            profiles = listOf(corrupted),
            onProfileTestingChanged = { id, testing ->
                events += id to testing
            },
        )

        assertEquals(SingboxProbeService.LATENCY_FAILED, dao.latencies[17L])
        assertEquals(0, probe.calls.get())
        assertTrue(events.isEmpty())
        assertNull(prefsFlow.value[selectedProfileKey])
    }

    @Test
    fun `probeAndAutoSelect rejects tcp-only fake when routed probe fails`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val probe = CountingProfileProbe()

        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val tcpOnly = makeProfile(id = 7L, host = "127.0.0.1", port = server.localPort)

            SingboxProbeService(dao, dataStore, probe)
                .probeAndAutoSelect(listOf(tcpOnly))
        }

        assertEquals(SingboxProbeService.LATENCY_FAILED, dao.latencies[7L])
        assertEquals(0, probe.calls.get())
        assertNull(prefsFlow.value[selectedProfileKey])
    }

    @Test
    fun `probeAndAutoSelect marks runtime busy as failed`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val profile = makeProfile(id = 7L, host = "busy.example", port = 443)
        dao.latencies[7L] = 31

        SingboxProbeService(
            dao,
            dataStore,
            FakeProfileProbe(mapOf("busy.example:443" to LATENCY_SKIPPED_ACTIVE_RUNTIME)),
        )
            .probeAndAutoSelect(listOf(profile))

        assertEquals(SingboxProbeService.LATENCY_FAILED, dao.latencies[7L])
        assertEquals(SingboxProbeService.PROBE_ERROR_RUNTIME_BUSY, dao.errors[7L])
        assertNull(prefsFlow.value[selectedProfileKey])
    }

    @Test
    fun `probeAndAutoSelect records busy result without stopping active runtime`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val profiles = listOf(
            makeProfile(id = 1L, host = "active-first.example", port = 443),
            makeProfile(id = 2L, host = "active-second.example", port = 443),
        )
        val probe = ActiveRuntimeBatchProfileProbe()

        SingboxProbeService(dao, dataStore, probe).probeAndAutoSelect(profiles)

        assertEquals(1, probe.batchCalls.get())
        assertEquals(0, probe.startCount.get())
        assertEquals(0, probe.stopCount.get())
        assertEquals(profiles.associate { it.id to SingboxProbeService.LATENCY_FAILED }, dao.latencies)
        assertEquals(profiles.associate { it.id to SingboxProbeService.PROBE_ERROR_RUNTIME_BUSY }, dao.errors)
        assertNull(prefsFlow.value[selectedProfileKey])
    }

    @Test
    fun `probeAndAutoSelect marks invalid port as failed and never calls probe`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val invalidPort = makeProfile(id = 9L, host = "bad-port.example", port = 4_449_499)
        val probe = CountingProfileProbe()

        SingboxProbeService(dao, dataStore, probe).probeAndAutoSelect(listOf(invalidPort))

        assertEquals(SingboxProbeService.LATENCY_FAILED, dao.latencies[9L])
        assertEquals(0, probe.calls.get())
        assertNull(prefsFlow.value[selectedProfileKey])
    }

    @Test
    fun `probeAndAutoSelect limits active profile probes and reports per profile testing state`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val profiles = (1L..25L).map { id -> makeProfile(id = id, host = "probe-$id.example", port = 443) }
        val testingNow = AtomicInteger(0)
        val maxTestingNow = AtomicInteger(0)
        val testedIds = ConcurrentHashMap.newKeySet<Long>()

        SingboxProbeService(dao, dataStore, TrackingProfileProbe())
            .probeAndAutoSelect(profiles, onProfileTestingChanged = { profileId, isTesting ->
                if (isTesting) {
                    testedIds.add(profileId)
                    val current = testingNow.incrementAndGet()
                    maxTestingNow.updateAndGet { previous -> maxOf(previous, current) }
                } else {
                    testingNow.decrementAndGet()
                }
            })

        assertEquals(profiles.map { it.id }.toSet(), testedIds)
        assertTrue(
            maxTestingNow.get() in 2..SingboxProbeService.MAX_CONCURRENT_PROFILE_PROBES,
            "profile testing UI state must stay inside the bounded probe batch",
        )
    }

    @Test
    fun `probeAndAutoSelect bounds each runtime batch to fifty targets`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val profiles = (1L..125L).map { id -> makeProfile(id, "batch-$id.example", 443) }
        val probe = TrackingBatchProfileProbe()

        SingboxProbeService(dao, dataStore, probe).probeAndAutoSelect(profiles)

        assertEquals(listOf(50, 50, 25), probe.batchSizes)
        assertEquals(3, probe.startCount.get())
        assertEquals(3, probe.stopCount.get())
        assertEquals(50, probe.maxConcurrentTargets.get())
        assertEquals(0, probe.legacyCalls.get())
        assertEquals(profiles.map { it.id }.toSet(), dao.latencies.keys)
    }

    @Test
    fun `probeAndAutoSelect records terminal failures when batch probe throws`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dao = FakeProxyProfileDao()
        val profiles = listOf(
            makeProfile(id = 1L, host = "failure-first.example", port = 443),
            makeProfile(id = 2L, host = "failure-second.example", port = 443),
        )
        val probe = object : SingboxProfileProbe, SingboxBatchProfileProbe {
            override suspend fun probeLatencyMs(bean: AbstractBean, settings: SingboxProfileProbeSettings): Int =
                SingboxProbeService.LATENCY_FAILED

            override suspend fun probeBatch(
                targets: List<SingboxProfileProbeTarget>,
                settings: SingboxProfileProbeSettings,
            ): Map<Long, SingboxProbeOutcome> = error("batch failed")
        }

        SingboxProbeService(dao, flowDataStore(prefsFlow), probe).probeAndAutoSelect(profiles)

        assertEquals(profiles.associate { it.id to SingboxProbeService.LATENCY_FAILED }, dao.latencies)
        assertEquals(profiles.associate { it.id to SingboxProbeService.PROBE_ERROR_FAILED }, dao.errors)
        assertNull(prefsFlow.value[selectedProfileKey])
    }

    @Test
    fun `probeAndAutoSelect passes configured timeout to profile probe`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(
            mutablePreferencesOf(SingboxProbeService.PROBE_TIMEOUT_MS_KEY to 10_000),
        )
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val probe = TimeoutRecordingProfileProbe()
        val profile = makeProfile(id = 10L, host = "timeout.example", port = 443)

        SingboxProbeService(dao, dataStore, probe).probeAndAutoSelect(listOf(profile))

        assertEquals(listOf(10_000), probe.timeouts)
        assertEquals(12, dao.latencies[10L])
    }

    @Test
    fun `probeAndAutoSelect applies timeout to the whole profile check`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(
            mutablePreferencesOf(SingboxProbeService.PROBE_TIMEOUT_MS_KEY to 1_000),
        )
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val profile = makeProfile(id = 12L, host = "slow-timeout.example", port = 443)

        SingboxProbeService(dao, dataStore, DelayingProfileProbe(delayMs = 2_000))
            .probeAndAutoSelect(listOf(profile))

        assertEquals(SingboxProbeService.LATENCY_FAILED, dao.latencies[12L])
        assertEquals(SingboxProbeService.PROBE_ERROR_TIMEOUT, dao.errors[12L])
        assertNull(prefsFlow.value[selectedProfileKey])
    }

    @Test
    fun `probeAndAutoSelect passes sorted DNS and global IPv6 to profile probe`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(
            mutablePreferencesOf(
                dnsServersKey to setOf("9.9.9.9", "149.112.112.112", "1.1.1.1"),
            ),
        )
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val probe = SettingsRecordingProfileProbe()
        val profile = makeProfile(id = 11L, host = "dns.example", port = 443)
        val settingsRepository = StaticSettingsRepository(SettingsModel(ipv6Enabled = true))

        SingboxProbeService(dao, dataStore, probe, settingsRepository).probeAndAutoSelect(listOf(profile))

        assertEquals(listOf("1.1.1.1", "149.112.112.112", "9.9.9.9"), probe.settings.single().dnsServers)
        assertTrue(probe.settings.single().ipv6Enabled)
        assertEquals(19, dao.latencies[11L])
    }

    @Test
    fun `probeAndAutoSelect breaks equal latency ties by input order not completion order`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val first = makeProfile(id = 1L, host = "first.example", port = 443)
        val second = makeProfile(id = 2L, host = "second.example", port = 443)

        val probe = object : SingboxProfileProbe {
            override suspend fun probeLatencyMs(bean: AbstractBean, settings: SingboxProfileProbeSettings): Int {
                when (bean.serverAddress) {
                    "first.example" -> delay(50)
                    "second.example" -> delay(0)
                }
                return 42
            }
        }

        SingboxProbeService(dao, dataStore, probe).probeAndAutoSelect(listOf(first, second))

        assertEquals(1L, prefsFlow.value[selectedProfileKey])
        assertEquals(42, dao.latencies[1L])
        assertEquals(42, dao.latencies[2L])
    }

    @Test
    fun `probe result is ignored when profile payload changes during the probe`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val profile = makeProfile(id = 17L, host = "replace-during-probe.example", port = 443)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val probe = object : SingboxProfileProbe {
            override suspend fun probeLatencyMs(bean: AbstractBean, settings: SingboxProfileProbeSettings): Int {
                started.complete(Unit)
                release.await()
                return 10
            }
        }

        val job = launch {
            SingboxProbeService(dao, dataStore, probe).probeAndAutoSelect(listOf(profile))
        }
        started.await()
        knownProfiles[profile.id] = profile.copy(beanBlob = byteArrayOf(1, 2, 3))
        release.complete(Unit)
        job.join()

        assertTrue(dao.latencies.isEmpty())
        assertNull(prefsFlow.value[selectedProfileKey])
    }

    @Test
    fun `probe auto selection does not overwrite a newer selection`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf(selectedProfileKey to 1L))
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val profile = makeProfile(id = 2L, host = "probe.example", port = 443)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val probe = object : SingboxProfileProbe {
            override suspend fun probeLatencyMs(bean: AbstractBean, settings: SingboxProfileProbeSettings): Int {
                started.complete(Unit)
                release.await()
                return 10
            }
        }

        val job = launch {
            SingboxProbeService(dao, dataStore, probe).probeAndAutoSelect(listOf(profile))
        }
        started.await()
        prefsFlow.value = mutablePreferencesOf(selectedProfileKey to 3L)
        release.complete(Unit)
        job.join()

        assertEquals(3L, prefsFlow.value[selectedProfileKey])
        assertNull(prefsFlow.value[beanKey])
        assertEquals(10, dao.latencies[2L])
    }

    @Test
    fun `probeAndAutoSelect with empty profiles leaves prefs and latencies unchanged`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()

        SingboxProbeService(dao, dataStore, FakeProfileProbe(emptyMap())).probeAndAutoSelect(emptyList())

        assertTrue(dao.latencies.isEmpty())
        assertNull(prefsFlow.value[selectedProfileKey])
        assertNull(prefsFlow.value[beanKey])
    }

    @Test
    fun `probeAndAutoSelect leaves selection unset when all probes fail`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val first = makeProfile(id = 1L, host = "first.example", port = 443)
        val second = makeProfile(id = 2L, host = "second.example", port = 443)

        SingboxProbeService(dao, dataStore, FakeProfileProbe(emptyMap())).probeAndAutoSelect(listOf(first, second))

        assertEquals(SingboxProbeService.LATENCY_FAILED, dao.latencies[1L])
        assertEquals(SingboxProbeService.LATENCY_FAILED, dao.latencies[2L])
        assertNull(prefsFlow.value[selectedProfileKey])
        assertNull(prefsFlow.value[beanKey])
    }

    @Test
    fun `probeAndAutoSelect marks negative latency failed when another profile succeeds`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val failed = makeProfile(id = 1L, host = "failed.example", port = 443)
        val successful = makeProfile(id = 2L, host = "ok.example", port = 443)

        SingboxProbeService(
            dao,
            dataStore,
            FakeProfileProbe(mapOf("failed.example:443" to -5, "ok.example:443" to 33)),
        ).probeAndAutoSelect(listOf(failed, successful))

        assertEquals(SingboxProbeService.LATENCY_FAILED, dao.latencies[1L])
        assertEquals(33, dao.latencies[2L])
        assertEquals(2L, prefsFlow.value[selectedProfileKey])
    }

    @Test
    fun `probeAndAutoSelect selects fastest successful profile and writes bean blob`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val slower = makeProfile(id = 1L, host = "slow.example", port = 443)
        val faster = makeProfile(id = 2L, host = "fast.example", port = 443)

        SingboxProbeService(
            dao,
            dataStore,
            FakeProfileProbe(mapOf("slow.example:443" to 100, "fast.example:443" to 10)),
        ).probeAndAutoSelect(listOf(slower, faster))

        assertEquals(2L, prefsFlow.value[selectedProfileKey])
        assertTrue(faster.beanBlob.contentEquals(prefsFlow.value[beanKey]))
        assertEquals(10, dao.latencies[2L])
    }

    @Test
    fun `probeAndAutoSelect can update latency without changing manual selection`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf(selectedProfileKey to 99L))
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val profile = makeProfile(id = 2L, host = "fresh.example", port = 443)

        SingboxProbeService(
            dao,
            dataStore,
            FakeProfileProbe(mapOf("fresh.example:443" to 12)),
        ).probeAndAutoSelect(
            profiles = listOf(profile),
            updateManualSelection = false,
        )

        assertEquals(99L, prefsFlow.value[selectedProfileKey])
        assertNull(prefsFlow.value[beanKey])
        assertEquals(12, dao.latencies[2L])
    }

    @Test
    fun `probeAndAutoSelect reports testing false when runtime skip short-circuits profile`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val events = mutableListOf<Pair<Long, Boolean>>()
        val profile = makeProfile(id = 5L, host = "busy.example", port = 443)

        SingboxProbeService(
            dao,
            dataStore,
            FakeProfileProbe(mapOf("busy.example:443" to LATENCY_SKIPPED_ACTIVE_RUNTIME)),
        ).probeAndAutoSelect(
            listOf(profile),
            onProfileTestingChanged = { id, testing -> events += id to testing },
        )

        assertEquals(listOf(5L to true, 5L to false), events)
        assertEquals(SingboxProbeService.LATENCY_FAILED, dao.latencies[5L])
        assertEquals(SingboxProbeService.PROBE_ERROR_RUNTIME_BUSY, dao.errors[5L])
    }

    @Test
    fun `probeAndAutoSelect cancellation stops before next profile`() = runTest {
        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = flowDataStore(prefsFlow)
        val dao = FakeProxyProfileDao()
        val first = makeProfile(id = 1L, host = "first.example", port = 443)
        val second = makeProfile(id = 2L, host = "second.example", port = 443)
        val probe = SlowProfileProbe()

        val job = launch {
            SingboxProbeService(dao, dataStore, probe).probeAndAutoSelect(listOf(first, second))
        }
        probe.started.await()
        job.cancel()
        job.join()

        assertTrue(probe.calls.get() in 1..2)
        assertEquals(
            mapOf(
                first.id to SingboxProbeService.LATENCY_FAILED,
                second.id to SingboxProbeService.LATENCY_FAILED,
            ),
            dao.latencies,
        )
        assertEquals(
            mapOf(
                first.id to SingboxProbeService.PROBE_ERROR_CANCELLED,
                second.id to SingboxProbeService.PROBE_ERROR_CANCELLED,
            ),
            dao.errors,
        )
        assertNull(prefsFlow.value[selectedProfileKey])
    }

    private fun makeProfile(
        id: Long,
        host: String,
        port: Int,
        type: String = "tcp",
    ): ProxyProfile =
        ProxyProfile(
            id = id,
            groupId = 1L,
            name = host,
            beanBlob = KryoSerializer.serialize(
                VLESSBean().apply {
                    uuid = "12345678-1234-1234-1234-123456789abc"
                    serverAddress = host
                    serverPort = port
                    this.type = type
                },
            ),
            protocolType = SingboxEngine.PROTOCOL_VLESS,
        ).also { knownProfiles[it.id] = it }

    private fun flowDataStore(prefsFlow: MutableStateFlow<Preferences>): DataStore<Preferences> =
        object : DataStore<Preferences> {
            override val data: Flow<Preferences> = prefsFlow

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                val updated = transform(prefsFlow.value)
                prefsFlow.value = updated
                return updated
            }
        }

    private class FakeProxyProfileDao : ProxyProfileDao {
        val latencies = ConcurrentHashMap<Long, Int>()
        val errors = ConcurrentHashMap<Long, String>()

        init {
            knownProfiles.clear()
        }

        override suspend fun updateProbeResult(id: Long, latency: Int, probeError: String?, lastProbeAt: Long) {
            latencies[id] = latency
            if (probeError == null) {
                errors.remove(id)
            } else {
                errors[id] = probeError
            }
        }

        override suspend fun updateProbeResultIfCurrent(
            id: Long,
            protocolType: Int,
            beanBlob: ByteArray,
            latency: Int,
            probeError: String?,
            lastProbeAt: Long,
        ): Int {
            val current = knownProfiles[id] ?: return 0
            if (current.protocolType != protocolType || !current.beanBlob.contentEquals(beanBlob)) return 0
            updateProbeResult(id, latency, probeError, lastProbeAt)
            return 1
        }

        override suspend fun insert(profile: ProxyProfile): Long = profile.id
        override suspend fun insertAll(profiles: List<ProxyProfile>) = Unit
        override suspend fun insertAllIgnoringConflicts(profiles: List<ProxyProfile>): List<Long> =
            profiles.map { it.id.takeIf { id -> id != 0L } ?: 1L }
        override suspend fun getById(id: Long): ProxyProfile? = knownProfiles[id]
        override fun getByIdFlow(id: Long): Flow<ProxyProfile?> = MutableStateFlow(null)
        override fun getAllFlow(): Flow<List<ProxyProfile>> = MutableStateFlow(emptyList())
        override fun getAllLimitedFlow(limit: Int): Flow<List<ProxyProfile>> = MutableStateFlow(emptyList())
        override fun getAutoCandidatesFlow(limit: Int): Flow<List<ProxyProfile>> = MutableStateFlow(emptyList())
        override fun getByGroupIdFlow(groupId: Long): Flow<List<ProxyProfile>> = MutableStateFlow(emptyList())
        override suspend fun getByGroupId(groupId: Long): List<ProxyProfile> = emptyList()
        override suspend fun getByGroupIdLimited(groupId: Long, limit: Int): List<ProxyProfile> = emptyList()
        override suspend fun getAutoCandidatesByGroupId(groupId: Long, limit: Int): List<ProxyProfile> =
            emptyList()
        override suspend fun deleteByGroupId(groupId: Long) = Unit
        override suspend fun getIdsByGroupId(groupId: Long): List<Long> = emptyList()
        override suspend fun deleteByIds(ids: List<Long>) = Unit
        override suspend fun replaceForGroup(groupId: Long, profiles: List<ProxyProfile>) = Unit
        override suspend fun countByGroupId(groupId: Long): Int = 0
        override suspend fun update(profile: ProxyProfile) = Unit
        override suspend fun delete(profile: ProxyProfile) = Unit
    }

    private class FakeProfileProbe(
        private val latenciesByAddress: Map<String, Int>,
    ) : SingboxProfileProbe {
        override suspend fun probeLatencyMs(bean: AbstractBean, settings: SingboxProfileProbeSettings): Int =
            latenciesByAddress[bean.displayAddress()] ?: SingboxProbeService.LATENCY_FAILED
    }

    private class TimeoutRecordingProfileProbe : SingboxProfileProbe {
        val timeouts = mutableListOf<Int>()
        override suspend fun probeLatencyMs(bean: AbstractBean, settings: SingboxProfileProbeSettings): Int {
            timeouts += settings.timeoutMs
            return 12
        }
    }

    private class SettingsRecordingProfileProbe : SingboxProfileProbe {
        val settings = mutableListOf<SingboxProfileProbeSettings>()
        override suspend fun probeLatencyMs(bean: AbstractBean, settings: SingboxProfileProbeSettings): Int {
            this.settings += settings
            return 19
        }
    }

    private class TrackingProfileProbe : SingboxProfileProbe {
        override suspend fun probeLatencyMs(bean: AbstractBean, settings: SingboxProfileProbeSettings): Int {
            delay(10)
            return 1
        }
    }

    private class TrackingBatchProfileProbe : SingboxProfileProbe, SingboxBatchProfileProbe {
        val startCount = AtomicInteger(0)
        val stopCount = AtomicInteger(0)
        val legacyCalls = AtomicInteger(0)
        val maxConcurrentTargets = AtomicInteger(0)
        val batchSizes = mutableListOf<Int>()

        override suspend fun probeLatencyMs(bean: AbstractBean, settings: SingboxProfileProbeSettings): Int {
            legacyCalls.incrementAndGet()
            return SingboxProbeService.LATENCY_FAILED
        }

        override suspend fun probeBatch(
            targets: List<SingboxProfileProbeTarget>,
            settings: SingboxProfileProbeSettings,
        ): Map<Long, SingboxProbeOutcome> {
            startCount.incrementAndGet()
            batchSizes += targets.size
            maxConcurrentTargets.updateAndGet { previous -> maxOf(previous, targets.size) }
            return try {
                delay(10)
                targets.associate { target ->
                    target.profileId to SingboxProbeOutcome.Success(target.profileId.toInt())
                }
            } finally {
                stopCount.incrementAndGet()
            }
        }
    }

    private class ActiveRuntimeBatchProfileProbe : SingboxProfileProbe, SingboxBatchProfileProbe {
        val batchCalls = AtomicInteger(0)
        val startCount = AtomicInteger(0)
        val stopCount = AtomicInteger(0)

        override suspend fun probeLatencyMs(bean: AbstractBean, settings: SingboxProfileProbeSettings): Int =
            LATENCY_SKIPPED_ACTIVE_RUNTIME

        override suspend fun probeBatch(
            targets: List<SingboxProfileProbeTarget>,
            settings: SingboxProfileProbeSettings,
        ): Map<Long, SingboxProbeOutcome> {
            batchCalls.incrementAndGet()
            return targets.associate { target ->
                target.profileId to SingboxProbeOutcome.SkippedActiveRuntime
            }
        }
    }

    private class DelayingProfileProbe(
        private val delayMs: Long,
    ) : SingboxProfileProbe {
        override suspend fun probeLatencyMs(bean: AbstractBean, settings: SingboxProfileProbeSettings): Int {
            delay(delayMs)
            return 1
        }
    }

    private class CountingProfileProbe : SingboxProfileProbe {
        val calls = AtomicInteger(0)
        override suspend fun probeLatencyMs(bean: AbstractBean, settings: SingboxProfileProbeSettings): Int {
            calls.incrementAndGet()
            return 1
        }
    }

    private class SlowProfileProbe : SingboxProfileProbe {
        val calls = AtomicInteger(0)
        val started = CompletableDeferred<Unit>()
        override suspend fun probeLatencyMs(bean: AbstractBean, settings: SingboxProfileProbeSettings): Int {
            calls.incrementAndGet()
            if (!started.isCompleted) started.complete(Unit)
            delay(10_000)
            return 1
        }
    }

    private class StaticSettingsRepository(model: SettingsModel) : SettingsRepository {
        override val settings: Flow<SettingsModel> = flowOf(model)
        override suspend fun setSplitMode(mode: SplitTunnelMode) = Unit
        override suspend fun setIpv6Enabled(enabled: Boolean) = Unit
        override suspend fun setAutoStart(enabled: Boolean) = Unit
        override suspend fun setManualEngine(engine: EngineId?) = Unit
        override suspend fun setUrnetworkEnabled(enabled: Boolean) = Unit
        override suspend fun setUrnetworkJwt(jwt: String?) = Unit
        override suspend fun setUrnetworkCountryCode(code: String?) = Unit
        override suspend fun setByedpiWinningArgs(args: String?) = Unit
        override suspend fun setByedpiDefaultAccepted(accepted: Boolean) = Unit
        override suspend fun setByedpiUseUiMode(enabled: Boolean) = Unit
        override suspend fun setByedpiUiSettings(settings: ByeDpiUiSettings) = Unit
        override suspend fun setCustomDnsServers(servers: List<String>) = Unit
        override suspend fun setHostsMode(mode: HostsMode) = Unit
        override suspend fun setHosts(hosts: List<String>) = Unit
        override suspend fun setUiLocaleTag(tag: String?) = Unit
        override suspend fun setAppMode(mode: AppMode) = Unit
        override suspend fun setKillswitchEnabled(enabled: Boolean) = Unit
        override suspend fun setAlwaysOnBannerDismissed(dismissed: Boolean) = Unit
        override suspend fun setTrafficMode(mode: TrafficMode) = Unit
    }
}
