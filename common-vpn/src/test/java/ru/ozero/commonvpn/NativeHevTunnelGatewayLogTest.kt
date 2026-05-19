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
    fun `start entry log содержит thread name и fd`() {
        assertTrue(startBody.contains("start entry"), "start должен иметь entry лог")
        assertTrue(
            startBody.contains("Thread.currentThread().name"),
            "должен логировать имя треда — Nubia/RedMagic диагностика",
        )
        assertTrue(startBody.contains("fd="), "должен логировать tunPfd.fd")
    }

    @Test
    fun `start checkpoint после loadOnce с timing и libraryLoaded`() {
        val loadIdx = startBody.indexOf("loader.loadOnce()")
        check(loadIdx >= 0) { "loader.loadOnce() missing — TProxyLoader interface обязан вызываться в start" }
        val tail = startBody.substring(loadIdx, minOf(startBody.length, loadIdx + 800))
        assertTrue(tail.contains("checkpoint loadOnce returned"), "должен иметь checkpoint после loadOnce")
        assertTrue(tail.contains("dt="), "timing после loadOnce")
        assertTrue(tail.contains("libraryLoaded="), "результат загрузки")
    }

    @Test
    fun `start checkpoints обрамляют writeConfig`() {
        assertTrue(startBody.contains("checkpoint pre-writeConfig"), "pre-writeConfig checkpoint")
        assertTrue(startBody.contains("checkpoint post-writeConfig"), "post-writeConfig checkpoint")
        assertTrue(startBody.contains("path=") && startBody.contains("bytes="), "post-writeConfig: path+size")
    }

    @Test
    fun `start checkpoints обрамляют nativeStart с timing`() {
        assertTrue(startBody.contains("checkpoint pre-nativeStart"), "pre-nativeStart checkpoint")
        assertTrue(startBody.contains("checkpoint post-nativeStart"), "post-nativeStart checkpoint")
        val post = startBody.substringAfter("checkpoint post-nativeStart")
        assertTrue(post.contains("code=") && post.contains("dt="), "post-nativeStart: код возврата + timing")
    }

    @Test
    fun `pre-nativeStart попадает в boot log через PersistentLoggers`() {
        assertTrue(
            startBody.contains("PersistentLoggers.instance?.info(TAG, \"checkpoint pre-nativeStart"),
            "pre-nativeStart обязан попадать в boot.log через PersistentLoggers.info — это последняя " +
                "строка перед blocking JNI TProxyStartService. Если нативка зависает, эта запись " +
                "укажет точный момент входа в JNI.",
        )
    }

    @Test
    fun `start не использует android_util_Log напрямую`() {
        val directLogCalls = Regex("\\bLog\\.[idew]\\(").findAll(startBody).count()
        assertTrue(
            directLogCalls == 0,
            "start не должен использовать android.util.Log — только PersistentLoggers (UnifiedLogger). " +
                "Прямых Log.x найдено: $directLogCalls",
        )
    }

    @Test
    fun `start запускает stats poller для discriminating tx_rx логов`() {
        assertTrue(
            source.contains("startStatsPoller()"),
            "после успешного nativeStart должен запускаться stats poller — " +
                "это discriminating-лог для диагностики мёртвого TUN→hev→byedpi pipeline",
        )
        assertTrue(
            source.contains("nativeStats"),
            "nativeStats должен быть injectable lambda — нужно для unit-тестов",
        )
        assertTrue(
            source.contains("hev stats IDLE") || source.contains("hev stats tx="),
            "stats poller обязан логировать tx/rx bytes — иначе нельзя отличить " +
                "'TUN пустой' от 'трафик идёт, но peer не отвечает'",
        )
    }

    @Test
    fun `stop останавливает stats poller`() {
        val stopBody = funBody(source, "stop")
        assertTrue(
            stopBody.contains("statsPoller.getAndSet(null)?.interrupt()"),
            "stop обязан interrupt() stats poller — иначе daemon thread утечёт после tunnel teardown",
        )
    }

    @Test
    fun `start больше не делает dup — raw tunPfd_fd передаётся в native`() {
        assertTrue(
            !startBody.contains(".dup()"),
            "Phase A4: dup() убран. Теперь передаём raw config.tunPfd.fd в native. " +
                "Симметрия с ByeDPIAndroid/ByeByeDPI. Закрытие fd — ответственность OzeroVpnService.",
        )
        assertTrue(
            !startBody.contains("dupedRef"),
            "dupedRef instance field removed — gateway больше не владеет fd lifecycle.",
        )
    }

    private fun funBody(src: String, name: String): String {
        val patterns = listOf("override fun $name(", "override suspend fun $name(", "fun $name(", "fun $name (")
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
