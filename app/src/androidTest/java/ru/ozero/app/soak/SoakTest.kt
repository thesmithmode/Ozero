package ru.ozero.app.soak

import android.content.Context
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Soak test: гоняет N HTTP запросов с 1-секундным интервалом и собирает метрики.
 *
 * В CI запускается с OZERO_SOAK_REQUESTS=100 (уменьшенный вариант, ~10 мин).
 * Полный прогон (24h) только вручную / локально с OZERO_SOAK_REQUESTS=86400.
 *
 * Аргументы инструментации:
 *   OZERO_SOAK=1             — включить тест
 *   OZERO_SOAK_REQUESTS=N    — количество запросов (default 100)
 *   OZERO_SOAK_TARGET=URL    — цель HTTP запросов (default http://connectivitycheck.gstatic.com/generate_204)
 */
@RunWith(AndroidJUnit4::class)
class SoakTest {

    companion object {
        private const val TAG = "SoakTest"
        private const val OUTPUT_FILE = "/sdcard/soak-metrics.json"
        private const val DEFAULT_TARGET = "http://connectivitycheck.gstatic.com/generate_204"
        private const val DEFAULT_REQUESTS = 100
        private const val ANR_THRESHOLD_MS = 5_000L
        private const val INTERVAL_MS = 1_000L
    }

    @Test
    fun runSoak() {
        val args = InstrumentationRegistry.getArguments()
        val soakEnabled = args.getString("OZERO_SOAK", "0") == "1" ||
            System.getenv("OZERO_SOAK") == "1"

        Assume.assumeTrue(
            "SoakTest пропущен: OZERO_SOAK не установлен в 1",
            soakEnabled
        )

        val requestCount = (args.getString("OZERO_SOAK_REQUESTS") ?: System.getenv("OZERO_SOAK_REQUESTS"))
            ?.toIntOrNull() ?: DEFAULT_REQUESTS
        val targetUrl = args.getString("OZERO_SOAK_TARGET")
            ?: System.getenv("OZERO_SOAK_TARGET")
            ?: DEFAULT_TARGET

        Log.i(TAG, "Запускаю soak: $requestCount запросов к $targetUrl с интервалом ${INTERVAL_MS}ms")

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val metrics = runSoakLoop(ctx, requestCount, targetUrl)

        val json = buildMetricsJson(metrics, requestCount, targetUrl)
        File(OUTPUT_FILE).writeText(json)

        Log.i(
            TAG,
            "Soak завершён. drops=${metrics.connectionDrops}, retryRate=${metrics.retryRate}%, " +
                "peakMemMb=${metrics.peakMemoryMb}, anrEvents=${metrics.anrLikeDelays}",
        )
        Log.i(TAG, "Метрики сохранены в $OUTPUT_FILE")
    }

    private fun runSoakLoop(ctx: Context, requestCount: Int, targetUrl: String): SoakMetrics {
        val successCount = AtomicInteger(0)
        val dropCount = AtomicInteger(0)
        val retryCount = AtomicInteger(0)
        val anrCount = AtomicInteger(0)
        var peakMemKb = 0L

        val memInfo = Debug.MemoryInfo()

        repeat(requestCount) { i ->
            val requestStart = System.currentTimeMillis()

            val ok = doHttpRequest(targetUrl)

            val elapsed = System.currentTimeMillis() - requestStart

            if (ok) {
                successCount.incrementAndGet()
            } else {
                dropCount.incrementAndGet()
                // Retry один раз
                if (doHttpRequest(targetUrl)) {
                    retryCount.incrementAndGet()
                    successCount.incrementAndGet()
                } else {
                    dropCount.incrementAndGet()
                }
            }

            if (elapsed > ANR_THRESHOLD_MS) {
                anrCount.incrementAndGet()
                Log.w(TAG, "ANR-like delay на запросе $i: ${elapsed}ms")
            }

            // Замеряем память каждые 10 запросов
            if (i % 10 == 0) {
                Debug.getMemoryInfo(memInfo)
                val currentMem = memInfo.totalPss.toLong()
                if (currentMem > peakMemKb) {
                    peakMemKb = currentMem
                }
                Log.d(
                    TAG,
                    "[$i/$requestCount] success=${successCount.get()} " +
                        "drops=${dropCount.get()} mem=${currentMem}KB",
                )
            }

            val sleepTime = INTERVAL_MS - (System.currentTimeMillis() - requestStart)
            if (sleepTime > 0) Thread.sleep(sleepTime)
        }

        val total = requestCount
        val retryRate = if (total > 0) (retryCount.get() * 100.0 / total) else 0.0

        return SoakMetrics(
            totalRequests = total,
            successCount = successCount.get(),
            connectionDrops = dropCount.get(),
            retryCount = retryCount.get(),
            retryRate = retryRate,
            peakMemoryMb = peakMemKb / 1024.0,
            anrLikeDelays = anrCount.get()
        )
    }

    private fun doHttpRequest(targetUrl: String): Boolean {
        return try {
            val conn = URL(targetUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.requestMethod = "GET"
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299 || code == 204
        } catch (e: Exception) {
            Log.w(TAG, "HTTP ошибка: ${e.message}")
            false
        }
    }

    private fun buildMetricsJson(metrics: SoakMetrics, requestCount: Int, targetUrl: String): String {
        return """
            {
              "test": "SoakTest",
              "timestamp": "${java.time.Instant.now()}",
              "config": {
                "total_requests": $requestCount,
                "target_url": "${targetUrl.replace("\"", "\\\"")}",
                "interval_ms": $INTERVAL_MS,
                "anr_threshold_ms": $ANR_THRESHOLD_MS
              },
              "metrics": {
                "success_count": ${metrics.successCount},
                "connection_drops": ${metrics.connectionDrops},
                "retry_count": ${metrics.retryCount},
                "retry_rate_pct": ${"%.2f".format(metrics.retryRate)},
                "peak_memory_mb": ${"%.2f".format(metrics.peakMemoryMb)},
                "anr_like_delays": ${metrics.anrLikeDelays}
              }
            }
        """.trimIndent()
    }

    data class SoakMetrics(
        val totalRequests: Int,
        val successCount: Int,
        val connectionDrops: Int,
        val retryCount: Int,
        val retryRate: Double,
        val peakMemoryMb: Double,
        val anrLikeDelays: Int
    )
}
