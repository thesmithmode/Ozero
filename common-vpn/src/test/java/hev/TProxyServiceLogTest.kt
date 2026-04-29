package hev

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TProxyServiceLogTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/hev/TProxyService.kt")
        assertTrue(f.exists(), "TProxyService.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `loadOnce логирует loadLibrary begin до System loadLibrary вызова`() {
        val loadOnceBody = funBody(source, "loadOnce")
        val beginIdx = loadOnceBody.indexOf("loadLibrary begin")
        val callIdx = loadOnceBody.indexOf("System.loadLibrary")
        assertTrue(beginIdx in 0 until callIdx, "begin-лог должен быть до System.loadLibrary в loadOnce")
    }

    @Test
    fun `loadOnce использует PersistentLoggers для persistent диагностики`() {
        val loadOnceBody = funBody(source, "loadOnce")
        assertTrue(
            loadOnceBody.contains("PersistentLoggers.instance"),
            "loadOnce должен дублировать в PersistentLoggers — иначе load failure не попадёт в boot.log",
        )
    }

    @Test
    fun `loadOnce обрабатывает оба UnsatisfiedLinkError и SecurityException`() {
        val loadOnceBody = funBody(source, "loadOnce")
        assertTrue(loadOnceBody.contains("UnsatisfiedLinkError"), "должен ловить UnsatisfiedLinkError")
        assertTrue(loadOnceBody.contains("SecurityException"), "должен ловить SecurityException")
    }

    @Test
    fun `loadOnce идемпотентен через loadAttempted флаг`() {
        val loadOnceBody = funBody(source, "loadOnce")
        assertTrue(
            loadOnceBody.contains("loadAttempted"),
            "loadOnce обязан использовать loadAttempted флаг — повторные вызовы не должны пытаться " +
                "loadLibrary дважды (после первого fail дальше повторно UnsatisfiedLinkError)",
        )
        assertTrue(
            loadOnceBody.contains("synchronized"),
            "loadOnce должен синхронизировать запись loadAttempted — race между gateway.start и engine.start",
        )
    }

    @Test
    fun `object не содержит eager init с loadLibrary`() {
        val objectStart = source.indexOf("object TProxyService")
        check(objectStart >= 0) { "object TProxyService not found" }
        val initPattern = Regex("(?<!fun\\s)init\\s*\\{[^}]*loadLibrary", RegexOption.DOT_MATCHES_ALL)
        assertFalse(
            initPattern.containsMatchIn(source),
            "object init не должен звать loadLibrary — только lazy через loadOnce(). " +
                "Иначе любое касание hev.TProxyService.* triggerит class init и SIGSEGV в JNI_OnLoad " +
                "убивает процесс ДО показа UI.",
        )
    }

    @Test
    fun `loadOnce логирует thread name id main looper для диагностики Nubia race`() {
        val body = funBody(source, "loadOnce")
        assertTrue(body.contains("thread="), "должен логировать имя треда")
        assertTrue(body.contains("tid="), "должен логировать thread id")
        assertTrue(body.contains("main="), "должен логировать isMainLooper — критично для Nubia/RedMagic")
        assertTrue(body.contains("Looper.getMainLooper()"), "должен сравнивать с main looper")
    }

    @Test
    fun `loadOnce логирует Build manufacturer brand model для device fingerprint`() {
        val body = funBody(source, "loadOnce")
        assertTrue(body.contains("Build.MANUFACTURER"), "device fingerprint обязателен — Nubia-specific краш")
        assertTrue(body.contains("Build.BRAND"), "BRAND нужен — RedMagic vs обычный Nubia")
        assertTrue(body.contains("Build.MODEL"), "MODEL нужен — какой именно девайс")
    }

    @Test
    fun `loadOnce логирует timing dt в ms для load library`() {
        val body = funBody(source, "loadOnce")
        assertTrue(body.contains("dt="), "timing нужен — отличить мгновенный краш от deadlock")
        assertTrue(body.contains("System.nanoTime()"), "timing через nanoTime")
    }

    @Test
    fun `loadOnce дампит vendor maps до loadLibrary`() {
        val body = funBody(source, "loadOnce")
        val dumpIdx = body.indexOf("dumpVendorMaps")
        val loadIdx = body.indexOf("System.loadLibrary")
        assertTrue(dumpIdx in 0 until loadIdx, "vendor maps дамп ОБЯЗАН быть до System.loadLibrary")
    }

    @Test
    fun `dumpVendorMaps читает proc self maps и фильтрует vendor keywords`() {
        assertTrue(source.contains("/proc/self/maps"), "должен читать /proc/self/maps")
        assertTrue(
            source.contains("\"nubia\"") &&
                source.contains("\"glnubia\"") &&
                source.contains("\"perf\""),
            "ключевые слова nubia/glnubia/perf должны фильтроваться",
        )
        assertTrue(source.contains("MAX_MAPS_LINES"), "должен ограничить вывод чтобы не флудить boot.log")
    }

    private fun funBody(src: String, name: String): String {
        val patterns = listOf("fun $name(", "fun $name (")
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
