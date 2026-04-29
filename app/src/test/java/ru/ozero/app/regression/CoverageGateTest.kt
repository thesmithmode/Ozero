package ru.ozero.app.regression

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class CoverageGateTest {

    @Test
    fun `jacoco verification gate стоит на 0_90 line+branch`() {
        val src = read("buildSrc/src/main/kotlin/ozero.jacoco.gradle.kts")
        val lineRule = Regex(
            "counter\\s*=\\s*\"LINE\".*?minimum\\s*=\\s*BigDecimal\\(\"0\\.9[0-9]\"\\)",
            RegexOption.DOT_MATCHES_ALL,
        )
        val branchRule = Regex(
            "counter\\s*=\\s*\"BRANCH\".*?minimum\\s*=\\s*BigDecimal\\(\"0\\.9[0-9]\"\\)",
            RegexOption.DOT_MATCHES_ALL,
        )
        assertTrue(
            lineRule.containsMatchIn(src),
            "Jacoco LINE coverage gate должен быть >=0.90 — иначе тесты можно отключать без последствий",
        )
        assertTrue(
            branchRule.containsMatchIn(src),
            "Jacoco BRANCH coverage gate должен быть >=0.90 — иначе непокрытые ветви проходят CI",
        )
    }

    @Test
    fun `CI вызывает jacocoTestCoverageVerification на dev`() {
        val ci = read(".github/workflows/ci.yml")
        assertTrue(
            ci.contains("jacocoTestCoverageVerification"),
            "CI workflow обязан вызывать jacocoTestCoverageVerification — иначе gate не активен",
        )
        val jacocoSection = ci.substringAfter("test-and-coverage")
        assertTrue(
            jacocoSection.contains("dev"),
            "Coverage-job должен быть привязан к ветке dev (не только к main)",
        )
    }

    @Test
    fun `jacoco таска зависит от testDebugUnitTest для свежих exec-данных`() {
        val src = read("buildSrc/src/main/kotlin/ozero.jacoco.gradle.kts")
        assertTrue(
            src.contains("dependsOn(\"testDebugUnitTest\")"),
            "jacocoTestReport обязан зависеть от testDebugUnitTest — иначе gate работает на старом exec",
        )
    }

    private fun read(rel: String): String {
        var d: File? = File(".").canonicalFile
        while (d != null && !File(d, "settings.gradle.kts").exists()) {
            d = d.parentFile
        }
        val root = d ?: error("settings.gradle.kts не найден")
        val f = File(root, rel)
        check(f.exists()) { "missing: ${f.absolutePath}" }
        return f.readText()
    }
}
