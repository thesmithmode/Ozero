package ru.ozero.commondns

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DnsResolverChainTest {
    @Test
    fun returnsPrimaryResultWhenPrimarySucceeds() = runTest {
        val primary = DnsResolver { _ -> DohResult.Ok(listOf("1.2.3.4")) }
        val fallbackCalled = mutableListOf<String>()
        val fallback = DnsResolver { host ->
            fallbackCalled += host
            DohResult.Ok(listOf("9.9.9.9"))
        }
        val chain = DnsResolverChain(listOf(primary, fallback))
        val result = chain.resolve("example.com")

        assertTrue(result is DohResult.Ok)
        assertEquals(listOf("1.2.3.4"), (result as DohResult.Ok).addresses)
        assertTrue(fallbackCalled.isEmpty(), "fallback не должен вызываться")
    }

    @Test
    fun fallsBackWhenPrimaryFails() = runTest {
        val primary = DnsResolver { _ -> DohResult.Failure("xray не запущен") }
        val fallback = DnsResolver { _ -> DohResult.Ok(listOf("1.1.1.1")) }
        val chain = DnsResolverChain(listOf(primary, fallback))
        val result = chain.resolve("example.com")

        assertTrue(result is DohResult.Ok)
        assertEquals(listOf("1.1.1.1"), (result as DohResult.Ok).addresses)
    }

    @Test
    fun returnsLastFailureWhenAllFail() = runTest {
        val primary = DnsResolver { _ -> DohResult.Failure("primary fail") }
        val fallback = DnsResolver { _ -> DohResult.Failure("fallback fail", statusCode = 502) }
        val chain = DnsResolverChain(listOf(primary, fallback))
        val result = chain.resolve("example.com")

        assertTrue(result is DohResult.Failure)
        assertEquals("fallback fail", (result as DohResult.Failure).reason)
        assertEquals(502, result.statusCode)
    }

    @Test
    fun emptyChainNotAllowed() {
        assertFailsWith<IllegalArgumentException> { DnsResolverChain(emptyList()) }
    }

    @Test
    fun threeLevelChainTriesAllInOrder() = runTest {
        val seen = mutableListOf<Int>()
        fun stage(n: Int, ok: Boolean) = DnsResolver { _ ->
            seen += n
            if (ok) DohResult.Ok(listOf("10.0.0.$n")) else DohResult.Failure("stage $n fail")
        }
        val chain = DnsResolverChain(listOf(stage(1, false), stage(2, false), stage(3, true)))
        val result = chain.resolve("example.com")

        assertTrue(result is DohResult.Ok)
        assertEquals(listOf("10.0.0.3"), (result as DohResult.Ok).addresses)
        assertEquals(listOf(1, 2, 3), seen)
    }
}
