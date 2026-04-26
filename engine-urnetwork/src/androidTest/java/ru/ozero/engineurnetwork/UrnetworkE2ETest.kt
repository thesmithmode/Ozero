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

/**
 * E2E instrumented тест для URnetwork engine.
 *
 * Требования:
 * - Env var URNETWORK_TEST_JWT должен быть задан (иначе тест пропускается)
 * - Реальный URnetworkSdk.aar должен быть подключён в engine-urnetwork/libs/
 * - Сетевой доступ к cloudflare.com/cdn-cgi/trace
 *
 * Запуск в CI: только когда URNETWORK_TEST_JWT задан в секретах.
 *
 * TODO (E15.4): полная реализация после сборки URnetworkSdk.aar в urnetwork-aar.yml.
 * Сейчас тест скипается из-за отсутствия JWT (в CI нет секрета).
 */
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

        // start
        val startResult = engine.start(config)
        assertIs<StartResult.Success>(startResult, "URnetwork engine не смог подключиться")

        try {
            // HTTP GET cloudflare trace
            val response = URL("https://cloudflare.com/cdn-cgi/trace").readText()

            // Проверяем стандартные поля Cloudflare trace
            assertContains(response, "fl=", message = "Ответ не содержит fl= (поле Cloudflare)")
            assertContains(response, "ip=", message = "Ответ не содержит ip= (IP клиента)")
        } finally {
            engine.stop()
        }
    }
}
