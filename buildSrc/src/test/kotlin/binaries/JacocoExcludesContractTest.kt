package binaries

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class JacocoExcludesContractTest {

    private val jacocoScript by lazy {
        val file = File("src/main/kotlin/ozero.jacoco.gradle.kts")
        assertThat(file.exists()).isTrue()
        file.readText()
    }

    @Test
    fun `coverage excludes do not hide broad testable production layers`() {
        listOf(
            "\"**/*Service*.*\"",
            "\"**/*Runtime*.*\"",
            "\"**/*Bridge*.*\"",
            "\"**/*Proxy*.*\"",
        ).forEach { mask ->
            assertThat(jacocoScript)
                .withFailMessage("$mask hides testable production code from coverage.")
                .doesNotContain(mask)
        }
    }
}
