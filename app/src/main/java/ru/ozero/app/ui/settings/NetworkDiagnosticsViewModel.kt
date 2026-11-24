package ru.ozero.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import ru.ozero.commonnet.AndroidNetworkDiagnostics
import ru.ozero.commonnet.NetworkDiagnosticsReport
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import javax.inject.Inject

@HiltViewModel
class NetworkDiagnosticsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val controller: TunnelController,
) : ViewModel() {
    private val diagnostics = AndroidNetworkDiagnostics(context)

    val canRun = combine(controller.state, controller.killswitchActive, controller.switching) { state, blocked, switching ->
        state == TunnelState.Idle && !blocked && switching == null
    }

    suspend fun run(): NetworkDiagnosticsReport? = diagnostics.run {
        controller.state.value == TunnelState.Idle &&
            !controller.killswitchActive.value &&
            controller.switching.value == null
    }
}
