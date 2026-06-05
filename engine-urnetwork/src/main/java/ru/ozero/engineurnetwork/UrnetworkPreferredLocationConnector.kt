package ru.ozero.engineurnetwork

import android.util.Log
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.ConnectLocationList
import com.bringyour.sdk.ConnectViewController
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.FilteredLocations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.ozero.enginescore.PersistentLoggers
import java.util.concurrent.atomic.AtomicBoolean

internal class UrnetworkPreferredLocationConnector(
    private val bridgeScope: CoroutineScope,
) {
    private val resolving = AtomicBoolean(false)

    fun connect(
        selection: UrnetworkLocationSelection,
        device: DeviceLocal,
        cv: ConnectViewController,
    ) {
        if (!resolving.compareAndSet(false, true)) return
        val locVc = runCatching { device.openLocationsViewController() }.getOrNull()
        if (locVc == null) {
            PersistentLoggers.warn(TAG, "openLocationsViewController null fallback connectBestAvailable")
            bridgeScope.launch(Dispatchers.Main.immediate) {
                runCatching { cv.connectBestAvailable() }
            }
            resolving.set(false)
            return
        }
        val attached = AtomicBoolean(false)
        fun finish() {
            resolving.set(false)
        }
        val timeoutJob = bridgeScope.launch(Dispatchers.Main.immediate) {
            delay(PREFERRED_COUNTRY_TIMEOUT_MS)
            if (attached.compareAndSet(false, true)) {
                runCatching { cv.connectBestAvailable() }
                runCatching { locVc.stop() }
                runCatching { locVc.close() }
                finish()
                PersistentLoggers.warn(
                    TAG,
                    "preferred ${selection.summary()} timeout (${PREFERRED_COUNTRY_TIMEOUT_MS}ms) fallback connectBestAvailable",
                )
            }
        }
        runCatching {
            locVc.addFilteredLocationsListener { filtered, _ ->
                bridgeScope.launch(Dispatchers.Main.immediate) {
                    if (filtered == null) return@launch
                    val match = findBestMatch(filtered, selection) ?: return@launch
                    if (attached.compareAndSet(false, true)) {
                        timeoutJob.cancel()
                        runCatching { cv.connect(match) }
                            .onSuccess { }
                            .onFailure { PersistentLoggers.warn(TAG, "connect(match) threw: ${it.message}") }
                        Log.i(TAG, "preferred ${selection.summary()} matched connected")
                        runCatching { locVc.stop() }
                        runCatching { locVc.close() }
                        finish()
                    }
                }
            }
            locVc.start()
            locVc.filterLocations("")
        }.onFailure { t ->
            if (attached.compareAndSet(false, true)) {
                timeoutJob.cancel()
                bridgeScope.launch(Dispatchers.Main.immediate) {
                    PersistentLoggers.warn(TAG, "locVc setup failed: ${t.message} fallback connectBestAvailable")
                    runCatching { cv.connectBestAvailable() }
                    runCatching { locVc.stop() }
                    runCatching { locVc.close() }
                    finish()
                }
            }
        }
    }

    private fun findBestMatch(
        filtered: FilteredLocations,
        selection: UrnetworkLocationSelection,
    ): ConnectLocation? {
        val cc = selection.countryCode?.uppercase()
        if (cc == null) {
            PersistentLoggers.warn(
                TAG,
                "findBestMatch: city/region match without countryCode is forbidden - wrong-country connect",
            )
            return null
        }
        if (!selection.city.isNullOrBlank()) {
            findIn(filtered.cities, cc) { loc ->
                runCatching { loc.name }.getOrNull()?.equals(selection.city, ignoreCase = true) == true
            }?.let { return it }
        }
        if (!selection.region.isNullOrBlank()) {
            findIn(filtered.regions, cc) { loc ->
                runCatching { loc.name }.getOrNull()?.equals(selection.region, ignoreCase = true) == true
            }?.let { return it }
        }
        return findIn(filtered.countries, cc) { true }
    }

    private inline fun findIn(
        list: ConnectLocationList?,
        countryCode: String?,
        predicate: (ConnectLocation) -> Boolean,
    ): ConnectLocation? {
        if (list == null) return null
        val n = list.len()
        for (i in 0 until n) {
            val loc = list.get(i) ?: continue
            if (countryCode != null &&
                runCatching { loc.countryCode }.getOrNull()?.uppercase() != countryCode
            ) {
                continue
            }
            if (predicate(loc)) return loc
        }
        return null
    }

    private companion object {
        const val TAG = "UrnetworkPreferredLoc"
        const val PREFERRED_COUNTRY_TIMEOUT_MS = 8_000L
    }
}
