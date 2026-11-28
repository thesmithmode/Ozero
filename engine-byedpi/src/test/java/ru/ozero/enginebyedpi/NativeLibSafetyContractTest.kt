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
    fun `JNI_GUARD_BUSY определён как -2`() {
        assertTrue(
            Regex("""#define\s+JNI_GUARD_BUSY\s+\(\s*-2\s*\)""").containsMatchIn(nativeLib),
            "JNI_GUARD_BUSY должен быть #define -2. Distinct от -1 (OOM/JNI/upstream fail) " +
                "чтобы Kotlin различал guard busy vs real failure. Совпадает с ByeDpiEngine.JNI_GUARD_BUSY.",
        )
    }

    @Test
    fun `jniStartProxy returns JNI_GUARD_BUSY when listener ownership is unavailable`() {
        val pattern = Regex(
            """if\s*\(\s*!ozero_listener_begin\(\)\s*\)\s*return\s+JNI_GUARD_BUSY""",
            RegexOption.DOT_MATCHES_ALL,
        )
        assertTrue(
            pattern.containsMatchIn(nativeLib),
            "jniStartProxy must return JNI_GUARD_BUSY when the previous owner has not finished. " +
                "Иначе Kotlin не различит guard busy от real failure.",
        )
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
                "Без него strdup-возврат NULL (OOM) → SIGSEGV в upstream getopt parsing.",
        )
    }

    @Test
    fun `argv loop strdup имеет NULL-check на каждой итерации`() {
        val pattern = Regex(
            """char\s+\*copy\s*=\s*strdup\(arg_str\)\s*;[^}]*if\s*\(!\s*copy\s*\)""",
            RegexOption.DOT_MATCHES_ALL,
        )
        assertTrue(
            pattern.containsMatchIn(nativeLib),
            "Per-iter strdup в argv loop обязан иметь NULL-check. " +
                "Иначе OOM в середине loop → NULL в argv → upstream getopt UB (SIGSEGV). " +
                "На fail полный cleanup через jni_start_fail.",
        )
    }

    @Test
    fun `JNI ExceptionCheck после GetObjectArrayElement`() {
        val pattern = Regex(
            """GetObjectArrayElement\(env,\s*args,\s*i\)\s*;\s*if\s*\(\s*\(\*env\)->ExceptionCheck\(env\)""",
            RegexOption.DOT_MATCHES_ALL,
        )
        assertTrue(
            pattern.containsMatchIn(nativeLib),
            "После GetObjectArrayElement обязан быть ExceptionCheck как ПЕРВЫЙ оператор — " +
                "pending JNI exception (ArrayIndexOutOfBoundsException, OOM) приведёт к " +
                "misbehavior в main() если пропустить. Паттерн: только whitespace между ; и if(ExceptionCheck).",
        )
    }

    @Test
    fun `JNI ExceptionCheck после GetStringUTFChars`() {
        val pattern = Regex(
            """GetStringUTFChars\(env,\s*arg,\s*0\)\s*;\s*if\s*\(\s*\(\*env\)->ExceptionCheck\(env\)""",
            RegexOption.DOT_MATCHES_ALL,
        )
        assertTrue(
            pattern.containsMatchIn(nativeLib),
            "После GetStringUTFChars обязан быть ExceptionCheck как ПЕРВЫЙ оператор — " +
                "pending OOM exception в JNI не вернёт control в Java сразу, " +
                "main() запустится в broken state. Паттерн: только whitespace между ; и if(ExceptionCheck).",
        )
    }

    @Test
    fun `jniForceClose requests shutdown without releasing native owner`() {
        val forceCloseBody = nativeLib.substringAfter("ByeDpiProxy_jniForceClose")
            .substringAfter("{")
            .substringBefore("}")
            .trim()
        assertTrue(forceCloseBody == "return ozero_listener_stop();")
    }

    @Test
    fun `native lib не экспортирует emergency reset guard bypass`() {
        assertTrue(
            !nativeLib.contains("jniEmergencyReset") && !nativeLib.contains("atomic_exchange(&g_proxy_running"),
            "native-lib.c не должен иметь emergency reset, который сбрасывает guard до возврата main(). " +
                "Преждевременный reset допускает concurrent main() на shared upstream globals.",
        )
    }

    @Test
    fun `jniStartProxy НЕ имеет blocking spin retry`() {
        val startBody = Regex(
            """Java_ru_ozero_enginebyedpi_ByeDpiProxy_jniStartProxy[^{]*\{(.*?)(?=^JNIEXPORT|\z)""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE),
        ).find(nativeLib)?.groupValues?.get(1)
            ?: error("jniStartProxy не найден в native-lib.c")

        val blockingCalls = listOf("usleep", "nanosleep", "sched_yield", "pthread_yield", "sleep(")
        val violations = blockingCalls.filter { startBody.contains(it) }
        assertTrue(
            violations.isEmpty(),
            "jniStartProxy НЕ должен делать blocking spin: $violations — Kotlin coroutine на " +
                "limitedParallelism(1) dispatcher не сможет дождаться отмены, и serializing " +
                "queue застрянет на спине. Retry обязан быть в Kotlin (delay(), cooperative).",
        )
    }
}
