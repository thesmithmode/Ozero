package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SentinelLogsRegressionTest {

    private val pipelineSrc by lazy { read("src/main/java/ru/ozero/commonvpn/pipeline/VpnEnginePipeline.kt") }
    private val gatewaySrc by lazy { read("src/main/java/ru/ozero/commonvpn/HevTunnelGateway.kt") }

    @Test
    fun `pipeline bringTunnelUp логирует entry до создания HevTunnelConfig`() {
        val body = funBody(pipelineSrc, "bringTunnelUp")
        val entryIdx = body.indexOf("bringTunnelUp entry")
        val configIdx = body.indexOf("HevTunnelConfig(")
        assertTrue(
            entryIdx in 0 until configIdx,
            "bringTunnelUp должен логировать entry до создания HevTunnelConfig — " +
                "иначе при краше внутри dup() мы не узнаем что pipeline дошёл до этой точки",
        )
    }

    @Test
    fun `pipeline логирует tunnelGateway start invoking перед blocking call`() {
        val body = funBody(pipelineSrc, "bringTunnelUp")
        val invokingIdx = body.indexOf("tunnelGateway.start invoking")
        val callIdx = body.indexOf("tunnelGateway.start(config)")
        assertTrue(
            invokingIdx in 0 until callIdx,
            "Лог 'invoking' должен предшествовать blocking nativeStart — иначе процесс молча умирает в JNI",
        )
    }

    @Test
    fun `gateway start первой строкой логирует libraryLoaded флаг`() {
        val body = funBody(gatewaySrc, "start")
        val libraryLoadedIdx = body.indexOf("start entry libraryLoaded=")
        val checkIdx = body.indexOf("if (!hev.TProxyService.libraryLoaded)")
        assertTrue(
            libraryLoadedIdx in 0 until checkIdx,
            "gateway.start должен логировать libraryLoaded ДО проверки — иначе lazy loadLibrary " +
                "может крашнуть на этапе проверки и мы не узнаем причину",
        )
    }

    @Test
    fun `gateway dup TUN fd перед nativeStart`() {
        val body = funBody(gatewaySrc, "start")
        val dupIdx = body.indexOf("config.tunPfd.dup()")
        val nativeIdx = body.indexOf("nativeStart(")
        assertTrue(
            dupIdx in 0 until nativeIdx,
            "Gateway обязан dup'ать TUN fd перед nativeStart — иначе ParcelFileDescriptor.close() " +
                "со стороны Service инвалидирует fd под носом у libhev → SIGSEGV",
        )
    }

    @Test
    fun `gateway передаёт duped fd в nativeStart, не оригинал`() {
        val body = funBody(gatewaySrc, "start")
        val nativePattern = Regex("nativeStart\\([^,]+,\\s*duped\\.fd\\)")
        assertTrue(
            nativePattern.containsMatchIn(body),
            "В nativeStart должен передаваться duped.fd — оригинальный config.tunPfd.fd под управлением Service",
        )
    }

    @Test
    fun `gateway stop закрывает duped pfd`() {
        val stopBody = funBody(gatewaySrc, "stop")
        assertTrue(
            stopBody.contains("closeDuped()"),
            "stop обязан закрывать duped pfd — иначе утечка fd на каждый connect/disconnect цикл",
        )
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
