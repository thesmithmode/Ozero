package ru.ozero.singboxcore

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibsingboxgojniContractTest {

    @Test
    fun `initial state is not loaded until explicit loadOnce`() {
        assertFalse(Libsingboxgojni.libraryLoaded)
        assertNull(Libsingboxgojni.loadError)
    }

    @Test
    fun `loadOnce keeps singbox native library isolated`() {
        val content = File(repoRoot(), "singbox-core/src/main/java/ru/ozero/singboxcore/Libsingboxgojni.kt")
            .readText()

        assertTrue(content.contains("""System.loadLibrary("box")"""))
        assertFalse(content.contains("""System.loadLibrary("gojni")"""))
        assertFalse(content.contains("""System.loadLibrary("am-go")"""))
    }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).canonicalFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return requireNotNull(dir)
    }
}
