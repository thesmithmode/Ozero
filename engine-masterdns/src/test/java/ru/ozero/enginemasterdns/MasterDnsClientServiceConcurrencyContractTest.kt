package ru.ozero.enginemasterdns

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class MasterDnsClientServiceConcurrencyContractTest {

    private val source: File = listOf(
        File("src/main/java/ru/ozero/enginemasterdns/MasterDnsClientService.kt"),
        File("engine-masterdns/src/main/java/ru/ozero/enginemasterdns/MasterDnsClientService.kt"),
    ).first { it.exists() }

    @Test
    fun `service serializes start via runMutex withLock`() {
        val text = source.readText()
        assertTrue(text.contains("runMutex.withLock")) {
            "MasterDnsClientService must serialize start via runMutex.withLock"
        }
    }

    @Test
    fun `service uses AtomicReference for processRef`() {
        val text = source.readText()
        assertTrue(text.contains("AtomicReference<Process?>")) {
            "MasterDnsClientService must hold process in AtomicReference"
        }
        assertTrue(text.contains("processRef.getAndSet")) {
            "MasterDnsClientService must use getAndSet for race-safe cleanup"
        }
    }

    @Test
    fun `service uses AtomicReference for jobRef`() {
        val text = source.readText()
        assertTrue(text.contains("jobRef")) { "jobRef missing" }
        assertTrue(text.contains("AtomicReference<Job?>"))
    }

    @Test
    fun `service cancels prior job on subsequent start`() {
        val text = source.readText()
        assertTrue(text.contains("jobRef.getAndSet(newJob)"))
        assertTrue(text.contains("?.cancel()"))
    }

    @Test
    fun `service publishes lazy replacement before cleanup and starts after cleanup`() {
        val text = source.readText()
        val newJobIndex = text.indexOf("val newJob = scope.launch(start = CoroutineStart.LAZY)")
        val publishIndex = text.indexOf("jobRef.getAndSet(newJob)?.cancel()")
        val childCleanupIndex = text.indexOf("cancelChildJobs()", publishIndex)
        val processCleanupIndex = text.indexOf("killProcess()", publishIndex)
        val startIndex = text.indexOf("newJob.start()")

        assertTrue(newJobIndex >= 0)
        assertTrue(publishIndex > newJobIndex)
        assertTrue(childCleanupIndex > publishIndex)
        assertTrue(processCleanupIndex > childCleanupIndex)
        assertTrue(startIndex > processCleanupIndex)
    }
}
