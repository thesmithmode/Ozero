package ru.ozero.app

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.work.WorkManager
import ru.ozero.app.data.CrashLogStore
import ru.ozero.app.logging.AppLogger
import ru.ozero.app.logging.BootDiagnostics
import ru.ozero.app.logging.BootFileLogger
import ru.ozero.app.logging.LogBuffer
import ru.ozero.app.relay.UrnetworkRelayCoordinator
import ru.ozero.app.ui.onboarding.FirstRunBootstrap
import ru.ozero.app.ui.splittunnel.AppListProvider
import ru.ozero.app.workers.SubscriptionUpdateWorker
import ru.ozero.singboxsubscription.GroupSeeder
import javax.inject.Inject

@HiltAndroidApp
class OzeroApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var logBuffer: LogBuffer

    @Inject lateinit var firstRunBootstrap: FirstRunBootstrap

    @Inject lateinit var appListProvider: AppListProvider

    @Inject lateinit var urnetworkRelayCoordinator: UrnetworkRelayCoordinator

    @Inject lateinit var urnetworkContractStatusObserver: ru.ozero.engineurnetwork.UrnetworkContractStatusObserver

    @Inject lateinit var groupSeeder: GroupSeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        runCatching {
            BootFileLogger.init(base)
            val crashStore = runCatching { CrashLogStore(base.filesDir) }.getOrNull()
            BootDiagnostics.installUncaughtHandler(
                crashSink = crashStore?.let { store -> { t, e -> store.write(t, e) } },
            )
            BootFileLogger.info(
                TAG,
                "attachBaseContext sdk=${Build.VERSION.SDK_INT} " +
                    "abi=${Build.SUPPORTED_ABIS.joinToString()} " +
                    "device=${Build.MANUFACTURER}/${Build.MODEL}",
            )
            BootDiagnostics.dumpExitReasons(base)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (isEngineSingboxProcess()) {
            return
        }
        if (isEngineWarpProcess()) {
            runCatching { System.loadLibrary("am-go") }
                .onFailure { BootFileLogger.error(TAG, "am-go eager load failed (engine_warp)", it) }
            return
        }
        runCatching { System.loadLibrary("gojni") }
            .onFailure { BootFileLogger.error(TAG, "gojni eager load failed (main)", it) }
        runCatching {
            AppLogger.attach(logBuffer)
        }.onFailure { BootFileLogger.error(TAG, "AppLogger.attach failed", it) }
        appScope.launch {
            runCatching { firstRunBootstrap.runIfFirstStart() }
                .onFailure { BootFileLogger.warn(TAG, "firstRunBootstrap.runIfFirstStart failed", it) }
        }
        appScope.launch {
            runCatching { appListProvider.loadApps() }
                .onFailure { BootFileLogger.warn(TAG, "appListProvider prewarm failed", it) }
        }
        runCatching { urnetworkRelayCoordinator.start() }
            .onFailure { BootFileLogger.warn(TAG, "urnetworkRelayCoordinator.start failed", it) }
        runCatching { urnetworkContractStatusObserver.start() }
            .onFailure { BootFileLogger.warn(TAG, "urnetworkContractStatusObserver.start failed", it) }
        runCatching { SubscriptionUpdateWorker.schedule(WorkManager.getInstance(this)) }
            .onFailure { BootFileLogger.warn(TAG, "SubscriptionUpdateWorker.schedule failed", it) }
        appScope.launch {
            runCatching { groupSeeder.seedPresets(loadPresetGroups()) }
                .onFailure { BootFileLogger.warn(TAG, "groupSeeder.seedPresets failed", it) }
        }
    }

    private fun loadPresetGroups(): List<GroupSeeder.PresetGroup> {
        val json = assets.open("singbox/preset_groups.json").bufferedReader().readText()
        val obj = org.json.JSONObject(json)
        val arr = obj.getJSONArray("groups")
        return (0 until arr.length()).map { i ->
            val g = arr.getJSONObject(i)
            GroupSeeder.PresetGroup(name = g.getString("name"), url = g.getString("url"))
        }
    }

    private fun isEngineSingboxProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= 28) {
            getProcessName()
        } else {
            readProcessNameLegacy() ?: return false
        }
        return name.endsWith(":engine_singbox")
    }

    private fun isEngineWarpProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= 28) {
            getProcessName()
        } else {
            readProcessNameLegacy() ?: return false
        }
        return name.endsWith(":engine_warp")
    }

    private fun readProcessNameLegacy(): String? = runCatching {
        val bytes = java.io.File("/proc/self/cmdline").readBytes()
        val end = bytes.indexOf(0.toByte()).takeIf { it >= 0 } ?: bytes.size
        String(bytes, 0, end, Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val TAG = "OzeroApp"
    }
}
