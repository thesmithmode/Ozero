package ru.ozero.engineurnetwork

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.PersistentLoggers
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class UrnetworkContractStatusObserver(
    private val bridge: UrnetworkSdkBridge,
    private val tunnelController: TunnelController,
    private val reportEngineFailure: (String) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    private val jobRef = AtomicReference<Job?>(null)
    private val warningSink = MutableSharedFlow<Warning>(replay = 1, extraBufferCapacity = 8)
    private val disconnectInFlight = AtomicBoolean(false)

    val warnings: SharedFlow<Warning> = warningSink.asSharedFlow()

    fun start() {
        val newJob = combine(
            bridge.contractStatus(),
            tunnelController.state,
        ) { contractStatus, tunnelState ->
            contractStatus to tunnelState
        }
            .distinctUntilChanged()
            .onEach { (contractStatus, tunnelState) ->
                handle(contractStatus, tunnelState)
            }
            .launchIn(scope)
        jobRef.getAndSet(newJob)?.cancel()
    }

    private fun handle(
        contractStatus: UrnetworkSdkBridge.ContractStatusSnapshot,
        tunnelState: TunnelState,
    ) {
        val urnetworkActive = when (tunnelState) {
            is TunnelState.Connecting -> tunnelState.engineId == EngineId.URNETWORK
            is TunnelState.Connected -> tunnelState.engineId == EngineId.URNETWORK
            else -> false
        }
        if (!urnetworkActive) {
            disconnectInFlight.set(false)
            return
        }
        if (contractStatus.insufficientBalance &&
            !contractStatus.premium &&
            disconnectInFlight.compareAndSet(false, true)
        ) {
            Log.i(TAG, "insufficientBalance=true → report engine failure")
            PersistentLoggers.warn(
                TAG,
                "URnetwork insufficient balance",
            )
            warningSink.tryEmit(Warning.InsufficientBalance)
            runCatching { reportEngineFailure(FAILURE_REASON_INSUFFICIENT_BALANCE) }
                .onFailure { PersistentLoggers.warn(TAG, "reportEngineFailure threw: ${it.message}") }
        }
    }

    fun stop() {
        jobRef.getAndSet(null)?.cancel()
        disconnectInFlight.set(false)
    }

    sealed interface Warning {
        data object InsufficientBalance : Warning
    }

    private companion object {
        const val TAG = "UrnetworkContractObs"
        const val FAILURE_REASON_INSUFFICIENT_BALANCE = "urnetwork-insufficient-balance"
    }
}
