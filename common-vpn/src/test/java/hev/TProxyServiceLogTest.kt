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
    fun `loadOnce ловит generic Throwable и сохраняет loadError`() {
        val loadOnceBody = funBody(source, "loadOnce")
        assertTrue(
            loadOnceBody.contains("catch (e: Throwable)") ||
                loadOnceBody.contains("catch(e: Throwable)") ||
                loadOnceBody.contains("catch (e: java.lang.Throwable)"),
            "loadOnce обязан иметь catch (e: Throwable) — System.loadLibrary может бросить " +
                "NoSuchMethodError (RegisterNatives mismatch), LinkageError, ClassNotFoundException и т.д. " +
                "Без generic catch loadError остаётся null, libraryLoaded=false, и NativeHevTunnel " +
                "репортит 'libhev not loaded: null' вместо реальной причины.",
        )
        val loadErrorAssignments = Regex("loadError\\s*=\\s*").findAll(loadOnceBody).count()
        assertTrue(
            loadErrorAssignments >= 3,
            "loadError должен заполняться во всех catch-блоках (минимум 3: UnsatisfiedLinkError, " +
                "SecurityException, Throwable). Найдено присваиваний: $loadErrorAssignments",
        )
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
    fun `loadOnce dumpVendorMaps только на error path не на success`() {
        val body = funBody(source, "loadOnce")
        val tryStart = body.indexOf("try {")
        val tryBlockEnd = body.indexOf("} catch", tryStart)
        check(tryStart >= 0 && tryBlockEnd > tryStart) { "try-блок не найден" }
        val tryBlock = body.substring(tryStart, tryBlockEnd)
        assertFalse(
            tryBlock.contains("dumpVendorMaps"),
            "dumpVendorMaps НЕ должен звониться на success path — это ~30 строк /proc/self/maps " +
                "на каждый cold start. После v1.0.3 фикса libhev грузится OK и дамп vendor maps " +
                "избыточен. Звать только в catch блоках для диагностики реальных load failures.",
        )
        val catchBlocks = body.substring(tryBlockEnd)
        assertTrue(
            catchBlocks.contains("dumpVendorMaps"),
            "dumpVendorMaps должен звониться в catch блоках — нужен для диагностики Nubia/RedMagic " +
                "vendor library races на failure path.",
        )
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
