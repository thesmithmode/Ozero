package ru.ozero.engineurnetwork

import android.util.Log
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Sub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.ozero.enginescore.PersistentLoggers
import java.util.concurrent.atomic.AtomicReference

internal class UrnetworkContractStatusListener {

    private val subRef = AtomicReference<Sub?>(null)
    private val flow = MutableStateFlow(UrnetworkSdkBridge.ContractStatusSnapshot.UNKNOWN)

    val status: StateFlow<UrnetworkSdkBridge.ContractStatusSnapshot> = flow.asStateFlow()

    fun attach(device: DeviceLocal) {
        refresh(device)
        runCatching {
            val sub = device.addContractStatusChangeListener { refresh(device) }
            subRef.set(sub)
            Log.i(TAG, "contractStatus listener attached")
        }.onFailure {
            PersistentLoggers.warn(TAG, "addContractStatusChangeListener threw: ${it.message}")
        }
    }

    fun detach() {
        subRef.getAndSet(null)?.also { sub ->
            runCatching { sub.close() }
                .onFailure { PersistentLoggers.warn(TAG, "contractStatus sub.close threw: ${it.message}") }
        }
        flow.value = UrnetworkSdkBridge.ContractStatusSnapshot.UNKNOWN
    }

    private fun refresh(device: DeviceLocal) {
        val cs = runCatching { device.contractStatus }.getOrNull() ?: return
        val snapshot = UrnetworkSdkBridge.ContractStatusSnapshot(
            insufficientBalance = runCatching { cs.insufficientBalance }.getOrDefault(false),
            noPermission = runCatching { cs.noPermission }.getOrDefault(false),
            premium = runCatching { cs.premium }.getOrDefault(false),
        )
        flow.value = snapshot
        Log.i(
            TAG,
            "contractStatus insufficient=${snapshot.insufficientBalance} " +
                "noPerm=${snapshot.noPermission} premium=${snapshot.premium}",
        )
    }

    private companion object {
        const val TAG = "UrnetworkContractStatus"
    }
}
