package ru.ozero.app.selfupdate

import okhttp3.CertificatePinner
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GithubPinnedClientTest {

    @Test
    fun clientHasCertificatePinnerSet() {
        val client = GithubPinnedClient.create()
        assertNotEquals(CertificatePinner.DEFAULT, client.certificatePinner)
    }

    @Test
    fun pinnerCoversApiGithubHost() {
        val client = GithubPinnedClient.create()
        val pinsForHost = client.certificatePinner.findMatchingPins("api.github.com")
        assertTrue(pinsForHost.isNotEmpty(), "должны быть pins для api.github.com")
    }

    @Test
    fun pinnerDoesNotCoverArbitraryHost() {
        val client = GithubPinnedClient.create()
        val foreign = client.certificatePinner.findMatchingPins("evil.example.com")
        assertEquals(0, foreign.size)
    }

    @Test
    fun pinsAreLeafOnlySha256Format() {
        val pins = GithubPinnedClient.pins()
        assertEquals(listOf("sha256/tt9RksdSBGiieTiyWkU8g3MOmCrfMcvXDGC4ZALs9rg="), pins)
        for (pin in pins) {
            assertTrue(pin.startsWith("sha256/"), "pin должен быть в формате sha256/<base64>: $pin")
            val b64 = pin.removePrefix("sha256/")
            assertEquals(44, b64.length, "ожидается 44-символьный base64: $pin")
        }
    }

    @Test
    fun pinnerRejectsEmptyChain() {
        val client = GithubPinnedClient.create()
        val ex = runCatching {
            client.certificatePinner.check("api.github.com", emptyList())
        }.exceptionOrNull()
        assertTrue(ex != null, "пустая цепочка должна быть отвергнута")
    }

    @Test
    fun urlBuilderAcceptsHttpsApiGithub() {
        val url = "https://${GithubPinnedClient.host()}/repos/owner/repo/releases/latest".toHttpUrl()
        assertEquals("api.github.com", url.host)
        assertEquals("https", url.scheme)
    }
}
