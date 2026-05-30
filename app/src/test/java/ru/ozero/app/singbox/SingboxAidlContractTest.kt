package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SingboxAidlContractTest {

    private val aidlDir get() = File(locateRepoRoot(), "engine-singbox/src/main/aidl/ru/ozero/enginesingbox")

    @Test
    fun `should ISingboxEngineProcess define startWithConfig`() {
        val content = aidlFile("ISingboxEngineProcess.aidl")
        assertTrue(content.contains("startWithConfig"), "ISingboxEngineProcess must have startWithConfig method")
        assertTrue(content.contains("ParcelFileDescriptor"), "startWithConfig must accept ParcelFileDescriptor tunFd")
        assertTrue(content.contains("ISingboxProtector"), "startWithConfig must accept ISingboxProtector")
    }

    @Test
    fun `should ISingboxEngineProcess define startWithConfigFile`() {
        val content = aidlFile("ISingboxEngineProcess.aidl")
        assertTrue(content.contains("startWithConfigFile"), "ISingboxEngineProcess must have startWithConfigFile")
        assertTrue(
            content.contains("configFilePath") || content.contains("String"),
            "startWithConfigFile must accept config file path",
        )
    }

    @Test
    fun `should ISingboxEngineProcess define stop`() {
        val content = aidlFile("ISingboxEngineProcess.aidl")
        assertTrue(content.contains("void stop()"), "ISingboxEngineProcess must have stop() method")
    }

    @Test
    fun `should ISingboxEngineProcess define acknowledged stop and runtime health`() {
        val content = aidlFile("ISingboxEngineProcess.aidl")
        assertTrue(
            content.contains("boolean stopAndWait(long timeoutMs)"),
            "ISingboxEngineProcess must expose stopAndWait(timeoutMs) so caller waits before unbind or restart",
        )
        assertTrue(
            content.contains("boolean runtimeRunning()"),
            "ISingboxEngineProcess must expose runtimeRunning() for startup health without faking stats",
        )
    }

    @Test
    fun `should ISingboxEngineProcess define getStats returning SingboxStats`() {
        val content = aidlFile("ISingboxEngineProcess.aidl")
        assertTrue(
            content.contains("SingboxStats getStats"),
            "ISingboxEngineProcess must have getStats() returning SingboxStats",
        )
    }

    @Test
    fun `should ISingboxEngineProcess define registerStatusCallback`() {
        val content = aidlFile("ISingboxEngineProcess.aidl")
        assertTrue(
            content.contains("registerStatusCallback"),
            "ISingboxEngineProcess must have registerStatusCallback",
        )
    }

    @Test
    fun `should ISingboxProtector be defined with protect method`() {
        val content = aidlFile("ISingboxProtector.aidl")
        assertTrue(content.contains("boolean protect"), "ISingboxProtector must have boolean protect(int fd)")
        assertTrue(content.contains("int"), "protect must accept int fd")
    }

    @Test
    fun `should ISingboxStatusCallback be defined`() {
        val content = aidlFile("ISingboxStatusCallback.aidl")
        assertTrue(
            content.contains("onStatusChanged") || content.contains("void "),
            "ISingboxStatusCallback must have at least one callback method",
        )
    }

    @Test
    fun `should SingboxStats aidl parcelable be declared`() {
        val content = aidlFile("SingboxStats.aidl")
        assertTrue(
            content.contains("parcelable SingboxStats"),
            "SingboxStats.aidl must declare parcelable SingboxStats",
        )
    }

    private fun aidlFile(name: String): String {
        val f = File(aidlDir, name)
        assertTrue(f.isFile, "AIDL file $name must exist in engine-singbox/src/main/aidl/")
        return f.readText()
    }

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("repo root not found")
    }
}
