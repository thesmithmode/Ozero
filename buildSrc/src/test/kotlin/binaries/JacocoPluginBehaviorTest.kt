package binaries

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class JacocoPluginBehaviorTest {
    @TempDir
    lateinit var tmp: Path

    @Test
    fun `jacoco plugin configures jvm projects`() {
        val project = ProjectBuilder.builder().withProjectDir(tmp.toFile()).build()
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        project.plugins.apply("ozero.jacoco")

        assertThat(project.tasks.findByName("jacocoTestReport")).isNotNull
        assertThat(project.tasks.findByName("jacocoTestCoverageVerification")).isNotNull
    }

    @Test
    fun `jacoco plugin configures android projects`() {
        val project = ProjectBuilder.builder().withProjectDir(tmp.resolve("android").toFile()).build()
        project.plugins.apply("com.android.library")
        project.plugins.apply("org.jetbrains.kotlin.android")
        project.plugins.apply("ozero.jacoco")

        assertThat(project.tasks.findByName("jacocoTestReport")).isNotNull
        assertThat(project.tasks.findByName("jacocoTestCoverageVerification")).isNotNull
    }
}
