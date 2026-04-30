package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SentinelLogsRegressionTest {

    private val gatewaySrc by lazy { read("src/main/java/ru/ozero/commonvpn/HevTunnelGateway.kt") }
    private val serviceSrc by lazy { read("src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt") }

    @Test
    fun `gateway start логирует libraryLoaded ДО проверки isLoaded`() {
        val body = funBody(gatewaySrc, "start")
        val libraryLoadedIdx = body.indexOf("libraryLoaded=")
        val checkIdx = body.indexOf("if (!hev.TProxyService.libraryLoaded)")
        assertTrue(
            libraryLoadedIdx in 0 until checkIdx,
            "gateway.start должен логировать libraryLoaded ДО проверки isLoaded — иначе lazy " +
                "loadLibrary может крашнуть на этапе проверки и мы не узнаем причину.",
        )
    }

    @Test
    fun `gateway передаёт raw tunPfd_fd в nativeStart (no dup)`() {
        val body = funBody(gatewaySrc, "start")
        assertTrue(
            !body.contains(".dup()"),
            "Phase A4: dup() удалён. ByeDPIAndroid/ByeByeDPI передают raw fd.fd в native. " +
                "Закрытие fd — ответственность OzeroVpnService.tunFdRef в performShutdown finally.",
        )
        val nativePattern = Regex("nativeStart\\([^,]+,\\s*fd\\)")
        assertTrue(
            nativePattern.containsMatchIn(body),
            "В nativeStart должен передаваться raw fd (val fd = config.tunPfd.fd, потом nativeStart(path, fd)).",
        )
    }

    @Test
    fun `gateway stop делегирует close fd на сервис`() {
        val stopBody = funBody(gatewaySrc, "stop")
        assertTrue(
            !stopBody.contains("closeDuped") && !stopBody.contains(".close()"),
            "Phase A4: gateway больше не закрывает fd. Только nativeStop. Закрытие fd живёт в " +
                "OzeroVpnService.performShutdown finally — после chainOrchestrator.stop() + tunnelGateway.stop().",
        )
        assertTrue(stopBody.contains("nativeStop"), "stop обязан вызвать nativeStop")
    }

    @Test
    fun `service performShutdown закрывает tunFd ПОСЛЕ tunnelGateway_stop`() {
        val body = serviceSrc.substringAfter("private suspend fun performShutdown()")
            .substringBefore("internal fun buildTunBuilder")
        val nativeIdx = body.indexOf("tunnelGateway.stop()")
        val closeIdx = body.indexOf("tunFdRef.getAndSet(null)?.close()")
        assertTrue(nativeIdx >= 0, "tunnelGateway.stop() должен присутствовать в performShutdown")
        assertTrue(closeIdx > nativeIdx, "tunFd close обязан быть ПОСЛЕ tunnelGateway.stop()")
    }

    private fun read(rel: String): String {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, rel)
        check(f.exists()) { "missing: ${f.absolutePath}" }
        return f.readText()
    }

    private fun funBody(src: String, name: String): String {
        val patterns = listOf("fun $name(", "suspend fun $name(", "override fun $name(", "override suspend fun $name(")
        var idx = -1
        for (p in patterns) {
            idx = src.indexOf(p)
            if (idx >= 0) break
        }
        check(idx >= 0) { "fun $name not found" }
        val openIdx = src.indexOf('{', idx)
        check(openIdx >= 0)
        var depth = 0
        var i = openIdx
        while (i < src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(openIdx, i + 1)
                }
            }
            i++
        }
        error("unclosed body for $name")
    }
}
