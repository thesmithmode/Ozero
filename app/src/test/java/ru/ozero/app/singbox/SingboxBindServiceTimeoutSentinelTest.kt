package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SingboxBindServiceTimeoutSentinelTest {

    @Test
    fun `should SingboxEngine call unbindService on bind timeout`() {
        val root = locateRepoRoot()
        val engine = File(
            root,
            "engine-singbox/src/main/java/ru/ozero/enginesingbox/SingboxEngine.kt",
        )
        assertTrue(engine.isFile, "SingboxEngine.kt must exist")
        val content = engine.readText()

        val bindOrFail = content.substringAfter("fun bindOrFail(")
            .substringBefore("private fun close()")

        assertTrue(
            bindOrFail.contains("unbindService"),
            "bindOrFail must call unbindService on timeout — otherwise next start() gets IllegalStateException from double-bind",
        )
        assertTrue(
            bindOrFail.contains("await(") || bindOrFail.contains("CountDownLatch"),
            "bindOrFail must use CountDownLatch or await for bind timeout detection",
        )
        assertTrue(
            bindOrFail.contains("StartResult.Failure"),
            "bindOrFail must return StartResult.Failure on timeout — not throw, not hang",
        )
    }

    @Test
    fun `should SingboxEngine use explicit ComponentName not implicit intent`() {
        val root = locateRepoRoot()
        val content = File(
            root,
            "engine-singbox/src/main/java/ru/ozero/enginesingbox/SingboxEngine.kt",
        ).readText()

        assertTrue(
            content.contains("ComponentName"),
            "SingboxEngine must use explicit ComponentName for bindService — " +
                "implicit intents to services fail on Android 5+ (SecurityException)",
        )
        assertTrue(
            content.contains("SingboxEngineService"),
            "ComponentName must reference SingboxEngineService class",
        )
    }

    @Test
    fun `should SingboxEngine handle DeathRecipient for binder death`() {
        val root = locateRepoRoot()
        val content = File(
            root,
            "engine-singbox/src/main/java/ru/ozero/enginesingbox/SingboxEngine.kt",
        ).readText()

        assertTrue(
            content.contains("DeathRecipient") || content.contains("linkToDeath"),
            "SingboxEngine must handle binder death via DeathRecipient — " +
                ":engine_singbox crash must not leave dangling binder reference in main process",
        )
    }

    @Test
    fun `should SingboxEngine handle onBindingDied callback`() {
        val root = locateRepoRoot()
        val content = File(
            root,
            "engine-singbox/src/main/java/ru/ozero/enginesingbox/SingboxEngine.kt",
        ).readText()

        assertTrue(
            content.contains("onBindingDied"),
            "SingboxEngine must implement onBindingDied — OS calls this when service process dies, " +
                "without it the ServiceConnection leaks and next bind() hangs",
        )
    }

    @Test
    fun `should singbox-core build gradle use implementation not compileOnly for go-stubs`() {
        val root = locateRepoRoot()
        val gradle = File(root, "singbox-core/build.gradle.kts")
        assertTrue(gradle.isFile, "singbox-core/build.gradle.kts must exist")
        val content = gradle.readText()
        val stubsDecl = content.lines().firstOrNull { it.contains("libs-stubs") }
        assertTrue(
            stubsDecl != null && stubsDecl.trimStart().startsWith("implementation("),
            "singbox-core/build.gradle.kts must use implementation(libs-stubs) not compileOnly — " +
                "compileOnly omits go.Seq classes from DEX; libbox.so calls GetMethodID on URnetwork's " +
                "incompatible go.Seq → JniAbort SIGSEGV. Actual decl: $stubsDecl",
        )
    }

    @Test
    fun `should SingboxEngine call onProcessDied from DeathRecipient and service callbacks`() {
        val root = locateRepoRoot()
        val content = File(
            root,
            "engine-singbox/src/main/java/ru/ozero/enginesingbox/SingboxEngine.kt",
        ).readText()

        assertTrue(
            content.contains("onProcessDied"),
            "SingboxEngine must have onProcessDied callback parameter — " +
                "missing callback means binder death never clears tunnel state (stuck Amber button)",
        )
        val deathBlock = content.substringAfter("IBinder.DeathRecipient {")
            .substringBefore("deathRecipient = recipient")
        assertTrue(
            deathBlock.contains("onProcessDied"),
            "DeathRecipient lambda must call onProcessDied() — " +
                "otherwise :engine_singbox crash leaves TunnelController in Connecting/Switching state forever",
        )
        assertTrue(
            content.substringAfter("onServiceDisconnected").substringBefore("onBindingDied")
                .contains("onProcessDied"),
            "onServiceDisconnected must call onProcessDied() — " +
                "system unbind must also clear tunnel state",
        )
        assertTrue(
            content.substringAfter("onBindingDied").substringBefore("private fun bindOrFail")
                .contains("onProcessDied"),
            "onBindingDied must call onProcessDied() — " +
                "binding death must also clear tunnel state",
        )
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
