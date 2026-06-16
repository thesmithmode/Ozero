package ru.ozero.enginesingbox

import ru.ozero.singboxroom.entity.ProxyProfile

fun prioritizeSingboxAutoProfiles(
    profiles: List<ProxyProfile>,
    limit: Int,
): List<ProxyProfile> {
    if (limit <= 0 || profiles.isEmpty()) return emptyList()
    val indexed = profiles.withIndex()
    val available = indexed
        .asSequence()
        .filter { it.value.latencyMs >= 0 }
        .sortedWith(compareBy<IndexedValue<ProxyProfile>> { it.value.latencyMs }.thenBy { it.index })
        .toList()
    val untested = indexed.filter { it.value.latencyMs == SingboxLatency.LATENCY_UNTESTED }
    val failed = indexed.filter { it.value.latencyMs < SingboxLatency.LATENCY_UNTESTED }
    val result = ArrayList<IndexedValue<ProxyProfile>>(limit)
    val untestedReserve = if (available.size >= limit && untested.isNotEmpty()) {
        minOf(UNTESTED_RESERVE, limit, untested.size)
    } else {
        0
    }
    val availablePrimary = (limit - untestedReserve).coerceAtLeast(0)
    result += available.take(availablePrimary)
    appendUntilLimit(result, untested, limit)
    appendUntilLimit(result, available.drop(availablePrimary), limit)
    appendUntilLimit(result, failed, limit)
    return result.map { it.value }
}

private fun appendUntilLimit(
    target: MutableList<IndexedValue<ProxyProfile>>,
    source: List<IndexedValue<ProxyProfile>>,
    limit: Int,
) {
    if (target.size >= limit) return
    target += source.take(limit - target.size)
}

object SingboxLatency {
    const val LATENCY_UNTESTED = -1
    const val LATENCY_FAILED = -2
}

private const val UNTESTED_RESERVE = 10
