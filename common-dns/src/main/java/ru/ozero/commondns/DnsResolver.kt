package ru.ozero.commondns

import ru.ozero.enginescore.PersistentLoggers

fun interface DnsResolver {
    suspend fun resolve(hostname: String): DohResult
}

class DnsResolverChain(private val resolvers: List<DnsResolver>) : DnsResolver {

    init {
        require(resolvers.isNotEmpty()) { "цепочка не может быть пустой" }
    }

    override suspend fun resolve(hostname: String): DohResult {
        var lastFailure: DohResult.Failure = DohResult.Failure("не запускался")
        for ((index, resolver) in resolvers.withIndex()) {
            when (val r = resolver.resolve(hostname)) {
                is DohResult.Ok -> {
                    if (index > 0) PersistentLoggers.debug(TAG, "fallback #$index OK")
                    return r
                }
                is DohResult.Failure -> {
                    PersistentLoggers.warn(TAG, "resolver #$index fail: ${r.reason}")
                    lastFailure = r
                }
            }
        }
        return lastFailure
    }

    private companion object {
        const val TAG = "DnsResolverChain"
    }
}

fun DohResolver.asDnsResolver(): DnsResolver = DnsResolver { resolve(it) }
