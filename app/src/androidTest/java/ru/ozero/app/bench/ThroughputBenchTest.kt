package ru.ozero.app.bench

import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ThroughputBenchTest {

    companion object {
        private const val TAG = "ThroughputBench"
        private const val BENCH_ARG = "OZERO_BENCH"
        private const val OUTPUT_FILE = "/sdcard/throughput-results.json"
    }

    @Test
    fun measureThroughput() {
        val args: Bundle = InstrumentationRegistry.getArguments()
        val benchEnabled = args.getString(BENCH_ARG, "0") == "1" ||
            System.getenv(BENCH_ARG) == "1"

        Assume.assumeTrue(
            "ThroughputBenchTest пропущен: OZERO_BENCH не установлен в 1",
            benchEnabled
        )

        Log.i(TAG, "Запускаю throughput bench...")

                val results = runThroughputProbes()

        val json = buildResultJson(results)
        File(OUTPUT_FILE).writeText(json)

        Log.i(TAG, "Throughput results: mean=${results.mean} Mbps, p50=${results.p50} Mbps, p95=${results.p95} Mbps")
        Log.i(TAG, "Результаты сохранены в $OUTPUT_FILE")
    }

    private fun runThroughputProbes(): ThroughputStats {
        val measurements = mutableListOf<Double>()

                val testUrl = "http://10.0.2.2:5201" 
        val sampleCount = 10
        val chunkSizeBytes = 1_024 * 64 

        repeat(sampleCount) { i ->
            try {
                val start = System.nanoTime()
                val url = java.net.URL("$testUrl/download?size=${chunkSizeBytes * 10}")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5_000
                conn.readTimeout = 10_000

                try {
                    conn.connect()
                    val bytesRead = conn.inputStream?.use { it.readBytes().size } ?: 0
                    val elapsed = (System.nanoTime() - start) / 1e9
                    if (bytesRead > 0 && elapsed > 0) {
                        val mbps = (bytesRead * 8.0) / (elapsed * 1_000_000)
                        measurements.add(mbps)
                        Log.d(TAG, "Sample $i: ${"%.2f".format(mbps)} Mbps")
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sample $i ошибка: ${e.message}")
                                measurements.add(0.0)
            }
        }

        return if (measurements.isEmpty()) {
            ThroughputStats(0.0, 0.0, 0.0, 0)
        } else {
            val sorted = measurements.sorted()
            val n = sorted.size
            ThroughputStats(
                mean = measurements.average(),
                p50 = sorted[n / 2],
                p95 = sorted[minOf((n * 0.95).toInt(), n - 1)],
                samples = n
            )
        }
    }

    private fun buildResultJson(stats: ThroughputStats): String = """
        {
          "mean_mbps": ${"%.2f".format(stats.mean)},
          "p50_mbps": ${"%.2f".format(stats.p50)},
          "p95_mbps": ${"%.2f".format(stats.p95)},
          "samples": ${stats.samples},
          "test": "ThroughputBenchTest",
          "timestamp": "${java.time.Instant.now()}"
        }
    """.trimIndent()

    data class ThroughputStats(
        val mean: Double,
        val p50: Double,
        val p95: Double,
        val samples: Int
    )
}
