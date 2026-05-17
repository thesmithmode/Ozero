package binaries

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class DownloadBinaryTaskTest {
    private lateinit var server: MockWebServer

    @TempDir
    lateinit var tmp: Path

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun writeLock(soBytes: ByteArray, urlPath: String): Path {
        val sha = sha256(soBytes)
        val lock = tmp.resolve("binaries.lock.yaml")
        Files.writeString(
            lock,
            """
            tag: binaries-test
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libbyedpi-arm64-v8a.so
                engine: byedpi
                abi: arm64-v8a
                destination: jniLibs
                download_url: ${server.url(urlPath)}
                sha256: $sha
                size_bytes: ${soBytes.size}
                source_repo: https://github.com/hufrea/byedpi
                source_commit: ${"a".repeat(40)}
            """.trimIndent(),
        )
        return lock
    }

    @Test
    fun `task downloads declared artifact to jniLibs destination`() {
        val data = "fake-so".toByteArray()
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(data)))
        val lockPath = writeLock(data, "/lib.so")

        val project = ProjectBuilder.builder().withProjectDir(tmp.toFile()).build()
        val testModuleDir = tmp.resolve("module")
        Files.createDirectories(testModuleDir)

        val task = project.tasks.register(
            "downloadBinaries",
            DownloadBinaryTask::class.java,
            Action<DownloadBinaryTask> {
                lockFile.set(lockPath.toFile())
                cacheDir.set(tmp.resolve("cache").toFile())
                moduleDir.set(testModuleDir.toFile())
                requestedArtifacts.set(listOf("libbyedpi-arm64-v8a.so"))
                retryDelaysMs.set(listOf(0L, 0L, 0L))
            },
        ).get()

        task.run()

        val expected = testModuleDir.resolve("src/main/jniLibs/arm64-v8a/libbyedpi-arm64-v8a.so")
        assertThat(Files.exists(expected)).isTrue()
        assertThat(Files.readAllBytes(expected)).isEqualTo(data)
    }

    @Test
    fun `task fails with canonical message when artifact missing from lock`() {
        val data = "fake".toByteArray()
        val lockPath = writeLock(data, "/x.so")

        val project = ProjectBuilder.builder().withProjectDir(tmp.toFile()).build()
        val testModuleDir = tmp.resolve("module")
        Files.createDirectories(testModuleDir)

        val task = project.tasks.register(
            "downloadBinaries",
            DownloadBinaryTask::class.java,
            Action<DownloadBinaryTask> {
                lockFile.set(lockPath.toFile())
                cacheDir.set(tmp.resolve("cache").toFile())
                moduleDir.set(testModuleDir.toFile())
                requestedArtifacts.set(listOf("not-in-lock.so"))
                retryDelaysMs.set(listOf(0L, 0L, 0L))
            },
        ).get()

        assertThatThrownBy { task.run() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("not-in-lock.so")
            .hasMessageContaining("not declared in")
    }

    @Test
    fun `task is idempotent on second invocation due to cache`() {
        val data = "fake-so".toByteArray()
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(data)))
        val lockPath = writeLock(data, "/lib.so")

        val project = ProjectBuilder.builder().withProjectDir(tmp.toFile()).build()
        val testModuleDir = tmp.resolve("module")
        Files.createDirectories(testModuleDir)
        val task = project.tasks.register(
            "downloadBinaries",
            DownloadBinaryTask::class.java,
            Action<DownloadBinaryTask> {
                lockFile.set(lockPath.toFile())
                cacheDir.set(tmp.resolve("cache").toFile())
                moduleDir.set(testModuleDir.toFile())
                requestedArtifacts.set(listOf("libbyedpi-arm64-v8a.so"))
                retryDelaysMs.set(listOf(0L, 0L, 0L))
            },
        ).get()

        task.run()
        task.run()
        val expected = testModuleDir.resolve("src/main/jniLibs/arm64-v8a/libbyedpi-arm64-v8a.so")
        assertThat(Files.exists(expected)).isTrue()
        assertThat(server.requestCount).isEqualTo(1)
    }
}
