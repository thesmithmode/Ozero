package ru.ozero.commondns

import android.util.Log

/**
 * Универсальный DNS-интерфейс. Реализации:
 *  - [DohResolver] — прямой OkHttp DoH (Cloudflare/Quad9).
 *  - [DnsResolverChain] — primary + fallback (Xray internal → OkHttp DoH и т.д.).
 *
 * Контракт: все реализации обязаны соблюдать privacy-минимум — не логировать
 * раскрываемые hostname, только endpoint/тип resolver-а.
 */
fun interface DnsResolver {
    suspend fun resolve(hostname: String): DohResult
}

/**
 * Цепочка резолверов: первый успешный возвращается, иначе пробуется следующий.
 *
 * Использование (E3.6): при активном Xray primary = XrayInternalResolver
 * (DNS через работающий Xray instance), fallback = OkHttp DoH. Когда Xray
 * не запущен — используется только OkHttp DoH.
 */
class DnsResolverChain(private val resolvers: List<DnsResolver>) : DnsResolver {

    init {
        require(resolvers.isNotEmpty()) { "цепочка не может быть пустой" }
    }

    override suspend fun resolve(hostname: String): DohResult {
        var lastFailure: DohResult.Failure = DohResult.Failure("не запускался")
        for ((index, resolver) in resolvers.withIndex()) {
            when (val r = resolver.resolve(hostname)) {
                is DohResult.Ok -> {
                    if (index > 0) Log.i(TAG, "fallback #$index OK")
                    return r
                }
                is DohResult.Failure -> {
                    Log.w(TAG, "resolver #$index fail: ${r.reason}")
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

/** Адаптер DohResolver → DnsResolver для использования в [DnsResolverChain]. */
fun DohResolver.asDnsResolver(): DnsResolver = DnsResolver { resolve(it) }
