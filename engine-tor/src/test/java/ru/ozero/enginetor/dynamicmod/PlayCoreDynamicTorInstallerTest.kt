package ru.ozero.enginetor.dynamicmod

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlayCoreDynamicTorInstallerTest {

    private val module = "dynamic_tor"
    private val abi = "arm64-v8a"
    private val libDir = File("/fake/lib")

    private fun installer(
        client: FakeSplitInstallClient,
        verifier: FakeTorBinaryVerifier = FakeTorBinaryVerifier(),
    ) = PlayCoreDynamicTorInstaller(
        client = client,
        verifier = verifier,
        nativeLibDirProvider = { libDir },
        currentAbi = { abi },
        moduleName = module,
    )

    @Test
    fun `AlreadyInstalled when module present and verifier OK`() = runTest {
        val client = FakeSplitInstallClient(installed = setOf(module))
        val v = FakeTorBinaryVerifier(VerifyResult.Ok)

        val result = installer(client, v).ensureInstalled()

        assertEquals(InstallResult.AlreadyInstalled, result)
        assertEquals(0, client.requestCount)
        assertEquals(0, client.uninstallCount, "uninstall не вызывается на Ok")
        assertEquals(1, v.verifyCount, "AlreadyInstalled путь тоже валидируется")
    }

    @Test
    fun `AlreadyInstalled but Corrupted → deferredUninstall and Failed`() = runTest {
        val client = FakeSplitInstallClient(installed = setOf(module))
        val v = FakeTorBinaryVerifier(
            VerifyResult.Corrupted(
                fileName = "libtor-arm64-v8a.so",
                expected = "39e1e4b1cd15c0436c57c934f0de17f3a0aa7ba88cfa4b2e9a5084c194e2ff85",
                actual = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
            ),
        )

        val result = installer(client, v).ensureInstalled()

        val failed = assertIs<InstallResult.Failed>(result)
        assertTrue(failed.reason.contains("checksum mismatch"), failed.reason)
        assertTrue(failed.reason.contains("libtor-arm64-v8a.so"), failed.reason)
        assertEquals(1, client.uninstallCount)
        assertEquals(listOf(module), client.uninstalledModules)
    }

    @Test
    fun `Installed flow + verifier Ok → Installed`() = runTest {
        val client = FakeSplitInstallClient(
            flow = flowOf(
                InstallResult.Installing(percent = 10),
                InstallResult.Installed,
            ),
        )
        val v = FakeTorBinaryVerifier(VerifyResult.Ok)

        val result = installer(client, v).ensureInstalled()

        assertEquals(InstallResult.Installed, result)
        assertEquals(0, client.uninstallCount)
    }

    @Test
    fun `Installed flow + verifier Corrupted → deferredUninstall and Failed`() = runTest {
        val client = FakeSplitInstallClient(flow = flowOf(InstallResult.Installed))
        val v = FakeTorBinaryVerifier(
            VerifyResult.Corrupted(
                fileName = "libiptproxy-arm64-v8a.so",
                expected = "8fa8040b8a179197c89bf7df7758cc5e71e773a858a51f4de152df3b6b4371fd",
                actual = "0000000000000000000000000000000000000000000000000000000000000000",
            ),
        )

        val result = installer(client, v).ensureInstalled()

        val failed = assertIs<InstallResult.Failed>(result)
        assertTrue(failed.reason.contains("checksum mismatch"), failed.reason)
        assertTrue(failed.reason.contains("libiptproxy-arm64-v8a.so"), failed.reason)
        assertEquals(1, client.uninstallCount)
    }

    @Test
    fun `Installed flow + verifier Missing → deferredUninstall and Failed missing`() = runTest {
        val client = FakeSplitInstallClient(flow = flowOf(InstallResult.Installed))
        val v = FakeTorBinaryVerifier(VerifyResult.Missing("libtor-arm64-v8a.so"))

        val result = installer(client, v).ensureInstalled()

        val failed = assertIs<InstallResult.Failed>(result)
        assertTrue(failed.reason.contains("missing binary"), failed.reason)
        assertTrue(failed.reason.contains("libtor-arm64-v8a.so"), failed.reason)
        assertEquals(1, client.uninstallCount)
    }

    @Test
    fun `Failed flow → propagated, verifier not called, no uninstall`() = runTest {
        val client = FakeSplitInstallClient(
            flow = flowOf(
                InstallResult.Installing(percent = 30),
                InstallResult.Failed(reason = "code=-100"),
            ),
        )
        val v = FakeTorBinaryVerifier(VerifyResult.Ok)

        val result = installer(client, v).ensureInstalled()

        val failed = assertIs<InstallResult.Failed>(result)
        assertEquals("code=-100", failed.reason)
        assertEquals(0, v.verifyCount)
        assertEquals(0, client.uninstallCount)
    }

    @Test
    fun `synthetic Failed when flow emits only Installing`() = runTest {
        val client = FakeSplitInstallClient(flow = flowOf(InstallResult.Installing(percent = 50)))
        val v = FakeTorBinaryVerifier(VerifyResult.Ok)

        val result = installer(client, v).ensureInstalled()

        val failed = assertIs<InstallResult.Failed>(result)
        assertEquals("no terminal emit", failed.reason)
        assertEquals(0, v.verifyCount)
        assertEquals(0, client.uninstallCount)
    }

    private class FakeSplitInstallClient(
        installed: Set<String> = emptySet(),
        private val flow: Flow<InstallResult> = flowOf(),
    ) : SplitInstallClient {
        override val installedModules: Set<String> = installed
        var requestCount: Int = 0
            private set
        var uninstallCount: Int = 0
            private set
        val uninstalledModules = mutableListOf<String>()
        override fun requestInstall(moduleName: String): Flow<InstallResult> {
            requestCount++
            return flow
        }
        override suspend fun deferredUninstall(moduleName: String) {
            uninstallCount++
            uninstalledModules.add(moduleName)
        }
    }

    private class FakeTorBinaryVerifier(
        private val result: VerifyResult = VerifyResult.Ok,
    ) : TorBinaryVerifier {
        var verifyCount: Int = 0
            private set
        override suspend fun verify(abi: String, libDir: File): VerifyResult {
            verifyCount++
            return result
        }
    }
}
