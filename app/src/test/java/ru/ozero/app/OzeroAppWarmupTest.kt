package ru.ozero.app

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class OzeroAppWarmupTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/app/OzeroApp.kt")
        assertTrue(f.exists(), "OzeroApp.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `onCreate включает warmup-libs guard для ранней детекции загрузки`() {
        val onCreateBody = funBody(source, "onCreate")
        assertTrue(
            onCreateBody.contains("warmup-libs"),
            "onCreate должен warmup'ить byedpi+hev — lazy loadLibrary рискует крашнуть в connect-флоу " +
                "(SIGSEGV ловится только через ApplicationExitInfo, тогда уже поздно)",
        )
        assertTrue(
            onCreateBody.contains("ByeDpiProxy.libraryLoaded"),
            "warmup обязан touchнуть ByeDpiProxy.libraryLoaded чтобы триггернуть init-блок",
        )
        assertTrue(
            onCreateBody.contains("hev.TProxyService.libraryLoaded"),
            "warmup обязан touchнуть hev.TProxyService.libraryLoaded чтобы триггернуть init-блок",
        )
    }

    @Test
    fun `warmup обёрнут в guardUnit для безопасности процесса старта`() {
        val onCreateBody = funBody(source, "onCreate")
        assertTrue(
            onCreateBody.contains("BootDiagnostics.guardUnit(\"warmup-libs\""),
            "warmup ОБЯЗАН быть в guardUnit — иначе exception в loadLibrary убьёт процесс ДО того " +
                "как мы залогируем что произошло",
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
