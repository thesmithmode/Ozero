package binaries

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PluginIntegrationTest {
    @TempDir
    lateinit var tmp: Path

    @Test
    fun `plugin registers downloadBinaries task and extension`() {
        val project = ProjectBuilder.builder().withProjectDir(tmp.toFile()).build()
        project.plugins.apply("ozero.binaries")
        val task = project.tasks.findByName("downloadBinaries")
        assertThat(task).isNotNull
        val ext = project.extensions.findByName("ozeroBinaries")
        assertThat(ext).isNotNull
    }

    @Test
    fun `extension DSL records artifact names`() {
        val project = ProjectBuilder.builder().withProjectDir(tmp.toFile()).build()
        project.plugins.apply("ozero.binaries")
        val ext = project.extensions.getByType(OzeroBinariesExtension::class.java)
        ext.artifact("libbyedpi-arm64-v8a.so")
        ext.artifact("libbyedpi-armeabi-v7a.so")
        assertThat(ext.names()).containsExactly(
            "libbyedpi-arm64-v8a.so",
            "libbyedpi-armeabi-v7a.so",
        )
    }
}
