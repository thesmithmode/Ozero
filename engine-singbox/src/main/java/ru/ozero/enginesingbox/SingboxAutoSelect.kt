package ru.ozero.enginesingbox

import ru.ozero.singboxroom.entity.ProxyProfile

fun prioritizeSingboxAutoProfiles(
    profiles: List<ProxyProfile>,
    limit: Int,
): List<ProxyProfile> {
    if (limit <= 0 || profiles.isEmpty()) return emptyList()
    return profiles.take(limit)
}

object SingboxLatency {
    const val LATENCY_UNTESTED = -1
    const val LATENCY_FAILED = -2
}
