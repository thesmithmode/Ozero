package ru.ozero.enginebyedpi

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class ByeDpiJniContractTest {

    private val moduleRoot = File(System.getProperty("user.dir") ?: ".")
    private val proxySource by lazy {
        File(moduleRoot, "src/main/java/ru/ozero/enginebyedpi/ByeDpiProxy.kt").readText()
    }

    @Test
    fun `ByeDpiProxyContract объявляет startProxy`() {
        val pattern = Regex("fun\\s+startProxy\\s*\\(\\s*\\w+\\s*:\\s*Array<String>\\s*\\)\\s*:\\s*Int")
        assertTrue(
            pattern.containsMatchIn(proxySource),
            "ByeDpiProxyContract должен объявлять `fun startProxy(args: Array<String>): Int`.",
        )
    }

    @Test
    fun `ByeDpiProxyContract объявляет stopProxy`() {
        val pattern = Regex("fun\\s+stopProxy\\s*\\(\\s*\\)\\s*:\\s*Int")
        assertTrue(
            pattern.containsMatchIn(proxySource),
            "ByeDpiProxyContract должен объявлять `fun stopProxy(): Int`.",
        )
    }

    @Test
    fun `ByeDpiProxyContract объявляет forceClose`() {
        val pattern = Regex("fun\\s+forceClose\\s*\\(\\s*\\)\\s*:\\s*Int")
        assertTrue(
            pattern.containsMatchIn(proxySource),
            "ByeDpiProxyContract должен объявлять `fun forceClose(): Int`.",
        )
    }

    @Test
    fun `jniStartProxy external fun имеет сигнатуру (Array String) Int`() {
        val pattern = Regex(
            "private\\s+external\\s+fun\\s+jniStartProxy\\s*\\(\\s*\\w+\\s*:\\s*Array<String>\\s*\\)\\s*:\\s*Int",
        )
        assertTrue(
            pattern.containsMatchIn(proxySource),
            "ByeDpiProxy должен объявлять `private external fun jniStartProxy(args: Array<String>): Int`. " +
                "Изменение сигнатуры = NoSuchMethodError в JNI_OnLoad.",
        )
    }

    @Test
    fun `jniStopProxy external fun имеет сигнатуру () Int`() {
        val pattern = Regex("private\\s+external\\s+fun\\s+jniStopProxy\\s*\\(\\s*\\)\\s*:\\s*Int")
        assertTrue(
            pattern.containsMatchIn(proxySource),
            "ByeDpiProxy должен объявлять `private external fun jniStopProxy(): Int`.",
        )
    }

    @Test
    fun `jniForceClose external fun имеет сигнатуру () Int`() {
        val pattern = Regex("private\\s+external\\s+fun\\s+jniForceClose\\s*\\(\\s*\\)\\s*:\\s*Int")
        assertTrue(
            pattern.containsMatchIn(proxySource),
            "ByeDpiProxy должен объявлять `private external fun jniForceClose(): Int`.",
        )
    }

    @Test
    fun `ByeDpiProxyContract объявляет emergencyReset`() {
        val pattern = Regex("fun\\s+emergencyReset\\s*\\(\\s*\\)\\s*:\\s*Int")
        assertTrue(
            pattern.containsMatchIn(proxySource),
            "ByeDpiProxyContract должен объявлять `fun emergencyReset(): Int` для hang recovery " +
                "после JNI_GUARD_BUSY от jniStartProxy.",
        )
    }

    @Test
    fun `jniEmergencyReset external fun имеет сигнатуру () Int`() {
        val pattern = Regex(
            "private\\s+external\\s+fun\\s+jniEmergencyReset\\s*\\(\\s*\\)\\s*:\\s*Int",
        )
        assertTrue(
            pattern.containsMatchIn(proxySource),
            "ByeDpiProxy должен объявлять `private external fun jniEmergencyReset(): Int`. " +
                "Изменение сигнатуры = NoSuchMethodError в JNI_OnLoad.",
        )
    }

    @Test
    fun `loadOnce загружает библиотеку с именем byedpi`() {
        assertTrue(
            proxySource.contains("System.loadLibrary(\"byedpi\")"),
            "loadOnce() должен вызывать System.loadLibrary(\"byedpi\") — " +
                "имя должно совпадать с libbyedpi.so в jniLibs/.",
        )
    }
}
