package ru.ozero.app

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OzeroAppWarmupTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/app/OzeroApp.kt")
        assertTrue(f.exists(), "OzeroApp.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `onCreate не делает eager-touch ByeDpiProxy`() {
        val onCreateBody = funBody(source, "onCreate")
        assertFalse(
            onCreateBody.contains("ByeDpiProxy"),
            "onCreate не должен упоминать ByeDpiProxy — companion object init выполнит loadLibrary " +
                "при первом обращении и SIGSEGV в JNI_OnLoad убьёт процесс до показа UI",
        )
    }

    @Test
    fun `onCreate не делает eager-touch TProxyService`() {
        val onCreateBody = funBody(source, "onCreate")
        assertFalse(
            onCreateBody.contains("TProxyService"),
            "onCreate не должен упоминать hev.TProxyService — object init выполнит loadLibrary " +
                "и SIGSEGV в JNI_OnLoad убьёт процесс до показа UI",
        )
    }

    @Test
    fun `onCreate не содержит упоминаний warmup-libs`() {
        val onCreateBody = funBody(source, "onCreate")
        assertFalse(
            onCreateBody.contains("warmup-libs"),
            "warmup-libs стратегия отключена — нативки грузятся лениво через loadOnce() " +
                "при первом VPN-старте",
        )
    }

    @Test
    fun `onCreate не вызывает UrnetworkRuntime_ensure eager`() {
        val onCreateBody = funBody(source, "onCreate")
        assertFalse(
            onCreateBody.contains("UrnetworkRuntime.ensure"),
            "UrnetworkRuntime.ensure() вызывает SDK init при старте приложения — до любых VPN операций. " +
                "ensure() должен вызываться только из RealUrnetworkSdkBridge.start().",
        )
    }

    private fun funBody(src: String, name: String): String {
        val idx = src.indexOf("override fun $name").takeIf { it >= 0 } ?: src.indexOf("fun $name")
        check(idx >= 0) { "fun $name not found" }
        val openIdx = src.indexOf('{', idx)
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
