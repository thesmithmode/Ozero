package ru.ozero.enginebyedpi

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class PrebuiltSoSymbolContractTest {

    private val moduleRoot = File(System.getProperty("user.dir") ?: ".")

    private val requiredSymbols = listOf(
        "Java_ru_ozero_enginebyedpi_ByeDpiProxy_jniStartProxy",
        "Java_ru_ozero_enginebyedpi_ByeDpiProxy_jniStopProxy",
        "Java_ru_ozero_enginebyedpi_ByeDpiProxy_jniForceClose",
        "Java_ru_ozero_enginebyedpi_ByeDpiProxy_jniEmergencyReset",
    )

    @Test
    fun `libbyedpi arm64-v8a содержит все JNI символы включая jniEmergencyReset`() {
        checkSoSymbols("arm64-v8a")
    }

    @Test
    fun `libbyedpi armeabi-v7a содержит все JNI символы включая jniEmergencyReset`() {
        checkSoSymbols("armeabi-v7a")
    }

    @Test
    fun `libbyedpi x86_64 содержит все JNI символы включая jniEmergencyReset`() {
        checkSoSymbols("x86_64")
    }

    private fun checkSoSymbols(abi: String) {
        val soFile = File(moduleRoot, "src/main/jniLibs/$abi/libbyedpi.so")
        assertTrue(soFile.exists(), "libbyedpi.so для $abi не найден: ${soFile.absolutePath}")

        val bytes = soFile.readBytes()
        val content = String(bytes, Charsets.ISO_8859_1)

        for (symbol in requiredSymbols) {
            assertTrue(
                symbol in content,
                "libbyedpi-$abi.so не экспортирует '$symbol'. " +
                    "Prebuilt .so устарел относительно native-lib.c — запустить binaries.yml " +
                    "workflow (workflow_dispatch, artifact=byedpi), скопировать новые .so " +
                    "в jniLibs/, обновить build-tools/binaries.lock.yaml.",
            )
        }
    }
}
