package ru.ozero.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.ozero.app.logging.LogcatReader
import ru.ozero.app.selfupdate.UpdateInstallEvent
import ru.ozero.app.selfupdate.UpdateInstallEventBus
import ru.ozero.app.settings.UserFlagsRepository
import ru.ozero.app.subscription.HarvestWorker
import ru.ozero.app.subscription.ServerImportService
import android.widget.Toast
import ru.ozero.app.ui.MainScreen
import ru.ozero.app.ui.MainViewModel
import ru.ozero.app.ui.about.AboutScreen
import ru.ozero.app.ui.diag.DiagnosticsScreen
import ru.ozero.app.ui.logs.BootLogScreen
import ru.ozero.app.ui.logs.LogsScreen
import ru.ozero.app.ui.onboarding.OnboardingScreen
import ru.ozero.app.ui.permission.BatteryOptimization
import ru.ozero.app.ui.servers.ServersScreen
import ru.ozero.app.ui.settings.SettingsScreen
import ru.ozero.app.ui.splittunnel.SplitTunnelScreen
import ru.ozero.app.ui.theme.OzeroTheme
import ru.ozero.commonvpn.OzeroVpnService
import javax.inject.Inject

enum class TopScreen { Main, Settings, Diagnostics, SplitTunnel, Servers, About, Logs, BootLog }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var userFlags: UserFlagsRepository

    @Inject lateinit var serverImporter: ServerImportService

    @Inject lateinit var logcatReader: LogcatReader

    private val batteryPromptLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.i(TAG, "battery prompt result code=${result.resultCode}")
        }

    private val updateConfirmLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.i(TAG, "update confirm result code=${result.resultCode}")
        }

    private val vpnPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.onConnectClick()
                viewModel.onVpnPermissionGranted()
                startVpnService()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { logcatReader.start() }.onFailure { Log.w(TAG, "logcatReader.start failed", it) }
        runCatching { HarvestWorker.enqueueUnique(applicationContext) }
            .onFailure { Log.w(TAG, "HarvestWorker.enqueueUnique failed", it) }
        observeSelfUpdateEvents()
        if (savedInstanceState == null) {
            handleSubscriptionIntent(intent)
        }
        setContent {
            OzeroTheme {
                var checked by rememberSaveable { mutableStateOf(false) }
                var showOnboarding by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    val completed = userFlags.isOnboardingCompleted()
                    showOnboarding = !completed
                    checked = true
                }
                if (!checked) return@OzeroTheme
                if (showOnboarding) {
                    OnboardingScreen(onCompleted = { showOnboarding = false })
                    return@OzeroTheme
                }
                var screen by rememberSaveable { mutableStateOf(TopScreen.Main) }
                when (screen) {
                    TopScreen.Settings ->
                        SettingsScreen(
                            onBack = { screen = TopScreen.Main },
                            onOpenAllowedApps = { screen = TopScreen.SplitTunnel },
                            onOpenServers = { screen = TopScreen.Servers },
                            onOpenAbout = { screen = TopScreen.About },
                            onOpenLogs = { screen = TopScreen.Logs },
                            onOpenBootLog = { screen = TopScreen.BootLog },
                        )
                    TopScreen.Logs ->
                        LogsScreen(onBack = { screen = TopScreen.Settings })
                    TopScreen.BootLog ->
                        BootLogScreen(onBack = { screen = TopScreen.Settings })
                    TopScreen.Diagnostics ->
                        DiagnosticsScreen(onBack = { screen = TopScreen.Main })
                    TopScreen.SplitTunnel ->
                        SplitTunnelScreen(onBack = { screen = TopScreen.Settings })
                    TopScreen.Servers ->
                        ServersScreen(onBack = { screen = TopScreen.Settings })
                    TopScreen.About ->
                        AboutScreen(onBack = { screen = TopScreen.Settings })
                    TopScreen.Main ->
                        MainScreen(
                            viewModel = viewModel,
                            onConnectClick = ::onConnectClick,
                            onOpenSettings = { screen = TopScreen.Settings },
                            onOpenDiagnostics = { screen = TopScreen.Diagnostics },
                        )
                }
            }
        }
    }

    private fun onConnectClick() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            viewModel.onConnectClick()
            viewModel.onVpnPermissionGranted()
            startVpnService()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSubscriptionIntent(intent)
    }

    private fun handleSubscriptionIntent(intent: Intent?) {
        if (intent == null) return
        val raw: String? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND ->
                if (intent.type == "text/plain") {
                    intent.getStringExtra(Intent.EXTRA_TEXT)
                } else {
                    null
                }
            else -> null
        }
        if (raw.isNullOrBlank()) return
        Log.i(TAG, "subscription intent action=${intent.action}")
        lifecycleScope.launch {
            when (val result = serverImporter.import(raw)) {
                is ServerImportService.ImportResult.Ok ->
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.import_ok, result.entity.protocol),
                        Toast.LENGTH_SHORT,
                    ).show()

                is ServerImportService.ImportResult.Error ->
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.import_error, result.reason),
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }
    }

    private fun startVpnService() {
        startService(
            Intent(this, OzeroVpnService::class.java).apply {
                action = OzeroVpnService.ACTION_START
            },
        )
        maybeShowBatteryPrompt()
    }

    private fun maybeShowBatteryPrompt() {
        lifecycleScope.launch {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return@launch
            val isIgnoring = if (Build.VERSION.SDK_INT >= BatteryOptimization.MIN_SDK) {
                pm.isIgnoringBatteryOptimizations(packageName)
            } else {
                true
            }
            val alreadyShown = userFlags.isBatteryPromptShown()
            val state = BatteryOptimization.resolve(
                sdkInt = Build.VERSION.SDK_INT,
                isIgnoring = isIgnoring,
                alreadyShown = alreadyShown,
            )
            if (state == BatteryOptimization.State.NeedsPrompt) {
                userFlags.markBatteryPromptShown()
                runCatching {
                    @Suppress("BatteryLife")
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    batteryPromptLauncher.launch(intent)
                }.onFailure { Log.w(TAG, "battery prompt launch failed", it) }
            } else {
                Log.i(TAG, "battery prompt skipped state=$state")
            }
        }
    }

    private fun observeSelfUpdateEvents() {
        lifecycleScope.launch {
            UpdateInstallEventBus.events
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .onEach { event ->
                    when (event) {
                        is UpdateInstallEvent.PendingUserAction -> {
                            Log.i(TAG, "self-update PendingUserAction → launch confirm")
                            updateConfirmLauncher.launch(event.intent)
                        }
                        is UpdateInstallEvent.Success ->
                            Log.i(TAG, "self-update Success session=${event.sessionId}")
                        is UpdateInstallEvent.Failure ->
                            Log.w(
                                TAG,
                                "self-update Failure session=${event.sessionId} " +
                                    "status=${event.statusCode} message=${event.message}",
                            )
                    }
                }
                .collect()
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
