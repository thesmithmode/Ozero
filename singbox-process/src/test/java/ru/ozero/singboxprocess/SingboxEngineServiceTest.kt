package ru.ozero.singboxprocess

import org.junit.jupiter.api.Test
import java.io.File
import java.math.BigInteger
import java.security.Principal
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxEngineServiceTest {

    private val source = File(
        locateRepoRoot(),
        "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxEngineService.kt",
    ).readText()

    private val runtimeSource = File(
        locateRepoRoot(),
        "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxRuntime.kt",
    ).readText()

    @Test
    fun `stop waits for runtime shutdown before returning`() {
        val stopBlock = source.substringAfter("override fun stop(ownerId: Long)")
            .substringBefore("override fun stopAndWait")
        assertTrue(stopBlock.contains("stopAndWait(ownerId, DEFAULT_STOP_TIMEOUT_MS)"))

        val stopAndWaitBlock = source.substringAfter("private fun stopRuntimeAndWait")
            .substringBefore("override fun getStats()")
        assertTrue(stopAndWaitBlock.contains("withTimeoutOrNull"))
        assertTrue(stopAndWaitBlock.contains("SingboxRuntime.stop(ownerId)"))
        assertTrue(stopAndWaitBlock.contains("getOrDefault(false)"))
        assertTrue(stopAndWaitBlock.contains("Process.killProcess"))
        assertTrue(stopAndWaitBlock.contains("launchHardWatchdog"))
        assertTrue(source.contains("AtomicBoolean"))
    }

    @Test
    fun `remote stop calls share one serialized lifecycle gate`() {
        assertTrue(source.contains("private val stopLock = Any()"))
        assertTrue(source.contains("synchronized(stopLock)"))
        assertTrue(source.contains("stopRuntimeAndWait(ownerId, timeoutMs)"))
    }

    @Test
    fun `all native start entry points use hard process watchdog`() {
        val startWithConfig = source.substringAfter("override fun startWithConfig(")
            .substringBefore("override fun startWithConfigFile(")
        val startWithConfigFile = source.substringAfter("override fun startWithConfigFile(")
            .substringBefore("override fun startProxyMode(")
        val startProxyMode = source.substringAfter("override fun startProxyMode(")
            .substringBefore("override fun startProxyModeIfIdle(")
        val startProxyModeIfIdle = source.substringAfter("override fun startProxyModeIfIdle(")
            .substringBefore("override fun stop(ownerId: Long)")
        val watchdog = source.substringAfter("private fun <T> startRuntimeWithWatchdog")
            .substringBefore("private fun stopRuntimeAndWait")

        listOf(startWithConfig, startWithConfigFile, startProxyMode, startProxyModeIfIdle).forEach { block ->
            assertTrue(block.contains("startRuntimeWithWatchdog"))
        }
        assertTrue(watchdog.contains("launchHardWatchdog"))
        assertTrue(watchdog.contains("DEFAULT_START_TIMEOUT_MS"))
        assertTrue(watchdog.contains("compareAndSet(false, true)"))
        assertTrue(source.contains("Process.killProcess"))
    }

    @Test
    fun `runtime exposes Android trust anchors to libbox`() {
        val first = FakeCertificate(byteArrayOf(1, 2, 3))
        val duplicate = FakeCertificate(byteArrayOf(1, 2, 3))
        val second = FakeCertificate(byteArrayOf(4, 5, 6))
        val trustManagers = arrayOf<TrustManager>(FakeTrustManager(first, duplicate, second))

        val certificates = TrustAnchorPemReader { bytes -> bytes.joinToString("") }.read(trustManagers)

        assertEquals(
            listOf(
                "-----BEGIN CERTIFICATE-----\n123\n-----END CERTIFICATE-----",
                "-----BEGIN CERTIFICATE-----\n456\n-----END CERTIFICATE-----",
            ),
            certificates,
        )
    }

    @Test
    fun `stats do not fake active connections from runtime flag`() {
        val statsBlock = source.substringAfter("override fun getStats()")
            .substringBefore("override fun onCreate()")
        assertTrue(statsBlock.contains("SingboxStats(available = false)"))
        assertFalse(statsBlock.contains("activeConnections = if"))
        assertFalse(statsBlock.contains("SingboxRuntime.isRunning()) 1"))
    }

    @Test
    fun `destroy path uses acknowledged stop`() {
        val destroyBlock = source.substringAfter("override fun onDestroy()")
            .substringBefore("companion object")
        assertTrue(destroyBlock.contains("binder.stopRuntimeAndWait(null, DEFAULT_STOP_TIMEOUT_MS)"))
        assertFalse(destroyBlock.contains("serviceScope"))
    }

    @Test
    fun `Android network bridge uses SFA callback split and concrete libbox properties`() {
        val bridge = File(
            locateRepoRoot(),
            "singbox-process/src/main/java/ru/ozero/singboxprocess/DefaultInterfaceMonitor.kt",
        ).readText()

        assertTrue(bridge.contains("registerBestMatchingNetworkCallback"))
        assertTrue(bridge.contains("connectivity.requestNetwork(request, callback, Handler"))
        assertTrue(bridge.contains("registerDefaultNetworkCallback(callback, Handler"))
        assertTrue(bridge.contains("value.name ="))
        assertTrue(bridge.contains("value.index ="))
        assertTrue(bridge.contains("value.mtu ="))
        assertTrue(bridge.contains("value.dnsServer ="))
        assertTrue(bridge.contains("value.type ="))
        assertTrue(bridge.contains("value.flags ="))
        assertTrue(bridge.contains("NetworkCapabilities.TRANSPORT_VPN"))
        assertTrue(bridge.contains("isCurrent(listener, callback)"))
        assertFalse(bridge.contains("applySetter"))
    }

    @Test
    fun `runtime fails unprotected auto detect sockets`() {
        val runtime = File(
            locateRepoRoot(),
            "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxRuntime.kt",
        ).readText()

        assertTrue(runtime.contains("val protected = protector.protect(fd)"))
        assertTrue(runtime.contains("check(protected) { \"active VPN protect failed\" }"))
    }

    @Test
    fun `profile probe start checks idle state under runtime mutex`() {
        val guardedStart = runtimeSource.substringAfter("suspend fun startIfIdle")
            .substringBefore("private fun startLocked")

        assertTrue(guardedStart.contains("mutex.withLock"))
        assertTrue(guardedStart.contains("if (commandServer != null) return@withLock false"))
        assertTrue(guardedStart.contains("startLocked"))
    }

    @Test
    fun `runtime teardown is scoped to the owner that started it`() {
        val stopBlock = runtimeSource.substringAfter("suspend fun stop(ownerId: Long? = null)")
            .substringBefore("fun isRunning")

        assertTrue(runtimeSource.contains("private var activeOwnerId: Long? = null"))
        assertTrue(runtimeSource.contains("activeOwnerId = ownerId"))
        assertTrue(stopBlock.contains("activeOwnerId != ownerId"))
        assertTrue(stopBlock.contains("return@withLock"))
        assertTrue(stopBlock.contains("activeOwnerId = null"))
    }

    @Test
    fun `runtime retains gomobile callbacks until native service stops`() {
        val startBlock = runtimeSource.substringAfter("private fun startLocked")
            .substringBefore("suspend fun stop(ownerId: Long? = null)")
        val stopBlock = runtimeSource.substringAfter("suspend fun stop(ownerId: Long? = null)")
            .substringBefore("fun isRunning()")

        assertTrue(startBlock.contains("platformInterface = platform"))
        assertTrue(startBlock.contains("commandServerHandler = handler"))
        assertTrue(stopBlock.contains("releaseServerCallbacks()"))
    }

    @Test
    fun `runtime releases gomobile callbacks on restart and failed start`() {
        val startBlock = runtimeSource.substringAfter("suspend fun start(")
            .substringBefore("suspend fun stop(ownerId: Long? = null)")
        val releaseBlock = runtimeSource.substringAfter("private fun releaseServerCallbacks()")
            .substringBefore("private class OzeroCommandServerHandler")
        val createBlock = runtimeSource.substringAfter("private fun createCommandServer")
            .substringBefore("private class OzeroCommandServerHandler")

        assertTrue(startBlock.contains("cleanupFailedServerStart(server, ownerId, e)"))
        assertTrue(startBlock.contains("closeCommandServer(oldServer, closeService = true)"))
        assertTrue(createBlock.contains("releaseServerCallbacks()"))
        assertTrue(releaseBlock.contains("platformInterface = null"))
        assertTrue(releaseBlock.contains("commandServerHandler = null"))
    }

    @Test
    fun `failed startup retained for cleanup belongs to the attempted owner`() {
        val cleanupBlock = runtimeSource.substringAfter("private fun cleanupFailedServerStart")
            .substringBefore("private fun closeCommandServer")

        assertTrue(cleanupBlock.contains("ownerId: Long"))
        assertTrue(cleanupBlock.contains("commandServer = server"))
        assertTrue(cleanupBlock.contains("activeOwnerId = ownerId"))
    }

    @Test
    fun `runtime health requires live gomobile callbacks`() {
        val runningBlock = runtimeSource.substringAfter("fun isRunning(): Boolean")
            .substringBefore("private fun releaseServerCallbacks()")

        assertTrue(runningBlock.contains("commandServer != null"))
        assertTrue(runningBlock.contains("platformInterface != null"))
        assertTrue(runningBlock.contains("commandServerHandler != null"))
    }

    private class FakeTrustManager(
        private vararg val certificates: X509Certificate,
    ) : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf(*certificates)

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
    }

    private class FakeCertificate(private val bytes: ByteArray) : X509Certificate() {
        override fun getEncoded(): ByteArray = bytes.copyOf()
        override fun verify(key: PublicKey) {}
        override fun verify(key: PublicKey, sigProvider: String) {}
        override fun toString(): String = bytes.contentToString()
        override fun getPublicKey(): PublicKey? = null
        override fun checkValidity() {}
        override fun checkValidity(date: Date) {}
        override fun getVersion(): Int = 3
        override fun getSerialNumber(): BigInteger = BigInteger.ONE
        override fun getIssuerDN(): Principal? = null
        override fun getSubjectDN(): Principal? = null
        override fun getNotBefore(): Date = Date(0)
        override fun getNotAfter(): Date = Date(0)
        override fun getTBSCertificate(): ByteArray = bytes.copyOf()
        override fun getSignature(): ByteArray = byteArrayOf()
        override fun getSigAlgName(): String = ""
        override fun getSigAlgOID(): String = ""
        override fun getSigAlgParams(): ByteArray? = null
        override fun getIssuerUniqueID(): BooleanArray? = null
        override fun getSubjectUniqueID(): BooleanArray? = null
        override fun getKeyUsage(): BooleanArray? = null
        override fun getBasicConstraints(): Int = -1
        override fun getCriticalExtensionOIDs(): Set<String>? = null
        override fun getNonCriticalExtensionOIDs(): Set<String>? = null
        override fun getExtensionValue(oid: String): ByteArray? = null
        override fun hasUnsupportedCriticalExtension(): Boolean = false
    }

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: dir
        }
        return File(System.getProperty("user.dir") ?: ".").absoluteFile
    }
}
