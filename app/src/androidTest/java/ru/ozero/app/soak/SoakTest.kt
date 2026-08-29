package ru.ozero.app.soak

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Debug
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import ru.ozero.app.di.SingboxSubscriptionEntryPoint
import ru.ozero.app.ui.settings.engines.singbox.SingboxProbeService
import ru.ozero.commonvpn.OzeroVpnService
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.TrafficMode
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginesingbox.SingboxEngine
import ru.ozero.enginesingbox.SingboxPrefs
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.V2RayFmt
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.dao.SubscriptionGroupDao
import ru.ozero.singboxroom.entity.ProxyProfile
import ru.ozero.singboxroom.entity.SubscriptionGroup
import ru.ozero.singboxsubscription.RawUpdater

@RunWith(AndroidJUnit4::class)
class SoakTest {

    @Test
    fun runSingboxTunSoak() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        Assume.assumeTrue("OZERO_SOAK must be 1", args.getString("OZERO_SOAK") == "1")

        val cyclesPerProtocol = args.getString("OZERO_SOAK_CYCLES")
            ?.toIntOrNull()
            ?: DEFAULT_CYCLES_PER_PROTOCOL
        require(cyclesPerProtocol in 1..MAX_CYCLES_PER_PROTOCOL)
        val requireReality = args.getString("OZERO_SOAK_REQUIRE_REALITY") == "1"
        val realityOnly = args.getString("OZERO_SOAK_REALITY_ONLY") == "1"
        val realityInput = if (requireReality) {
            SoakProfileInput(
                "vless_reality",
                requireNotNull(args.getString("OZERO_SOAK_VLESS_REALITY")),
                requireNotNull(args.getString("OZERO_SOAK_REALITY_TARGET")),
                requireNotNull(args.getString("OZERO_SOAK_REALITY_MARKER")),
            )
        } else {
            null
        }
        val targetContext = instrumentation.targetContext
        val testContext = instrumentation.context
        check(VpnService.prepare(targetContext) == null) {
            "VPN consent was not granted to the soak APK"
        }
        val dependencies = EntryPointAccessors.fromApplication(
            targetContext.applicationContext,
            SoakTestEntryPoint::class.java,
        )
        val profiles = if (realityOnly) {
            listOf(seedProfile(dependencies, requireNotNull(realityInput)))
        } else {
            val subscriptionProfiles = loadSubscriptionProfiles(dependencies)
            buildList {
                add(subscriptionProfiles.getValue("vless"))
                realityInput?.let { add(seedProfile(dependencies, it)) }
                add(subscriptionProfiles.getValue("vmess"))
                add(subscriptionProfiles.getValue("trojan"))
            }
        }
        dependencies.settingsRepository().setTrafficMode(TrafficMode.TUN)
        dependencies.settingsRepository().setManualEngine(EngineId.SINGBOX)
        dependencies.settingsRepository().setIpv6Enabled(false)
        dependencies.settingsRepository().setKillswitchEnabled(false)

        val successfulCycles = profiles.associateTo(linkedMapOf()) { it.protocol to 0 }
        var peakMemoryKb = 0L
        val startedAt = System.currentTimeMillis()
        Log.i(TAG, "start profiles=${profiles.size} cycles=$cyclesPerProtocol realityOnly=$realityOnly")
        try {
            profiles.forEach { soakProfile ->
                selectProfile(dependencies.singboxDataStore(), soakProfile.profile)
                repeat(cyclesPerProtocol) { cycle ->
                    Log.i(
                        TAG,
                        "cycle protocol=${soakProfile.protocol} index=${cycle + 1}/$cyclesPerProtocol phase=start",
                    )
                    stopVpn(targetContext, dependencies.tunnelController())
                    startVpn(targetContext)
                    awaitConnected(dependencies.tunnelController())
                    if (soakProfile.protocol == "vless" && cycle == 0) {
                        refreshSelectedSubscriptionMetadata(
                            dependencies,
                            soakProfile.profile,
                            dependencies.tunnelController(),
                        )
                    }
                    val probe = probeFromExternalUid(
                        targetContext,
                        testContext,
                        soakProfile.targetUrl,
                        soakProfile.expectedMarker,
                    )
                    check(probe.vpnTransport) { "external probe did not use Android VPN transport" }
                    check(probe.httpCode in 200..299) {
                        "external routed HTTP failed code=${probe.httpCode} stage=${probe.stage} " +
                            "exceptionClass=${probe.exceptionClass} sanitizedMessage=${probe.sanitizedMessage} " +
                            "elapsedMs=${probe.elapsedMs}"
                    }
                    check(probe.markerMatch) { "external routed HTTP returned an unexpected marker" }
                    successfulCycles[soakProfile.protocol] =
                        successfulCycles.getValue(soakProfile.protocol) + 1
                    peakMemoryKb = maxOf(peakMemoryKb, currentMemoryKb())
                    stopVpn(targetContext, dependencies.tunnelController())
                    Log.i(
                        TAG,
                        "cycle protocol=${soakProfile.protocol} index=${cycle + 1}/$cyclesPerProtocol phase=done",
                    )
                }
            }
            if (!realityOnly) {
                Log.i(TAG, "auto-select phase=start")
                selectAutoProfile(dependencies.singboxDataStore())
                startVpn(targetContext)
                awaitConnected(dependencies.tunnelController())
                val probe = probeFromExternalUid(
                    targetContext,
                    testContext,
                    DEFAULT_TARGET,
                    DEFAULT_MARKER,
                )
                check(probe.vpnTransport) {
                    "auto-select external probe did not use Android VPN transport"
                }
                check(probe.httpCode in 200..299) {
                    "auto-select routed HTTP failed code=${probe.httpCode}"
                }
                check(probe.markerMatch) { "auto-select routed HTTP returned an unexpected marker" }
                stopVpn(targetContext, dependencies.tunnelController())
                Log.i(TAG, "auto-select phase=done")
            }
        } finally {
            runCatching { stopVpn(targetContext, dependencies.tunnelController()) }
            writeMetrics(
                targetContext,
                cyclesPerProtocol,
                successfulCycles,
                peakMemoryKb,
                System.currentTimeMillis() - startedAt,
            )
            Log.i(TAG, "finish successfulCycles=$successfulCycles")
        }

        check(successfulCycles.values.all { it == cyclesPerProtocol })
    }

    private suspend fun loadSubscriptionProfiles(
        dependencies: SoakTestEntryPoint,
    ): Map<String, SoakProfile> {
        val groupId = dependencies.subscriptionGroupDao().insert(
            SubscriptionGroup(
                name = "Sing-box local subscription",
                subscriptionUrl = LOCAL_SUBSCRIPTION_URL,
                autoUpdate = false,
            ),
        )
        val group = requireNotNull(dependencies.subscriptionGroupDao().getById(groupId))
        check(subscriptionUpdater().refresh(group).getOrThrow() == 3) {
            "subscription must import exactly three supported profiles"
        }
        val refreshedGroup = requireNotNull(dependencies.subscriptionGroupDao().getById(groupId))
        check(refreshedGroup.lastServerCount == 3) { "subscription reported an unexpected server count" }
        val profiles = dependencies.proxyProfileDao().getByGroupId(groupId)
        check(profiles.size == 3) { "unsupported subscription profile was persisted" }
        return profiles.associate { profile ->
            val protocol = when (profile.protocolType) {
                0 -> "vless"
                1 -> "vmess"
                2 -> "trojan"
                else -> error("unexpected subscription protocol ${profile.protocolType}")
            }
            protocol to SoakProfile(protocol, profile, DEFAULT_TARGET, DEFAULT_MARKER)
        }
    }

    private suspend fun seedProfile(
        dependencies: SoakTestEntryPoint,
        input: SoakProfileInput,
    ): SoakProfile {
        val groupId = dependencies.subscriptionGroupDao().insert(
            SubscriptionGroup(name = "Sing-box Reality soak", autoUpdate = false),
        )
        val bean = parseProfile(input.uri)
        val profile = ProxyProfile(
            groupId = groupId,
            name = bean.javaClass.simpleName,
            beanBlob = KryoSerializer.serialize(bean),
            protocolType = protocolType(bean),
        )
        return SoakProfile(
            protocol = input.protocol,
            profile = profile.copy(id = dependencies.proxyProfileDao().insert(profile)),
            targetUrl = input.targetUrl,
            expectedMarker = input.expectedMarker,
        )
    }

    private suspend fun CoroutineScope.refreshSelectedSubscriptionMetadata(
        dependencies: SoakTestEntryPoint,
        selectedProfile: ProxyProfile,
        controller: TunnelController,
    ) {
        val group = requireNotNull(dependencies.subscriptionGroupDao().getById(selectedProfile.groupId))
        val lifecycleViolation = async(start = CoroutineStart.UNDISPATCHED) {
            controller.state.first { it !is TunnelState.Connected }
        }
        try {
            check(subscriptionUpdater().refresh(group).getOrThrow() == 3) {
                "active subscription refresh failed"
            }
            val selected = requireNotNull(dependencies.proxyProfileDao().getById(selectedProfile.id))
            check(selected.id == selectedProfile.id) { "active selected profile was replaced" }
            check(!selected.beanBlob.contentEquals(selectedProfile.beanBlob)) {
                "subscription refresh did not update the selected profile payload"
            }
            val transition = withTimeoutOrNull(RUNTIME_REFRESH_OBSERVATION_MS) { lifecycleViolation.await() }
            check(transition == null) { "display-only subscription refresh changed runtime state to $transition" }
        } finally {
            lifecycleViolation.cancel()
        }
        awaitConnected(controller)
    }

    private fun parseProfile(uri: String): AbstractBean = when {
        uri.startsWith("vless://") -> V2RayFmt.parseVLESS(uri)
        uri.startsWith("vmess://") -> V2RayFmt.parseVMess(uri)
        uri.startsWith("trojan://") -> V2RayFmt.parseTrojan(uri)
        else -> error("unsupported soak profile")
    }

    private fun protocolType(bean: AbstractBean): Int = when (bean) {
        is VLESSBean -> 0
        is VMessBean -> 1
        is TrojanBean -> 2
        else -> error("unsupported soak protocol")
    }

    private suspend fun selectProfile(dataStore: DataStore<Preferences>, profile: ProxyProfile) {
        dataStore.edit { prefs ->
            prefs[SingboxProbeService.SELECTED_PROFILE_KEY] = profile.id
            prefs[SingboxProbeService.BEAN_KEY] = profile.beanBlob
        }
        dataStore.data.first { it[SingboxProbeService.SELECTED_PROFILE_KEY] == profile.id }
    }

    private suspend fun selectAutoProfile(dataStore: DataStore<Preferences>) {
        dataStore.edit { prefs ->
            prefs[SingboxProbeService.SELECTED_PROFILE_KEY] = SingboxEngine.SELECTED_AUTO
            prefs.remove(SingboxProbeService.BEAN_KEY)
        }
        dataStore.data.first {
            it[SingboxProbeService.SELECTED_PROFILE_KEY] == SingboxEngine.SELECTED_AUTO
        }
    }

    private fun startVpn(context: Context) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, OzeroVpnService::class.java).setAction(OzeroVpnService.ACTION_START),
        )
    }

    private suspend fun awaitConnected(controller: TunnelController) {
        val state = withTimeout(START_TIMEOUT_MS) {
            controller.state.first { it is TunnelState.Connected || it is TunnelState.Failed }
        }
        check(state is TunnelState.Connected && state.engineId == EngineId.SINGBOX) {
            "Sing-box failed to connect: $state"
        }
    }

    private suspend fun stopVpn(context: Context, controller: TunnelController) {
        if (controller.state.value is TunnelState.Idle) return
        context.startService(
            Intent(context, OzeroVpnService::class.java).setAction(OzeroVpnService.ACTION_STOP),
        )
        withTimeout(STOP_TIMEOUT_MS) {
            controller.state.first { it is TunnelState.Idle }
        }
    }

    private suspend fun probeFromExternalUid(
        callbackContext: Context,
        probeContext: Context,
        targetUrl: String,
        expectedMarker: String,
    ): ExternalProbeResult {
        val result = CompletableDeferred<ExternalProbeResult>()
        val resultAction = "$EXTERNAL_PROBE_RESULT_ACTION.${System.nanoTime()}"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                result.complete(
                    ExternalProbeResult(
                        httpCode = intent.getIntExtra(SoakExternalProbeReceiver.RESULT_HTTP_CODE, -1),
                        vpnTransport = intent.getBooleanExtra(
                            SoakExternalProbeReceiver.RESULT_VPN_TRANSPORT,
                            false,
                        ),
                        markerMatch = intent.getBooleanExtra(
                            SoakExternalProbeReceiver.RESULT_MARKER_MATCH,
                            false,
                        ),
                        stage = intent.getStringExtra(SoakExternalProbeReceiver.RESULT_STAGE) ?: "unknown",
                        exceptionClass = intent.getStringExtra(
                            SoakExternalProbeReceiver.RESULT_EXCEPTION_CLASS,
                        ) ?: "unknown",
                        sanitizedMessage = intent.getStringExtra(
                            SoakExternalProbeReceiver.RESULT_SANITIZED_MESSAGE,
                        ) ?: "none",
                        elapsedMs = intent.getLongExtra(SoakExternalProbeReceiver.RESULT_ELAPSED_MS, -1L),
                    ),
                )
            }
        }
        ContextCompat.registerReceiver(
            callbackContext,
            receiver,
            IntentFilter(resultAction),
            ContextCompat.RECEIVER_EXPORTED,
        )
        try {
            probeContext.sendBroadcast(
                Intent(probeContext, SoakExternalProbeReceiver::class.java)
                    .putExtra(SoakExternalProbeReceiver.EXTRA_URL, targetUrl)
                    .putExtra(SoakExternalProbeReceiver.EXTRA_EXPECTED_MARKER, expectedMarker)
                    .putExtra(SoakExternalProbeReceiver.EXTRA_RESULT_ACTION, resultAction)
                    .putExtra(
                        SoakExternalProbeReceiver.EXTRA_RESULT_PACKAGE,
                        callbackContext.packageName,
                    ),
            )
            return withTimeout(PROBE_TIMEOUT_MS) { result.await() }
        } finally {
            callbackContext.unregisterReceiver(receiver)
        }
    }

    private fun currentMemoryKb(): Long {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss.toLong()
    }

    private fun writeMetrics(
        context: Context,
        cyclesPerProtocol: Int,
        successfulCycles: Map<String, Int>,
        peakMemoryKb: Long,
        durationMs: Long,
    ) {
        val output = requireNotNull(context.getExternalFilesDir(null)).resolve(METRICS_FILE)
        val successfulCyclesJson = successfulCycles.entries.joinToString(
            separator = ",",
            prefix = "{",
            postfix = "}",
        ) { (name, count) -> "\"$name\": $count" }
        output.writeText(
            """
            {
              "test": "SingboxTunSoak",
              "cycles_per_protocol": $cyclesPerProtocol,
              "successful_cycles": $successfulCyclesJson,
              "peak_memory_mb": ${peakMemoryKb / 1024.0},
              "duration_ms": $durationMs
            }
            """.trimIndent(),
        )
    }

    private data class ExternalProbeResult(
        val httpCode: Int,
        val vpnTransport: Boolean,
        val markerMatch: Boolean,
        val stage: String,
        val exceptionClass: String,
        val sanitizedMessage: String,
        val elapsedMs: Long,
    )

    private data class SoakProfile(
        val protocol: String,
        val profile: ProxyProfile,
        val targetUrl: String,
        val expectedMarker: String,
    )

    private data class SoakProfileInput(
        val protocol: String,
        val uri: String,
        val targetUrl: String,
        val expectedMarker: String,
    )

    private fun subscriptionUpdater(): RawUpdater =
        EntryPointAccessors.fromApplication(
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext,
            SingboxSubscriptionEntryPoint::class.java,
        ).rawUpdater()

    companion object {
        private const val DEFAULT_CYCLES_PER_PROTOCOL = 20
        private const val MAX_CYCLES_PER_PROTOCOL = 100
        private const val DEFAULT_TARGET = "http://10.0.2.2:18080/ozero-soak-marker"
        private const val LOCAL_SUBSCRIPTION_URL = "http://10.0.2.2:18080/subscription.txt"
        private const val DEFAULT_MARKER = "ozero-singbox-routed"
        private const val START_TIMEOUT_MS = 30_000L
        private const val STOP_TIMEOUT_MS = 10_000L
        private const val PROBE_TIMEOUT_MS = 15_000L
        private const val RUNTIME_REFRESH_OBSERVATION_MS = 2_000L
        private const val METRICS_FILE = "soak-metrics.json"
        private const val EXTERNAL_PROBE_RESULT_ACTION = "ru.ozero.app.soak.EXTERNAL_PROBE_RESULT"
        private const val TAG = "SingboxSoak"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SoakTestEntryPoint {
    fun settingsRepository(): SettingsRepository

    fun tunnelController(): TunnelController

    fun subscriptionGroupDao(): SubscriptionGroupDao

    fun proxyProfileDao(): ProxyProfileDao

    @SingboxPrefs
    fun singboxDataStore(): DataStore<Preferences>
}
