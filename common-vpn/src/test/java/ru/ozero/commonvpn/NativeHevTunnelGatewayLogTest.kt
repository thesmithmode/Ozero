package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class NativeHevTunnelGatewayLogTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/HevTunnelGateway.kt")
        assertTrue(f.exists(), "HevTunnelGateway.kt не найден: $f")
        f.readText()
    }

    private val startBody by lazy { funBody(source, "start") }

    @Test
    fun `start entry log содержит thread name и originalFd`() {
        assertTrue(startBody.contains("start entry"), "start должен иметь entry лог")
        assertTrue(startBody.contains("Thread.currentThread().name"), "должен логировать имя треда — Nubia диагностика")
        assertTrue(startBody.contains("originalFd="), "должен логировать original tunPfd.fd")
    }

    @Test
    fun `start checkpoint после loadOnce с timing и libraryLoaded`() {
        val loadIdx = startBody.indexOf("hev.TProxyService.loadOnce()")
        check(loadIdx >= 0) { "loadOnce missing" }
        val tail = startBody.substring(loadIdx, minOf(startBody.length, loadIdx + 800))
        assertTrue(tail.contains("checkpoint loadOnce returned"), "должен иметь checkpoint после loadOnce")
        assertTrue(tail.contains("dt="), "timing после loadOnce")
        assertTrue(tail.contains("libraryLoaded="), "результат загрузки")
    }

    @Test
    fun `start checkpoints обрамляют dup pre и post с newFd`() {
        assertTrue(startBody.contains("checkpoint pre-dup"), "pre-dup checkpoint")
        assertTrue(startBody.contains("checkpoint post-dup"), "post-dup checkpoint")
        assertTrue(startBody.contains("newFd="), "post-dup должен показать новый fd после dup")
        val pre = startBody.indexOf("checkpoint pre-dup")
        val post = startBody.indexOf("checkpoint post-dup")
        assertTrue(pre in 0 until post, "pre-dup перед post-dup")
    }

    @Test
    fun `start checkpoints обрамляют writeConfig`() {
        assertTrue(startBody.contains("checkpoint pre-writeConfig"), "pre-writeConfig checkpoint")
        assertTrue(startBody.contains("checkpoint post-writeConfig"), "post-writeConfig checkpoint")
        assertTrue(startBody.contains("path=") && startBody.contains("bytes="), "post-writeConfig: path+size файла")
    }

    @Test
    fun `start checkpoints обрамляют nativeStart с timing`() {
        assertTrue(startBody.contains("checkpoint pre-nativeStart"), "pre-nativeStart checkpoint")
        assertTrue(startBody.contains("checkpoint post-nativeStart"), "post-nativeStart checkpoint")
        val post = startBody.substringAfter("checkpoint post-nativeStart")
        assertTrue(post.contains("code=") && post.contains("dt="), "post-nativeStart: код возврата + timing")
    }

    @Test
    fun `все checkpoints дублируются в PersistentLoggers для boot log`() {
        val checkpointCount = Regex("checkpoint ").findAll(startBody).count()
        val persistentCount = Regex("PersistentLoggers\\.instance\\?\\.info\\(\\s*TAG,\\s*\"checkpoint ")
            .findAll(startBody)
            .count()
        assertTrue(
            persistentCount >= checkpointCount / 2,
            "checkpoints должны попадать в PersistentLoggers (boot.log) — иначе при native crash логи теряются. " +
                "checkpoints=$checkpointCount, persistent=$persistentCount",
        )
    }

    private fun funBody(src: String, name: String): String {
        val patterns = listOf("override fun $name(", "fun $name(", "fun $name (")
        var idx = -1
        for (p in patterns) {
            idx = src.indexOf(p)
            if (idx >= 0) break
        }
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
