package ru.ozero.commondns

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublicDnsServersTest {

    @Test
    fun `DOH_ENDPOINTS не содержит dns_google — pinning для него не настроен в DohResolver`() {
        assertFalse(
            PublicDnsServers.DOH_ENDPOINTS.any { it.contains("dns.google", ignoreCase = true) },
            "dns.google убран из DOH_ENDPOINTS пока DohResolver.defaultPinner не покрывает его SPKI — " +
                "иначе pinning fall-open и MITM проходит",
        )
    }

    @Test
    fun `DOH_ENDPOINTS содержит cloudflare и quad9 — оба покрыты defaultPinner`() {
        assertTrue(PublicDnsServers.DOH_ENDPOINTS.any { it.contains("cloudflare-dns.com") })
        assertTrue(PublicDnsServers.DOH_ENDPOINTS.any { it.contains("dns.quad9.net") })
    }

    @Test
    fun `все endpoints в DOH_ENDPOINTS используют HTTPS`() {
        PublicDnsServers.DOH_ENDPOINTS.forEach { endpoint ->
            assertTrue(
                endpoint.startsWith("https://"),
                "DoH endpoint должен использовать HTTPS: $endpoint",
            )
        }
    }
}
