package ru.ozero.enginebyedpi

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class NativeLibSafetyContractTest {

    private val moduleRoot = File(System.getProperty("user.dir") ?: ".")
    private val nativeLib by lazy {
        File(moduleRoot, "src/main/cpp/native-lib.c").readText()
    }

    @Test
    fun `strdup для argv0 имеет NULL-check на OOM`() {
        val pattern = Regex(
            """argv\[0\]\s*=\s*strdup\("byedpi"\)\s*;\s*if\s*\(!\s*argv\[0\]\s*\)""",
            RegexOption.DOT_MATCHES_ALL,
        )
        assertTrue(
            pattern.containsMatchIn(nativeLib),
            "argv[0] = strdup(\"byedpi\") обязан иметь immediate NULL-check. " +
                "Без него strdup-возврат NULL (OOM) → SIGSEGV в upstream getopt parsing. " +
                "Defensive cleanup: free(argv) + atomic_store(g_proxy_running, 0) + return -1.",
        )
    }

    @Test
    fun `NULL-check для argv0 содержит cleanup free и release guard`() {
        val pattern = Regex(
            """if\s*\(!\s*argv\[0\]\s*\)\s*\{[^}]*free\(argv\)[^}]*""" +
                """atomic_store\(\&g_proxy_running,\s*0\)[^}]*return\s+-1""",
            RegexOption.DOT_MATCHES_ALL,
        )
        assertTrue(
            pattern.containsMatchIn(nativeLib),
            "NULL-check блок для argv[0] обязан выполнить полный cleanup: " +
                "free(argv) + atomic_store(&g_proxy_running, 0) + return -1. " +
                "Иначе leak памяти и stuck guard на OOM-path.",
        )
    }

    @Test
    fun `jniStartProxy имеет bounded retry для cancel-edge guard hold`() {
        val startBody = Regex(
            """Java_ru_ozero_enginebyedpi_ByeDpiProxy_jniStartProxy[^{]*\{(.*?)(?=^JNIEXPORT|\z)""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE),
        ).find(nativeLib)?.groupValues?.get(1)
            ?: error("jniStartProxy не найден в native-lib.c")

        val hasRetryLoop = Regex(
            """for\s*\([^)]*attempt[^)]*<\s*100[^)]*\)\s*\{[^}]*atomic_compare_exchange_strong[^}]*usleep\(10000\)""",
            RegexOption.DOT_MATCHES_ALL,
        ).containsMatchIn(startBody)

        assertTrue(
            hasRetryLoop,
            "jniStartProxy обязан иметь bounded retry CAS (100×10ms=1s spin). Без него " +
                "после Kotlin oldJob.cancel() guard остаётся удержан старым JNI → " +
                "новый jniStartProxy вернёт -1 → engine.start() Failure без причины. " +
                "Retry даёт upstream main() уйти после close(fd).",
        )
    }

    @Test
    fun `jniForceClose не сбрасывает g_proxy_running — guard owned by jniStartProxy`() {
        val forceCloseBody = Regex(
            """Java_ru_ozero_enginebyedpi_ByeDpiProxy_jniForceClose[^{]*\{(.*?)^\}""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE),
        ).find(nativeLib)?.groupValues?.get(1)
            ?: error("jniForceClose не найден в native-lib.c")

        assertTrue(
            !forceCloseBody.contains("atomic_store(&g_proxy_running"),
            "jniForceClose НЕ должен сбрасывать g_proxy_running — иначе вторая " +
                "jniStartProxy CAS пройдёт пока старая main() ещё в cleanup → " +
                "concurrent main() с shared upstream globals → memory corruption. " +
                "Guard релизит только jniStartProxy после возврата main().",
        )
    }
}
