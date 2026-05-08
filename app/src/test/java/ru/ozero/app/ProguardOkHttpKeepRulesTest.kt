package ru.ozero.app

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class ProguardOkHttpKeepRulesTest {

    private val rules by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "proguard-rules.pro")
        assertTrue(f.exists(), "proguard-rules.pro не найден: $f")
        f.readText()
    }

    @Test
    fun `okhttp PlatformRegistry keep rule присутствует`() {
        assertTrue(
            rules.contains("okhttp3.internal.platform.**"),
            "Без keep rule R8 удаляет PlatformRegistry → ClassNotFoundException на runtime " +
                "при первом WARP старте (наблюдалось v0.0.2). okhttp используется в engine-warp HttpClient.",
        )
        assertTrue(rules.contains("-keep class okhttp3."))
        assertTrue(rules.contains("-keep class okio."))
    }

    @Test
    fun `amnezia awg keep rule присутствует`() {
        assertTrue(rules.contains("org.amnezia.awg.**"))
    }
}
