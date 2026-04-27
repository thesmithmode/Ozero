package ru.ozero.engineurnetwork

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.StartResult
import java.net.URL
import kotlin.test.assertContains
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class UrnetworkE2ETest {

    private val testJwt: String? = System.getenv("URNETWORK_TEST_JWT")

    @Before
    fun skipIfNoJwt() {
        Assume.assumeTrue(
            "URNETWORK_TEST_JWT не задан — пропускаем E2E (только для sandbox CI)",
            !testJwt.isNullOrBlank(),
        )
    }

    @Test
    fun connectAndProbeCloudflareTrace() = runBlocking {
        val delegate = UrnetworkSdkDelegate()
        val engine = UrnetworkEngine(delegate)

        val config = EngineConfig.Urnetwork(
            jwtToken = testJwt!!,
            mode = "consumer",
        )

                val startResult = engine.start(config)
        assertIs<StartResult.Success>(startResult, "URnetwork engine не смог подключиться")

        try {
                        val response = URL("https://cloudflare.com/cdn-cgi/trace").readText()

                        assertContains(response, "fl=", message = "Ответ не содержит fl= (поле Cloudflare)")
            assertContains(response, "ip=", message = "Ответ не содержит ip= (IP клиента)")
        } finally {
            engine.stop()
        }
    }
}
