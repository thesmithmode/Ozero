package ru.ozero.app.vpn

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EngineRuntimeConfigProvider
import javax.inject.Inject

class EngineRuntimeConfigRestartObserver @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards EngineRuntimeConfigProvider>,
) {
    fun start(
        scope: CoroutineScope,
        lifecycle: Lifecycle,
        exceptionHandler: CoroutineExceptionHandler,
        state: StateFlow<TunnelState>,
        restart: suspend (String) -> Unit,
    ) {
        providers.forEach { provider ->
            observeFlow(
                scope = scope,
                lifecycle = lifecycle,
                changes = provider.changes,
                engineId = provider.engineId,
                reason = provider.restartReason,
                includeStarting = provider.includeStarting,
                replayAfterStarting = provider.replayAfterStarting,
                adoptedBaselineFrom = provider.adoptedBaselineFrom,
                exceptionHandler = exceptionHandler,
                state = state,
                restart = restart,
            )
        }
    }

    internal fun observeFlow(
        scope: CoroutineScope,
        lifecycle: Lifecycle?,
        changes: Flow<Any?>,
        engineId: EngineId,
        reason: String,
        includeStarting: Boolean = true,
        replayAfterStarting: Boolean = false,
        adoptedBaselineFrom: Any? = null,
        exceptionHandler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, _ -> },
        state: Flow<TunnelState>,
        restart: suspend (String) -> Unit,
    ) {
        scope.launch(exceptionHandler, start = CoroutineStart.UNDISPATCHED) {
            val lifecycleFlow = if (lifecycle != null) {
                changes.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            } else {
                changes
            }
            var baseline: Any? = UNSET
            var pending: PendingRestart? = null
            lifecycleFlow.distinctUntilChanged()
                .combine(state.distinctUntilChanged()) { change, tunnelState -> change to tunnelState }
                .collect { (current, tunnelState) ->
                    val pendingRestart = pending
                    if (pendingRestart != null) {
                        when {
                            current == pendingRestart.baseline -> pending = null
                            connectedEngine(tunnelState) == engineId && current == pendingRestart.fingerprint -> {
                                pending = null
                                baseline = current
                                restart(pendingRestart.reason)
                                return@collect
                            }
                            startingEngine(tunnelState) == engineId -> {
                                pending = pendingRestart.copy(fingerprint = current)
                            }
                            else -> pending = null
                        }
                    }
                    if (current == baseline) return@collect
                    val previous = baseline
                    if (previous === UNSET) {
                        baseline = current
                        return@collect
                    }
                    if (previous == adoptedBaselineFrom) {
                        baseline = current
                        return@collect
                    }
                    if (activeEngine(tunnelState, includeStarting) == engineId) {
                        baseline = current
                        restart(reason)
                    } else if (replayAfterStarting && !includeStarting && startingEngine(tunnelState) == engineId) {
                        pending = PendingRestart(
                            reason = reason,
                            fingerprint = current,
                            baseline = previous,
                        )
                    } else {
                        baseline = current
                    }
                }
        }
    }

    private fun activeEngine(state: TunnelState, includeStarting: Boolean): EngineId? = when (state) {
        is TunnelState.Connected -> state.engineId
        is TunnelState.Connecting -> state.engineId.takeIf { includeStarting }
        is TunnelState.Probing -> state.engineId.takeIf { includeStarting }
        else -> null
    }

    private fun startingEngine(state: TunnelState): EngineId? = when (state) {
        is TunnelState.Connecting -> state.engineId
        is TunnelState.Probing -> state.engineId
        else -> null
    }

    private fun connectedEngine(state: TunnelState): EngineId? = (state as? TunnelState.Connected)?.engineId

    private data class PendingRestart(
        val reason: String,
        val fingerprint: Any?,
        val baseline: Any?,
    )

    private companion object {
        private val UNSET = Any()
    }
}
