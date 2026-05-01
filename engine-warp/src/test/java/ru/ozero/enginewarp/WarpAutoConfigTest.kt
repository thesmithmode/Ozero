package ru.ozero.enginewarp

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WarpAutoConfigTest {

    private val sampleResponse = """
        {
          "id": "abc123",
          "account": {
            "license": "TEST-LICENSE-KEY"
          },
          "config": {
            "interface": {
              "addresses": {
                "v4": "172.16.0.2",
                "v6": "2606:4700:110:8a36:1234:5678:9abc:def0"
              }
            },
            "peers": [
              {
                "public_key": "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=",
                "endpoint": {
                  "host": "engage.cloudflareclient.com:2408",
                  "v4": "162.159.193.10:2408",
                  "v6": "[2606:4700:d0::a29f:c101]:2408"
                }
              }
            ]
          }
        }
    """.trimIndent()

    private val keypairGen = StubWireguardKeyPairGenerator()

    @Test
    fun `register parses well-formed Cloudflare JSON to WarpConfig`() = runTest {
        val http = FakeHttpClient(Result.success(sampleResponse))
        val auto = CloudflareWarpAutoConfig(http, keypairGen)

        val result = auto.register()

        assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()?.message}")
        val cfg = result.getOrThrow()
        assertEquals("stub-priv-base64", cfg.privateKey)
        assertEquals("stub-pub-base64", cfg.publicKey)
        assertEquals("bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=", cfg.peerPublicKey)
        assertEquals("engage.cloudflareclient.com:2408", cfg.peerEndpoint)
        assertEquals("172.16.0.2/32", cfg.interfaceAddressV4)
        assertTrue(cfg.interfaceAddressV6.startsWith("2606:4700:110:8a36"))
        assertEquals("TEST-LICENSE-KEY", cfg.accountLicense)
    }

    @Test
    fun `register sends User-Agent okhttp and JSON body with pub key`() = runTest {
        val http = FakeHttpClient(Result.success(sampleResponse))
        val auto = CloudflareWarpAutoConfig(http, keypairGen)

        auto.register()

        val call = assertNotNull(http.lastCall)
        assertEquals("okhttp/3.12.1", call.userAgent)
        assertTrue(call.url.contains("/v0a2158/reg"))
        assertTrue(call.body.contains("\"key\":\"stub-pub-base64\""))
        assertTrue(call.body.contains("\"locale\":\"en_US\""))
        assertTrue(call.body.contains("\"model\":\"PC\""))
    }

    @Test
    fun `register network failure returns Result_failure`() = runTest {
        val http = FakeHttpClient(Result.failure(java.io.IOException("boom")))
        val auto = CloudflareWarpAutoConfig(http, keypairGen)

        val result = auto.register()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is java.io.IOException)
    }

    @Test
    fun `register malformed JSON missing peer public_key returns failure`() = runTest {
        val malformed = """{"id":"x","account":{"license":"L"},""" +
            """"config":{"interface":{"addresses":{"v4":"1.2.3.4","v6":"::1"}},""" +
            """"peers":[{"endpoint":{"host":"h:1"}}]}}"""
        val http = FakeHttpClient(Result.success(malformed))
        val auto = CloudflareWarpAutoConfig(http, keypairGen)

        val result = auto.register()

        assertTrue(result.isFailure)
    }

    private class FakeHttpClient(
        private val response: Result<String>,
    ) : HttpClient {
        data class Call(val url: String, val body: String, val userAgent: String)

        var lastCall: Call? = null
            private set

        override suspend fun postJson(
            url: String,
            body: String,
            userAgent: String,
        ): Result<String> {
            lastCall = Call(url, body, userAgent)
            return response
        }
    }
}
