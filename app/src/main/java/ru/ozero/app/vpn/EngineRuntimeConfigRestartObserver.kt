package ru.ozero.app.vpn

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
        exceptionHandler: CoroutineExceptionHandler,
        state: StateFlow<TunnelState>,
        restart: suspend (String) -> Boolean,
    ) {
        providers.forEach { provider ->
            observeFlow(
                scope = scope,
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
        changes: Flow<Any?>,
        engineId: EngineId,
        reason: String,
        includeStarting: Boolean = true,
        replayAfterStarting: Boolean = false,
        adoptedBaselineFrom: Any? = null,
        exceptionHandler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, _ -> },
        state: Flow<TunnelState>,
        restart: suspend (String) -> Boolean,
    ) {
        scope.launch(exceptionHandler, start = CoroutineStart.UNDISPATCHED) {
            val observerState = ObserveState()
            changes.distinctUntilChanged()
                .combine(state.distinctUntilChanged()) { change, tunnelState -> change to tunnelState }
                .collect { (current, tunnelState) ->
                    if (observerState.handlePendingRestart(
                            current = current,
                            tunnelState = tunnelState,
                            engineId = engineId,
                            restart = restart,
                        )
                    ) {
                        return@collect
                    }
                    observerState.handleCurrentChange(
                        current = current,
                        tunnelState = tunnelState,
                        engineId = engineId,
                        reason = reason,
                        includeStarting = includeStarting,
                        replayAfterStarting = replayAfterStarting,
                        adoptedBaselineFrom = adoptedBaselineFrom,
                        restart = restart,
                    )
                }
        }
    }

    private data class ObserveState(
        var baseline: Any? = UNSET,
        var pendingRestart: PendingRestart? = null,
    ) {
        suspend fun handlePendingRestart(
            current: Any?,
            tunnelState: TunnelState,
            engineId: EngineId,
            restart: suspend (String) -> Boolean,
        ): Boolean {
            val pending = pendingRestart ?: return false
            return when {
                current == pending.baseline -> {
                    pendingRestart = null
                    true
                }
                connectedEngine(tunnelState) == engineId && current == pending.fingerprint -> {
                    pendingRestart = if (restart(pending.reason)) {
                        baseline = current
                        null
                    } else {
                        pending
                    }
                    true
                }
                startingEngine(tunnelState) == engineId -> {
                    pendingRestart = pending.copy(fingerprint = current)
                    true
                }
                else -> false.also { pendingRestart = null }
            }
        }

        suspend fun handleCurrentChange(
            current: Any?,
            tunnelState: TunnelState,
            engineId: EngineId,
            reason: String,
            includeStarting: Boolean,
            replayAfterStarting: Boolean,
            adoptedBaselineFrom: Any?,
            restart: suspend (String) -> Boolean,
        ) {
            if (current == baseline) return
            if (isFreshBaseline(previous = baseline, adoptedBaselineFrom = adoptedBaselineFrom)) {
                baseline = current
                return
            }
            if (activeEngine(tunnelState, includeStarting) == engineId) {
                if (restart(reason)) baseline = current
            } else if (shouldReplay(replayAfterStarting, includeStarting, tunnelState, engineId)) {
                pendingRestart = PendingRestart(
                    reason = reason,
                    fingerprint = current,
                    baseline = baseline,
                )
            } else {
                baseline = current
            }
        }

        private fun isFreshBaseline(previous: Any?, adoptedBaselineFrom: Any?): Boolean =
            previous === UNSET || previous == adoptedBaselineFrom

        private fun shouldReplay(
            replayAfterStarting: Boolean,
            includeStarting: Boolean,
            tunnelState: TunnelState,
            engineId: EngineId,
        ): Boolean {
            return replayAfterStarting &&
                !includeStarting &&
                startingEngine(tunnelState) == engineId
        }
    }

    private data class PendingRestart(
        val reason: String,
        val fingerprint: Any?,
        val baseline: Any?,
    )

    private companion object {
        private val UNSET = Any()
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
