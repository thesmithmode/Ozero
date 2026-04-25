package ru.ozero.enginetor.dynamicmod

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlayCoreDynamicTorInstallerTest {

    private val module = "dynamic_tor"

    @Test
    fun `returns AlreadyInstalled when module present in client snapshot`() = runTest {
        val client = FakeSplitInstallClient(installed = setOf(module))
        val installer = PlayCoreDynamicTorInstaller(client, moduleName = module)

        val result = installer.ensureInstalled()

        assertEquals(InstallResult.AlreadyInstalled, result)
        assertEquals(0, client.requestCount, "не должны звать requestInstall если модуль уже есть")
    }

    @Test
    fun `returns Installed when flow ends with Installed terminal`() = runTest {
        val client = FakeSplitInstallClient(
            installed = emptySet(),
            flow = flowOf(
                InstallResult.Installing(percent = 10),
                InstallResult.Installing(percent = 90),
                InstallResult.Installed,
            ),
        )
        val installer = PlayCoreDynamicTorInstaller(client, moduleName = module)

        val result = installer.ensureInstalled()

        assertEquals(InstallResult.Installed, result)
    }

    @Test
    fun `returns Failed when flow ends with Failed and ignores prior Installing emits`() = runTest {
        val client = FakeSplitInstallClient(
            installed = emptySet(),
            flow = flowOf(
                InstallResult.Installing(percent = 30),
                InstallResult.Failed(reason = "code=-100"),
            ),
        )
        val installer = PlayCoreDynamicTorInstaller(client, moduleName = module)

        val result = installer.ensureInstalled()

        val failed = assertIs<InstallResult.Failed>(result)
        assertEquals("code=-100", failed.reason)
    }

    @Test
    fun `returns synthetic Failed when flow emits only Installing (no terminal)`() = runTest {
        val client = FakeSplitInstallClient(
            installed = emptySet(),
            flow = flowOf(InstallResult.Installing(percent = 50)),
        )
        val installer = PlayCoreDynamicTorInstaller(client, moduleName = module)

        val result = installer.ensureInstalled()

        val failed = assertIs<InstallResult.Failed>(result)
        assertEquals("no terminal emit", failed.reason)
    }

    private class FakeSplitInstallClient(
        installed: Set<String>,
        private val flow: Flow<InstallResult> = flowOf(),
    ) : SplitInstallClient {
        override val installedModules: Set<String> = installed
        var requestCount: Int = 0
            private set
        override fun requestInstall(moduleName: String): Flow<InstallResult> {
            requestCount++
            return flow
        }
    }
}
