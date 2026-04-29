package ru.ozero.app.subscription

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class HarvestWorkerEnqueueTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/app/subscription/HarvestWorker.kt")
        assertTrue(f.exists(), "HarvestWorker.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `HarvestWorker имеет enqueueOneShotExpedited для cold start`() {
        assertTrue(
            source.contains("fun enqueueOneShotExpedited"),
            "HarvestWorker обязан иметь enqueueOneShotExpedited(context) для cold start. " +
                "PeriodicWorkRequest с интервалом 6h не подтянет серверы вовремя для первого Connect.",
        )
    }

    @Test
    fun `enqueueOneShotExpedited использует OneTimeWorkRequest и setExpedited`() {
        val body = source.substringAfter("fun enqueueOneShotExpedited")
            .substringBefore("\n        }")
        assertTrue(
            body.contains("OneTimeWorkRequest"),
            "enqueueOneShotExpedited обязан строить OneTimeWorkRequest — не Periodic.",
        )
        assertTrue(
            body.contains("setExpedited"),
            "enqueueOneShotExpedited обязан звать setExpedited(...) — иначе " +
                "запуск задержится в очереди WorkManager.",
        )
    }

    @Test
    fun `enqueueOneShotExpedited имеет networkType CONNECTED constraint`() {
        val body = source.substringAfter("fun enqueueOneShotExpedited")
            .substringBefore("\n        }")
        assertTrue(
            body.contains("NetworkType.CONNECTED"),
            "enqueueOneShotExpedited требует сеть — без неё нет смысла стучаться в GitHub raw.",
        )
    }
}
