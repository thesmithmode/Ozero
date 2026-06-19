package ru.ozero.enginemasterdns

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
class MasterDnsClientServiceTest {

    @Test
    fun `start with live process transitions to Running`(@TempDir tmp: Path) = runTest {
        val service = makeService(tmp, FakeProcess(alive = true), StandardTestDispatcher(testScheduler))
        service.start(runtime())
        val state = service.state.first { it is MasterDnsClientState.Running || it is MasterDnsClientState.Error }
        assertTrue(state is MasterDnsClientState.Running) { "got=$state" }
        assertEquals(18000, (state as MasterDnsClientState.Running).port)
        service.stop()
    }

    @Test
    fun `start with early-exiting process transitions to Error`(@TempDir tmp: Path) = runTest {
        val service = makeService(
            tmp,
            FakeProcess(alive = false, exitOutput = "bad config"),
            StandardTestDispatcher(testScheduler),
        )
        service.start(runtime())
        val state = service.state.first { it is MasterDnsClientState.Running || it is MasterDnsClientState.Error }
        assertTrue(state is MasterDnsClientState.Error) { "got=$state" }
        assertEquals("masterdns exited: bad config", (state as MasterDnsClientState.Error).message)
        service.stop()
    }

    @Test
    fun `start with failed payload probe transitions to Error and kills process`(@TempDir tmp: Path) = runTest {
        val process = FakeProcess(alive = true)
        val workDir = File(tmp.toFile(), "masterdns")
        val service = MasterDnsClientService(
            workDirProvider = { workDir },
            wrapperFactory = {
                object : MasterDnsClientWrapperContract {
                    override val binary: File = File("/tmp/libmdnsvpn.so")
                    override fun startClient(
                        configPath: String,
                        resolversPath: String,
                        logPath: String?,
                    ): Process = process
                }
            },
            writer = MasterDnsConfigWriter(workDir),
            startupCheckMs = 1,
            readinessProbe = { throw IOException("no socks") },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        service.start(runtime())

        val state = service.state.first { it is MasterDnsClientState.Error }
        assertEquals("masterdns payload probe failed: no socks", (state as MasterDnsClientState.Error).message)
        assertTrue(process.destroyed)
        service.stop()
    }

    @Test
    fun `stop returns to Idle and kills process`(@TempDir tmp: Path) = runTest {
        val process = FakeProcess(alive = true)
        val service = makeService(tmp, process, StandardTestDispatcher(testScheduler))
        service.start(runtime())
        service.state.first { it is MasterDnsClientState.Running }
        service.stop()
        assertEquals(MasterDnsClientState.Idle, service.state.first())
        assertTrue(process.destroyed)
    }

    @Test
    fun `reader consumes stdout until truncation cap`(@TempDir tmp: Path) = runTest {
        val output = (0..205).joinToString("\n") { "line-$it" }
        val service = makeService(
            tmp,
            FakeProcess(alive = true, exitOutput = output),
            StandardTestDispatcher(testScheduler),
        )
        service.start(runtime())
        service.state.first { it is MasterDnsClientState.Running }
        runCurrent()
        val state = service.state.first()
        assertTrue(state is MasterDnsClientState.Running)
        service.stop()
    }

    @Test
    fun `watchdog reports unexpected process exit`(@TempDir tmp: Path) = runTest {
        val process = FakeProcess(alive = true)
        val service = makeService(tmp, process, StandardTestDispatcher(testScheduler))
        service.start(runtime())
        service.state.first { it is MasterDnsClientState.Running }
        process.alive = false
        advanceTimeBy(500)
        val state = service.state.first { it is MasterDnsClientState.Error }
        assertEquals("masterdns exited unexpectedly", (state as MasterDnsClientState.Error).message)
        service.stop()
    }

    @Test
    fun `stop during startup cancels launch and kills process`(@TempDir tmp: Path) = runTest {
        val process = FakeProcess(alive = true)
        val service = makeService(
            tmp,
            process,
            StandardTestDispatcher(testScheduler),
            startupCheckMs = 1_000,
        )
        service.start(runtime())
        runCurrent()
        service.stop()
        assertTrue(process.destroyed)
        assertEquals(MasterDnsClientState.Idle, service.state.first())
    }

    @Test
    fun `wrapper throws yields Error state`(@TempDir tmp: Path) = runTest {
        val wrapperFactory: () -> MasterDnsClientWrapperContract = {
            object : MasterDnsClientWrapperContract {
                override val binary: File = File("/tmp/libmdnsvpn.so")
                override fun startClient(
                    configPath: String,
                    resolversPath: String,
                    logPath: String?,
                ): Process =
                    throw java.io.IOException("binary missing")
            }
        }
        val workDir = File(tmp.toFile(), "masterdns")
        val service = MasterDnsClientService(
            workDirProvider = { workDir },
            wrapperFactory = wrapperFactory,
            writer = MasterDnsConfigWriter(workDir),
            startupCheckMs = 1,
            readinessProbe = {},
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        service.start(runtime())
        val state = service.state.first { it is MasterDnsClientState.Error }
        assertTrue(state is MasterDnsClientState.Error)
        assertTrue((state as MasterDnsClientState.Error).message.contains("binary missing"))
        service.stop()
    }

    @Test
    fun `stop before start stays idle`(@TempDir tmp: Path) = runTest {
        val service = makeService(tmp, FakeProcess(alive = true), StandardTestDispatcher(testScheduler))

        service.stop()

        assertEquals(MasterDnsClientState.Idle, service.state.first())
    }

    @Test
    fun `writer throwable without message yields unknown error`(@TempDir tmp: Path) = runTest {
        val workDir = File(tmp.toFile(), "masterdns")
        val service = MasterDnsClientService(
            workDirProvider = { workDir },
            wrapperFactory = {
                object : MasterDnsClientWrapperContract {
                    override val binary: File = File("/tmp/libmdnsvpn.so")
                    override fun startClient(
                        configPath: String,
                        resolversPath: String,
                        logPath: String?,
                    ): Process = FakeProcess(alive = true)
                }
            },
            writer = object : MasterDnsConfigWriter(workDir) {
                override fun write(runtime: MasterDnsRuntimeConfig): Files = throw UnknownMasterDnsWriterFailure()
            },
            startupCheckMs = 1,
            readinessProbe = {},
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        service.start(runtime())

        val state = service.state.first { it is MasterDnsClientState.Error }
        assertEquals("unknown error", (state as MasterDnsClientState.Error).message)
        service.stop()
    }

    @Test
    fun `successive starts cancel prior job`(@TempDir tmp: Path) = runTest {
        val firstProcess = FakeProcess(alive = true)
        val secondProcess = FakeProcess(alive = true)
        val processes = mutableListOf(firstProcess, secondProcess)
        val workDir = File(tmp.toFile(), "masterdns")
        val service = MasterDnsClientService(
            workDirProvider = { workDir },
            wrapperFactory = {
                object : MasterDnsClientWrapperContract {
                    override val binary: File = File("/tmp/libmdnsvpn.so")
                    override fun startClient(
                        configPath: String,
                        resolversPath: String,
                        logPath: String?,
                    ): Process =
                        processes.removeAt(0)
                }
            },
            writer = MasterDnsConfigWriter(workDir),
            startupCheckMs = 1,
            readinessProbe = {},
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        service.start(runtime())
        service.state.first { it is MasterDnsClientState.Running }
        service.start(runtime().copy(socksPort = 18100))
        val state = service.state.first {
            it is MasterDnsClientState.Running && (it as MasterDnsClientState.Running).port == 18100
        }
        assertTrue(state is MasterDnsClientState.Running)
        assertEquals(18100, (state as MasterDnsClientState.Running).port)
        assertTrue(firstProcess.destroyed)
        service.stop()
    }

    private fun runtime() = MasterDnsRuntimeConfig(
        configToml = "DOMAINS = [\"v.x\"]\n",
        resolvers = listOf("8.8.8.8"),
        socksPort = 18000,
    )

    private fun makeService(
        tmp: Path,
        process: FakeProcess,
        dispatcher: CoroutineDispatcher,
        startupCheckMs: Long = 1,
    ): MasterDnsClientService {
        val workDir = File(tmp.toFile(), "masterdns")
        return MasterDnsClientService(
            workDirProvider = { workDir },
            wrapperFactory = {
                object : MasterDnsClientWrapperContract {
                    override val binary: File = File("/tmp/libmdnsvpn.so")
                    override fun startClient(
                        configPath: String,
                        resolversPath: String,
                        logPath: String?,
                    ): Process = process
                }
            },
            writer = MasterDnsConfigWriter(workDir),
            startupCheckMs = startupCheckMs,
            readinessProbe = {},
            ioDispatcher = dispatcher,
        )
    }

    private class FakeProcess(
        var alive: Boolean,
        private val exitOutput: String = "",
    ) : Process() {
        var destroyed = false
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun getInputStream(): InputStream = ByteArrayInputStream(exitOutput.toByteArray())
        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun waitFor(): Int {
            destroyed = true
            return 0
        }
        override fun exitValue(): Int = 0
        override fun destroy() {
            destroyed = true
        }
        override fun destroyForcibly(): Process {
            destroyed = true
            return this
        }
        override fun isAlive(): Boolean = alive && !destroyed
    }

    private class UnknownMasterDnsWriterFailure : RuntimeException(null as String?)
}
