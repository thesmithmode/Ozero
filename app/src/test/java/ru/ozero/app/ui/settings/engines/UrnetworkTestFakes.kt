package ru.ozero.app.ui.settings.engines

import com.bringyour.sdk.LocationsViewController
import ru.ozero.commonvpn.TunnelController
import ru.ozero.enginescore.EngineId
import ru.ozero.engineurnetwork.UrnetworkConfigStore
import ru.ozero.engineurnetwork.UrnetworkProvideControlMode
import ru.ozero.engineurnetwork.UrnetworkProvideNetworkMode
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import ru.ozero.engineurnetwork.UrnetworkWindowType
import java.util.concurrent.atomic.AtomicInteger

internal fun activeTunnel(): TunnelController {
    val tc = TunnelController()
    tc.onProbing()
    tc.onConnecting(EngineId.URNETWORK)
    tc.onEngineStarted(EngineId.URNETWORK, 1080)
    return tc
}

internal fun idleTunnel(): TunnelController = TunnelController()

internal fun fakeUrnetworkConfigStore(): UrnetworkConfigStore =
    ru.ozero.engineurnetwork.InMemoryUrnetworkConfigStore(
        ru.ozero.engineurnetwork.UrnetworkConfig(walletOverride = "0xWALLET"),
    )

internal fun fakeUrnetworkConfigStoreWithJwt(jwt: String = "test-jwt"): UrnetworkConfigStore =
    ru.ozero.engineurnetwork.InMemoryUrnetworkConfigStore(
        ru.ozero.engineurnetwork.UrnetworkConfig(walletOverride = "0xWALLET", byClientJwt = jwt),
    )

internal data class FakeLocationToken(override val countryCode: String?) : UrnetworkSdkBridge.LocationToken

internal class FakeUrnetworkBridge(
    private val connected: Boolean = false,
    private val initialLocation: UrnetworkSdkBridge.LocationToken? = null,
    private val peerCountValue: Int = 0,
    private val deviceAvailable: Boolean = false,
) : UrnetworkSdkBridge {
    val initDeviceCallCount = AtomicInteger(0)
    override suspend fun initDeviceForLocations(byClientJwt: String, walletAddress: String): Boolean {
        initDeviceCallCount.incrementAndGet()
        return deviceAvailable
    }
    override fun isDeviceAvailable(): Boolean = deviceAvailable
    override suspend fun start(
        walletAddress: String,
        apiUrl: String,
        connectUrl: String,
        byClientJwt: String,
    ): UrnetworkSdkBridge.StartResult = UrnetworkSdkBridge.StartResult.Success
    override suspend fun stop() = Unit
    override fun isRunning(): Boolean = connected
    override suspend fun attachTun(tunFd: Int): UrnetworkSdkBridge.AttachResult =
        UrnetworkSdkBridge.AttachResult.Success
    override fun connectTo(location: UrnetworkSdkBridge.LocationToken) = Unit
    override fun connectBestAvailable() = Unit
    override fun selectedLocation(): UrnetworkSdkBridge.LocationToken? = initialLocation
    override fun openLocationsViewController(): LocationsViewController? = null
    var lastAppliedWindowType: UrnetworkWindowType? = null
    var lastAppliedFixedIp: Boolean? = null
    var lastAppliedAllowDirect: Boolean? = null
    override fun applyPerformanceProfile(
        windowType: UrnetworkWindowType,
        fixedIpSize: Boolean,
        allowDirect: Boolean,
    ) {
        lastAppliedWindowType = windowType
        lastAppliedFixedIp = fixedIpSize
        lastAppliedAllowDirect = allowDirect
    }
    var lastProvideControlMode: UrnetworkProvideControlMode? = null
    override fun setProvideControlMode(mode: UrnetworkProvideControlMode) {
        lastProvideControlMode = mode
    }
    var lastProvideNetworkMode: UrnetworkProvideNetworkMode? = null
    override fun setProvideNetworkMode(mode: UrnetworkProvideNetworkMode) {
        lastProvideNetworkMode = mode
    }
    var lastPausedValue: Boolean? = null
    val setProvidePausedCallCount = AtomicInteger(0)
    private var _isProvidePaused: Boolean = false
    override fun setProvidePaused(paused: Boolean) {
        lastPausedValue = paused
        _isProvidePaused = paused
        setProvidePausedCallCount.incrementAndGet()
    }
    override fun isProvidePaused(): Boolean = _isProvidePaused
    val peerCountCallCount = AtomicInteger(0)
    override fun peerCount(): Int {
        peerCountCallCount.incrementAndGet()
        return peerCountValue
    }
    override fun unpaidByteCount(): Long = 0L
    override fun fetchTransferStats() = Unit
    override suspend fun fetchSubscriptionBalance(): UrnetworkSdkBridge.SubscriptionBalanceSnapshot? = null
    override suspend fun fetchAccountPoints(): UrnetworkSdkBridge.AccountPointsSnapshot? = null
}

internal class FakeSettingsRepo : ru.ozero.enginescore.settings.SettingsRepository {
    private val state = kotlinx.coroutines.flow.MutableStateFlow(
        ru.ozero.enginescore.settings.SettingsModel.DEFAULT,
    )
    override val settings: kotlinx.coroutines.flow.Flow<ru.ozero.enginescore.settings.SettingsModel> = state
    val countryCodeUpdates = mutableListOf<String?>()
    override suspend fun setSplitMode(mode: ru.ozero.enginescore.settings.SplitTunnelMode) = Unit
    override suspend fun setIpv6Enabled(enabled: Boolean) = Unit
    override suspend fun setAutoStart(enabled: Boolean) = Unit
    override suspend fun setManualEngine(engine: ru.ozero.enginescore.EngineId?) = Unit
    override suspend fun setUrnetworkEnabled(enabled: Boolean) = Unit
    override suspend fun setUrnetworkJwt(jwt: String?) = Unit
    override suspend fun setUrnetworkCountryCode(code: String?) {
        countryCodeUpdates += code
    }
    override suspend fun setByedpiWinningArgs(args: String?) = Unit
    override suspend fun setByedpiDefaultAccepted(accepted: Boolean) = Unit
    override suspend fun setCustomDnsServers(servers: List<String>) = Unit
    override suspend fun setHostsMode(mode: ru.ozero.enginescore.settings.HostsMode) = Unit
    override suspend fun setHosts(hosts: List<String>) = Unit
    override suspend fun setUiLocaleTag(tag: String?) = Unit
    override suspend fun setAppMode(mode: ru.ozero.enginescore.settings.AppMode) = Unit
    override suspend fun setKillswitchEnabled(enabled: Boolean) = Unit
    override suspend fun setAlwaysOnBannerDismissed(dismissed: Boolean) = Unit
}
