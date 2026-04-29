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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineExceptionHandler
import ru.ozero.app.logging.AppLogger
import ru.ozero.app.logging.BootFileLogger
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
import ru.ozero.app.ui.logs.LogsScreen
import ru.ozero.app.ui.onboarding.OnboardingScreen
import ru.ozero.app.ui.permission.BatteryOptimization
import ru.ozero.app.ui.servers.ServersScreen
import ru.ozero.app.ui.settings.SettingsScreen
import ru.ozero.app.ui.splittunnel.SplitTunnelScreen
import ru.ozero.app.ui.theme.OzeroTheme
import ru.ozero.commonvpn.OzeroVpnService
import ru.ozero.coreorchestrator.OrchestratorState
import ru.ozero.security.SecurityStateHolder
import javax.inject.Inject

enum class TopScreen { Main, Settings, Diagnostics, SplitTunnel, Servers, About, Logs }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var userFlags: UserFlagsRepository

    @Inject lateinit var serverImporter: ServerImportService

    @Inject lateinit var logcatReader: LogcatReader

    private val batteryPromptLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            AppLogger.i(TAG, "battery prompt result code=${result.resultCode}")
        }

    private val updateConfirmLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            AppLogger.i(TAG, "update confirm result code=${result.resultCode}")
        }

    private val vpnPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            runCatching { BootFileLogger.info(TAG, "vpnPermissionLauncher result code=${result.resultCode}") }
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.onVpnPermissionGranted()
                startVpnService()
            } else {
                viewModel.onVpnPermissionDenied()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            runCatching { BootFileLogger.info(TAG, "notificationPermissionLauncher result granted=$granted") }
            proceedToVpnRequest()
        }

    private val safeUiCoroutineHandler = CoroutineExceptionHandler { _, throwable ->
        AppLogger.e(TAG, "uncaught coroutine in MainActivity", throwable)
        Toast.makeText(
            this,
            getString(R.string.import_error, throwable.message ?: "неизвестная ошибка"),
            Toast.LENGTH_LONG,
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        runCatching { BootFileLogger.info(TAG, "onCreate before super") }
        super.onCreate(savedInstanceState)
        runCatching { BootFileLogger.info(TAG, "onCreate after super (Hilt inject done)") }
        runCatching { logcatReader.start() }.onFailure { AppLogger.w(TAG, "logcatReader.start failed", it) }
        runCatching { HarvestWorker.enqueueUnique(applicationContext) }
            .onFailure { AppLogger.w(TAG, "HarvestWorker.enqueueUnique failed", it) }
        observeSelfUpdateEvents()
        if (savedInstanceState == null) {
            handleSubscriptionIntent(intent)
        }
        runCatching { BootFileLogger.info(TAG, "onCreate before setContent") }
        runCatching { BootFileLogger.info(TAG, "before composition trigger") }
        setContent {
            OzeroTheme {
                var checked by rememberSaveable { mutableStateOf(false) }
                var showOnboarding by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    runCatching { BootFileLogger.info(TAG, "composition entry (LaunchedEffect first run)") }
                    val completed = runCatching { userFlags.isOnboardingCompleted() }
                        .onFailure { AppLogger.w(TAG, "isOnboardingCompleted threw — пропускаю онбординг", it) }
                        .getOrDefault(true)
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
                        )
                    TopScreen.Logs ->
                        LogsScreen(onBack = { screen = TopScreen.Settings })
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
        runCatching { BootFileLogger.info(TAG, "onCreate after setContent") }
    }

    private fun onConnectClick() {
        val current = viewModel.state.value
        runCatching { BootFileLogger.info(TAG, "onConnectClick state=${current::class.simpleName}") }
        when (current) {
            is OrchestratorState.Connected -> {
                viewModel.onConnectClick()
                stopVpnService()
            }
            is OrchestratorState.Idle, is OrchestratorState.Failed -> requestVpnAndStart()
            else -> Unit
        }
    }

    private fun requestVpnAndStart() {
        if (SecurityStateHolder.isCompromised) {
            val reasons = SecurityStateHolder.compromised.value
            AppLogger.w(TAG, "VPN start refused — security compromised: $reasons")
            runCatching { BootFileLogger.info(TAG, "VPN start refused — security compromised: $reasons") }
            Toast.makeText(
                this,
                getString(R.string.security_blocked),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                runCatching { BootFileLogger.info(TAG, "POST_NOTIFICATIONS not granted → request") }
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        proceedToVpnRequest()
    }

    private fun proceedToVpnRequest() {
        viewModel.onConnectClick()
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            runCatching { BootFileLogger.info(TAG, "VpnService.prepare → permission dialog") }
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            runCatching { BootFileLogger.info(TAG, "VpnService.prepare null → startVpnService") }
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
        AppLogger.i(TAG, "subscription intent action=${intent.action}")
        lifecycleScope.launch(safeUiCoroutineHandler) {
            if (raw.length > MAX_IMPORT_URI_LENGTH) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.import_error, "слишком длинный URI"),
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            val result = runCatching { serverImporter.import(raw) }
                .getOrElse { throwable ->
                    AppLogger.e(TAG, "subscription import crashed", throwable)
                    ServerImportService.ImportResult.Error(
                        throwable.message ?: "неизвестная ошибка импорта",
                    )
                }
            when (result) {
                is ServerImportService.ImportResult.Ok -> {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.import_ok, result.entity.protocol),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                is ServerImportService.ImportResult.Error -> {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.import_error, result.reason),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun stopVpnService() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, OzeroVpnService::class.java).apply {
                action = OzeroVpnService.ACTION_STOP
            },
        )
    }

    private fun startVpnService() {
        runCatching { BootFileLogger.info(TAG, "startForegroundService(OzeroVpnService, ACTION_START)") }
        ContextCompat.startForegroundService(
            this,
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
                }.onFailure { AppLogger.w(TAG, "battery prompt launch failed", it) }
            } else {
                AppLogger.i(TAG, "battery prompt skipped state=$state")
            }
        }
    }

    private fun observeSelfUpdateEvents() {
        lifecycleScope.launch(safeUiCoroutineHandler) {
            UpdateInstallEventBus.events
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .catch { throwable ->
                    AppLogger.e(TAG, "self-update events flow failed", throwable)
                }
                .onEach { event ->
                    when (event) {
                        is UpdateInstallEvent.PendingUserAction -> {
                            AppLogger.i(TAG, "self-update PendingUserAction → launch confirm")
                            updateConfirmLauncher.launch(event.intent)
                        }
                        is UpdateInstallEvent.Success ->
                            AppLogger.i(TAG, "self-update Success session=${event.sessionId}")
                        is UpdateInstallEvent.Failure ->
                            AppLogger.w(
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
        const val MAX_IMPORT_URI_LENGTH = 16 * 1024
    }
}
